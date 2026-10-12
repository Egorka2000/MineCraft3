package io.github.egorka2000.tf2mc.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.egorka2000.tf2mc.physics.MoveParams;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Цикл подшагов с подставным игроком: проверяет то, что раньше ловил только клиентский игровой тест
 * (замечание аудита О1-А). Подставной игрок двигается как точка: пол на высоте 0, по желанию стена
 * и потолок; при столкновении, как и игра, обнуляет составляющую скорости.
 */
class SubstepLoopTest {
	private static final double SCALE = 45.0;
	private static final int SUBSTEPS = 3;
	private static final double EPSILON = 1e-9;
	private static final MoveParams PARAMS = MoveParams.tf2Defaults(MoveParams.BASE_SPEED);

	/** Полное нажатие клавиши в том виде, в каком его передаёт игра: уже умноженное на 0,98. */
	private static final double FULL_INPUT = SubstepLoop.VANILLA_INPUT_SCALE;

	@Test
	void standingPlayerIsPressedIntoGround() {
		FakeBody body = FakeBody.onFloor();

		SubstepLoop.tick(body, 0.0, 0.0, false, PARAMS, SCALE, SUBSTEPS, false);

		// Каждый подшаг игрок смещается вниз на долю прижатия, иначе игра не увидит опоры.
		assertEquals(SUBSTEPS, body.moves.size());

		for (double[] move : body.moves) {
			assertEquals(-SubstepLoop.GROUND_PROBE / SUBSTEPS, move[1], EPSILON);
		}

		assertTrue(body.onGround());
		assertEquals(0.0, body.y, EPSILON);
		// Скорость прижатия не остаётся в игроке после тика.
		assertEquals(0.0, body.velocityY(), EPSILON);
	}

	@Test
	void fullInputReachesMaxSpeedDespiteVanillaScale() {
		FakeBody body = FakeBody.onFloor();

		for (int tick = 0; tick < 40; tick++) {
			SubstepLoop.tick(body, 0.0, FULL_INPUT, false, PARAMS, SCALE, SUBSTEPS, false);
		}

		// Без поправки на 0,98 скорость остановилась бы на 294 u/s.
		assertEquals(MoveParams.BASE_SPEED, unitsPerSecond(body.velocityZ()), 1e-6);
		assertEquals(0.0, body.velocityX(), EPSILON);
	}

	@Test
	void inputDirectionFollowsYaw() {
		FakeBody body = FakeBody.onFloor();
		body.yaw = 90.0;

		for (int tick = 0; tick < 40; tick++) {
			SubstepLoop.tick(body, 0.0, FULL_INPUT, false, PARAMS, SCALE, SUBSTEPS, false);
		}

		// Поворот на 90 градусов — взгляд вдоль -x.
		assertEquals(-MoveParams.BASE_SPEED, unitsPerSecond(body.velocityX()), 1e-6);
		assertEquals(0.0, body.velocityZ(), 1e-6);
	}

	@Test
	void velocityIsReadBackAfterCollision() {
		FakeBody body = FakeBody.onFloor();
		body.wallX = 0.0;
		body.vx = blocksPerTick(MoveParams.BASE_SPEED);

		// Ввод вбок при угле 0 направлен вдоль +x, прямо в стену.
		SubstepLoop.tick(body, FULL_INPUT, 0.0, false, PARAMS, SCALE, SUBSTEPS, false);

		// Стена обнулила скорость при перемещении, и цикл взял её из игрока, а не из своего расчёта.
		assertEquals(0.0, body.velocityX(), EPSILON);
		assertEquals(0.0, body.x, EPSILON);
	}

	@Test
	void jumpGivesSameVerticalSpeedForAnySubsteps() {
		for (int substeps : new int[] {1, SUBSTEPS}) {
			FakeBody body = FakeBody.onFloor();

			SubstepLoop.tick(body, 0.0, 0.0, true, PARAMS, SCALE, substeps, false);

			// За тик гравитация отнимает 800 × 0,05 = 40 u/s от скорости прыжка.
			double expected = PARAMS.jumpSpeed() - PARAMS.gravity() * UnitConversion.TICK_SECONDS;
			assertEquals(expected, unitsPerSecond(body.velocityY()), 1e-6, "подшагов: " + substeps);
			assertFalse(body.onGround());
		}
	}

	@Test
	void jumpIsAppliedOnlyOnFirstSubstep() {
		// Потолок прямо над головой: прыжок упирается в него, игрок за тот же тик снова встаёт на пол.
		FakeBody body = FakeBody.onFloor();
		body.ceilingY = 0.0;

		SubstepLoop.tick(body, 0.0, 0.0, true, PARAMS, SCALE, SUBSTEPS, false);

		// Если бы прыжок повторялся на каждом подшаге, игрок, вставший на пол, прыгнул бы второй раз.
		long upwardMoves = body.moves.stream().filter(move -> move[1] > 0.0).count();
		assertEquals(1, upwardMoves);
		assertTrue(body.velocityY() <= 0.0);
	}

	@Test
	void steppingOffEdgeStartsFallFromZero() {
		// Игрок только что стоял на краю: опоры уже нет, но по прошлому тику он ещё на земле.
		FakeBody body = FakeBody.onFloor();
		body.floor = false;

		SubstepLoop.tick(body, 0.0, 0.0, false, PARAMS, SCALE, SUBSTEPS, false);

		// Первый подшаг ещё прижимает к земле, его скорость в падение не переходит:
		// падение набирает гравитацию за 2,5 подшага — половину первого и два следующих целиком.
		double dt = UnitConversion.TICK_SECONDS / SUBSTEPS;
		assertEquals(-2.5 * PARAMS.gravity() * dt, unitsPerSecond(body.velocityY()), 1e-6);
		assertFalse(body.onGround());
	}

	@Test
	void rejectsZeroSubsteps() {
		assertThrows(IllegalArgumentException.class,
				() -> SubstepLoop.tick(FakeBody.onFloor(), 0.0, 0.0, false, PARAMS, SCALE, 0, false));
	}

	@Test
	void smoothTurnMakesSubstepsCountForAirStrafe() {
		// Замечание аудита О2. Взгляд меняется раз в тик; без плавного поворота три подшага
		// дают ту же прибавку от стрейфа, что и один.
		double oneStep = strafeForOneSecond(1, false);
		double threeSteps = strafeForOneSecond(3, false);
		double threeSmooth = strafeForOneSecond(3, true);

		assertEquals(oneStep, threeSteps, 1.0);
		assertTrue(threeSmooth > threeSteps + 10.0, "плавный поворот не усилил стрейф: " + threeSteps + " и " + threeSmooth);
		// 300 u/s на старте, секунда стрейфа. Один шаг и три подшага без поворота: по 30 u/s вбок за тик.
		assertEquals(328.6, oneStep, 1.0);
		assertEquals(345.6, threeSmooth, 1.0);
	}

	@Test
	void smoothTurnTakesShortestWayAcrossWrap() {
		assertEquals(20.0, SubstepLoop.shortestTurn(350.0, 10.0), EPSILON);
		assertEquals(-20.0, SubstepLoop.shortestTurn(10.0, 350.0), EPSILON);
		assertEquals(90.0, SubstepLoop.shortestTurn(-45.0, 45.0), EPSILON);
		assertEquals(-180.0, SubstepLoop.shortestTurn(180.0, 0.0), EPSILON);
		assertEquals(0.0, SubstepLoop.shortestTurn(720.0, 0.0), EPSILON);
	}

	@Test
	void smoothTurnDoesNotChangeStraightRunning() {
		FakeBody plain = FakeBody.onFloor();
		FakeBody smooth = FakeBody.onFloor();

		for (int tick = 0; tick < 40; tick++) {
			SubstepLoop.tick(plain, 0.0, FULL_INPUT, false, PARAMS, SCALE, SUBSTEPS, false);
			SubstepLoop.tick(smooth, 0.0, FULL_INPUT, false, PARAMS, SCALE, SUBSTEPS, true);
		}

		assertEquals(plain.velocityZ(), smooth.velocityZ(), EPSILON);
	}

	@Test
	void slowingBlockIsAppliedOncePerTick() {
		// Замечание аудита О5. Замедляющий блок умножает скорость после каждого перемещения;
		// при трёх подшагах без поправки замедление получалось бы втрое сильнее ванильного.
		double factor = 0.4;
		FakeBody body = FakeBody.onFloor();
		body.slowFactor = factor;
		// Скорость на установившемся ходу: за тик трение и разгон возвращают её к максимуму.
		body.setVelocity(0.0, 0.0, blocksPerTick(MoveParams.BASE_SPEED));

		SubstepLoop.tick(body, 0.0, FULL_INPUT, false, PARAMS, SCALE, SUBSTEPS, false);

		assertEquals(MoveParams.BASE_SPEED * factor, unitsPerSecond(body.velocityZ()), 1e-6);
	}

	@Test
	void slowingBlockDoesNotHideCollision() {
		// Если игрок упёрся в стену на промежуточном подшаге, скорость берётся после столкновения.
		FakeBody body = FakeBody.onFloor();
		body.wallX = 0.05;
		body.setVelocity(blocksPerTick(MoveParams.BASE_SPEED), 0.0, 0.0);

		SubstepLoop.tick(body, 0.0, 0.0, false, PARAMS, SCALE, SUBSTEPS, false);

		assertEquals(0.0, body.velocityX(), EPSILON);
	}

	/**
	 * Идеальный стрейф в воздухе в течение секунды: ввод вбок, взгляд раз в тик поворачивается
	 * по направлению полёта, как это делает игрок мышью. Возвращает итоговую скорость, u/s.
	 */
	private static double strafeForOneSecond(int substeps, boolean smoothTurn) {
		FakeBody body = new FakeBody();
		body.floor = false;
		body.setVelocity(0.0, 0.0, blocksPerTick(MoveParams.BASE_SPEED));
		// Без гравитации: проверяется только горизонтальный разгон.
		MoveParams params = new MoveParams(10.0, 10.0, 4.0, 100.0, 0.0, 30.0, 289.0, 3500.0, MoveParams.BASE_SPEED);

		for (int tick = 0; tick < 20; tick++) {
			body.previousYaw = body.yaw;
			// Взгляд вдоль скорости: при угле 0 это +z, положительный ввод вбок — влево от взгляда.
			body.yaw = Math.toDegrees(Math.atan2(-body.velocityX(), body.velocityZ()));
			SubstepLoop.tick(body, FULL_INPUT, 0.0, false, params, SCALE, substeps, smoothTurn);
		}

		return unitsPerSecond(Math.hypot(body.velocityX(), body.velocityZ()));
	}

	private static double unitsPerSecond(double blocksPerTick) {
		return UnitConversion.toUnitsPerSecond(blocksPerTick, SCALE);
	}

	private static double blocksPerTick(double unitsPerSecond) {
		return UnitConversion.toBlocksPerTick(unitsPerSecond, SCALE);
	}

	/** Подставной игрок: точка с полом на высоте 0 и необязательными стеной по x и потолком. */
	private static final class FakeBody implements MovingBody {
		private final List<double[]> moves = new ArrayList<>();
		private double x;
		private double y;
		private double z;
		private double vx;
		private double vy;
		private double vz;
		private boolean grounded;
		private boolean floor = true;
		private double wallX = Double.POSITIVE_INFINITY;
		private double ceilingY = Double.POSITIVE_INFINITY;
		private double yaw;
		private double previousYaw;
		private double slowFactor = 1.0;
		private boolean bumped;

		static FakeBody onFloor() {
			FakeBody body = new FakeBody();
			body.grounded = true;
			return body;
		}

		@Override
		public double velocityX() {
			return vx;
		}

		@Override
		public double velocityY() {
			return vy;
		}

		@Override
		public double velocityZ() {
			return vz;
		}

		@Override
		public void setVelocity(double newX, double newY, double newZ) {
			vx = newX;
			vy = newY;
			vz = newZ;
		}

		@Override
		public void move(double dx, double dy, double dz) {
			moves.add(new double[] {dx, dy, dz});

			bumped = false;
			double allowedX = dx;

			if (x + dx > wallX) {
				allowedX = wallX - x;
				vx = 0.0;
				bumped = true;
			}

			double allowedY = dy;
			grounded = false;

			if (dy < 0.0 && floor && y + dy < 0.0) {
				allowedY = -y;
				vy = 0.0;
				grounded = true;
			} else if (dy > 0.0 && y + dy > ceilingY) {
				allowedY = ceilingY - y;
				vy = 0.0;
			}

			x += allowedX;
			y += allowedY;
			z += dz;

			// Как замедляющий блок в игре: после каждого перемещения горизонтальная скорость умножается.
			vx *= slowFactor;
			vz *= slowFactor;
		}

		@Override
		public boolean onGround() {
			return grounded;
		}

		@Override
		public double yawDegrees() {
			return yaw;
		}

		@Override
		public double previousYawDegrees() {
			return previousYaw;
		}

		@Override
		public boolean horizontalCollision() {
			return bumped;
		}
	}
}

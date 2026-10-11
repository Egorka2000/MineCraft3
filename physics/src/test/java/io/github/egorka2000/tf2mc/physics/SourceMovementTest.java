package io.github.egorka2000.tf2mc.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SourceMovementTest {
	private static final double SOLDIER_SPEED = 240.0;
	private static final MoveParams SOLDIER = MoveParams.tf2Defaults(SOLDIER_SPEED);

	/** Шаг тика Minecraft. */
	private static final double MC_TICK = 0.05;
	/** Три подшага внутри тика Minecraft. */
	private static final double SUBSTEP = MC_TICK / 3.0;
	/** Шаг тика TF2. */
	private static final double TF2_TICK = 0.015;

	private static final MoveInput FORWARD = new MoveInput(1.0, 0.0, false);

	// --- Земля ---

	@ParameterizedTest
	@ValueSource(doubles = {TF2_TICK, SUBSTEP, MC_TICK})
	void groundAccelerationReachesClassSpeedAndNeverExceedsIt(double dt) {
		MoveState state = MoveState.REST;
		double timeToMax = Double.NaN;

		for (int step = 1; step * dt <= 2.0; step++) {
			state = stepOnGround(state, FORWARD, dt);
			assertTrue(state.horizontalSpeed() <= SOLDIER_SPEED + 1e-9, "скорость выше максимума класса: " + state.horizontalSpeed());

			if (Double.isNaN(timeToMax) && state.horizontalSpeed() >= SOLDIER_SPEED - 1e-9) {
				timeToMax = step * dt;
			}
		}

		// Расчёт в docs/02-physics.md даёт около 0,13 с; допуск — на длину шага.
		assertFalse(Double.isNaN(timeToMax), "максимальная скорость не достигнута за 2 с");
		assertTrue(timeToMax <= 0.13 + 2 * dt, "разгон слишком долгий: " + timeToMax + " с");
	}

	@Test
	void groundAccelerationTakesAboutThirteenHundredthsOfASecond() {
		// Мелкий шаг приближает непрерывное движение, для которого сделан расчёт.
		double dt = 0.001;
		MoveState state = MoveState.REST;
		int steps = 0;

		while (state.horizontalSpeed() < SOLDIER_SPEED - 1e-6) {
			state = stepOnGround(state, FORWARD, dt);
			steps++;
			assertTrue(steps < 10_000, "максимальная скорость не достигнута");
		}

		assertEquals(0.13, steps * dt, 0.01);
	}

	@ParameterizedTest
	@ValueSource(doubles = {TF2_TICK, SUBSTEP, MC_TICK})
	void frictionStopsPlayerWithoutInput(double dt) {
		MoveState state = new MoveState(SOLDIER_SPEED, 0.0, 0.0, true);
		double previousSpeed = state.horizontalSpeed();
		double stopTime = Double.NaN;

		for (int step = 1; step * dt <= 2.0; step++) {
			state = stepOnGround(state, MoveInput.NONE, dt);
			assertTrue(state.horizontalSpeed() <= previousSpeed, "скорость выросла без ввода");
			previousSpeed = state.horizontalSpeed();

			if (Double.isNaN(stopTime) && previousSpeed == 0.0) {
				stopTime = step * dt;
			}
		}

		assertFalse(Double.isNaN(stopTime), "игрок не остановился за 2 с");
		assertTrue(stopTime < 1.0, "остановка слишком долгая: " + stopTime + " с");
	}

	@Test
	void frictionKeepsDirection() {
		MoveState state = stepOnGround(new MoveState(120.0, -160.0, 0.0, true), MoveInput.NONE, TF2_TICK);

		assertEquals(120.0 / -160.0, state.vx() / state.vy(), 1e-12);
		assertTrue(state.horizontalSpeed() < 200.0);
	}

	@Test
	void partialInputGivesPartialSpeed() {
		MoveState state = MoveState.REST;

		for (int step = 0; step < 200; step++) {
			state = stepOnGround(state, new MoveInput(0.5, 0.0, false), TF2_TICK);
		}

		assertTrue(state.horizontalSpeed() <= SOLDIER_SPEED * 0.5 + 1e-9);
		assertTrue(state.horizontalSpeed() > SOLDIER_SPEED * 0.4);
	}

	@Test
	void groundStateHasNoVerticalSpeed() {
		MoveState state = stepOnGround(new MoveState(0.0, 0.0, -50.0, true), MoveInput.NONE, TF2_TICK);

		assertEquals(0.0, state.vz());
	}

	// --- Воздух ---

	@Test
	void airInputAlongFlightAddsNothingWhenAlreadyFast() {
		MoveState flying = new MoveState(SOLDIER_SPEED, 0.0, 0.0, false);
		MoveState after = SourceMovement.beforeMove(flying, FORWARD, SOLDIER, TF2_TICK);

		assertEquals(SOLDIER_SPEED, after.vx());
		assertEquals(0.0, after.vy());
	}

	@Test
	void airHasNoFriction() {
		MoveState state = new MoveState(SOLDIER_SPEED, 0.0, 0.0, false);

		for (int step = 0; step < 100; step++) {
			state = SourceMovement.afterMove(SourceMovement.beforeMove(state, MoveInput.NONE, SOLDIER, TF2_TICK), SOLDIER, TF2_TICK);
		}

		assertEquals(SOLDIER_SPEED, state.horizontalSpeed());
	}

	@ParameterizedTest
	@ValueSource(doubles = {TF2_TICK, SUBSTEP, MC_TICK})
	void airStrafeGainsSpeedAboveClassMaximum(double dt) {
		double speed = strafeForOneSecond(dt);

		assertTrue(speed > SOLDIER_SPEED * 1.1, "стрейф не разогнал игрока: " + speed);
	}

	@Test
	void airStrafeGainDependsOnStepLength() {
		// Прибавка за шаг ограничена потолком 30 u/s, поэтому чем больше шагов в секунду,
		// тем сильнее разгон. Это довод за подшаги: один шаг 0,05 с заметно слабее тика TF2.
		double tf2 = strafeForOneSecond(TF2_TICK);
		double substeps = strafeForOneSecond(SUBSTEP);
		double single = strafeForOneSecond(MC_TICK);

		assertEquals(343.0, tf2, 1.0);
		assertEquals(334.0, substeps, 1.0);
		assertEquals(275.0, single, 1.0);
	}

	@Test
	void airGainPerStepIsLimitedByAirSpeedCap() {
		MoveState flying = new MoveState(SOLDIER_SPEED, 0.0, 0.0, false);
		// Большой шаг: ускорение 10 * 240 * 0,05 = 120 превышает потолок 30.
		MoveState after = SourceMovement.beforeMove(flying, new MoveInput(0.0, 1.0, false), SOLDIER, MC_TICK);

		assertEquals(SOLDIER.airSpeedCap(), after.vy(), 1e-12);
	}

	@Test
	void airInputAgainstFlightSlowsDown() {
		MoveState flying = new MoveState(SOLDIER_SPEED, 0.0, 0.0, false);
		MoveState after = SourceMovement.beforeMove(flying, new MoveInput(-1.0, 0.0, false), SOLDIER, TF2_TICK);

		assertTrue(after.vx() < SOLDIER_SPEED);
	}

	// --- Прыжок и гравитация ---

	@ParameterizedTest
	@ValueSource(doubles = {TF2_TICK, SUBSTEP, MC_TICK})
	void jumpHeightDoesNotDependOnStepLength(double dt) {
		// 289² / (2 * 800), см. docs/05-sources.md.
		double expected = SOLDIER.jumpSpeed() * SOLDIER.jumpSpeed() / (2.0 * SOLDIER.gravity());
		double apex = simulateJumpApex(dt);

		// Вершина попадает между шагами, поэтому замер ниже расчёта не больше чем на g * dt² / 8.
		double samplingError = SOLDIER.gravity() * dt * dt / 8.0;
		assertEquals(52.2, expected, 0.01);
		assertTrue(apex <= expected + 1e-9, "прыжок выше расчётного: " + apex);
		assertTrue(apex >= expected - samplingError - 1e-9, "прыжок ниже расчётного: " + apex + " при шаге " + dt);
	}

	@Test
	void jumpLeavesGround() {
		MoveState after = SourceMovement.beforeMove(MoveState.REST, new MoveInput(0.0, 0.0, true), SOLDIER, TF2_TICK);

		assertFalse(after.onGround());
		assertEquals(SOLDIER.jumpSpeed() - SOLDIER.gravity() * TF2_TICK / 2.0, after.vz(), 1e-12);
	}

	@Test
	void jumpInAirDoesNothing() {
		MoveState falling = new MoveState(0.0, 0.0, -100.0, false);
		MoveState withJump = SourceMovement.beforeMove(falling, new MoveInput(0.0, 0.0, true), SOLDIER, TF2_TICK);
		MoveState withoutJump = SourceMovement.beforeMove(falling, MoveInput.NONE, SOLDIER, TF2_TICK);

		assertEquals(withoutJump, withJump);
	}

	@Test
	void jumpOnLandingStepSkipsFriction() {
		// Распрыжка: игрок приземлился быстрее максимума класса и прыгает в тот же шаг.
		MoveState landed = new MoveState(400.0, 0.0, 0.0, true);

		MoveState hop = SourceMovement.beforeMove(landed, new MoveInput(0.0, 0.0, true), SOLDIER, TF2_TICK);
		MoveState walk = SourceMovement.beforeMove(landed, MoveInput.NONE, SOLDIER, TF2_TICK);

		assertEquals(400.0, hop.horizontalSpeed());
		assertTrue(walk.horizontalSpeed() < 400.0);
	}

	@Test
	void fallingSpeedIsCappedPerAxis() {
		MoveState state = new MoveState(0.0, 0.0, 0.0, false);

		for (int step = 0; step < 1000; step++) {
			state = SourceMovement.afterMove(SourceMovement.beforeMove(state, MoveInput.NONE, SOLDIER, MC_TICK), SOLDIER, MC_TICK);
		}

		assertEquals(-SOLDIER.maxVelocity(), state.vz());
	}

	// --- Общее ---

	@Test
	void sameInputGivesSameOutput() {
		MoveState state = new MoveState(123.456, -78.9, 12.3, false);
		MoveInput input = new MoveInput(0.3, -0.7, false);

		assertEquals(
				SourceMovement.beforeMove(state, input, SOLDIER, SUBSTEP),
				SourceMovement.beforeMove(state, input, SOLDIER, SUBSTEP));
		assertEquals(
				SourceMovement.afterMove(state, SOLDIER, SUBSTEP),
				SourceMovement.afterMove(state, SOLDIER, SUBSTEP));
	}

	@Test
	void inputLongerThanOneIsTreatedAsFullSpeed() {
		MoveState full = runOnGround(FORWARD, 200);
		MoveState overlong = runOnGround(new MoveInput(5.0, 0.0, false), 200);

		assertEquals(full, overlong);
	}

	@Test
	void invalidArgumentsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> SourceMovement.beforeMove(MoveState.REST, MoveInput.NONE, SOLDIER, 0.0));
		assertThrows(IllegalArgumentException.class, () -> SourceMovement.afterMove(MoveState.REST, SOLDIER, -0.05));
		assertThrows(IllegalArgumentException.class, () -> new MoveInput(Double.NaN, 0.0, false));
		assertThrows(IllegalArgumentException.class, () -> MoveParams.tf2Defaults(-1.0));
	}

	// --- Вспомогательное ---

	/** Один шаг по ровной земле: перемещение не отрывает игрока от неё. */
	private static MoveState stepOnGround(MoveState state, MoveInput input, double dt) {
		MoveState moving = SourceMovement.beforeMove(state, input, SOLDIER, dt);
		return SourceMovement.afterMove(moving.withOnGround(true), SOLDIER, dt);
	}

	/**
	 * Идеальный стрейф в течение секунды: ввод перпендикулярно полёту, взгляд поворачивается вслед за скоростью.
	 * Возвращает итоговую горизонтальную скорость; начальная равна максимуму класса.
	 */
	private static double strafeForOneSecond(double dt) {
		MoveState state = new MoveState(SOLDIER_SPEED, 0.0, 0.0, false);
		long steps = Math.round(1.0 / dt);

		for (long step = 0; step < steps; step++) {
			double heading = Math.atan2(state.vy(), state.vx());
			MoveInput strafe = MoveInput.towards(heading + Math.PI / 2.0, false);
			state = SourceMovement.beforeMove(state, strafe, SOLDIER, dt);
		}

		return state.horizontalSpeed();
	}

	private static MoveState runOnGround(MoveInput input, int steps) {
		MoveState state = MoveState.REST;

		for (int step = 0; step < steps; step++) {
			state = stepOnGround(state, input, TF2_TICK);
		}

		return state;
	}

	/** Прыжок с места над ровной землёй на высоте 0; возвращает наибольшую высоту на границах шагов. */
	private static double simulateJumpApex(double dt) {
		MoveState state = MoveState.REST;
		double height = 0.0;
		double apex = 0.0;
		boolean jump = true;

		for (int step = 0; step < 10_000; step++) {
			MoveState moving = SourceMovement.beforeMove(state, new MoveInput(0.0, 0.0, jump), SOLDIER, dt);
			jump = false;
			height += moving.vz() * dt;

			boolean landed = height <= 0.0 && moving.vz() < 0.0;

			if (landed) {
				return apex;
			}

			apex = Math.max(apex, height);
			state = SourceMovement.afterMove(moving, SOLDIER, dt);
		}

		throw new AssertionError("игрок не приземлился");
	}
}

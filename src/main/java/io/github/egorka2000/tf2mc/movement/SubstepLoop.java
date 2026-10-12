package io.github.egorka2000.tf2mc.movement;

import io.github.egorka2000.tf2mc.physics.MoveInput;
import io.github.egorka2000.tf2mc.physics.MoveParams;
import io.github.egorka2000.tf2mc.physics.MoveState;
import io.github.egorka2000.tf2mc.physics.SourceMovement;

/**
 * Один тик движения TF2, разбитый на подшаги: на каждом подшаге {@link SourceMovement} считает
 * скорость, а игрок перемещается штатным способом игры со столкновениями.
 *
 * <p>Работает с игроком через {@link MovingBody} и не использует классы игры, поэтому проверяется
 * юнит-тестами с подставным игроком (docs/08-architecture.md, раздел 5).
 */
public final class SubstepLoop {
	/**
	 * На сколько блоков за тик игрок, стоящий на земле, прижимается вниз.
	 * Без этого игра не видит опоры под ногами и считает игрока висящим в воздухе.
	 * Величина близка к ванильной гравитации за тик.
	 */
	static final double GROUND_PROBE = 0.08;

	/**
	 * Игра умножает ввод игрока на 0,98 перед расчётом движения (LivingEntity.applyInput).
	 * Без поправки полное нажатие давало бы 98% скорости класса.
	 */
	static final double VANILLA_INPUT_SCALE = 0.98;

	private SubstepLoop() {
	}

	/**
	 * Считает один тик и оставляет в игроке скорость на конец тика.
	 *
	 * @param body          игрок
	 * @param strafe        ввод вбок в том виде, в каком его передаёт игра (уже умножен на 0,98)
	 * @param forward       ввод вперёд в том же виде
	 * @param jump          запрошен ли прыжок в этот тик; срабатывает только на первом подшаге
	 * @param params        параметры движения в единицах TF2
	 * @param unitsPerBlock масштаб, u/блок
	 * @param substeps      число подшагов за тик, не меньше 1
	 * @param smoothTurn    поворачивать ли взгляд плавно между подшагами: от угла прошлого тика к текущему
	 */
	public static void tick(MovingBody body, double strafe, double forward, boolean jump,
			MoveParams params, double unitsPerBlock, int substeps, boolean smoothTurn) {
		if (substeps < 1) {
			throw new IllegalArgumentException("подшагов должно быть не меньше одного: " + substeps);
		}

		double dt = UnitConversion.TICK_SECONDS / substeps;

		// Игра обновляет взгляд раз в тик. Если считать все подшаги с одним углом, стрейф в воздухе
		// получает прибавку скорости один раз за тик: вторая и третья проекции на то же направление
		// уже упираются в потолок. Плавный поворот даёт каждому подшагу свой угол, как было бы
		// при частоте тиков TF2.
		double yawEnd = body.yawDegrees();
		double yawStart = smoothTurn ? body.previousYawDegrees() : yawEnd;
		double yawTurn = shortestTurn(yawStart, yawEnd);

		MoveState state = UnitConversion.toMoveState(
				body.velocityX(), body.velocityY(), body.velocityZ(), body.onGround(), unitsPerBlock);

		for (int step = 0; step < substeps; step++) {
			MoveInput moveInput = UnitConversion.toMoveInput(
					strafe / VANILLA_INPUT_SCALE, forward / VANILLA_INPUT_SCALE,
					yawStart + yawTurn * (step + 1) / substeps, jump && step == 0);
			MoveState moving = SourceMovement.beforeMove(state, moveInput, params, dt);
			boolean probingGround = moving.onGround();

			double perTickX = UnitConversion.toBlocksPerTick(moving.vx(), unitsPerBlock);
			double perTickUp = probingGround ? -GROUND_PROBE : UnitConversion.toBlocksPerTick(moving.vz(), unitsPerBlock);
			double perTickZ = UnitConversion.toBlocksPerTick(moving.vy(), unitsPerBlock);

			// Скорость записывается до перемещения: при столкновении игра сама обнуляет
			// нужную составляющую, и после перемещения её остаётся только прочитать.
			body.setVelocity(perTickX, perTickUp, perTickZ);
			body.move(perTickX / substeps, perTickUp / substeps, perTickZ / substeps);

			boolean onGround = body.onGround();
			// Замедляющие блоки (песок душ, мёд) игра учитывает при каждом перемещении. Чтобы замедление
			// действовало раз за тик, как в ванили, на промежуточных подшагах берётся скорость до
			// перемещения, если игрок ни во что не упёрся.
			boolean keepHorizontal = step < substeps - 1 && !body.horizontalCollision();
			double afterX = keepHorizontal ? perTickX : body.velocityX();
			double afterZ = keepHorizontal ? perTickZ : body.velocityZ();
			// Сошёл с края, пока прижимался к земле: падение начинается с нуля, а не со скорости прижатия.
			double up = onGround || probingGround ? 0.0 : body.velocityY();

			state = SourceMovement.afterMove(
					UnitConversion.toMoveState(afterX, up, afterZ, onGround, unitsPerBlock), params, dt);
		}

		body.setVelocity(
				UnitConversion.toBlocksPerTick(state.vx(), unitsPerBlock),
				UnitConversion.toBlocksPerTick(state.vz(), unitsPerBlock),
				UnitConversion.toBlocksPerTick(state.vy(), unitsPerBlock));
	}

	/** Кратчайший поворот от одного угла к другому, градусы: от -180 до 180. */
	static double shortestTurn(double fromDegrees, double toDegrees) {
		double turn = (toDegrees - fromDegrees) % 360.0;

		if (turn > 180.0) {
			turn -= 360.0;
		} else if (turn < -180.0) {
			turn += 360.0;
		}

		return turn;
	}
}

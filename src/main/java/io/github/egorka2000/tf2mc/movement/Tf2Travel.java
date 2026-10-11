package io.github.egorka2000.tf2mc.movement;

import io.github.egorka2000.tf2mc.config.Tf2Config;
import io.github.egorka2000.tf2mc.physics.MoveInput;
import io.github.egorka2000.tf2mc.physics.MoveParams;
import io.github.egorka2000.tf2mc.physics.MoveState;
import io.github.egorka2000.tf2mc.physics.SourceMovement;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Мост между игрой и {@link SourceMovement}: собирает ввод и скорость, считает шаг в единицах TF2,
 * перемещает игрока штатным методом игры и записывает скорость обратно.
 *
 * <p>Работает только для локального игрока на клиенте: позицию своего игрока в Minecraft
 * определяет клиент, а сервер её проверяет (docs/08-architecture.md, раздел 5).
 */
public final class Tf2Travel {
	/**
	 * На сколько блоков за тик игрок, стоящий на земле, прижимается вниз.
	 * Без этого игра не видит опоры под ногами и считает игрока висящим в воздухе.
	 * Величина близка к ванильной гравитации за тик.
	 */
	private static final double GROUND_PROBE = 0.08;

	/**
	 * Игра умножает ввод игрока на 0,98 перед расчётом движения (LivingEntity.applyInput).
	 * Без поправки полное нажатие давало бы 98% скорости класса.
	 */
	private static final double VANILLA_INPUT_SCALE = 0.98;

	// До шага 8 плана классов нет, поэтому скорость одна для всех: 100% по меркам TF2.
	private static final double TEMPORARY_MAX_SPEED = MoveParams.BASE_SPEED;

	/** В какой тик игрока запрошен прыжок; -1, если запроса нет. */
	private static int jumpRequestTick = -1;

	private Tf2Travel() {
	}

	/** Нужно ли считать движение этой сущности по правилам TF2 прямо сейчас. */
	public static boolean appliesTo(LivingEntity entity) {
		if (!(entity instanceof Player player) || !player.isLocalPlayer()) {
			return false;
		}

		if (!LocalMovementState.get().enabled()) {
			return false;
		}

		// В этих случаях остаётся ванильное движение. Для воды, лавы и элитр игра вызывает другие
		// методы движения, но прыжок на мелководье идёт через общий jumpFromGround. Без проверки
		// жидкости такой прыжок был бы перехвачен и потерян: движение TF2 в жидкости не считается.
		return !player.getAbilities().flying
				&& !player.isInLiquid()
				&& !player.isSpectator()
				&& !player.isPassenger()
				&& !player.onClimbable()
				&& !player.hasEffect(MobEffects.LEVITATION);
	}

	/**
	 * Запоминает прыжок: саму скорость прыжка задаёт {@link SourceMovement} в ближайшем шаге движения.
	 *
	 * @return true, если прыжок взят на себя и ванильный нужно отменить
	 */
	public static boolean requestJump(LivingEntity entity) {
		if (!appliesTo(entity)) {
			return false;
		}

		jumpRequestTick = entity.tickCount;
		return true;
	}

	/**
	 * Один тик движения.
	 *
	 * @param input ввод игрока в его осях: x — вбок, z — вперёд
	 * @return true, если движение посчитано и ванильный расчёт нужно отменить
	 */
	public static boolean travel(LivingEntity entity, Vec3 input) {
		if (!appliesTo(entity)) {
			return false;
		}

		Tf2Config config = LocalMovementState.get().config();
		MoveParams params = config.toMoveParams(TEMPORARY_MAX_SPEED);
		double unitsPerBlock = config.unitsPerBlock();
		int substeps = config.substeps();
		double dt = UnitConversion.TICK_SECONDS / substeps;

		// Прыжок действителен только в тот тик, когда запрошен: игра вызывает его прямо перед движением.
		boolean jump = jumpRequestTick == entity.tickCount;
		jumpRequestTick = -1;

		Vec3 velocity = entity.getDeltaMovement();
		MoveState state = UnitConversion.toMoveState(velocity.x, velocity.y, velocity.z, entity.onGround(), unitsPerBlock);

		for (int step = 0; step < substeps; step++) {
			MoveInput moveInput = UnitConversion.toMoveInput(
					input.x / VANILLA_INPUT_SCALE, input.z / VANILLA_INPUT_SCALE, entity.getYRot(), jump && step == 0);
			MoveState moving = SourceMovement.beforeMove(state, moveInput, params, dt);
			boolean probingGround = moving.onGround();

			Vec3 perTick = new Vec3(
					UnitConversion.toBlocksPerTick(moving.vx(), unitsPerBlock),
					probingGround ? -GROUND_PROBE : UnitConversion.toBlocksPerTick(moving.vz(), unitsPerBlock),
					UnitConversion.toBlocksPerTick(moving.vy(), unitsPerBlock));

			// Скорость записывается до перемещения: при столкновении игра сама обнуляет
			// нужную составляющую, и после перемещения её остаётся только прочитать.
			entity.setDeltaMovement(perTick);
			entity.move(MoverType.SELF, perTick.scale(1.0 / substeps));

			Vec3 moved = entity.getDeltaMovement();
			boolean onGround = entity.onGround();
			// Сошёл с края, пока прижимался к земле: падение начинается с нуля, а не со скорости прижатия.
			double up = onGround || probingGround ? 0.0 : moved.y;

			state = SourceMovement.afterMove(UnitConversion.toMoveState(moved.x, up, moved.z, onGround, unitsPerBlock), params, dt);
		}

		entity.setDeltaMovement(
				UnitConversion.toBlocksPerTick(state.vx(), unitsPerBlock),
				UnitConversion.toBlocksPerTick(state.vz(), unitsPerBlock),
				UnitConversion.toBlocksPerTick(state.vy(), unitsPerBlock));
		return true;
	}
}

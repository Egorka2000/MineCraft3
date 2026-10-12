package io.github.egorka2000.tf2mc.movement;

import io.github.egorka2000.tf2mc.config.Tf2Config;
import io.github.egorka2000.tf2mc.physics.MoveParams;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Мост между игрой и движением TF2: решает, применяется ли движение TF2, принимает прыжок
 * и передаёт тик в {@link SubstepLoop}, оборачивая игрока Minecraft в {@link MovingBody}.
 *
 * <p>Работает только для локального игрока на клиенте: позицию своего игрока в Minecraft
 * определяет клиент, а сервер её проверяет (docs/08-architecture.md, раздел 5).
 */
public final class Tf2Travel {
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

		// Прыжок действителен только в тот тик, когда запрошен: игра вызывает его прямо перед движением.
		boolean jump = jumpRequestTick == entity.tickCount;
		jumpRequestTick = -1;

		SubstepLoop.tick(new EntityBody(entity), input.x, input.z, jump,
				config.toMoveParams(TEMPORARY_MAX_SPEED), config.unitsPerBlock(), config.substeps());
		return true;
	}

	/** Игрок Minecraft для {@link SubstepLoop}: только передаёт вызовы штатным методам игры. */
	private record EntityBody(LivingEntity entity) implements MovingBody {
		@Override
		public double velocityX() {
			return entity.getDeltaMovement().x;
		}

		@Override
		public double velocityY() {
			return entity.getDeltaMovement().y;
		}

		@Override
		public double velocityZ() {
			return entity.getDeltaMovement().z;
		}

		@Override
		public void setVelocity(double x, double y, double z) {
			entity.setDeltaMovement(x, y, z);
		}

		@Override
		public void move(double dx, double dy, double dz) {
			entity.move(MoverType.SELF, new Vec3(dx, dy, dz));
		}

		@Override
		public boolean onGround() {
			return entity.onGround();
		}

		@Override
		public double yawDegrees() {
			return entity.getYRot();
		}
	}
}

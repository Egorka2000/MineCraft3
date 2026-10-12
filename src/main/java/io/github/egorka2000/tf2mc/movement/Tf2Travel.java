package io.github.egorka2000.tf2mc.movement;

import io.github.egorka2000.tf2mc.clazz.PlayerClasses;
import io.github.egorka2000.tf2mc.config.Tf2Config;

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
	/**
	 * За сколько тиков до приземления можно зажать прыжок, чтобы он засчитался. Предварительное
	 * значение: выбрано без оценки ощущений, см. docs/04-decisions.md.
	 */
	private static final int JUMP_BUFFER_TICKS = 2;

	// Плавный поворот взгляда между подшагами: без него подшаги почти не усиливают стрейф.
	private static final boolean SMOOTH_TURN = true;

	private static final JumpGate JUMP_GATE = new JumpGate(JUMP_BUFFER_TICKS);

	/** В какой тик игрока запрошен прыжок; -1, если запроса нет. */
	private static int jumpRequestTick = -1;

	/** Горизонтальная скорость перед ванильным прыжком на мелководье, блоков за тик. */
	private static double shallowJumpX;
	private static double shallowJumpZ;
	private static boolean shallowJumpPending;

	/**
	 * Взгляд на прошлом тике движения. Собственное поле игры для прошлого угла к моменту расчёта
	 * движения уже равно текущему, поэтому угол запоминается здесь.
	 */
	private static double lastYaw;
	private static int lastYawTick = Integer.MIN_VALUE;

	private Tf2Travel() {
	}

	/** Нужно ли считать движение этой сущности по правилам TF2 прямо сейчас. */
	public static boolean appliesTo(LivingEntity entity) {
		return isTf2Player(entity) && !entity.isInLiquid();
	}

	/** Локальный игрок с классом TF2 в состоянии, где движение TF2 вообще возможно. */
	private static boolean isTf2Player(LivingEntity entity) {
		if (!(entity instanceof Player player) || !player.isLocalPlayer()) {
			return false;
		}

		// Движение TF2 включено ровно тогда, когда у игрока выбран класс.
		if (PlayerClasses.get(player).isEmpty()) {
			return false;
		}

		// В этих случаях остаётся ванильное движение. Вода, лава и элитры проверяются отдельно:
		// для них игра вызывает другие методы движения.
		return !player.getAbilities().flying
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

		// Прыжок, для которого клавишу не нажали заново, отменяется целиком: ванильный тоже не нужен.
		if (JUMP_GATE.acceptPress()) {
			jumpRequestTick = entity.tickCount;
		}

		return true;
	}

	/**
	 * Вызывается в начале каждого тика сущности: была ли клавиша прыжка зажата в прошлом тике.
	 * К этому моменту игра ещё не прочитала ввод текущего тика.
	 */
	public static void observeJumpKey(LivingEntity entity, boolean wasHeld) {
		if (entity instanceof Player player && player.isLocalPlayer()) {
			JUMP_GATE.observePreviousTick(wasHeld);
		}
	}

	/**
	 * Перед ванильным прыжком. На мелководье прыжок остаётся ванильным, потому что движение
	 * в жидкости мод не считает, но его скорость подменяется скоростью прыжка TF2.
	 */
	public static void beforeVanillaJump(LivingEntity entity) {
		shallowJumpPending = isTf2Player(entity) && entity.isInLiquid();

		if (shallowJumpPending) {
			shallowJumpX = entity.getDeltaMovement().x;
			shallowJumpZ = entity.getDeltaMovement().z;
		}
	}

	/**
	 * После ванильного прыжка на мелководье: вертикальная скорость как у прыжка TF2,
	 * горизонтальная — какой была до прыжка, без ванильного толчка вперёд при беге.
	 * Иначе ванильный толчок вместе с гравитацией TF2 дал бы прыжок в полтора раза выше обычного.
	 */
	public static void afterVanillaJump(LivingEntity entity) {
		if (!shallowJumpPending) {
			return;
		}

		shallowJumpPending = false;
		Tf2Config config = LocalMovementState.get().config();
		entity.setDeltaMovement(shallowJumpX, UnitConversion.toBlocksPerTick(config.jumpSpeed(), config.unitsPerBlock()), shallowJumpZ);
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

		// Если прошлый тик движение TF2 не считалось (вода, лестница, только что включили),
		// поворачивать не от чего: берётся текущий угол.
		double yaw = entity.getYRot();
		double previousYaw = lastYawTick == entity.tickCount - 1 ? lastYaw : yaw;
		lastYaw = yaw;
		lastYawTick = entity.tickCount;

		// appliesTo уже проверил, что это игрок с классом.
		double maxSpeed = PlayerClasses.get((Player) entity).orElseThrow().maxSpeed();

		SubstepLoop.tick(new EntityBody(entity, previousYaw), input.x, input.z, jump,
				config.toMoveParams(maxSpeed), config.unitsPerBlock(), config.substeps(), SMOOTH_TURN);
		return true;
	}

	/** Игрок Minecraft для {@link SubstepLoop}: только передаёт вызовы штатным методам игры. */
	private record EntityBody(LivingEntity entity, double previousYaw) implements MovingBody {
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

		@Override
		public double previousYawDegrees() {
			return previousYaw;
		}

		@Override
		public boolean horizontalCollision() {
			return entity.horizontalCollision;
		}
	}
}

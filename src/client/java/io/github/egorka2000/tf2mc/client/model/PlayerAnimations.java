package io.github.egorka2000.tf2mc.client.model;

import java.util.Map;
import java.util.WeakHashMap;

import io.github.egorka2000.tf2mc.weapon.WeaponAction;

import net.minecraft.client.player.AbstractClientPlayer;

/**
 * Что каждый игрок на клиенте играл недавно: основная последовательность и та, с которой
 * он на неё перешёл (для плавного перехода), и жест оружия, присланный сервером.
 *
 * <p>Время — в тиках жизни игрока ({@code ageInTicks} состояния отрисовки), с долями тика.
 * Записи держатся, пока жив объект игрока на клиенте.
 */
public final class PlayerAnimations {
	private static final Map<AbstractClientPlayer, PlayerAnimations> BY_PLAYER = new WeakHashMap<>();

	private String sequence;
	private String previous;
	private float switchedAt;
	private WeaponAction gesture;
	private float gestureAt;

	private PlayerAnimations() {
	}

	public static PlayerAnimations of(AbstractClientPlayer player) {
		return BY_PLAYER.computeIfAbsent(player, p -> new PlayerAnimations());
	}

	/** Сервер прислал событие оружия игрока. */
	public static void onWeaponAction(AbstractClientPlayer player, WeaponAction action) {
		PlayerAnimations animations = of(player);
		animations.gesture = action;
		animations.gestureAt = player.tickCount;
	}

	/**
	 * Выбрана основная последовательность в момент {@code age}. Если она другая, запоминается
	 * прежняя и время перехода.
	 */
	public void play(String name, float age) {
		if (!name.equals(sequence)) {
			previous = sequence;
			sequence = name;
			switchedAt = age;
		}
	}

	/** Прежняя последовательность, с которой идёт переход; {@code null} — перехода не было. */
	public String previous() {
		return previous;
	}

	/** Сколько тиков назад сменилась основная последовательность. */
	public float sinceSwitch(float age) {
		return age - switchedAt;
	}

	/** Текущий жест оружия; {@code null} — жеста нет. */
	public WeaponAction gesture() {
		return gesture;
	}

	/** Сколько тиков идёт жест: с тика, в который пришло событие. */
	public float sinceGesture(float age) {
		return age - gestureAt;
	}

	/** Положение тела, от которого зависят основная анимация и жесты. */
	public enum Posture {
		STAND,
		CROUCH,
		AIR,
		SWIM
	}

	/** Жест TF2 с основным оружием для события оружия и положения тела. */
	public static String gestureName(WeaponAction action, Posture posture) {
		return gestureName(action, posture, WeaponView.PRIMARY);
	}

	/**
	 * Жест TF2 для события оружия, положения тела и набора анимаций оружия ({@link WeaponView#slot}).
	 * Имена у TF2 неодинаковые: перезарядка второго оружия в воздухе и в воде — {@code SECONDARY_reload_*}.
	 *
	 * @return {@code null}, если у TF2 такого жеста нет: с основным оружием в воде перезарядка —
	 *         одна последовательность без повтора и конца
	 */
	public static String gestureName(WeaponAction action, Posture posture, String slot) {
		if (action == WeaponAction.FIRE) {
			return switch (posture) {
				case CROUCH -> "AttackCrouch_" + slot;
				case SWIM -> "AttackSwim_" + slot;
				default -> "AttackStand_" + slot;
			};
		}

		boolean primary = slot.equals(WeaponView.PRIMARY);

		if (posture == Posture.SWIM && primary) {
			return action == WeaponAction.RELOAD_END ? null : "ReloadSwim_PRIMARY";
		}

		String reload = switch (posture) {
			case AIR -> primary ? "ReloadAirwalk_PRIMARY" : slot + "_reload_airwalk";
			case SWIM -> slot + "_reload_swim";
			case CROUCH -> "ReloadCrouch_" + slot;
			default -> "ReloadStand_" + slot;
		};

		return switch (action) {
			case RELOAD_LOOP -> reload + "_loop";
			case RELOAD_END -> reload + "_end";
			default -> reload;
		};
	}

	/** Жест закончился или прерван: больше не играется. */
	public void stopGesture() {
		gesture = null;
	}
}

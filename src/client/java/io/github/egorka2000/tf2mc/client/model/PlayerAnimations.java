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

	/** Жест TF2 с основным оружием для события оружия и положения тела: стоя, в приседе или в воздухе. */
	public static String gestureName(WeaponAction action, boolean crouching, boolean inAir) {
		if (action == WeaponAction.FIRE) {
			return crouching ? "AttackCrouch_PRIMARY" : "AttackStand_PRIMARY";
		}

		String reload = inAir ? "ReloadAirwalk_PRIMARY" : crouching ? "ReloadCrouch_PRIMARY" : "ReloadStand_PRIMARY";

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

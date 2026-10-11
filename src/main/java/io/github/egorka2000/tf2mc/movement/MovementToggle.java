package io.github.egorka2000.tf2mc.movement;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверный список игроков, у которых включено движение TF2 и спидометр.
 *
 * <p>Временное решение до появления классов (шаг 8 плана): тогда движение будет включаться
 * выбором класса. Поэтому состояние живёт только в памяти и после перезахода сбрасывается.
 */
public final class MovementToggle {
	private static final Set<UUID> MOVEMENT = ConcurrentHashMap.newKeySet();
	private static final Set<UUID> SPEEDOMETER = ConcurrentHashMap.newKeySet();

	private MovementToggle() {
	}

	public static boolean isMovementEnabled(UUID player) {
		return MOVEMENT.contains(player);
	}

	public static void setMovementEnabled(UUID player, boolean enabled) {
		set(MOVEMENT, player, enabled);
	}

	public static boolean isSpeedometerEnabled(UUID player) {
		return SPEEDOMETER.contains(player);
	}

	public static void setSpeedometerEnabled(UUID player, boolean enabled) {
		set(SPEEDOMETER, player, enabled);
	}

	public static void forget(UUID player) {
		MOVEMENT.remove(player);
		SPEEDOMETER.remove(player);
	}

	private static void set(Set<UUID> set, UUID player, boolean enabled) {
		if (enabled) {
			set.add(player);
		} else {
			set.remove(player);
		}
	}
}

package io.github.egorka2000.tf2mc.movement;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверный список игроков, которым показывается спидометр.
 *
 * <p>Спидометр — отладочный инструмент, поэтому состояние живёт только в памяти сервера
 * и после перезахода сбрасывается.
 */
public final class SpeedometerToggle {
	private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();

	private SpeedometerToggle() {
	}

	public static boolean isEnabled(UUID player) {
		return ENABLED.contains(player);
	}

	public static void setEnabled(UUID player, boolean enabled) {
		if (enabled) {
			ENABLED.add(player);
		} else {
			ENABLED.remove(player);
		}
	}

	public static void forget(UUID player) {
		ENABLED.remove(player);
	}
}

package io.github.egorka2000.tf2mc.movement;

import io.github.egorka2000.tf2mc.config.Tf2Config;

/**
 * Что сервер сообщил этому клиенту о движении его игрока.
 *
 * <p>Заполняется только на клиенте, из пакета сервера. Настройки берутся отсюда, а не из
 * локального файла: на сервере и у всех игроков должны действовать одни и те же значения.
 */
public final class LocalMovementState {
	private static final Snapshot DISABLED = new Snapshot(false, false, Tf2Config.DEFAULT);

	private static volatile Snapshot current = DISABLED;

	private LocalMovementState() {
	}

	/**
	 * Состояние на момент последнего пакета.
	 *
	 * @param enabled   включено ли движение TF2 для этого игрока
	 * @param showSpeed показывать ли спидометр
	 * @param config    настройки сервера
	 */
	public record Snapshot(boolean enabled, boolean showSpeed, Tf2Config config) {
	}

	public static Snapshot get() {
		return current;
	}

	public static void set(Snapshot snapshot) {
		current = snapshot;
	}

	/** Сброс при выходе с сервера: на следующем может не быть мода. */
	public static void reset() {
		current = DISABLED;
	}
}

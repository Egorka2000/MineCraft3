package io.github.egorka2000.tf2mc.client;

import java.util.Locale;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Предупреждение о малом объёме памяти. Сколько памяти дать игре, задаёт лаунчер аргументом
 * {@code -Xmx} до запуска Java; мод изменить это не может, поэтому только подсказывает.
 *
 * <p>Предупреждение пишется в чат при первом входе в мир за запуск игры.
 */
public final class MemoryCheck {
	/** Сколько памяти советуется дать игре, ГБ. */
	public static final int RECOMMENDED_GB = 4;

	/**
	 * Порог предупреждения. С {@code -Xmx4G} Java сообщает чуть меньше 4 ГБ: часть уходит на
	 * служебные области сборщика мусора. Поэтому порог ниже совета.
	 */
	static final long THRESHOLD_BYTES = 3500L * 1024 * 1024;

	private static final double BYTES_PER_GB = 1024.0 * 1024 * 1024;

	private static boolean warned;

	private MemoryCheck() {
	}

	public static void register() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			String text = warning(Runtime.getRuntime().maxMemory());

			if (text != null && !warned) {
				warned = true;
				client.execute(() -> {
					if (client.player != null) {
						client.player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.YELLOW));
					}
				});
			}
		});
	}

	/**
	 * Текст предупреждения.
	 *
	 * @param maxMemory сколько памяти может занять Java, байт ({@link Runtime#maxMemory()})
	 * @return текст; {@code null}, если памяти достаточно
	 */
	public static String warning(long maxMemory) {
		// Long.MAX_VALUE — предела нет вовсе.
		if (maxMemory >= THRESHOLD_BYTES || maxMemory == Long.MAX_VALUE) {
			return null;
		}

		// Дробная часть через запятую, как принято по-русски.
		String gigabytes = String.format(Locale.ROOT, "%.1f", maxMemory / BYTES_PER_GB).replace('.', ',');

		return "tf2-mc: игре выделено " + gigabytes + " ГБ памяти, советуем не меньше " + RECOMMENDED_GB + " ГБ. "
				+ "В лаунчере: Установки → ваша установка → Дополнительно → Аргументы JVM, замените -Xmx… на -Xmx"
				+ RECOMMENDED_GB + "G.";
	}
}

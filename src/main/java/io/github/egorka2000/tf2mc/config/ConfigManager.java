package io.github.egorka2000.tf2mc.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Хранит действующие настройки и читает их из файла.
 *
 * <p>Ошибка в файле не роняет игру и не сбрасывает настройки: остаются прежние значения,
 * а причина возвращается вызывающему коду.
 */
public final class ConfigManager {
	private final Path file;
	private volatile Tf2Config current = Tf2Config.DEFAULT;

	public ConfigManager(Path file) {
		this.file = file;
	}

	/**
	 * Итог чтения файла.
	 *
	 * @param success  применены ли настройки из файла
	 * @param message  что произошло, для показа пользователю
	 * @param warnings замечания к файлу, которые не помешали загрузке
	 */
	public record LoadResult(boolean success, String message, List<String> warnings) {
		public LoadResult {
			warnings = List.copyOf(warnings);
		}
	}

	public Tf2Config current() {
		return current;
	}

	public Path file() {
		return file;
	}

	/** Читает файл настроек. Если файла нет, создаёт его со значениями по умолчанию. */
	public LoadResult load() {
		if (Files.notExists(file)) {
			return createDefaultFile();
		}

		try {
			ConfigParser.Result parsed = ConfigParser.parse(Files.readString(file, StandardCharsets.UTF_8));
			current = parsed.config();
			return new LoadResult(true, "настройки загружены из " + file.getFileName(), parsed.warnings());
		} catch (ConfigException e) {
			return new LoadResult(false, "ошибка в " + file.getFileName() + ": " + e.getMessage() + "; действуют прежние настройки", List.of());
		} catch (IOException e) {
			return new LoadResult(false, "не удалось прочитать " + file.getFileName() + ": " + e.getMessage() + "; действуют прежние настройки", List.of());
		}
	}

	private LoadResult createDefaultFile() {
		current = Tf2Config.DEFAULT;

		try {
			Path parent = file.getParent();

			if (parent != null) {
				Files.createDirectories(parent);
			}

			Files.writeString(file, ConfigParser.toJson(Tf2Config.DEFAULT), StandardCharsets.UTF_8);
			return new LoadResult(true, "создан файл настроек " + file.getFileName() + " со значениями по умолчанию", List.of());
		} catch (IOException e) {
			return new LoadResult(false, "не удалось создать " + file.getFileName() + ": " + e.getMessage() + "; действуют значения по умолчанию", List.of());
		}
	}
}

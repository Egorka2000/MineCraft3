package io.github.egorka2000.tf2mc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Проверяет, что все JSON-файлы в исходниках разбираются.
 * Игра сообщает о битом JSON только в логе при запуске, сборку он не ломает.
 */
class ResourceJsonTest {
	// Gradle запускает тесты из корня проекта.
	private static final Path SOURCES = Path.of("src");

	/** Путь в имени ресурса Minecraft. */
	private static final Pattern VALID_PATH = Pattern.compile("[a-z0-9/._-]+");
	/** Имя ресурса с необязательным пространством имён. */
	private static final Pattern VALID_ID = Pattern.compile("([a-z0-9._-]+:)?[a-z0-9/._-]+");

	@Test
	void allJsonFilesAreValid() throws IOException {
		List<Path> files;

		try (Stream<Path> paths = Files.walk(SOURCES)) {
			files = paths.filter(path -> path.toString().endsWith(".json")).toList();
		}

		assertFalse(files.isEmpty(), "JSON-файлы не найдены в " + SOURCES.toAbsolutePath());

		for (Path file : files) {
			try {
				JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			} catch (JsonParseException e) {
				fail("битый JSON: " + file + ": " + e.getMessage());
			}
		}
	}

	/**
	 * Правила, по которым игра читает sounds.json. Одна ошибка — и игра отбрасывает весь файл
	 * с одним предупреждением в логе, а звуки молча становятся тишиной или заглушками.
	 */
	@Test
	void soundDefinitionsFollowGameRules() throws IOException {
		List<Path> files;

		try (Stream<Path> paths = Files.walk(SOURCES)) {
			files = paths.filter(path -> path.getFileName().toString().equals("sounds.json")).toList();
		}

		assertFalse(files.isEmpty(), "sounds.json не найдены в " + SOURCES.toAbsolutePath());

		for (Path file : files) {
			JsonObject events = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();

			for (Map.Entry<String, JsonElement> event : events.entrySet()) {
				String where = file + ": " + event.getKey();
				assertTrue(VALID_PATH.matcher(event.getKey()).matches(), "недопустимое имя звука: " + where);

				JsonArray sounds = event.getValue().getAsJsonObject().getAsJsonArray("sounds");
				assertTrue(sounds != null && !sounds.isEmpty(), "у звука нет файлов: " + where);

				for (JsonElement sound : sounds) {
					String name = sound.isJsonObject() ? sound.getAsJsonObject().get("name").getAsString() : sound.getAsString();
					assertTrue(VALID_ID.matcher(name).matches(), "недопустимое имя файла " + name + ": " + where);

					if (sound.isJsonObject()) {
						JsonObject entry = sound.getAsJsonObject();
						assertPositive(entry, "volume", where);
						assertPositive(entry, "pitch", where);
						assertPositive(entry, "weight", where);
					}
				}
			}
		}
	}

	private static void assertPositive(JsonObject entry, String key, String where) {
		if (entry.has(key)) {
			assertTrue(entry.get(key).getAsDouble() > 0, key + " должно быть больше нуля: " + where);
		}
	}
}

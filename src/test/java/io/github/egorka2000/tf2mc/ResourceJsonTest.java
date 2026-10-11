package io.github.egorka2000.tf2mc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

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
}

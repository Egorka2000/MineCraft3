package io.github.egorka2000.tf2mc.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigManagerTest {
	@TempDir
	Path directory;

	@Test
	void missingFileIsCreatedWithDefaults() throws IOException {
		Path file = directory.resolve("config").resolve("tf2mc.json");
		ConfigManager manager = new ConfigManager(file);

		ConfigManager.LoadResult result = manager.load();

		assertTrue(result.success(), result.message());
		assertEquals(Tf2Config.DEFAULT, manager.current());
		assertEquals(Tf2Config.DEFAULT, ConfigParser.parse(Files.readString(file, StandardCharsets.UTF_8)).config());
	}

	@Test
	void changedFileIsAppliedOnReload() throws IOException {
		Path file = directory.resolve("tf2mc.json");
		ConfigManager manager = new ConfigManager(file);
		manager.load();

		Files.writeString(file, "{\"movement\": {\"gravity\": 400}, \"unitsPerBlock\": 40}", StandardCharsets.UTF_8);
		ConfigManager.LoadResult result = manager.load();

		assertTrue(result.success(), result.message());
		assertEquals(400.0, manager.current().gravity());
		assertEquals(40.0, manager.current().unitsPerBlock());
	}

	@Test
	void brokenFileKeepsPreviousSettings() throws IOException {
		Path file = directory.resolve("tf2mc.json");
		ConfigManager manager = new ConfigManager(file);
		Files.writeString(file, "{\"substeps\": 5}", StandardCharsets.UTF_8);
		manager.load();

		Files.writeString(file, "{\"substeps\": 500}", StandardCharsets.UTF_8);
		ConfigManager.LoadResult result = manager.load();

		assertFalse(result.success());
		assertTrue(result.message().contains("substeps"), result.message());
		assertEquals(5, manager.current().substeps());
	}

	@Test
	void warningsAreReturned() throws IOException {
		Path file = directory.resolve("tf2mc.json");
		Files.writeString(file, "{\"gravity\": 400}", StandardCharsets.UTF_8);

		ConfigManager.LoadResult result = new ConfigManager(file).load();

		assertTrue(result.success());
		assertEquals(1, result.warnings().size());
	}
}

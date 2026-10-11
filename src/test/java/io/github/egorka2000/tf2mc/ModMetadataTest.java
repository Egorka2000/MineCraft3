package io.github.egorka2000.tf2mc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Проверяет, что fabric.mod.json согласован с кодом: опечатка в имени класса
 * или файла миксинов иначе всплывает только при запуске игры.
 */
class ModMetadataTest {
	private static final String BASE_PACKAGE = "io.github.egorka2000.tf2mc";

	private static JsonObject metadata;

	@BeforeAll
	static void loadMetadata() throws IOException {
		metadata = readJson("fabric.mod.json");
	}

	@Test
	void modIdMatchesCode() {
		assertEquals(Tf2Mc.MOD_ID, metadata.get("id").getAsString());
	}

	@Test
	void versionPlaceholderIsExpanded() {
		String version = metadata.get("version").getAsString();
		assertFalse(version.contains("$"), "версия не подставлена при сборке: " + version);
	}

	@Test
	void entrypointClassesExist() {
		JsonObject entrypoints = metadata.getAsJsonObject("entrypoints");
		List<String> classNames = new ArrayList<>();

		for (String kind : entrypoints.keySet()) {
			for (JsonElement entry : entrypoints.getAsJsonArray(kind)) {
				classNames.add(entry.getAsString());
			}
		}

		assertFalse(classNames.isEmpty(), "в fabric.mod.json нет точек входа");

		for (String className : classNames) {
			assertTrue(className.startsWith(BASE_PACKAGE), "точка входа вне пакета мода: " + className);
			assertClassExists(className);
		}
	}

	@Test
	void mixinConfigsExistAndListRealClasses() throws IOException {
		JsonArray mixins = metadata.getAsJsonArray("mixins");
		assertFalse(mixins.isEmpty(), "в fabric.mod.json нет конфигов миксинов");

		for (JsonElement entry : mixins) {
			String configName = entry.isJsonObject()
					? entry.getAsJsonObject().get("config").getAsString()
					: entry.getAsString();
			JsonObject config = readJson(configName);
			String mixinPackage = config.get("package").getAsString();

			assertTrue(mixinPackage.startsWith(BASE_PACKAGE), configName + ": пакет вне мода: " + mixinPackage);

			for (String section : List.of("mixins", "client", "server")) {
				if (!config.has(section)) {
					continue;
				}

				for (JsonElement mixin : config.getAsJsonArray(section)) {
					assertClassExists(mixinPackage + "." + mixin.getAsString());
				}
			}
		}
	}

	// Проверяется наличие файла класса, а не его загрузка: классы миксинов нельзя загружать напрямую.
	private static void assertClassExists(String className) {
		String path = className.replace('.', '/') + ".class";
		assertNotNull(ModMetadataTest.class.getClassLoader().getResource(path), "класс не найден: " + className);
	}

	private static JsonObject readJson(String resource) throws IOException {
		InputStream stream = ModMetadataTest.class.getClassLoader().getResourceAsStream(resource);
		assertNotNull(stream, "ресурс не найден: " + resource);

		try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}
}

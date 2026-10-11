package io.github.egorka2000.tf2mc.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Чтение и запись настроек в JSON. Классы Minecraft не используются, поэтому разбор проверяется юнит-тестами.
 *
 * <p>Пропущенный ключ получает значение по умолчанию: файл старой версии мода остаётся рабочим.
 * Неизвестный ключ не ошибка, но о нём сообщается: чаще всего это опечатка.
 */
public final class ConfigParser {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final String MOVEMENT = "movement";
	private static final String UNITS_PER_BLOCK = "unitsPerBlock";
	private static final String SUBSTEPS = "substeps";

	private static final Set<String> ROOT_KEYS = Set.of(MOVEMENT, UNITS_PER_BLOCK, SUBSTEPS);
	private static final Set<String> MOVEMENT_KEYS = Set.of(
			"accelerate", "airAccelerate", "friction", "stopSpeed", "gravity", "airSpeedCap", "jumpSpeed", "maxVelocity");

	private ConfigParser() {
	}

	/**
	 * Результат разбора.
	 *
	 * @param config   настройки
	 * @param warnings замечания, которые не мешают загрузке
	 */
	public record Result(Tf2Config config, List<String> warnings) {
		public Result {
			warnings = List.copyOf(warnings);
		}
	}

	/**
	 * Разбирает текст файла настроек.
	 *
	 * @throws ConfigException если это не JSON, значение не того типа или вне допустимых границ
	 */
	public static Result parse(String json) {
		JsonElement parsed;

		try {
			parsed = JsonParser.parseString(json);
		} catch (JsonParseException e) {
			throw new ConfigException("файл не является корректным JSON: " + e.getMessage());
		}

		if (!parsed.isJsonObject()) {
			throw new ConfigException("в корне файла должен быть объект {...}");
		}

		JsonObject root = parsed.getAsJsonObject();
		List<String> warnings = new ArrayList<>();
		warnAboutUnknownKeys(root, ROOT_KEYS, "", warnings);

		JsonObject movement = new JsonObject();

		if (root.has(MOVEMENT)) {
			if (!root.get(MOVEMENT).isJsonObject()) {
				throw new ConfigException(MOVEMENT + " должен быть объектом {...}");
			}

			movement = root.getAsJsonObject(MOVEMENT);
			warnAboutUnknownKeys(movement, MOVEMENT_KEYS, MOVEMENT + ".", warnings);
		}

		Tf2Config defaults = Tf2Config.DEFAULT;
		Tf2Config config = new Tf2Config(
				number(movement, "accelerate", MOVEMENT + ".", defaults.accelerate()),
				number(movement, "airAccelerate", MOVEMENT + ".", defaults.airAccelerate()),
				number(movement, "friction", MOVEMENT + ".", defaults.friction()),
				number(movement, "stopSpeed", MOVEMENT + ".", defaults.stopSpeed()),
				number(movement, "gravity", MOVEMENT + ".", defaults.gravity()),
				number(movement, "airSpeedCap", MOVEMENT + ".", defaults.airSpeedCap()),
				number(movement, "jumpSpeed", MOVEMENT + ".", defaults.jumpSpeed()),
				number(movement, "maxVelocity", MOVEMENT + ".", defaults.maxVelocity()),
				number(root, UNITS_PER_BLOCK, "", defaults.unitsPerBlock()),
				wholeNumber(root, SUBSTEPS, defaults.substeps()));

		return new Result(config, warnings);
	}

	/** Текст файла настроек для заданных значений. */
	public static String toJson(Tf2Config config) {
		JsonObject movement = new JsonObject();
		movement.addProperty("accelerate", config.accelerate());
		movement.addProperty("airAccelerate", config.airAccelerate());
		movement.addProperty("friction", config.friction());
		movement.addProperty("stopSpeed", config.stopSpeed());
		movement.addProperty("gravity", config.gravity());
		movement.addProperty("airSpeedCap", config.airSpeedCap());
		movement.addProperty("jumpSpeed", config.jumpSpeed());
		movement.addProperty("maxVelocity", config.maxVelocity());

		JsonObject root = new JsonObject();
		root.add(MOVEMENT, movement);
		root.addProperty(UNITS_PER_BLOCK, config.unitsPerBlock());
		root.addProperty(SUBSTEPS, config.substeps());

		return GSON.toJson(root) + System.lineSeparator();
	}

	private static double number(JsonObject object, String key, String prefix, double defaultValue) {
		if (!object.has(key)) {
			return defaultValue;
		}

		JsonElement element = object.get(key);

		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			throw new ConfigException(prefix + key + " должен быть числом");
		}

		return element.getAsDouble();
	}

	private static int wholeNumber(JsonObject object, String key, int defaultValue) {
		double value = number(object, key, "", defaultValue);

		if (value != Math.rint(value) || Math.abs(value) > Integer.MAX_VALUE) {
			throw new ConfigException(key + " должен быть целым числом: " + value);
		}

		return (int) value;
	}

	private static void warnAboutUnknownKeys(JsonObject object, Set<String> known, String prefix, List<String> warnings) {
		for (String key : object.keySet()) {
			if (!known.contains(key)) {
				warnings.add("неизвестный параметр " + prefix + key + " пропущен");
			}
		}
	}
}

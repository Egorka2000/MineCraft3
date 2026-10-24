package io.github.egorka2000.tf2mc.clazz;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Числа класса из файла данных {@code data/tf2mc/classes.json}. Источники чисел — docs/05-sources.md.
 *
 * <p>Файл лежит внутри мода и одинаков на сервере и у клиентов, поэтому разбор строгий: ошибка в
 * нём — ошибка сборки, и её ловят юнит-тесты. Классы Minecraft не используются.
 *
 * @param maxHealth максимальное здоровье в единицах TF2
 * @param maxSpeed  максимальная скорость на земле, u/s
 * @param airJumps  сколько раз за полёт можно прыгнуть в воздухе
 */
public record ClassStats(int maxHealth, double maxSpeed, int airJumps) {
	/** Путь к файлу данных внутри мода. */
	public static final String RESOURCE = "/data/tf2mc/classes.json";

	private static final List<String> KEYS = List.of("health", "speed", "airJumps");

	public ClassStats {
		if (maxHealth <= 0 || !(maxSpeed > 0.0) || Double.isInfinite(maxSpeed) || airJumps < 0) {
			throw new IllegalArgumentException("числа класса вне границ: здоровье " + maxHealth + ", скорость " + maxSpeed + ", прыжков в воздухе " + airJumps);
		}
	}

	/**
	 * Разбирает файл данных классов.
	 *
	 * @param json текст файла
	 * @param ids  имена классов, которые должны в нём быть, и только они
	 * @throws IllegalArgumentException если файл не JSON, класса не хватает, есть лишний класс или ключ, число не того вида
	 */
	public static Map<String, ClassStats> parse(String json, Set<String> ids) {
		JsonElement parsed;

		try {
			parsed = JsonParser.parseString(json);
		} catch (JsonParseException e) {
			throw new IllegalArgumentException("classes.json не является корректным JSON: " + e.getMessage(), e);
		}

		if (!parsed.isJsonObject()) {
			throw new IllegalArgumentException("в корне classes.json должен быть объект {...}");
		}

		JsonObject root = parsed.getAsJsonObject();

		if (!root.keySet().equals(ids)) {
			throw new IllegalArgumentException("в classes.json классы " + root.keySet() + ", а в моде " + ids);
		}

		Map<String, ClassStats> result = new HashMap<>();

		for (String id : ids) {
			if (!root.get(id).isJsonObject()) {
				throw new IllegalArgumentException(id + " должен быть объектом {...}");
			}

			JsonObject entry = root.getAsJsonObject(id);

			if (!Set.copyOf(KEYS).equals(entry.keySet())) {
				throw new IllegalArgumentException(id + ": ожидались ключи " + KEYS + ", а в файле " + entry.keySet());
			}

			result.put(id, new ClassStats(whole(entry, id, "health"), number(entry, id, "speed"), whole(entry, id, "airJumps")));
		}

		return Map.copyOf(result);
	}

	private static double number(JsonObject entry, String id, String key) {
		JsonElement element = entry.get(key);

		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			throw new IllegalArgumentException(id + "." + key + " должен быть числом");
		}

		return element.getAsDouble();
	}

	private static int whole(JsonObject entry, String id, String key) {
		double value = number(entry, id, key);

		if (value != Math.rint(value) || Math.abs(value) > Integer.MAX_VALUE) {
			throw new IllegalArgumentException(id + "." + key + " должен быть целым числом: " + value);
		}

		return (int) value;
	}
}

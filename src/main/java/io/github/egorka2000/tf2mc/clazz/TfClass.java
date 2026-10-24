package io.github.egorka2000.tf2mc.clazz;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Класс TF2. Здесь только имя и то, что задаёт код (какое оружие выдаётся — в моде оружия);
 * числа — здоровье, скорость, прыжки в воздухе — лежат в файле данных {@code data/tf2mc/classes.json}
 * ({@link ClassStats}). Источники чисел — docs/05-sources.md.
 *
 * <p>Пока здесь Soldier и Scout; остальные классы появятся позже. Классы игры не используются.
 */
public enum TfClass {
	SCOUT("scout"),
	SOLDIER("soldier");

	/** Слово в командах, которое снимает класс и возвращает ванильное поведение. Классом не является. */
	public static final String VANILLA_ID = "vanilla";

	private final String id;

	TfClass(String id) {
		this.id = id;
	}

	/** Имя класса в командах, в сохранённых данных игрока и в файле данных. */
	public String id() {
		return id;
	}

	/** Числа класса из файла данных. */
	public ClassStats stats() {
		return Data.STATS.get(id);
	}

	/** Максимальное здоровье в единицах TF2; одна единица равна одной единице здоровья Minecraft. */
	public int maxHealth() {
		return stats().maxHealth();
	}

	/** Максимальная скорость на земле, u/s. */
	public double maxSpeed() {
		return stats().maxSpeed();
	}

	/** Сколько раз за полёт можно прыгнуть в воздухе: у Scout — один двойной прыжок. */
	public int airJumps() {
		return stats().airJumps();
	}

	/** Класс по имени. Неизвестное имя и «vanilla» дают пустой результат. */
	public static Optional<TfClass> byId(String id) {
		for (TfClass tfClass : values()) {
			if (tfClass.id.equals(id)) {
				return Optional.of(tfClass);
			}
		}

		return Optional.empty();
	}

	/** Файл данных читается при первом обращении к числам. Внутри перечисления: его поля ещё не готовы при создании значений. */
	private static final class Data {
		static final Map<String, ClassStats> STATS = ClassStats.parse(read(),
				Arrays.stream(values()).map(TfClass::id).collect(Collectors.toUnmodifiableSet()));

		private static String read() {
			try (InputStream in = TfClass.class.getResourceAsStream(ClassStats.RESOURCE)) {
				if (in == null) {
					throw new IllegalStateException("в моде нет файла " + ClassStats.RESOURCE);
				}

				return new String(in.readAllBytes(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}
}

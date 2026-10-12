package io.github.egorka2000.tf2mc.clazz;

import java.util.Optional;

/**
 * Класс TF2: здоровье и скорость. Числа сверены, источники в docs/05-sources.md.
 *
 * <p>Пока здесь только Soldier: остальные классы и файл данных появятся после первой вехи.
 * Классы игры не используются.
 */
public enum TfClass {
	SOLDIER("soldier", 200, 240.0);

	/** Слово в командах, которое снимает класс и возвращает ванильное поведение. Классом не является. */
	public static final String VANILLA_ID = "vanilla";

	private final String id;
	private final int maxHealth;
	private final double maxSpeed;

	TfClass(String id, int maxHealth, double maxSpeed) {
		this.id = id;
		this.maxHealth = maxHealth;
		this.maxSpeed = maxSpeed;
	}

	/** Имя класса в командах и в сохранённых данных игрока. */
	public String id() {
		return id;
	}

	/** Максимальное здоровье в единицах TF2; одна единица равна одной единице здоровья Minecraft. */
	public int maxHealth() {
		return maxHealth;
	}

	/** Максимальная скорость на земле, u/s. */
	public double maxSpeed() {
		return maxSpeed;
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
}

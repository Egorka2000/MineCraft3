package io.github.egorka2000.tf2mc.clazz;

/**
 * Правила здоровья TF2: урон от падения и оверхил. Числа сверены, источники в docs/05-sources.md.
 *
 * <p>Чистая математика без классов игры: здоровье в единицах TF2, скорости в u/s, высоты в u.
 */
public final class HealthRules {
	/** Скорость падения, до которой включительно урона нет, u/s. */
	public static final double SAFE_FALL_SPEED = 650.0;

	/** Случайный разброс урона от падения: множитель от {@code 1 - SPREAD} до {@code 1 + SPREAD}. */
	public static final double FALL_DAMAGE_SPREAD = 0.2;

	/** Предел оверхила в долях максимального здоровья. */
	public static final double OVERHEAL_LIMIT = 1.5;

	/** За сколько секунд полный оверхил спадает до максимального здоровья. */
	public static final double OVERHEAL_DECAY_SECONDS = 15.0;

	/** Делитель скорости в формуле урона от падения, u/s. */
	private static final double FALL_DAMAGE_SPEED_UNIT = 300.0;

	private static final double OVERHEAL_ROUNDING = 5.0;

	private HealthRules() {
	}

	/**
	 * Скорость, с которой тело приземляется после свободного падения с высоты.
	 *
	 * @param heightUnits высота падения, u
	 * @param gravity     гравитация, u/s²
	 */
	public static double fallSpeed(double heightUnits, double gravity) {
		return heightUnits <= 0.0 ? 0.0 : Math.sqrt(2.0 * gravity * heightUnits);
	}

	/**
	 * Урон от падения: 5 × (скорость / 300) процентов максимального здоровья.
	 *
	 * @param speed     скорость приземления, u/s
	 * @param maxHealth максимальное здоровье класса
	 * @param spread    множитель случайного разброса; 1 — без разброса
	 */
	public static double fallDamage(double speed, double maxHealth, double spread) {
		if (speed <= SAFE_FALL_SPEED) {
			return 0.0;
		}

		double percent = 5.0 * (speed / FALL_DAMAGE_SPEED_UNIT);
		return maxHealth * percent / 100.0 * spread;
	}

	/** Наибольшее здоровье с оверхилом: 150% максимума, с округлением вниз до кратного пяти. */
	public static double maxOverheal(double maxHealth) {
		return Math.floor(maxHealth * OVERHEAL_LIMIT / OVERHEAL_ROUNDING) * OVERHEAL_ROUNDING;
	}

	/** Сколько здоровья сверх максимума даёт полный оверхил. */
	public static double overhealCapacity(double maxHealth) {
		return maxOverheal(maxHealth) - maxHealth;
	}

	/** Скорость, с которой спадает оверхил, единиц здоровья в секунду. */
	public static double overhealDecayPerSecond(double maxHealth) {
		return overhealCapacity(maxHealth) / OVERHEAL_DECAY_SECONDS;
	}
}

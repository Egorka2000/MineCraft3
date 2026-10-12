package io.github.egorka2000.tf2mc.physics;

/**
 * Параметры движения в единицах TF2 (u, u/s, u/s²). Источники значений: docs/05-sources.md.
 *
 * @param accelerate          ускорение на земле (sv_accelerate)
 * @param airAccelerate       ускорение в воздухе (sv_airaccelerate)
 * @param friction            трение о землю (sv_friction)
 * @param stopSpeed           скорость, ниже которой трение считается как для неё (sv_stopspeed)
 * @param gravity             гравитация (sv_gravity)
 * @param airSpeedCap         потолок желаемой скорости в воздухе; из-за него работает стрейф
 * @param jumpSpeed           вертикальная скорость в момент прыжка
 * @param maxVelocity         потолок скорости по каждой оси (sv_maxvelocity)
 * @param maxSpeed            максимальная скорость класса на земле
 * @param bunnyHopSpeedFactor во сколько раз скорость в момент прыжка может превышать максимум класса;
 *                            0 — без ограничения
 * @param backSpeedFactor     доля максимума класса, быстрее которой нельзя двигаться назад; 1 — без ограничения
 * @param backSpeedMin        скорость, ниже которой ограничение движения назад не действует
 */
public record MoveParams(
		double accelerate,
		double airAccelerate,
		double friction,
		double stopSpeed,
		double gravity,
		double airSpeedCap,
		double jumpSpeed,
		double maxVelocity,
		double maxSpeed,
		double bunnyHopSpeedFactor,
		double backSpeedFactor,
		double backSpeedMin) {
	/** Скорость, принятая в TF2 за 100%. */
	public static final double BASE_SPEED = 300.0;

	/** Ограничение распрыжки в TF2: скорость в момент прыжка не выше 1,2 максимума класса. */
	public static final double TF2_BUNNY_HOP_SPEED_FACTOR = 1.2;
	/** Назад в TF2 двигаются не быстрее 90% максимума класса. */
	public static final double TF2_BACK_SPEED_FACTOR = 0.9;
	/** Ограничение движения назад действует на скорости выше 100 u/s. */
	public static final double TF2_BACK_SPEED_MIN = 100.0;

	public MoveParams {
		requireNonNegative(accelerate, "accelerate");
		requireNonNegative(airAccelerate, "airAccelerate");
		requireNonNegative(friction, "friction");
		requireNonNegative(stopSpeed, "stopSpeed");
		requireNonNegative(gravity, "gravity");
		requireNonNegative(airSpeedCap, "airSpeedCap");
		requireNonNegative(jumpSpeed, "jumpSpeed");
		requireNonNegative(maxVelocity, "maxVelocity");
		requireNonNegative(maxSpeed, "maxSpeed");
		requireNonNegative(bunnyHopSpeedFactor, "bunnyHopSpeedFactor");
		requireNonNegative(backSpeedFactor, "backSpeedFactor");
		requireNonNegative(backSpeedMin, "backSpeedMin");
	}

	/** Параметры с ограничениями распрыжки и движения назад как в TF2. */
	public MoveParams(double accelerate, double airAccelerate, double friction, double stopSpeed, double gravity,
			double airSpeedCap, double jumpSpeed, double maxVelocity, double maxSpeed) {
		this(accelerate, airAccelerate, friction, stopSpeed, gravity, airSpeedCap, jumpSpeed, maxVelocity, maxSpeed,
				TF2_BUNNY_HOP_SPEED_FACTOR, TF2_BACK_SPEED_FACTOR, TF2_BACK_SPEED_MIN);
	}

	/** Значения TF2 по умолчанию для класса с заданной максимальной скоростью. */
	public static MoveParams tf2Defaults(double maxSpeed) {
		return new MoveParams(10.0, 10.0, 4.0, 100.0, 800.0, 30.0, 289.0, 3500.0, maxSpeed);
	}

	private static void requireNonNegative(double value, String name) {
		if (!(value >= 0.0) || Double.isInfinite(value)) {
			throw new IllegalArgumentException(name + " должен быть конечным и неотрицательным: " + value);
		}
	}
}

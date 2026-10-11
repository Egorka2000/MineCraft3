package io.github.egorka2000.tf2mc.physics;

/**
 * Параметры движения в единицах TF2 (u, u/s, u/s²). Источники значений: docs/05-sources.md.
 *
 * @param accelerate    ускорение на земле (sv_accelerate)
 * @param airAccelerate ускорение в воздухе (sv_airaccelerate)
 * @param friction      трение о землю (sv_friction)
 * @param stopSpeed     скорость, ниже которой трение считается как для неё (sv_stopspeed)
 * @param gravity       гравитация (sv_gravity)
 * @param airSpeedCap   потолок желаемой скорости в воздухе; из-за него работает стрейф
 * @param jumpSpeed     вертикальная скорость в момент прыжка
 * @param maxVelocity   потолок скорости по каждой оси (sv_maxvelocity)
 * @param maxSpeed      максимальная скорость класса на земле
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
		double maxSpeed) {
	/** Скорость, принятая в TF2 за 100%. */
	public static final double BASE_SPEED = 300.0;

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

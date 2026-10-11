package io.github.egorka2000.tf2mc.config;

import io.github.egorka2000.tf2mc.physics.MoveParams;

/**
 * Настройки мода. Параметры движения — в единицах TF2, источники значений: docs/05-sources.md.
 *
 * @param accelerate    ускорение на земле
 * @param airAccelerate ускорение в воздухе
 * @param friction      трение о землю
 * @param stopSpeed     скорость, ниже которой трение считается как для неё
 * @param gravity       гравитация, u/s²
 * @param airSpeedCap   потолок желаемой скорости в воздухе, u/s
 * @param jumpSpeed     вертикальная скорость прыжка, u/s
 * @param maxVelocity   потолок скорости по каждой оси, u/s
 * @param unitsPerBlock масштаб: сколько единиц TF2 в одном блоке
 * @param substeps      сколько раз за тик Minecraft считается движение
 */
public record Tf2Config(
		double accelerate,
		double airAccelerate,
		double friction,
		double stopSpeed,
		double gravity,
		double airSpeedCap,
		double jumpSpeed,
		double maxVelocity,
		double unitsPerBlock,
		int substeps) {
	public static final int MIN_SUBSTEPS = 1;
	public static final int MAX_SUBSTEPS = 10;

	/** Значения TF2 и решения проекта: масштаб 45 u/блок, три подшага. */
	public static final Tf2Config DEFAULT = fromDefaults();

	public Tf2Config {
		requireNonNegative(accelerate, "movement.accelerate");
		requireNonNegative(airAccelerate, "movement.airAccelerate");
		requireNonNegative(friction, "movement.friction");
		requireNonNegative(stopSpeed, "movement.stopSpeed");
		requireNonNegative(gravity, "movement.gravity");
		requireNonNegative(airSpeedCap, "movement.airSpeedCap");
		requireNonNegative(jumpSpeed, "movement.jumpSpeed");
		requireNonNegative(maxVelocity, "movement.maxVelocity");

		if (!(unitsPerBlock > 0.0) || Double.isInfinite(unitsPerBlock)) {
			throw new ConfigException("unitsPerBlock должен быть положительным числом: " + unitsPerBlock);
		}

		if (substeps < MIN_SUBSTEPS || substeps > MAX_SUBSTEPS) {
			throw new ConfigException("substeps должен быть от " + MIN_SUBSTEPS + " до " + MAX_SUBSTEPS + ": " + substeps);
		}
	}

	/** Параметры движения для класса с заданной максимальной скоростью, u/s. */
	public MoveParams toMoveParams(double maxSpeed) {
		return new MoveParams(accelerate, airAccelerate, friction, stopSpeed, gravity, airSpeedCap, jumpSpeed, maxVelocity, maxSpeed);
	}

	private static Tf2Config fromDefaults() {
		MoveParams tf2 = MoveParams.tf2Defaults(MoveParams.BASE_SPEED);
		return new Tf2Config(
				tf2.accelerate(),
				tf2.airAccelerate(),
				tf2.friction(),
				tf2.stopSpeed(),
				tf2.gravity(),
				tf2.airSpeedCap(),
				tf2.jumpSpeed(),
				tf2.maxVelocity(),
				45.0,
				3);
	}

	private static void requireNonNegative(double value, String name) {
		if (!(value >= 0.0) || Double.isInfinite(value)) {
			throw new ConfigException(name + " должен быть неотрицательным числом: " + value);
		}
	}
}

package io.github.egorka2000.tf2mc.physics;

/**
 * Скорость игрока в единицах TF2 и признак опоры.
 * Оси как в TF2: x и y горизонтальные, z вверх.
 *
 * @param vx       скорость по x, u/s
 * @param vy       скорость по y, u/s
 * @param vz       вертикальная скорость, u/s
 * @param onGround стоит ли игрок на земле
 */
public record MoveState(double vx, double vy, double vz, boolean onGround) {
	/** Неподвижный игрок на земле. */
	public static final MoveState REST = new MoveState(0.0, 0.0, 0.0, true);

	/** Горизонтальная скорость, u/s. */
	public double horizontalSpeed() {
		return Math.hypot(vx, vy);
	}

	public MoveState withOnGround(boolean newOnGround) {
		return new MoveState(vx, vy, vz, newOnGround);
	}
}

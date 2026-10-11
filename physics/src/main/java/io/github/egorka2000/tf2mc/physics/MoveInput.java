package io.github.egorka2000.tf2mc.physics;

/**
 * Ввод игрока за один шаг.
 *
 * <p>Желаемое направление задаётся в мировых осях, уже с учётом поворота взгляда:
 * перевод «вперёд/вбок + угол» в вектор делает вызывающий код. Длина вектора — доля
 * максимальной скорости от 0 до 1; более длинный вектор укорачивается до 1.
 *
 * @param wishX желаемое направление по x
 * @param wishY желаемое направление по y
 * @param jump  нажат ли прыжок
 */
public record MoveInput(double wishX, double wishY, boolean jump) {
	/** Нет ввода. */
	public static final MoveInput NONE = new MoveInput(0.0, 0.0, false);

	public MoveInput {
		if (!Double.isFinite(wishX) || !Double.isFinite(wishY)) {
			throw new IllegalArgumentException("направление должно быть конечным: " + wishX + ", " + wishY);
		}
	}

	/** Движение в направлении угла (радианы, от оси x к оси y) на полной скорости. */
	public static MoveInput towards(double angle, boolean jump) {
		return new MoveInput(Math.cos(angle), Math.sin(angle), jump);
	}
}

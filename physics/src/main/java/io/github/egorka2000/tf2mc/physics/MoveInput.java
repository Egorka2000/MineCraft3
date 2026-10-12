package io.github.egorka2000.tf2mc.physics;

/**
 * Ввод игрока за один шаг.
 *
 * <p>Желаемое направление задаётся в мировых осях, уже с учётом поворота взгляда:
 * перевод «вперёд/вбок + угол» в вектор делает вызывающий код. Длина вектора — доля
 * максимальной скорости от 0 до 1; более длинный вектор укорачивается до 1.
 *
 * <p>Направление взгляда нужно только для ограничения скорости движения назад. Нулевой вектор
 * означает «взгляд неизвестен»: тогда это ограничение не применяется.
 *
 * @param wishX    желаемое направление по x
 * @param wishY    желаемое направление по y
 * @param forwardX направление взгляда по x, единичный вектор в горизонтальной плоскости
 * @param forwardY направление взгляда по y
 * @param jump     нажат ли прыжок
 */
public record MoveInput(double wishX, double wishY, double forwardX, double forwardY, boolean jump) {
	/** Нет ввода. */
	public static final MoveInput NONE = new MoveInput(0.0, 0.0, false);

	public MoveInput {
		if (!Double.isFinite(wishX) || !Double.isFinite(wishY) || !Double.isFinite(forwardX) || !Double.isFinite(forwardY)) {
			throw new IllegalArgumentException(
					"направления должны быть конечными: " + wishX + ", " + wishY + ", " + forwardX + ", " + forwardY);
		}
	}

	/** Ввод без направления взгляда: скорость движения назад не ограничивается. */
	public MoveInput(double wishX, double wishY, boolean jump) {
		this(wishX, wishY, 0.0, 0.0, jump);
	}

	/** Движение в направлении угла (радианы, от оси x к оси y) на полной скорости. */
	public static MoveInput towards(double angle, boolean jump) {
		return new MoveInput(Math.cos(angle), Math.sin(angle), jump);
	}
}

package io.github.egorka2000.tf2mc.movement;

import io.github.egorka2000.tf2mc.physics.MoveInput;
import io.github.egorka2000.tf2mc.physics.MoveState;

/**
 * Единственное место, где единицы Minecraft переводятся в единицы TF2 и обратно.
 *
 * <p>Minecraft хранит скорость в блоках за тик, TF2 — в юнитах в секунду. Оси тоже разные:
 * в TF2 вверх направлена z, в Minecraft — y. Горизонтальные x и z Minecraft становятся x и y TF2.
 * Классы игры здесь не используются, поэтому пересчёт проверяется юнит-тестами.
 */
public final class UnitConversion {
	/** Тиков Minecraft в секунде. */
	public static final double TICKS_PER_SECOND = 20.0;
	/** Длина тика Minecraft, с. */
	public static final double TICK_SECONDS = 1.0 / TICKS_PER_SECOND;

	private UnitConversion() {
	}

	/** Скорость из блоков за тик в юниты в секунду. */
	public static double toUnitsPerSecond(double blocksPerTick, double unitsPerBlock) {
		return blocksPerTick * TICKS_PER_SECOND * unitsPerBlock;
	}

	/** Скорость из юнитов в секунду в блоки за тик. */
	public static double toBlocksPerTick(double unitsPerSecond, double unitsPerBlock) {
		return unitsPerSecond / unitsPerBlock / TICKS_PER_SECOND;
	}

	/** Расстояние из блоков в юниты. */
	public static double toUnits(double blocks, double unitsPerBlock) {
		return blocks * unitsPerBlock;
	}

	/**
	 * Состояние для расчёта из скорости Minecraft.
	 *
	 * @param x  скорость по x Minecraft, блоков за тик
	 * @param up скорость вверх (y Minecraft), блоков за тик
	 * @param z  скорость по z Minecraft, блоков за тик
	 */
	public static MoveState toMoveState(double x, double up, double z, boolean onGround, double unitsPerBlock) {
		return new MoveState(
				toUnitsPerSecond(x, unitsPerBlock),
				toUnitsPerSecond(z, unitsPerBlock),
				toUnitsPerSecond(up, unitsPerBlock),
				onGround);
	}

	/**
	 * Желаемое направление в мировых осях из ввода игрока.
	 *
	 * <p>Поворот тот же, что применяет сама игра: при угле 0 «вперёд» смотрит вдоль +z,
	 * положительный {@code strafe} — влево от взгляда.
	 *
	 * @param strafe     ввод вбок, от -1 до 1
	 * @param forward    ввод вперёд, от -1 до 1
	 * @param yawDegrees угол поворота взгляда, градусы
	 */
	public static MoveInput toMoveInput(double strafe, double forward, double yawDegrees, boolean jump) {
		double yaw = Math.toRadians(yawDegrees);
		double sin = Math.sin(yaw);
		double cos = Math.cos(yaw);

		// Направление взгляда — это ввод «вперёд» единичной длины: при угле 0 оно смотрит вдоль +z.
		return new MoveInput(strafe * cos - forward * sin, forward * cos + strafe * sin, -sin, cos, jump);
	}
}

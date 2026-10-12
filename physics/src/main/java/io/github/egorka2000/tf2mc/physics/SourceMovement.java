package io.github.egorka2000.tf2mc.physics;

/**
 * Движение игрока в стиле Source: трение, ускорение на земле и в воздухе, прыжок, гравитация.
 *
 * <p>Класс считает только скорость. Перемещение и столкновения остаются за вызывающим кодом,
 * поэтому шаг разбит на две половины:
 *
 * <ol>
 *   <li>{@link #beforeMove} — половина гравитации, прыжок, трение, ускорение от ввода;</li>
 *   <li>вызывающий код перемещает игрока с полученной скоростью и определяет, стоит ли он на земле;</li>
 *   <li>{@link #afterMove} — вторая половина гравитации.</li>
 * </ol>
 *
 * <p>Гравитация двумя половинами даёт траекторию, которая не зависит от длины шага:
 * прыжок одинаков при шаге 0,05 с и при трёх подшагах.
 *
 * <p>Все величины в единицах TF2, время в секундах. Состояния нет: одинаковый вход
 * всегда даёт одинаковый выход, на клиенте и на сервере.
 */
public final class SourceMovement {
	/** Ниже этой скорости трение не считается: игрок уже стоит. */
	private static final double MIN_FRICTION_SPEED = 0.1;

	private SourceMovement() {
	}

	/**
	 * Первая половина шага: вызывается до перемещения.
	 *
	 * @param state  скорость и опора на начало шага
	 * @param input  ввод игрока
	 * @param params параметры движения
	 * @param dt     длина шага, с
	 * @return скорость, с которой нужно переместить игрока; после прыжка {@code onGround} равен false
	 */
	public static MoveState beforeMove(MoveState state, MoveInput input, MoveParams params, double dt) {
		requireStep(dt);

		double vx = state.vx();
		double vy = state.vy();
		double vz = state.vz();
		boolean onGround = state.onGround();

		// Прыжок проверяется до трения: если нажать его в шаг приземления,
		// трение не успевает сработать и скорость сохраняется. На этом держится распрыжка.
		if (onGround && input.jump()) {
			// Ограничение распрыжки TF2: скорость в момент прыжка не выше заданной доли максимума класса.
			double hopLimit = params.bunnyHopSpeedFactor() * params.maxSpeed();
			double hopSpeed = Math.hypot(vx, vy);

			if (hopLimit > 0.0 && hopSpeed > hopLimit) {
				vx *= hopLimit / hopSpeed;
				vy *= hopLimit / hopSpeed;
			}

			vz = params.jumpSpeed();
			onGround = false;
		}

		if (!onGround) {
			vz -= params.gravity() * dt * 0.5;
		}

		double wishLength = Math.hypot(input.wishX(), input.wishY());
		double wishDirX = 0.0;
		double wishDirY = 0.0;

		if (wishLength > 0.0) {
			wishDirX = input.wishX() / wishLength;
			wishDirY = input.wishY() / wishLength;
		}

		double wishSpeed = Math.min(wishLength, 1.0) * params.maxSpeed();

		if (onGround) {
			vz = 0.0;

			double speed = Math.hypot(vx, vy);

			if (speed >= MIN_FRICTION_SPEED) {
				// Медленного игрока трение тормозит так, будто он движется со stopSpeed:
				// иначе остановка тянулась бы бесконечно.
				double control = Math.max(speed, params.stopSpeed());
				double drop = control * params.friction() * dt;
				double scale = Math.max(speed - drop, 0.0) / speed;
				vx *= scale;
				vy *= scale;
			} else {
				vx = 0.0;
				vy = 0.0;
			}

			double gain = speedGain(vx, vy, wishDirX, wishDirY, wishSpeed, wishSpeed, params.accelerate(), dt);
			vx += wishDirX * gain;
			vy += wishDirY * gain;

			// На земле скорость не превышает максимум класса, что бы её ни разогнало: приземление после
			// взрыва, движение по диагонали или ввод под углом к стене. Без этого игрок, упёршийся
			// в стену под углом, скользил бы вдоль неё быстрее максимума.
			double[] limited = limitLength(vx, vy, params.maxSpeed());
			limited = limitBackwardSpeed(limited[0], limited[1], input, params);
			vx = limited[0];
			vy = limited[1];
		} else {
			// В воздухе прибавка ограничена так, будто игрок хочет двигаться не быстрее airSpeedCap,
			// но само ускорение считается от полной желаемой скорости. Если направление ввода
			// перпендикулярно полёту, проекция скорости на него близка к нулю и прибавка есть всегда:
			// так поворот со стрейфом разгоняет игрока выше максимальной скорости класса.
			double cappedWishSpeed = Math.min(wishSpeed, params.airSpeedCap());
			double gain = speedGain(vx, vy, wishDirX, wishDirY, cappedWishSpeed, wishSpeed, params.airAccelerate(), dt);
			vx += wishDirX * gain;
			vy += wishDirY * gain;
		}

		return clamp(vx, vy, vz, onGround, params.maxVelocity());
	}

	/**
	 * Вторая половина шага: вызывается после перемещения.
	 *
	 * @param state  скорость после перемещения; {@code onGround} — по итогам перемещения
	 * @param params параметры движения
	 * @param dt     длина шага, с
	 * @return скорость на конец шага
	 */
	public static MoveState afterMove(MoveState state, MoveParams params, double dt) {
		requireStep(dt);

		if (state.onGround()) {
			return new MoveState(state.vx(), state.vy(), 0.0, true);
		}

		double vz = state.vz() - params.gravity() * dt * 0.5;
		return clamp(state.vx(), state.vy(), vz, false, params.maxVelocity());
	}

	/**
	 * Прибавка скорости вдоль желаемого направления за шаг.
	 *
	 * @param limitSpeed до какой скорости вдоль направления разрешено разгоняться
	 * @param accelSpeed от какой скорости считается величина ускорения
	 */
	private static double speedGain(double vx, double vy, double wishDirX, double wishDirY,
			double limitSpeed, double accelSpeed, double accel, double dt) {
		double currentSpeed = vx * wishDirX + vy * wishDirY;
		double room = limitSpeed - currentSpeed;

		if (room <= 0.0) {
			return 0.0;
		}

		return Math.min(accel * accelSpeed * dt, room);
	}

	/** Укорачивает вектор до заданной длины, сохраняя направление. */
	private static double[] limitLength(double x, double y, double limit) {
		double length = Math.hypot(x, y);

		if (length <= limit) {
			return new double[] {x, y};
		}

		return new double[] {x * limit / length, y * limit / length};
	}

	/**
	 * Ограничивает составляющую скорости, направленную назад относительно взгляда.
	 * Боковая составляющая не меняется: пятиться быстро нельзя, а двигаться боком можно.
	 */
	private static double[] limitBackwardSpeed(double vx, double vy, MoveInput input, MoveParams params) {
		double forwardLength = Math.hypot(input.forwardX(), input.forwardY());

		if (forwardLength == 0.0 || params.backSpeedFactor() >= 1.0 || Math.hypot(vx, vy) <= params.backSpeedMin()) {
			return new double[] {vx, vy};
		}

		double forwardX = input.forwardX() / forwardLength;
		double forwardY = input.forwardY() / forwardLength;
		double along = vx * forwardX + vy * forwardY;

		if (along >= 0.0) {
			return new double[] {vx, vy};
		}

		double limit = params.maxSpeed() * params.backSpeedFactor();
		double limitedAlong = Math.max(along, -limit);
		double sideX = vx - forwardX * along;
		double sideY = vy - forwardY * along;

		return limitLength(sideX + forwardX * limitedAlong, sideY + forwardY * limitedAlong, params.maxSpeed());
	}

	private static MoveState clamp(double vx, double vy, double vz, boolean onGround, double maxVelocity) {
		return new MoveState(
				Math.clamp(vx, -maxVelocity, maxVelocity),
				Math.clamp(vy, -maxVelocity, maxVelocity),
				Math.clamp(vz, -maxVelocity, maxVelocity),
				onGround);
	}

	private static void requireStep(double dt) {
		if (!(dt > 0.0) || Double.isInfinite(dt)) {
			throw new IllegalArgumentException("шаг должен быть конечным и положительным: " + dt);
		}
	}
}

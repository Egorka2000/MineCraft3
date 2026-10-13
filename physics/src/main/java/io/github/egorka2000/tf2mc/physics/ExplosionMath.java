package io.github.egorka2000.tf2mc.physics;

/**
 * Урон и отбрасывание от взрыва по правилам TF2. Числа и источники — в docs/05-sources.md,
 * алгоритм своими словами — в docs/02-physics.md.
 *
 * <p>Чистая математика: расстояния в u, скорости в u/s, оси как в TF2 (z вверх).
 */
public final class ExplosionMath {
	/** Доля урона на самом краю взрыва. */
	public static final double EDGE_DAMAGE_FRACTION = 0.5;

	/** Множитель силы для Soldier, которого задел собственный взрыв в воздухе: ракетный прыжок. */
	public static final double SCALE_SELF_AIRBORNE = 10.0;

	/** Множитель силы для Soldier, которого задел собственный взрыв на земле. */
	public static final double SCALE_SELF_GROUNDED = 5.0;

	/** Множитель силы для урона от другого игрока. */
	public static final double SCALE_OTHER = 6.0;

	/** Доля урона, которую Soldier получает от собственного взрыва в воздухе. */
	public static final double SELF_DAMAGE_AIRBORNE = 0.6;

	/** Наибольшая скорость, которую даёт один толчок, u/s. */
	public static final double MAX_PUSH = 1000.0;

	/** Ширина и глубина халла игрока, u. */
	public static final double HULL_WIDTH = 49.0;

	/** Высота халла стоящего игрока, u. */
	public static final double HULL_HEIGHT = 83.0;

	/**
	 * Высота халла, которая подставляется пригнувшемуся игроку при уроне от собственного взрыва:
	 * меньше настоящей, поэтому прыжок в приседе выходит сильнее.
	 */
	public static final double HULL_HEIGHT_CROUCHED_SELF = 55.0;

	/** На сколько точка взрыва опускается при расчёте направления толчка, u. Добавляет толчку подъём. */
	public static final double BLAST_ORIGIN_DROP = 10.0;

	/** Объём эталонного игрока: толчок обратно пропорционален объёму халла. */
	private static final double REFERENCE_VOLUME = 48.0 * 48.0 * 82.0;

	private ExplosionMath() {
	}

	/**
	 * Вектор в единицах TF2.
	 *
	 * @param x горизонтальная составляющая
	 * @param y горизонтальная составляющая
	 * @param z вертикальная составляющая, вверх
	 */
	public record Vector(double x, double y, double z) {
		public static final Vector ZERO = new Vector(0.0, 0.0, 0.0);

		public double length() {
			return Math.sqrt(x * x + y * y + z * z);
		}

		public Vector scale(double factor) {
			return new Vector(x * factor, y * factor, z * factor);
		}
	}

	/**
	 * Урон взрыва на расстоянии от его центра: полный в центре, {@link #EDGE_DAMAGE_FRACTION}
	 * на краю, за краем урона нет.
	 *
	 * @param baseDamage урон в центре взрыва
	 * @param distance   расстояние от центра взрыва до цели, u
	 * @param radius     радиус взрыва, u
	 */
	public static double splashDamage(double baseDamage, double distance, double radius) {
		if (distance > radius || radius <= 0.0) {
			return 0.0;
		}

		double fraction = Math.max(distance, 0.0) / radius;
		return baseDamage * (1.0 - (1.0 - EDGE_DAMAGE_FRACTION) * fraction);
	}

	/**
	 * Скорость, которую урон придаёт игроку: растёт с уроном, обратно пропорциональна объёму халла
	 * и ограничена сверху {@link #MAX_PUSH}.
	 *
	 * @param damage     урон, по которому считается сила
	 * @param scale      множитель силы: {@link #SCALE_SELF_AIRBORNE}, {@link #SCALE_SELF_GROUNDED}
	 *                   или {@link #SCALE_OTHER}
	 * @param hullHeight высота халла игрока, u
	 */
	public static double pushSpeed(double damage, double scale, double hullHeight) {
		if (damage <= 0.0) {
			return 0.0;
		}

		double speed = REFERENCE_VOLUME / (HULL_WIDTH * HULL_WIDTH * hullHeight) * damage * scale;
		return Math.min(speed, MAX_PUSH);
	}

	/**
	 * Направление толчка: от точки взрыва, опущенной на {@link #BLAST_ORIGIN_DROP}, к центру игрока.
	 *
	 * @return единичный вектор; нулевой, если точки совпали
	 */
	public static Vector pushDirection(Vector blast, Vector playerCenter) {
		Vector delta = new Vector(
				playerCenter.x() - blast.x(),
				playerCenter.y() - blast.y(),
				playerCenter.z() - (blast.z() - BLAST_ORIGIN_DROP));
		double length = delta.length();

		return length == 0.0 ? Vector.ZERO : delta.scale(1.0 / length);
	}

	/**
	 * Толчок, который Soldier получает от собственной ракеты. Добавляется к скорости игрока.
	 *
	 * @param damage    урон взрыва по игроку до снижения самоурона
	 * @param airborne  был ли игрок в воздухе в момент взрыва
	 * @param crouching был ли игрок в приседе
	 */
	public static Vector selfPush(Vector blast, Vector playerCenter, double damage, boolean airborne, boolean crouching) {
		double scale = airborne ? SCALE_SELF_AIRBORNE : SCALE_SELF_GROUNDED;
		double hullHeight = crouching ? HULL_HEIGHT_CROUCHED_SELF : HULL_HEIGHT;

		return pushDirection(blast, playerCenter).scale(pushSpeed(damage, scale, hullHeight));
	}

	/** Толчок от взрыва чужой ракеты. Добавляется к скорости игрока. */
	public static Vector otherPush(Vector blast, Vector playerCenter, double damage) {
		return pushDirection(blast, playerCenter).scale(pushSpeed(damage, SCALE_OTHER, HULL_HEIGHT));
	}

	/**
	 * Урон, который Soldier получает от собственной ракеты: в воздухе он снижен.
	 *
	 * @param damage   урон взрыва по игроку
	 * @param airborne был ли игрок в воздухе в момент взрыва
	 */
	public static double selfDamage(double damage, boolean airborne) {
		return airborne ? damage * SELF_DAMAGE_AIRBORNE : damage;
	}
}

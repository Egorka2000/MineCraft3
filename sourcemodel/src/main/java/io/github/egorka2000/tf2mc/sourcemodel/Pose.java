package io.github.egorka2000.tf2mc.sourcemodel;

import java.util.List;

/**
 * Поза скелета: положение (x y z) и поворот (кватернион x y z w) каждой кости относительно
 * родителя. Из позы получаются матрицы для отрисовки: кость в пространство модели и вершина
 * через кость ({@link #skinMatrices}).
 *
 * <p>Перевод углов в кватернион и устройство матрицы 3×4 — по открытому Source SDK 2013
 * ({@code mathlib}): углы x y z — крен, тангаж, рысканье; матрица по строкам, четвёртый столбец —
 * смещение. Код написан заново.
 */
public final class Pose {
	private final float[] positions;
	private final float[] rotations;

	public Pose(int bones) {
		this.positions = new float[bones * 3];
		this.rotations = new float[bones * 4];
	}

	/** Начальная поза модели: положение и поворот костей из MDL. */
	public static Pose rest(List<StudioModel.Bone> bones) {
		Pose pose = new Pose(bones.size());

		for (int i = 0; i < bones.size(); i++) {
			pose.set(i, bones.get(i).position(), bones.get(i).rotation());
		}

		return pose;
	}

	public int bones() {
		return positions.length / 3;
	}

	public float[] positions() {
		return positions;
	}

	public float[] rotations() {
		return rotations;
	}

	public void set(int bone, float[] position, float[] rotation) {
		System.arraycopy(position, 0, positions, bone * 3, 3);
		System.arraycopy(rotation, 0, rotations, bone * 4, 4);
	}

	/** Смешивает две позы: {@code (1 − t)·a + t·b}; повороты — сферической интерполяцией. */
	public static Pose blend(Pose a, Pose b, float t) {
		Pose out = new Pose(a.bones());

		for (int i = 0; i < a.positions.length; i++) {
			out.positions[i] = a.positions[i] + (b.positions[i] - a.positions[i]) * t;
		}

		float[] q = new float[4];

		for (int bone = 0; bone < a.bones(); bone++) {
			slerp(a.rotations, b.rotations, bone * 4, t, q);
			System.arraycopy(q, 0, out.rotations, bone * 4, 4);
		}

		return out;
	}

	/**
	 * Матрицы отрисовки: для каждой кости 12 чисел, матрица 3×4 по строкам, которая переводит
	 * вершину из пространства привязки (как она записана в VVD) в пространство модели в этой позе.
	 */
	public float[] skinMatrices(List<StudioModel.Bone> bones) {
		int count = bones.size();
		float[] world = new float[count * 12];
		float[] local = new float[12];
		float[] skin = new float[count * 12];

		for (int i = 0; i < count; i++) {
			matrix(rotations, i * 4, positions, i * 3, local);
			int parent = bones.get(i).parent();

			if (parent < 0) {
				System.arraycopy(local, 0, world, i * 12, 12);
			} else {
				concat(world, parent * 12, local, 0, world, i * 12);
			}

			concat(world, i * 12, bones.get(i).poseToBone(), 0, skin, i * 12);
		}

		return skin;
	}

	/** Углы x y z (радианы) в кватернион x y z w. */
	public static float[] eulerToQuaternion(float x, float y, float z) {
		double sr = Math.sin(x * 0.5);
		double cr = Math.cos(x * 0.5);
		double sp = Math.sin(y * 0.5);
		double cp = Math.cos(y * 0.5);
		double sy = Math.sin(z * 0.5);
		double cy = Math.cos(z * 0.5);

		return new float[] {
			(float) (sr * cp * cy - cr * sp * sy),
			(float) (cr * sp * cy + sr * cp * sy),
			(float) (cr * cp * sy - sr * sp * cy),
			(float) (cr * cp * cy + sr * sp * sy)};
	}

	/** Сферическая интерполяция кватернионов {@code a[at..]} и {@code b[at..]} по кратчайшему пути. */
	static void slerp(float[] a, float[] b, int at, float t, float[] out) {
		double ax = a[at];
		double ay = a[at + 1];
		double az = a[at + 2];
		double aw = a[at + 3];
		double bx = b[at];
		double by = b[at + 1];
		double bz = b[at + 2];
		double bw = b[at + 3];
		double dot = ax * bx + ay * by + az * bz + aw * bw;

		if (dot < 0.0) {
			dot = -dot;
			bx = -bx;
			by = -by;
			bz = -bz;
			bw = -bw;
		}

		double wa;
		double wb;

		if (dot > 0.9995) {
			wa = 1.0 - t;
			wb = t;
		} else {
			double angle = Math.acos(dot);
			double sin = Math.sin(angle);
			wa = Math.sin((1.0 - t) * angle) / sin;
			wb = Math.sin(t * angle) / sin;
		}

		double x = wa * ax + wb * bx;
		double y = wa * ay + wb * by;
		double z = wa * az + wb * bz;
		double w = wa * aw + wb * bw;
		double length = Math.sqrt(x * x + y * y + z * z + w * w);
		out[0] = (float) (x / length);
		out[1] = (float) (y / length);
		out[2] = (float) (z / length);
		out[3] = (float) (w / length);
	}

	/** Кватернион и смещение в матрицу 3×4 по строкам. */
	static void matrix(float[] q, int qi, float[] p, int pi, float[] out) {
		float x = q[qi];
		float y = q[qi + 1];
		float z = q[qi + 2];
		float w = q[qi + 3];

		out[0] = 1 - 2 * y * y - 2 * z * z;
		out[1] = 2 * x * y - 2 * w * z;
		out[2] = 2 * x * z + 2 * w * y;
		out[3] = p[pi];
		out[4] = 2 * x * y + 2 * w * z;
		out[5] = 1 - 2 * x * x - 2 * z * z;
		out[6] = 2 * y * z - 2 * w * x;
		out[7] = p[pi + 1];
		out[8] = 2 * x * z - 2 * w * y;
		out[9] = 2 * y * z + 2 * w * x;
		out[10] = 1 - 2 * x * x - 2 * y * y;
		out[11] = p[pi + 2];
	}

	/** {@code out = a · b} для матриц 3×4: сначала применяется b, потом a. */
	static void concat(float[] a, int ai, float[] b, int bi, float[] out, int oi) {
		float[] r = new float[12];

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 4; col++) {
				float sum = a[ai + row * 4] * b[bi + col] + a[ai + row * 4 + 1] * b[bi + 4 + col] + a[ai + row * 4 + 2] * b[bi + 8 + col];
				r[row * 4 + col] = col == 3 ? sum + a[ai + row * 4 + 3] : sum;
			}
		}

		System.arraycopy(r, 0, out, oi, 12);
	}
}

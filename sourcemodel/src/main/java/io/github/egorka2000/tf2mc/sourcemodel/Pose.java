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
	 * Накладывает на позу поправку из дельта-анимации с весом {@code weight}: смещения
	 * прибавляются, повороты домножаются на долю поправки. У поправок «после» ({@code post})
	 * поворот поправки применяется после основного: основной · поправка; иначе поправка · основной.
	 */
	public static Pose addDelta(Pose base, Pose delta, float weight, boolean post) {
		return layer(base, delta, weight, null, true, post);
	}

	/**
	 * Накладывает позу последовательности на уже набранную позу, как игра TF2: каждая кость — со
	 * своей силой {@code weight × boneWeights[кость]}. Поправка (дельта) прибавляется
	 * ({@link #addDelta}), обычная поза смешивается ({@link #blend}). Кости с силой 0 не меняются.
	 *
	 * <p>Правило — по открытому Source SDK 2013 ({@code bone_setup.cpp}, смешивание костей);
	 * код написан заново.
	 *
	 * @param boneWeights веса костей из последовательности; {@code null} — все 1
	 */
	public static Pose layer(Pose base, Pose over, float weight, float[] boneWeights, boolean delta, boolean post) {
		Pose out = new Pose(base.bones());
		float[] identity = {0, 0, 0, 1};
		float[] scaled = new float[4];
		float[] q = new float[4];
		float s = Math.clamp(weight, 0.0F, 1.0F);

		for (int bone = 0; bone < base.bones(); bone++) {
			float w = s * (boneWeights == null || bone >= boneWeights.length ? 1.0F : boneWeights[bone]);
			int p = bone * 3;
			int r = bone * 4;

			if (w <= 0.0F) {
				System.arraycopy(base.positions, p, out.positions, p, 3);
				System.arraycopy(base.rotations, r, out.rotations, r, 4);
				continue;
			}

			if (delta) {
				for (int i = 0; i < 3; i++) {
					out.positions[p + i] = base.positions[p + i] + over.positions[p + i] * w;
				}

				float[] d = {over.rotations[r], over.rotations[r + 1], over.rotations[r + 2], over.rotations[r + 3]};
				slerp(identity, d, 0, Math.min(w, 1.0F), scaled);
				float[] b = {base.rotations[r], base.rotations[r + 1], base.rotations[r + 2], base.rotations[r + 3]};
				float[] result = post ? multiply(b, scaled) : multiply(scaled, b);
				normalize(result);
				System.arraycopy(result, 0, out.rotations, r, 4);
			} else {
				float t = Math.min(w, 1.0F);

				for (int i = 0; i < 3; i++) {
					out.positions[p + i] = base.positions[p + i] + (over.positions[p + i] - base.positions[p + i]) * t;
				}

				slerp(base.rotations, over.rotations, r, t, q);
				System.arraycopy(q, 0, out.rotations, r, 4);
			}
		}

		return out;
	}

	/** Произведение кватернионов {@code a · b}: сначала поворот b, затем a. */
	static float[] multiply(float[] a, float[] b) {
		return new float[] {
			a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
			a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
			a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
			a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
	}

	private static void normalize(float[] q) {
		float length = (float) Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);

		for (int i = 0; i < 4; i++) {
			q[i] /= length;
		}
	}

	/**
	 * Матрицы отрисовки: для каждой кости 12 чисел, матрица 3×4 по строкам, которая переводит
	 * вершину из пространства привязки (как она записана в VVD) в пространство модели в этой позе.
	 */
	public float[] skinMatrices(List<StudioModel.Bone> bones) {
		return skinMatrices(bones, worldMatrices(bones));
	}

	/** Матрицы костей в пространстве модели: 12 чисел на кость, 3×4 по строкам. */
	public float[] worldMatrices(List<StudioModel.Bone> bones) {
		int count = bones.size();
		float[] world = new float[count * 12];
		float[] local = new float[12];

		for (int i = 0; i < count; i++) {
			matrix(rotations, i * 4, positions, i * 3, local);
			int parent = bones.get(i).parent();

			if (parent < 0) {
				System.arraycopy(local, 0, world, i * 12, 12);
			} else {
				concat(world, parent * 12, local, 0, world, i * 12);
			}
		}

		return world;
	}

	/**
	 * Матрицы отрисовки модели, прикреплённой к другой, как оружие TF2 к персонажу: кость с тем же
	 * именем, что у кости носителя, повторяет её положение; остальные висят на своих родителях
	 * в начальной позе.
	 *
	 * @param carrierWorld матрицы костей носителя ({@link #worldMatrices})
	 */
	public static float[] attachedSkinMatrices(List<StudioModel.Bone> bones, List<StudioModel.Bone> carrierBones, float[] carrierWorld) {
		int count = bones.size();
		float[] world = new float[count * 12];
		float[] local = new float[12];

		for (int i = 0; i < count; i++) {
			StudioModel.Bone bone = bones.get(i);
			int carrier = indexOf(carrierBones, bone.name());

			if (carrier >= 0) {
				System.arraycopy(carrierWorld, carrier * 12, world, i * 12, 12);
				continue;
			}

			matrix(bone.rotation(), 0, bone.position(), 0, local);

			if (bone.parent() < 0) {
				System.arraycopy(local, 0, world, i * 12, 12);
			} else {
				concat(world, bone.parent() * 12, local, 0, world, i * 12);
			}
		}

		return skinMatrices(bones, world);
	}

	private static float[] skinMatrices(List<StudioModel.Bone> bones, float[] world) {
		float[] skin = new float[bones.size() * 12];

		for (int i = 0; i < bones.size(); i++) {
			concat(world, i * 12, bones.get(i).poseToBone(), 0, skin, i * 12);
		}

		return skin;
	}

	private static int indexOf(List<StudioModel.Bone> bones, String name) {
		for (int i = 0; i < bones.size(); i++) {
			if (bones.get(i).name().equalsIgnoreCase(name)) {
				return i;
			}
		}

		return -1;
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

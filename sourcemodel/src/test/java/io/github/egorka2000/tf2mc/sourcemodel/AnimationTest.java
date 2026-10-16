package io.github.egorka2000.tf2mc.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

class AnimationTest {
	private static final float EPSILON = 1e-4F;

	/** Сжатая дорожка из серий: каждая серия — сколько значений, сколько кадров, значения. */
	private static Bytes track(int[]... runs) {
		ByteBuffer out = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
		int at = 0;

		for (int[] run : runs) {
			out.put(at, (byte) run[0]);
			out.put(at + 1, (byte) run[1]);
			at += 2;

			for (int i = 2; i < run.length; i++) {
				out.putShort(at, (short) run[i]);
				at += 2;
			}
		}

		return new Bytes(out.array(), "test");
	}

	@Test
	void compressedTrackRepeatsLastValueAndCrossesRuns() {
		// Серия 1: два значения на четыре кадра; серия 2: одно значение на два кадра.
		Bytes in = track(new int[] {2, 4, 10, 20}, new int[] {1, 2, 30});

		assertArrayEquals(new int[] {10, 20}, AnimationSet.extract(in, 0, 0));
		assertArrayEquals(new int[] {20, 20}, AnimationSet.extract(in, 0, 1));
		// Кадры за последним записанным значением повторяют его, а последний кадр серии смотрит в следующую.
		assertArrayEquals(new int[] {20, 20}, AnimationSet.extract(in, 0, 2));
		assertArrayEquals(new int[] {20, 30}, AnimationSet.extract(in, 0, 3));
		assertArrayEquals(new int[] {30, 30}, AnimationSet.extract(in, 0, 4));
	}

	@Test
	void singleValueTrackIsConstant() {
		assertArrayEquals(new int[] {7, 7}, AnimationSet.extract(track(new int[] {1, 1, 7}), 0, 0));
	}

	@Test
	void halfFloatsAreDecoded() {
		assertEquals(1.0F, AnimationSet.half(0x3C00));
		assertEquals(-2.0F, AnimationSet.half(0xC000));
		assertEquals(0.5F, AnimationSet.half(0x3800));
		assertEquals(65504.0F, AnimationSet.half(0x7C00));
		assertEquals(0.0F, AnimationSet.half(0x7E00));
		assertEquals((float) Math.pow(2, -24), AnimationSet.half(0x0001));
	}

	@Test
	void eulerAnglesBecomeQuaternions() {
		assertArrayEquals(new float[] {0, 0, 0, 1}, Pose.eulerToQuaternion(0, 0, 0), EPSILON);
		// Угол z — поворот вокруг z.
		float half = (float) Math.sin(0.5);
		assertArrayEquals(new float[] {0, 0, half, (float) Math.cos(0.5)}, Pose.eulerToQuaternion(0, 0, 1), EPSILON);
		assertArrayEquals(new float[] {half, 0, 0, (float) Math.cos(0.5)}, Pose.eulerToQuaternion(1, 0, 0), EPSILON);
	}

	@Test
	void restPoseSkinsToIdentity() {
		// В начальной позе вершина остаётся там, где записана: кость · обратная привязка = единичная.
		List<StudioModel.Bone> bones = StudioModel.read(TinyModel.mdl()).bones();
		float[] skin = Pose.rest(bones).skinMatrices(bones);
		float[] identity = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0};

		for (int bone = 0; bone < bones.size(); bone++) {
			for (int i = 0; i < 12; i++) {
				assertEquals(identity[i], skin[bone * 12 + i], EPSILON, "кость " + bone + ", элемент " + i);
			}
		}
	}

	@Test
	void tinyAnimationIsSampled() {
		AnimationSet set = AnimationSet.read(TinyModel.animations());

		assertEquals(List.of("move_x"), set.parameters().stream().map(AnimationSet.PoseParameter::name).toList());
		assertEquals(0, set.parameter("MOVE_X"));
		assertEquals(-1, set.parameter("move_y"));
		AnimationSet.Sequence idle = set.sequence("IDLE").orElseThrow();
		assertTrue(idle.looping());
		assertEquals("a_idle", set.animations().get(idle.animation(0, 0)).name());
		assertEquals(3, set.animations().get(0).frames());

		// Середина: кадр 1 — угол 1 радиан вокруг z; смещение 5 × 0,5 по x плюс начальное (0, 0, 10).
		Pose middle = set.sample(idle, 0.5F, new float[1]);
		assertArrayEquals(new float[] {0, 0, (float) Math.sin(0.5), (float) Math.cos(0.5)},
				java.util.Arrays.copyOfRange(middle.rotations(), 4, 8), EPSILON);
		assertArrayEquals(new float[] {2.5F, 0, 10}, java.util.Arrays.copyOfRange(middle.positions(), 3, 6), EPSILON);
		// Корневая кость не анимирована: начальная поза.
		assertArrayEquals(new float[] {0, 0, 0, 1}, java.util.Arrays.copyOfRange(middle.rotations(), 0, 4), EPSILON);

		// Между кадрами 1 и 2: угол 1,5 радиана.
		Pose between = set.sample(idle, 0.75F, new float[1]);
		assertArrayEquals(new float[] {0, 0, (float) Math.sin(0.75), (float) Math.cos(0.75)},
				java.util.Arrays.copyOfRange(between.rotations(), 4, 8), EPSILON);
	}

	@Test
	void posesBlendHalfway() {
		Pose a = new Pose(1);
		a.set(0, new float[] {0, 0, 0}, new float[] {0, 0, 0, 1});
		Pose b = new Pose(1);
		b.set(0, new float[] {2, 0, 0}, Pose.eulerToQuaternion(0, 0, 1));
		Pose half = Pose.blend(a, b, 0.5F);

		assertEquals(1.0F, half.positions()[0], EPSILON);
		assertArrayEquals(Pose.eulerToQuaternion(0, 0, 0.5F), half.rotations(), EPSILON);
	}

	@Test
	void realSoldierAnimationsStandUpright() throws IOException {
		Path dir = Path.of(System.getProperty("user.home"), "Documents", "tf2mc-expansion-pack", "pack", "models", "player");
		String env = System.getenv("TF2MC_PACK_DIR");
		if (env != null) {
			dir = Path.of(env, "models", "player");
		}

		assumeTrue(Files.isRegularFile(dir.resolve("soldier_animations.mdl")), "нет расширительного пака");
		SourceModel model = SourceModel.read(Files.readAllBytes(dir.resolve("soldier.mdl")), Files.readAllBytes(dir.resolve("soldier.vvd")),
				Files.readAllBytes(dir.resolve("soldier.dx90.vtx")), 0);
		AnimationSet set = AnimationSet.read(Files.readAllBytes(dir.resolve("soldier_animations.mdl")));
		List<StudioModel.Bone> bones = model.studio().bones();

		// Кости файла анимаций — те же и в том же порядке.
		assertEquals(bones.stream().map(StudioModel.Bone::name).toList(), set.bones().stream().map(StudioModel.Bone::name).toList());

		float[] poseValues = new float[set.parameters().size()];
		poseValues[set.parameter("move_x")] = 1.0F;

		for (String name : List.of("stand_PRIMARY", "run_PRIMARY", "crouch_PRIMARY", "jump_float_PRIMARY")) {
			AnimationSet.Sequence sequence = set.sequence(name).orElseThrow();

			for (float cycle : new float[] {0.0F, 0.5F, 1.0F}) {
				Pose pose = set.sample(sequence, cycle, poseValues);
				float[] q = pose.rotations();

				for (int bone = 0; bone < pose.bones(); bone++) {
					float length = q[bone * 4] * q[bone * 4] + q[bone * 4 + 1] * q[bone * 4 + 1] + q[bone * 4 + 2] * q[bone * 4 + 2] + q[bone * 4 + 3] * q[bone * 4 + 3];
					assertEquals(1.0, length, 1e-3, name + ": кватернион кости " + bone);
				}

				// После анимации модель стоит по оси z: верх выше 40 u, ступни не ниже −5 u (в прыжке — выше).
				float[] height = heightRange(model, pose.skinMatrices(bones));
				assertTrue(height[1] > 40 && height[0] > -5, name + " " + cycle + ": высота " + height[0] + ".." + height[1]);
			}
		}
	}

	private static float[] heightRange(SourceModel model, float[] skin) {
		float[] p = model.vertices().positions();
		int[] bones = model.vertices().boneIndices();
		float[] weights = model.vertices().boneWeights();
		float min = Float.MAX_VALUE;
		float max = -Float.MAX_VALUE;

		for (int v = 0; v < model.vertices().count(); v++) {
			float z = 0;

			for (int k = 0; k < 3; k++) {
				int s = bones[v * 3 + k] * 12 + 8;
				z += weights[v * 3 + k] * (skin[s] * p[v * 3] + skin[s + 1] * p[v * 3 + 1] + skin[s + 2] * p[v * 3 + 2] + skin[s + 3]);
			}

			min = Math.min(min, z);
			max = Math.max(max, z);
		}

		return new float[] {min, max};
	}
}

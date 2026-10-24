package io.github.egorka2000.tf2mc.sourcemodel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Анимации модели TF2 из файла MDL (у игроков — {@code *_animations.mdl}): параметры позы,
 * последовательности и сами анимации, сжатые по костям.
 *
 * <p>Устройство описаний, последовательностей и сжатия — по открытому Source SDK 2013
 * ({@code studio.h}, {@code studio.cpp}, {@code bone_setup.cpp}, {@code compressed_vector.h}):
 * на Valve Developer Wiki их нет. Код написан заново. Поддерживаются анимации внутри файла
 * (блок 0), в том числе разбитые на секции; внешние блоки {@code .ani} — нет.
 */
public final class AnimationSet {
	private static final int SEQUENCE_SIZE = 212;
	private static final int ANIMATION_SIZE = 100;
	private static final int POSE_PARAMETER_SIZE = 20;

	/** Последовательность повторяется по кругу. */
	public static final int SEQUENCE_LOOPING = 0x0001;
	/** Последовательность — поправка к позе (дельта). */
	public static final int SEQUENCE_DELTA = 0x0004;
	/** Поправка применяется после основного поворота. */
	public static final int SEQUENCE_POST = 0x0400;

	private static final int AUTO_LAYER_SIZE = 24;
	private static final int EVENT_SIZE = 80;
	/** Событие анимации старого вида, у которого нет имени: проиграть звук на клиенте. */
	private static final int EVENT_CLIENT_SOUND = 5004;
	/** Глубина вложенных слоёв ограничена: в чужом файле слой мог бы ссылаться сам на себя. */
	private static final int MAX_LAYER_DEPTH = 4;
	/** Нарастание и спад силы слоя сглажены кривой 3s² − 2s³. */
	public static final int LAYER_SPLINE = 0x0040;
	/** На спаде слой перетекает в основную позу, а не просто слабеет. */
	public static final int LAYER_XFADE = 0x0080;
	/** Сила слоя не зависит от силы основной последовательности. */
	public static final int LAYER_NOBLEND = 0x0200;
	/** Слой, который игра накладывает отдельно, по своему времени: здесь не поддерживается. */
	public static final int LAYER_LOCAL = 0x1000;
	/** Отрезок слоя отсчитывается не по времени, а по значению параметра позы. */
	public static final int LAYER_POSE = 0x4000;

	/**
	 * Слой, который последовательность накладывает на себя сама, например матрица прицеливания или
	 * руки в жесте перезарядки. Если начало и конец совпадают, слой действует всегда, с тем же
	 * временем, что и основная последовательность. Иначе — только на отрезке от начала до конца
	 * (по доле проигрывания или по параметру позы {@code pose}), нарастая до пика и спадая после
	 * хвоста; время слоя на отрезке идёт от 0 до 1.
	 *
	 * <p>Правила — по открытому Source SDK 2013 ({@code bone_setup.cpp}, наложение слоёв
	 * последовательности); код написан заново.
	 */
	public record AutoLayer(int sequence, int pose, int flags, float start, float peak, float tail, float end) {
		/**
		 * Сила слоя без учёта силы основной последовательности; 0 — вне отрезка.
		 *
		 * @param index доля проигрывания основной последовательности, а у слоя по параметру позы — значение параметра
		 */
		public float strength(float index) {
			if (start == end) {
				return 1.0F;
			}

			if (index < start || index >= end) {
				return 0.0F;
			}

			float s = 1.0F;

			if (index < peak && peak != start) {
				s = (index - start) / (peak - start);
			} else if (index > tail && end != tail) {
				s = (end - index) / (end - tail);
			}

			return (flags & LAYER_SPLINE) != 0 ? s * s * (3.0F - 2.0F * s) : s;
		}

		/** Сила слоя, если основная последовательность наложена с силой {@code weight}. */
		public float weight(float index, float weight) {
			float s = strength(index);

			if (start == end) {
				return weight;
			}

			if ((flags & LAYER_XFADE) != 0 && index > tail) {
				return s * weight / (1.0F - weight + s * weight);
			}

			return (flags & LAYER_NOBLEND) != 0 ? s : weight * s;
		}

		/** Время слоя: на отрезке по времени — доля отрезка, иначе — время основной последовательности. */
		public float cycle(float cycle) {
			return start == end || (flags & LAYER_POSE) != 0 ? cycle : (cycle - start) / (end - start);
		}
	}

	// Флаги данных кости в анимации.
	private static final int RAW_POSITION = 0x01;
	private static final int RAW_ROTATION = 0x02;
	private static final int ANIMATED_POSITION = 0x04;
	private static final int ANIMATED_ROTATION = 0x08;
	private static final int DELTA = 0x10;
	private static final int RAW_ROTATION_64 = 0x20;

	/** Параметр позы, например {@code move_x}: от значения зависит смешивание анимаций. */
	public record PoseParameter(String name, float start, float end, float loop) {
	}

	/**
	 * Последовательность: сетка анимаций, смешиваемых по одному или двум параметрам позы.
	 *
	 * @param groupSize  размер сетки по каждому параметру (1, если параметр не используется)
	 * @param parameters номера параметров позы по осям сетки; -1 — нет
	 * @param blends      номера анимаций в сетке построчно, {@code groupSize[0] × groupSize[1]}
	 * @param boneWeights сила последовательности по костям, 0..1; у жестов для рук — только кости рук
	 * @param fadeIn      за сколько секунд игра TF2 переходит на эту последовательность
	 * @param fadeOut     за сколько секунд игра TF2 уходит с неё
	 * @param events      события в ходе последовательности, по возрастанию доли
	 */
	public record Sequence(String name, String activity, int flags, int[] groupSize, int[] parameters,
			float[] parameterStart, float[] parameterEnd, int[] blends, List<AutoLayer> layers, float[] boneWeights, float fadeIn, float fadeOut,
			List<Event> events) {
		/** Звуки, которые последовательность играет сама, с долей, на которой они звучат. */
		public List<Event> sounds() {
			return events.stream().filter(Event::sound).toList();
		}

		/** Последовательность — поправка к другой позе, а не поза целиком. */
		public boolean delta() {
			return (flags & SEQUENCE_DELTA) != 0;
		}

		/** Поправка применяется после основного поворота кости. */
		public boolean post() {
			return (flags & SEQUENCE_POST) != 0;
		}
		public boolean looping() {
			return (flags & SEQUENCE_LOOPING) != 0;
		}

		/** Анимация в узле сетки; номера за пределами прижимаются к краю. */
		public int animation(int x, int y) {
			int cx = Math.clamp(x, 0, groupSize[0] - 1);
			int cy = Math.clamp(y, 0, groupSize[1] - 1);
			return blends[cy * groupSize[0] + cx];
		}
	}

	/**
	 * Событие последовательности: на доле {@code cycle} игра делает что-то помимо позы — например,
	 * у рук от первого лица играет звук помпы дробовика.
	 *
	 * @param event   номер события старого вида; у событий нового вида — 0, а смысл в имени
	 * @param name    имя события нового вида, например {@code AE_CL_PLAYSOUND}; у старого — пусто
	 * @param options параметр события: для звука — имя звука TF2
	 */
	public record Event(float cycle, int event, String name, String options) {
		/** Событие проигрывает звук на клиенте. */
		public boolean sound() {
			return event == EVENT_CLIENT_SOUND || name.equalsIgnoreCase("AE_CL_PLAYSOUND");
		}
	}

	/** Описание анимации: где лежат её данные и сколько в ней кадров. */
	public record Animation(String name, float fps, int frames, int flags, int offset, int dataOffset, int sectionFrames, int sectionOffset) {
	}

	private final Bytes in;
	private final List<StudioModel.Bone> bones;
	private final List<PoseParameter> parameters;
	private final List<Sequence> sequences;
	private final List<Animation> animations;
	private final Map<String, Integer> sequenceByName = new HashMap<>();

	private AnimationSet(Bytes in, List<StudioModel.Bone> bones, List<PoseParameter> parameters, List<Sequence> sequences, List<Animation> animations) {
		this.in = in;
		this.bones = bones;
		this.parameters = parameters;
		this.sequences = sequences;
		this.animations = animations;

		for (int i = 0; i < sequences.size(); i++) {
			sequenceByName.putIfAbsent(sequences.get(i).name().toLowerCase(Locale.ROOT), i);
		}
	}

	public static AnimationSet read(byte[] data) {
		Bytes in = new Bytes(data, "MDL");

		if (!in.magic().equals("IDST")) {
			throw new ModelFormatException("MDL: не файл модели Source, начало " + in.magic());
		}

		int version = in.int32(4);

		if (version < StudioModel.MIN_VERSION || version > StudioModel.MAX_VERSION) {
			throw new ModelFormatException("MDL: версия " + version + " не поддерживается");
		}

		List<StudioModel.Bone> bones = StudioModel.readBones(in);
		return new AnimationSet(in, bones, readParameters(in), readSequences(in, bones.size()), readAnimations(in));
	}

	private static List<PoseParameter> readParameters(Bytes in) {
		int count = in.int32(300);
		int offset = in.int32(304);
		in.checkArray(offset, count, POSE_PARAMETER_SIZE, "параметры позы");
		List<PoseParameter> list = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			int at = offset + i * POSE_PARAMETER_SIZE;
			list.add(new PoseParameter(in.string(at + in.int32(at)), in.float32(at + 8), in.float32(at + 12), in.float32(at + 16)));
		}

		return List.copyOf(list);
	}

	private static List<Sequence> readSequences(Bytes in, int bones) {
		int count = in.int32(188);
		int offset = in.int32(192);
		in.checkArray(offset, count, SEQUENCE_SIZE, "последовательности");
		List<Sequence> list = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			int at = offset + i * SEQUENCE_SIZE;
			int[] groupSize = {Math.max(1, in.int32(at + 68)), Math.max(1, in.int32(at + 72))};
			int blendOffset = at + in.int32(at + 60);
			in.checkArray(blendOffset, groupSize[0] * groupSize[1], 2, "сетка анимаций");
			int[] blends = new int[groupSize[0] * groupSize[1]];

			for (int b = 0; b < blends.length; b++) {
				blends[b] = in.int16(blendOffset + b * 2);
			}

			int activity = in.int32(at + 8);
			list.add(new Sequence(in.string(at + in.int32(at + 4)), activity == 0 ? "" : in.string(at + activity), in.int32(at + 12), groupSize,
					new int[] {in.int32(at + 76), in.int32(at + 80)}, new float[] {in.float32(at + 84), in.float32(at + 88)},
					new float[] {in.float32(at + 92), in.float32(at + 96)}, blends, readLayers(in, at), readBoneWeights(in, at, bones),
					in.float32(at + 104), in.float32(at + 108), readEvents(in, at)));
		}

		return List.copyOf(list);
	}

	/**
	 * События последовательности. Описание события: доля, номер, вид, параметр (строка в 64 байта),
	 * смещение имени от начала описания; у событий нового вида номер не задан, а смысл — в имени.
	 */
	private static List<Event> readEvents(Bytes in, int sequence) {
		int count = in.int32(sequence + 24);

		if (count <= 0) {
			return List.of();
		}

		int offset = sequence + in.int32(sequence + 28);
		in.checkArray(offset, count, EVENT_SIZE, "события последовательности");
		List<Event> events = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			int at = offset + i * EVENT_SIZE;
			int name = in.int32(at + 76);
			events.add(new Event(in.float32(at), in.int32(at + 4), name == 0 ? "" : in.string(at + name), in.string(at + 12)));
		}

		return List.copyOf(events);
	}

	/** Веса костей последовательности; без списка весов — все 1. */
	private static float[] readBoneWeights(Bytes in, int sequence, int bones) {
		int offset = in.int32(sequence + 156);
		float[] weights = new float[bones];

		if (offset == 0) {
			Arrays.fill(weights, 1.0F);
			return weights;
		}

		in.checkArray(sequence + offset, bones, 4, "веса костей последовательности");

		for (int i = 0; i < bones; i++) {
			weights[i] = in.float32(sequence + offset + i * 4);
		}

		return weights;
	}

	private static List<AutoLayer> readLayers(Bytes in, int sequence) {
		int count = in.int32(sequence + 148);
		int offset = sequence + in.int32(sequence + 152);

		if (count <= 0) {
			return List.of();
		}

		in.checkArray(offset, count, AUTO_LAYER_SIZE, "слои последовательности");
		List<AutoLayer> layers = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			int at = offset + i * AUTO_LAYER_SIZE;
			layers.add(new AutoLayer(in.int16(at), in.int16(at + 2), in.int32(at + 4), in.float32(at + 8), in.float32(at + 12), in.float32(at + 16), in.float32(at + 20)));
		}

		return List.copyOf(layers);
	}

	private static List<Animation> readAnimations(Bytes in) {
		int count = in.int32(180);
		int offset = in.int32(184);
		in.checkArray(offset, count, ANIMATION_SIZE, "анимации");
		List<Animation> list = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			int at = offset + i * ANIMATION_SIZE;
			list.add(new Animation(in.string(at + in.int32(at + 4)), in.float32(at + 8), Math.max(1, in.int32(at + 16)), in.int32(at + 12),
					at, in.int32(at + 56), in.int32(at + 84), in.int32(at + 80)));
		}

		return List.copyOf(list);
	}

	/**
	 * Тот же набор для модели, у которой после костей анимаций есть ещё свои кости: у Scout это
	 * вспомогательные кости предплечий {@code hlp_forearm_L/R}. В анимациях их нет, поэтому они
	 * остаются в начальной позе относительно своих родителей.
	 *
	 * @param modelBones кости модели; первые должны совпадать с костями набора по именам и порядку
	 * @return пусто, если кости модели не начинаются с костей набора
	 */
	public Optional<AnimationSet> forModelBones(List<StudioModel.Bone> modelBones) {
		if (modelBones.size() < bones.size()) {
			return Optional.empty();
		}

		for (int i = 0; i < bones.size(); i++) {
			if (!bones.get(i).name().equals(modelBones.get(i).name())) {
				return Optional.empty();
			}
		}

		if (modelBones.size() == bones.size()) {
			return Optional.of(this);
		}

		List<StudioModel.Bone> extended = new ArrayList<>(bones);
		extended.addAll(modelBones.subList(bones.size(), modelBones.size()));
		List<Sequence> widened = new ArrayList<>(sequences.size());

		for (Sequence sequence : sequences) {
			float[] weights = Arrays.copyOf(sequence.boneWeights(), extended.size());
			Arrays.fill(weights, bones.size(), weights.length, 1.0F);
			widened.add(new Sequence(sequence.name(), sequence.activity(), sequence.flags(), sequence.groupSize(), sequence.parameters(),
					sequence.parameterStart(), sequence.parameterEnd(), sequence.blends(), sequence.layers(), weights, sequence.fadeIn(),
					sequence.fadeOut(), sequence.events()));
		}

		return Optional.of(new AnimationSet(in, List.copyOf(extended), parameters, List.copyOf(widened), animations));
	}

	public List<StudioModel.Bone> bones() {
		return bones;
	}

	public List<PoseParameter> parameters() {
		return parameters;
	}

	public List<Sequence> sequences() {
		return sequences;
	}

	public List<Animation> animations() {
		return animations;
	}

	/** Последовательность по имени без учёта регистра. */
	public Optional<Sequence> sequence(String name) {
		Integer index = sequenceByName.get(name.toLowerCase(Locale.ROOT));
		return index == null ? Optional.empty() : Optional.of(sequences.get(index));
	}

	/** Номер параметра позы по имени; -1, если такого нет. */
	public int parameter(String name) {
		for (int i = 0; i < parameters.size(); i++) {
			if (parameters.get(i).name().equalsIgnoreCase(name)) {
				return i;
			}
		}

		return -1;
	}

	/**
	 * Поза последовательности.
	 *
	 * @param cycle      доля проигрывания 0..1
	 * @param poseValues значения параметров позы по их номерам; лишние и недостающие — 0
	 */
	public Pose sample(Sequence sequence, float cycle, float[] poseValues) {
		float[] grid = new float[2];
		int[] cell = new int[2];

		for (int axis = 0; axis < 2; axis++) {
			int size = sequence.groupSize()[axis];
			int parameter = sequence.parameters()[axis];

			if (size <= 1 || parameter < 0) {
				continue;
			}

			float value = parameter < poseValues.length ? poseValues[parameter] : 0.0F;
			float start = sequence.parameterStart()[axis];
			float end = sequence.parameterEnd()[axis];
			float position = end == start ? 0.0F : Math.clamp((value - start) / (end - start), 0.0F, 1.0F) * (size - 1);
			cell[axis] = Math.min((int) position, size - 2);
			grid[axis] = position - cell[axis];
		}

		Pose bottom = Pose.blend(sample(animations.get(sequence.animation(cell[0], cell[1])), cycle, sequence.delta()),
				sample(animations.get(sequence.animation(cell[0] + 1, cell[1])), cycle, sequence.delta()), grid[0]);

		if (sequence.groupSize()[1] <= 1) {
			return bottom;
		}

		Pose top = Pose.blend(sample(animations.get(sequence.animation(cell[0], cell[1] + 1)), cycle, sequence.delta()),
				sample(animations.get(sequence.animation(cell[0] + 1, cell[1] + 1)), cycle, sequence.delta()), grid[0]);
		return Pose.blend(bottom, top, grid[1]);
	}

	/**
	 * Поза последовательности вместе с её слоями: на основную позу накладываются слои
	 * последовательности (у игроков TF2 — матрица прицеливания по {@code body_yaw} и
	 * {@code body_pitch}) с той же долей проигрывания и теми же параметрами позы.
	 */
	public Pose sampleWithLayers(Sequence sequence, float cycle, float[] poseValues) {
		return accumulate(Pose.rest(bones), sequence, cycle, 1.0F, poseValues);
	}

	/**
	 * Накладывает последовательность со всеми её слоями на уже набранную позу, как игра TF2
	 * накладывает жесты (выстрел, перезарядка) поверх основной анимации: сначала сама
	 * последовательность с силой {@code weight} по весам костей ({@link Pose#layer}), затем её слои
	 * ({@link AutoLayer}), каждый тем же способом.
	 *
	 * <p>Порядок — по открытому Source SDK 2013 ({@code bone_setup.cpp}, накопление позы); код
	 * написан заново.
	 */
	public Pose accumulate(Pose base, Sequence sequence, float cycle, float weight, float[] poseValues) {
		return accumulate(base, sequence, cycle, weight, poseValues, 0);
	}

	private Pose accumulate(Pose base, Sequence sequence, float cycle, float weight, float[] poseValues, int depth) {
		if (weight <= 0.0F) {
			return base;
		}

		Pose pose = Pose.layer(base, sample(sequence, cycle, poseValues), weight, sequence.boneWeights(), sequence.delta(), sequence.post());

		if (depth >= MAX_LAYER_DEPTH) {
			return pose;
		}

		for (AutoLayer layer : sequence.layers()) {
			if ((layer.flags() & LAYER_LOCAL) != 0 || layer.sequence() < 0 || layer.sequence() >= sequences.size()) {
				continue;
			}

			float index = cycle;

			if ((layer.flags() & LAYER_POSE) != 0) {
				index = layer.pose() >= 0 && layer.pose() < poseValues.length ? poseValues[layer.pose()] : 0.0F;
			}

			if (layer.strength(index) <= 0.0F) {
				continue;
			}

			pose = accumulate(pose, sequences.get(layer.sequence()), layer.cycle(cycle), layer.weight(index, weight), poseValues, depth + 1);
		}

		return pose;
	}

	/** Поза одной анимации; между кадрами — интерполяция. */
	public Pose sample(Animation animation, float cycle) {
		return sample(animation, cycle, false);
	}

	/** @param delta анимация — поправка, даже если в её описании это не отмечено */
	private Pose sample(Animation animation, float cycle, boolean delta) {
		float frame = Math.clamp(cycle, 0.0F, 1.0F) * (animation.frames() - 1);
		int whole = (int) frame;
		float fraction = frame - whole;
		Pose pose = Pose.rest(bones);
		if (delta || (animation.flags() & SEQUENCE_DELTA) != 0) {
			for (int i = 0; i < bones.size(); i++) {
				pose.set(i, new float[3], new float[] {0.0F, 0.0F, 0.0F, 1.0F});
			}
		}

		int[] local = {whole};
		int data = dataStart(animation, local);
		int localFrame = local[0];

		while (data > 0) {
			int bone = in.uint8(data);
			int flags = in.uint8(data + 1);
			int next = in.int16(data + 2);

			if (bone < bones.size()) {
				StudioModel.Bone base = bones.get(bone);
				pose.set(bone, position(data + 4, flags, localFrame, fraction, base),
						rotation(data + 4, flags, localFrame, fraction, base));
			}

			data = next == 0 ? 0 : data + next;
		}

		return pose;
	}

	/** Начало данных по костям для кадра; у анимаций с секциями кадр пересчитывается внутри секции. */
	private int dataStart(Animation animation, int[] frame) {
		if (animation.sectionFrames() == 0) {
			return animation.offset() + animation.dataOffset();
		}

		int section;

		if (animation.frames() > animation.sectionFrames() && frame[0] == animation.frames() - 1) {
			frame[0] = 0;
			section = animation.frames() / animation.sectionFrames() + 1;
		} else {
			section = frame[0] / animation.sectionFrames();
			frame[0] -= section * animation.sectionFrames();
		}

		int entry = animation.offset() + animation.sectionOffset() + section * 8;
		in.checkArray(entry, 1, 8, "секция анимации");

		if (in.int32(entry) != 0) {
			throw new ModelFormatException("анимация " + animation.name() + " во внешнем блоке: не поддерживается");
		}

		return animation.offset() + in.int32(entry + 4);
	}

	private float[] rotation(int data, int flags, int frame, float fraction, StudioModel.Bone base) {
		boolean delta = (flags & DELTA) != 0;

		if ((flags & RAW_ROTATION) != 0) {
			return quaternion48(data);
		}

		if ((flags & RAW_ROTATION_64) != 0) {
			return quaternion64(data);
		}

		if ((flags & ANIMATED_ROTATION) == 0) {
			return delta ? new float[] {0.0F, 0.0F, 0.0F, 1.0F} : base.rotation().clone();
		}

		float[] first = new float[3];
		float[] second = new float[3];

		for (int axis = 0; axis < 3; axis++) {
			float[] values = track(data, axis, frame, base.rotationScale()[axis]);
			first[axis] = values[0] + (delta ? 0.0F : base.euler()[axis]);
			second[axis] = values[1] + (delta ? 0.0F : base.euler()[axis]);
		}

		float[] a = Pose.eulerToQuaternion(first[0], first[1], first[2]);

		if (fraction <= 0.001F || (first[0] == second[0] && first[1] == second[1] && first[2] == second[2])) {
			return a;
		}

		float[] out = new float[4];
		Pose.slerp(a, Pose.eulerToQuaternion(second[0], second[1], second[2]), 0, fraction, out);
		return out;
	}

	private float[] position(int data, int flags, int frame, float fraction, StudioModel.Bone base) {
		boolean delta = (flags & DELTA) != 0;

		if ((flags & RAW_POSITION) != 0) {
			int at = data + ((flags & RAW_ROTATION) != 0 ? 6 : 0) + ((flags & RAW_ROTATION_64) != 0 ? 8 : 0);
			return new float[] {half(in.uint16(at)), half(in.uint16(at + 2)), half(in.uint16(at + 4))};
		}

		if ((flags & ANIMATED_POSITION) == 0) {
			return delta ? new float[3] : base.position().clone();
		}

		// Смещения идут после указателей на углы, если углы тоже анимированы.
		int pointers = data + ((flags & ANIMATED_ROTATION) != 0 ? 6 : 0);
		float[] out = new float[3];

		for (int axis = 0; axis < 3; axis++) {
			float[] values = track(pointers, axis, frame, base.positionScale()[axis]);
			out[axis] = values[0] + (values[1] - values[0]) * fraction + (delta ? 0.0F : base.position()[axis]);
		}

		return out;
	}

	/** Значения дорожки по оси для кадра и следующего за ним, уже умноженные на масштаб. */
	private float[] track(int pointers, int axis, int frame, float scale) {
		int offset = in.int16(pointers + axis * 2);

		if (offset <= 0) {
			return new float[2];
		}

		int[] values = extract(in, pointers + offset, frame);
		return new float[] {values[0] * scale, values[1] * scale};
	}

	/**
	 * Значение сжатой дорожки в кадре {@code frame} и в следующем. Дорожка — серии: заголовок
	 * (сколько значений записано, сколько кадров покрыто), затем записанные значения; кадры серии
	 * за последним записанным значением повторяют его.
	 */
	static int[] extract(Bytes in, int track, int frame) {
		int at = track;
		int k = frame;
		int valid = in.uint8(at);
		int total = in.uint8(at + 1);

		// Одно значение на всю анимацию.
		if (valid == 1 && total == 1) {
			int value = in.int16(at + 2);
			return new int[] {value, value};
		}

		while (total <= k) {
			k -= total;
			at += (valid + 1) * 2;
			valid = in.uint8(at);
			total = in.uint8(at + 1);

			if (total == 0) {
				return new int[2];
			}
		}

		int first = valid > k ? in.int16(at + (k + 1) * 2) : in.int16(at + valid * 2);
		int second;

		if (valid > k + 1) {
			second = in.int16(at + (k + 2) * 2);
		} else if (total > k + 1) {
			second = first;
		} else {
			// Следующий кадр — первое значение следующей серии.
			int next = at + (valid + 1) * 2;
			second = in.uint8(next + 1) == 0 ? first : in.int16(next + 2);
		}

		return new int[] {first, second};
	}

	/** Сжатый кватернион 48 бит: x и y по 16 бит, z 15 бит, знак w. */
	private float[] quaternion48(int at) {
		int x = in.uint16(at);
		int y = in.uint16(at + 2);
		int zw = in.uint16(at + 4);
		return finish((x - 32768) / 32768.0, (y - 32768) / 32768.0, ((zw & 0x7FFF) - 16384) / 16384.0, (zw & 0x8000) != 0);
	}

	/** Сжатый кватернион 64 бита: x, y, z по 21 биту и знак w. */
	private float[] quaternion64(int at) {
		long bits = (in.int32(at) & 0xFFFFFFFFL) | ((long) in.int32(at + 4) << 32);
		long x = bits & 0x1FFFFF;
		long y = (bits >>> 21) & 0x1FFFFF;
		long z = (bits >>> 42) & 0x1FFFFF;
		double scale = 1.0 / 1048576.5;
		return finish((x - 1048576) * scale, (y - 1048576) * scale, (z - 1048576) * scale, (bits >>> 63) != 0);
	}

	private static float[] finish(double x, double y, double z, boolean negativeW) {
		double w = Math.sqrt(Math.max(0.0, 1.0 - x * x - y * y - z * z));
		return new float[] {(float) x, (float) y, (float) z, (float) (negativeW ? -w : w)};
	}

	/** Число половинной точности (16 бит) в обычное. */
	static float half(int bits) {
		int sign = (bits >> 15) & 1;
		int exponent = (bits >> 10) & 0x1F;
		int mantissa = bits & 0x3FF;
		double value;

		if (exponent == 31) {
			value = mantissa != 0 ? 0.0 : 65504.0;
		} else if (exponent == 0) {
			value = mantissa / 1024.0 * Math.pow(2, -14);
		} else {
			value = Math.pow(2, exponent - 15) * (1.0 + mantissa / 1024.0);
		}

		return (float) (sign == 1 ? -value : value);
	}
}

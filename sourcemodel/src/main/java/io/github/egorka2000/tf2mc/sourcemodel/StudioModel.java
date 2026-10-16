package io.github.egorka2000.tf2mc.sourcemodel;

import java.util.ArrayList;
import java.util.List;

/**
 * Содержимое файла MDL: скелет, части тела с сетками, имена текстур, таблица скинов, хитбоксы.
 * Сами вершины лежат в VVD ({@link VertexData}), треугольники — в VTX ({@link StripData}).
 *
 * <p>Устройство файла: заголовок — по Valve Developer Wiki (страница MDL); кости, части тела,
 * модели, сетки, текстуры и хитбоксы — по открытому Source SDK 2013 ({@code studio.h}), потому что
 * на вики их нет. Код написан заново; поддерживаются версии 44–48 (TF2 — 48).
 */
public final class StudioModel {
	/** Наименьшая и наибольшая поддерживаемые версии формата. */
	public static final int MIN_VERSION = 44;
	public static final int MAX_VERSION = 48;

	// Размеры структур в файле, байт.
	private static final int BONE_SIZE = 216;
	private static final int BODY_PART_SIZE = 16;
	private static final int MODEL_SIZE = 148;
	private static final int MESH_SIZE = 116;
	private static final int TEXTURE_SIZE = 64;
	private static final int HITBOX_SET_SIZE = 12;
	private static final int HITBOX_SIZE = 68;
	private static final int INCLUDE_MODEL_SIZE = 8;
	/** Размер вершины в VVD: смещения вершин в MDL записаны в байтах. */
	private static final int VERTEX_SIZE = 48;

	/**
	 * Кость скелета в позе, в которой сделана модель.
	 *
	 * @param parent     индекс родительской кости; -1 у корня
	 * @param position   положение относительно родителя, x y z
	 * @param rotation   поворот относительно родителя, кватернион x y z w
	 * @param poseToBone матрица 3×4 по строкам: из пространства модели в пространство кости
	 */
	public record Bone(String name, int parent, float[] position, float[] rotation, float[] poseToBone) {
	}

	/**
	 * @param material     номер места под материал; через таблицу скинов даёт номер текстуры
	 * @param vertexOffset первая вершина сетки, считая от первой вершины модели
	 */
	public record Mesh(int material, int vertexOffset, int vertexCount) {
	}

	/** @param firstVertex первая вершина модели в общем массиве вершин VVD */
	public record Model(String name, int firstVertex, int vertexCount, List<Mesh> meshes) {
	}

	/** Часть тела: несколько вариантов модели, из которых показывается один. */
	public record BodyPart(String name, List<Model> models) {
	}

	/** Хитбокс: прямоугольник в пространстве кости. */
	public record Hitbox(int bone, int group, float[] min, float[] max, String name) {
	}

	public record HitboxSet(String name, List<Hitbox> hitboxes) {
	}

	private final int version;
	private final int checksum;
	private final String name;
	private final List<Bone> bones;
	private final List<BodyPart> bodyParts;
	private final List<String> textures;
	private final List<String> textureDirs;
	private final int[][] skins;
	private final List<HitboxSet> hitboxSets;
	private final List<String> includeModels;

	private StudioModel(int version, int checksum, String name, List<Bone> bones, List<BodyPart> bodyParts, List<String> textures,
			List<String> textureDirs, int[][] skins, List<HitboxSet> hitboxSets, List<String> includeModels) {
		this.version = version;
		this.checksum = checksum;
		this.name = name;
		this.bones = bones;
		this.bodyParts = bodyParts;
		this.textures = textures;
		this.textureDirs = textureDirs;
		this.skins = skins;
		this.hitboxSets = hitboxSets;
		this.includeModels = includeModels;
	}

	public static StudioModel read(byte[] data) {
		Bytes in = new Bytes(data, "MDL");

		if (!in.magic().equals("IDST")) {
			throw new ModelFormatException("MDL: не файл модели Source, начало " + in.magic());
		}

		int version = in.int32(4);

		if (version < MIN_VERSION || version > MAX_VERSION) {
			throw new ModelFormatException("MDL: версия " + version + " не поддерживается, нужна " + MIN_VERSION + "–" + MAX_VERSION);
		}

		return new StudioModel(version, in.int32(8), in.fixedString(12, 64),
				readBones(in), readBodyParts(in), readTextures(in), readTextureDirs(in), readSkins(in), readHitboxSets(in), readIncludeModels(in));
	}

	private static List<Bone> readBones(Bytes in) {
		int count = in.int32(156);
		int offset = in.int32(160);
		in.checkArray(offset, count, BONE_SIZE, "кости");
		List<Bone> bones = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			int bone = offset + i * BONE_SIZE;
			// Имя, родитель, шесть контроллеров, положение, кватернион, углы, масштабы, матрица.
			float[] poseToBone = new float[12];

			for (int k = 0; k < 12; k++) {
				poseToBone[k] = in.float32(bone + 96 + k * 4);
			}

			bones.add(new Bone(in.string(bone + in.int32(bone)), in.int32(bone + 4),
					floats(in, bone + 32, 3), floats(in, bone + 44, 4), poseToBone));
		}

		return List.copyOf(bones);
	}

	private static List<BodyPart> readBodyParts(Bytes in) {
		int count = in.int32(232);
		int offset = in.int32(236);
		in.checkArray(offset, count, BODY_PART_SIZE, "части тела");
		List<BodyPart> parts = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			int part = offset + i * BODY_PART_SIZE;
			int modelCount = in.int32(part + 4);
			int modelOffset = part + in.int32(part + 12);
			in.checkArray(modelOffset, modelCount, MODEL_SIZE, "модели");
			List<Model> models = new ArrayList<>(modelCount);

			for (int m = 0; m < modelCount; m++) {
				models.add(readModel(in, modelOffset + m * MODEL_SIZE));
			}

			parts.add(new BodyPart(in.string(part + in.int32(part)), List.copyOf(models)));
		}

		return List.copyOf(parts);
	}

	private static Model readModel(Bytes in, int model) {
		int meshCount = in.int32(model + 72);
		int meshOffset = model + in.int32(model + 76);
		in.checkArray(meshOffset, meshCount, MESH_SIZE, "сетки");
		List<Mesh> meshes = new ArrayList<>(meshCount);

		for (int i = 0; i < meshCount; i++) {
			int mesh = meshOffset + i * MESH_SIZE;
			meshes.add(new Mesh(in.int32(mesh), in.int32(mesh + 12), in.int32(mesh + 8)));
		}

		return new Model(in.fixedString(model, 64), in.int32(model + 84) / VERTEX_SIZE, in.int32(model + 80), List.copyOf(meshes));
	}

	private static List<String> readTextures(Bytes in) {
		int count = in.int32(204);
		int offset = in.int32(208);
		in.checkArray(offset, count, TEXTURE_SIZE, "текстуры");
		List<String> names = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			int texture = offset + i * TEXTURE_SIZE;
			names.add(in.string(texture + in.int32(texture)));
		}

		return List.copyOf(names);
	}

	private static List<String> readTextureDirs(Bytes in) {
		int count = in.int32(212);
		int offset = in.int32(216);
		in.checkArray(offset, count, 4, "папки текстур");
		List<String> dirs = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			dirs.add(in.string(in.int32(offset + i * 4)));
		}

		return List.copyOf(dirs);
	}

	/** Таблица скинов: строка на семейство (у игроков TF2 — 0 RED, 1 BLU), в строке номера текстур. */
	private static int[][] readSkins(Bytes in) {
		int references = in.int32(220);
		int families = in.int32(224);
		int offset = in.int32(228);
		in.checkArray(offset, references * families, 2, "таблица скинов");
		int[][] table = new int[families][references];

		for (int family = 0; family < families; family++) {
			for (int reference = 0; reference < references; reference++) {
				table[family][reference] = in.int16(offset + (family * references + reference) * 2);
			}
		}

		return table;
	}

	private static List<HitboxSet> readHitboxSets(Bytes in) {
		int count = in.int32(172);
		int offset = in.int32(176);
		in.checkArray(offset, count, HITBOX_SET_SIZE, "наборы хитбоксов");
		List<HitboxSet> sets = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			int set = offset + i * HITBOX_SET_SIZE;
			int boxCount = in.int32(set + 4);
			int boxOffset = set + in.int32(set + 8);
			in.checkArray(boxOffset, boxCount, HITBOX_SIZE, "хитбоксы");
			List<Hitbox> boxes = new ArrayList<>(boxCount);

			for (int b = 0; b < boxCount; b++) {
				int box = boxOffset + b * HITBOX_SIZE;
				int nameIndex = in.int32(box + 32);
				boxes.add(new Hitbox(in.int32(box), in.int32(box + 4), floats(in, box + 8, 3), floats(in, box + 20, 3),
						nameIndex == 0 ? "" : in.string(box + nameIndex)));
			}

			sets.add(new HitboxSet(in.string(set + in.int32(set)), List.copyOf(boxes)));
		}

		return List.copyOf(sets);
	}

	/** Другие MDL, из которых модель берёт анимации ({@code $includemodel}). */
	private static List<String> readIncludeModels(Bytes in) {
		int count = in.int32(336);
		int offset = in.int32(340);
		in.checkArray(offset, count, INCLUDE_MODEL_SIZE, "подключённые модели");
		List<String> names = new ArrayList<>(count);

		for (int i = 0; i < count; i++) {
			int include = offset + i * INCLUDE_MODEL_SIZE;
			names.add(in.string(include + in.int32(include + 4)));
		}

		return List.copyOf(names);
	}

	private static float[] floats(Bytes in, int offset, int count) {
		float[] values = new float[count];

		for (int i = 0; i < count; i++) {
			values[i] = in.float32(offset + i * 4);
		}

		return values;
	}

	public int version() {
		return version;
	}

	/** Контрольная сумма: у VVD и VTX этой модели она та же. */
	public int checksum() {
		return checksum;
	}

	public String name() {
		return name;
	}

	public List<Bone> bones() {
		return bones;
	}

	public List<BodyPart> bodyParts() {
		return bodyParts;
	}

	/** Имена текстур без папки, например {@code soldier_red}. */
	public List<String> textures() {
		return textures;
	}

	/** Папки, в которых ищутся текстуры, например {@code models\player\soldier\}. */
	public List<String> textureDirs() {
		return textureDirs;
	}

	public int skinFamilies() {
		return skins.length;
	}

	/** Номер текстуры для места под материал в семействе скинов. */
	public int texture(int family, int material) {
		int[] row = skins[Math.min(family, skins.length - 1)];
		return material < row.length ? row[material] : material;
	}

	public List<HitboxSet> hitboxSets() {
		return hitboxSets;
	}

	public List<String> includeModels() {
		return includeModels;
	}
}

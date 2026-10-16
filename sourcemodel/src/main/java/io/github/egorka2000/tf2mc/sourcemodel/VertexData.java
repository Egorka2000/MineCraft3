package io.github.egorka2000.tf2mc.sourcemodel;

/**
 * Вершины модели из файла VVD для самого подробного уровня детализации: положение, нормаль,
 * текстурные координаты и до трёх костей с весами.
 *
 * <p>Устройство файла — по Valve Developer Wiki (страница VVD). Если в файле есть таблица
 * поправок, вершины первого уровня собираются по ней: компилятор хранит общие для уровней
 * вершины один раз.
 */
public final class VertexData {
	/** Сколько костей влияет на вершину наибольшее. */
	public static final int MAX_BONES_PER_VERTEX = 3;

	private static final int VERTEX_SIZE = 48;
	private static final int FIXUP_SIZE = 12;
	private static final int SUPPORTED_VERSION = 4;

	private final int checksum;
	private final float[] positions;
	private final float[] normals;
	private final float[] texCoords;
	private final int[] boneIndices;
	private final float[] boneWeights;

	private VertexData(int checksum, float[] positions, float[] normals, float[] texCoords, int[] boneIndices, float[] boneWeights) {
		this.checksum = checksum;
		this.positions = positions;
		this.normals = normals;
		this.texCoords = texCoords;
		this.boneIndices = boneIndices;
		this.boneWeights = boneWeights;
	}

	public static VertexData read(byte[] data) {
		Bytes in = new Bytes(data, "VVD");

		if (!in.magic().equals("IDSV")) {
			throw new ModelFormatException("VVD: не файл вершин Source, начало " + in.magic());
		}

		if (in.int32(4) != SUPPORTED_VERSION) {
			throw new ModelFormatException("VVD: версия " + in.int32(4) + " не поддерживается, нужна " + SUPPORTED_VERSION);
		}

		int count = in.int32(16);
		int fixupCount = in.int32(48);
		int fixupOffset = in.int32(52);
		int vertexOffset = in.int32(56);
		in.checkArray(fixupOffset, fixupCount, FIXUP_SIZE, "таблица поправок");
		in.checkArray(vertexOffset, count, VERTEX_SIZE, "вершины");

		// Порядок вершин первого уровня: без поправок — подряд, иначе по таблице.
		int[] order = new int[count];

		if (fixupCount == 0) {
			for (int i = 0; i < count; i++) {
				order[i] = i;
			}
		} else {
			int next = 0;

			for (int f = 0; f < fixupCount; f++) {
				int fixup = fixupOffset + f * FIXUP_SIZE;
				int source = in.int32(fixup + 4);
				int length = in.int32(fixup + 8);

				// Поправка нужна уровню 0, если она относится к уровню 0 или дальше.
				if (in.int32(fixup) >= 0) {
					for (int k = 0; k < length && next < count; k++) {
						order[next++] = source + k;
					}
				}
			}

			if (next != count) {
				throw new ModelFormatException("VVD: таблица поправок даёт " + next + " вершин вместо " + count);
			}
		}

		float[] positions = new float[count * 3];
		float[] normals = new float[count * 3];
		float[] texCoords = new float[count * 2];
		int[] boneIndices = new int[count * MAX_BONES_PER_VERTEX];
		float[] boneWeights = new float[count * MAX_BONES_PER_VERTEX];

		for (int i = 0; i < count; i++) {
			int vertex = vertexOffset + order[i] * VERTEX_SIZE;
			in.checkArray(vertex, 1, VERTEX_SIZE, "вершина");
			int bones = Math.min(in.uint8(vertex + 15), MAX_BONES_PER_VERTEX);

			for (int b = 0; b < MAX_BONES_PER_VERTEX; b++) {
				boneIndices[i * MAX_BONES_PER_VERTEX + b] = b < bones ? in.uint8(vertex + 12 + b) : 0;
				boneWeights[i * MAX_BONES_PER_VERTEX + b] = b < bones ? in.float32(vertex + b * 4) : 0.0F;
			}

			for (int axis = 0; axis < 3; axis++) {
				positions[i * 3 + axis] = in.float32(vertex + 16 + axis * 4);
				normals[i * 3 + axis] = in.float32(vertex + 28 + axis * 4);
			}

			texCoords[i * 2] = in.float32(vertex + 40);
			texCoords[i * 2 + 1] = in.float32(vertex + 44);
		}

		return new VertexData(in.int32(8), positions, normals, texCoords, boneIndices, boneWeights);
	}

	public int checksum() {
		return checksum;
	}

	public int count() {
		return positions.length / 3;
	}

	/** Положения вершин подряд, x y z, в единицах Source (дюймах). */
	public float[] positions() {
		return positions;
	}

	public float[] normals() {
		return normals;
	}

	/** Текстурные координаты подряд, u v; v отсчитывается сверху текстуры. */
	public float[] texCoords() {
		return texCoords;
	}

	/** По три номера костей на вершину; лишние — 0 с весом 0. */
	public int[] boneIndices() {
		return boneIndices;
	}

	public float[] boneWeights() {
		return boneWeights;
	}
}

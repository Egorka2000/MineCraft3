package io.github.egorka2000.tf2mc.sourcemodel;

import java.util.ArrayList;
import java.util.List;

/**
 * Треугольники модели из файла VTX ({@code .dx90.vtx}) для самого подробного уровня детализации.
 * Для каждой сетки — номера её вершин тройками; номер считается от первой вершины сетки.
 *
 * <p>Устройство файла — по Valve Developer Wiki (страница VTX). Структуры в файле упакованы без
 * выравнивания. Поддерживается версия 7 для моделей до версии 48: начиная с 49 у групп полос
 * другой размер.
 */
public final class StripData {
	private static final int SUPPORTED_VERSION = 7;

	private static final int BODY_PART_SIZE = 8;
	private static final int MODEL_SIZE = 8;
	private static final int LOD_SIZE = 12;
	private static final int MESH_SIZE = 9;
	private static final int STRIP_GROUP_SIZE = 25;
	private static final int STRIP_SIZE = 27;
	private static final int VERTEX_SIZE = 9;

	/** Полоса задана тройками вершин; иначе — лентой треугольников. */
	private static final int STRIP_IS_TRILIST = 0x01;
	private static final int STRIP_IS_TRISTRIP = 0x02;

	private final int checksum;
	/** [часть тела][модель][сетка] → номера вершин сетки тройками. */
	private final List<List<List<int[]>>> triangles;

	private StripData(int checksum, List<List<List<int[]>>> triangles) {
		this.checksum = checksum;
		this.triangles = triangles;
	}

	public static StripData read(byte[] data) {
		Bytes in = new Bytes(data, "VTX");

		if (in.int32(0) != SUPPORTED_VERSION) {
			throw new ModelFormatException("VTX: версия " + in.int32(0) + " не поддерживается, нужна " + SUPPORTED_VERSION);
		}

		int partCount = in.int32(28);
		int partOffset = in.int32(32);
		in.checkArray(partOffset, partCount, BODY_PART_SIZE, "части тела");
		List<List<List<int[]>>> parts = new ArrayList<>(partCount);

		for (int p = 0; p < partCount; p++) {
			int part = partOffset + p * BODY_PART_SIZE;
			int modelCount = in.int32(part);
			int modelOffset = part + in.int32(part + 4);
			in.checkArray(modelOffset, modelCount, MODEL_SIZE, "модели");
			List<List<int[]>> models = new ArrayList<>(modelCount);

			for (int m = 0; m < modelCount; m++) {
				int model = modelOffset + m * MODEL_SIZE;
				int lodCount = in.int32(model);
				models.add(lodCount == 0 ? List.of() : readLod(in, model + in.int32(model + 4)));
			}

			parts.add(List.copyOf(models));
		}

		return new StripData(in.int32(16), List.copyOf(parts));
	}

	/** Первый, самый подробный уровень детализации модели. */
	private static List<int[]> readLod(Bytes in, int lod) {
		int meshCount = in.int32(lod);
		int meshOffset = lod + in.int32(lod + 4);
		in.checkArray(meshOffset, meshCount, MESH_SIZE, "сетки");
		List<int[]> meshes = new ArrayList<>(meshCount);

		for (int i = 0; i < meshCount; i++) {
			int mesh = meshOffset + i * MESH_SIZE;
			meshes.add(readMesh(in, mesh + in.int32(mesh + 4), in.int32(mesh)));
		}

		return List.copyOf(meshes);
	}

	private static int[] readMesh(Bytes in, int groupOffset, int groupCount) {
		in.checkArray(groupOffset, groupCount, STRIP_GROUP_SIZE, "группы полос");
		List<Integer> result = new ArrayList<>();

		for (int g = 0; g < groupCount; g++) {
			int group = groupOffset + g * STRIP_GROUP_SIZE;
			int vertexCount = in.int32(group);
			int vertexOffset = group + in.int32(group + 4);
			int indexCount = in.int32(group + 8);
			int indexOffset = group + in.int32(group + 12);
			int stripCount = in.int32(group + 16);
			int stripOffset = group + in.int32(group + 20);
			in.checkArray(vertexOffset, vertexCount, VERTEX_SIZE, "вершины группы");
			in.checkArray(indexOffset, indexCount, 2, "индексы группы");
			in.checkArray(stripOffset, stripCount, STRIP_SIZE, "полосы");

			for (int s = 0; s < stripCount; s++) {
				int strip = stripOffset + s * STRIP_SIZE;
				int first = in.int32(strip + 4);
				int count = in.int32(strip);
				int flags = in.uint8(strip + 18);

				if (first < 0 || first + count > indexCount) {
					throw new ModelFormatException("VTX: полоса выходит за индексы группы");
				}

				for (int k = 0; k + 2 < count; k += (flags & STRIP_IS_TRISTRIP) != 0 ? 1 : 3) {
					int[] corner = new int[3];

					for (int c = 0; c < 3; c++) {
						int index = in.uint16(indexOffset + (first + k + c) * 2);

						if (index >= vertexCount) {
							throw new ModelFormatException("VTX: индекс " + index + " за пределами вершин группы");
						}

						// Вершина группы указывает на вершину сетки.
						corner[c] = in.uint16(vertexOffset + index * VERTEX_SIZE + 4);
					}

					// У ленты каждый второй треугольник развёрнут: порядок углов меняется.
					if ((flags & STRIP_IS_TRILIST) == 0 && (flags & STRIP_IS_TRISTRIP) != 0 && k % 2 == 1) {
						int swap = corner[1];
						corner[1] = corner[2];
						corner[2] = swap;
					}

					result.add(corner[0]);
					result.add(corner[1]);
					result.add(corner[2]);
				}
			}
		}

		return result.stream().mapToInt(Integer::intValue).toArray();
	}

	public int checksum() {
		return checksum;
	}

	/**
	 * Треугольники сетки.
	 *
	 * @return номера вершин сетки тройками; пустой массив, если такой сетки нет
	 */
	public int[] triangles(int bodyPart, int model, int mesh) {
		if (bodyPart >= triangles.size() || model >= triangles.get(bodyPart).size() || mesh >= triangles.get(bodyPart).get(model).size()) {
			return new int[0];
		}

		return triangles.get(bodyPart).get(model).get(mesh);
	}
}

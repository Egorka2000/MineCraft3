package io.github.egorka2000.tf2mc.sourcemodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Модель TF2, собранная из трёх файлов: MDL (скелет, сетки, материалы), VVD (вершины) и VTX
 * (треугольники). Готова к отрисовке: вершины общие, треугольники разбиты по материалам.
 *
 * <p>Единицы и оси — как в Source: дюймы, ось z вверх. Перевод в блоки и оси Minecraft — дело
 * того, кто рисует.
 */
public final class SourceModel {
	/**
	 * Треугольники одного материала.
	 *
	 * @param materials где искать материал, по порядку: каждая папка текстур модели плюс имя
	 *                  текстуры, через «/» и строчными буквами. Игра берёт первый существующий.
	 * @param triangles номера вершин в общем массиве {@link #vertices()} тройками
	 */
	public record Part(List<String> materials, int[] triangles) {
	}

	private final StudioModel studio;
	private final VertexData vertices;
	private final List<Part> parts;

	private SourceModel(StudioModel studio, VertexData vertices, List<Part> parts) {
		this.studio = studio;
		this.vertices = vertices;
		this.parts = parts;
	}

	/**
	 * Собирает модель с вариантом 0 каждой части тела.
	 *
	 * @param skinFamily семейство скинов: у игроков TF2 0 — RED, 1 — BLU
	 */
	public static SourceModel read(byte[] mdl, byte[] vvd, byte[] vtx, int skinFamily) {
		StudioModel studio = StudioModel.read(mdl);
		VertexData vertices = VertexData.read(vvd);
		StripData strips = StripData.read(vtx);

		if (vertices.checksum() != studio.checksum() || strips.checksum() != studio.checksum()) {
			throw new ModelFormatException("файлы модели " + studio.name() + " из разных сборок: контрольные суммы не совпадают");
		}

		List<Part> parts = new ArrayList<>();

		for (int p = 0; p < studio.bodyParts().size(); p++) {
			List<StudioModel.Model> models = studio.bodyParts().get(p).models();

			if (models.isEmpty()) {
				continue;
			}

			StudioModel.Model model = models.get(0);

			for (int m = 0; m < model.meshes().size(); m++) {
				StudioModel.Mesh mesh = model.meshes().get(m);
				int[] local = strips.triangles(p, 0, m);
				int[] global = new int[local.length];
				int base = model.firstVertex() + mesh.vertexOffset();

				for (int i = 0; i < local.length; i++) {
					global[i] = base + local[i];

					if (global[i] >= vertices.count()) {
						throw new ModelFormatException("модель " + studio.name() + ": вершина " + global[i] + " за пределами VVD");
					}
				}

				if (global.length > 0) {
					parts.add(new Part(materialPaths(studio, studio.texture(skinFamily, mesh.material())), global));
				}
			}
		}

		return new SourceModel(studio, vertices, List.copyOf(parts));
	}

	/** Возможные пути материала: папка текстур плюс имя, для каждой папки модели и без папки. */
	private static List<String> materialPaths(StudioModel studio, int texture) {
		String name = texture < studio.textures().size() ? studio.textures().get(texture) : "missing";
		List<String> paths = new ArrayList<>();

		for (String dir : studio.textureDirs()) {
			paths.add(normalize(dir + name));
		}

		// Имя текстуры иногда уже содержит папку.
		paths.add(normalize(name));
		return List.copyOf(paths.stream().distinct().toList());
	}

	private static String normalize(String path) {
		return path.replace('\\', '/').toLowerCase(Locale.ROOT);
	}

	public StudioModel studio() {
		return studio;
	}

	public VertexData vertices() {
		return vertices;
	}

	public List<Part> parts() {
		return parts;
	}

	/** Сколько всего треугольников. */
	public int triangleCount() {
		int count = 0;

		for (Part part : parts) {
			count += part.triangles().length / 3;
		}

		return count;
	}
}

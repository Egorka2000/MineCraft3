package io.github.egorka2000.tf2mc.client.model;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.sourcemodel.ModelFormatException;
import io.github.egorka2000.tf2mc.sourcemodel.SourceModel;
import io.github.egorka2000.tf2mc.sourcemodel.VertexData;
import io.github.egorka2000.tf2mc.sourcemodel.VmtMaterial;
import io.github.egorka2000.tf2mc.sourcemodel.VtfImage;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Модели персонажей TF2 из расширительного пака, готовые к отрисовке.
 *
 * <p>Файлы TF2 лежат в паке как есть, под {@code assets/tf2mc/tf2/}: модели в {@code models/},
 * материалы и текстуры в {@code materials/}. Мод читает их сам (подпроект {@code sourcemodel}),
 * распаковывает текстуры в память и переводит сетку в оси Minecraft.
 */
public final class SourceModels {
	/** Модель Soldier: файлы MDL, VVD и VTX рядом. */
	public static final String SOLDIER = "models/player/soldier";

	/** Семейства скинов у игроков TF2. */
	public static final int RED = 0;
	public static final int BLU = 1;

	private SourceModels() {
	}

	/**
	 * Треугольники одной текстуры в осях Minecraft: на каждый угол x y z (блоки, ось y вверх,
	 * перед модели — к −z, как у ванильных моделей после поворота тела), u v, нормаль x y z.
	 */
	public record Draw(Identifier texture, float[] corners) {
		/** Сколько чисел на один угол. */
		public static final int STRIDE = 8;
	}

	/** Сетка модели для обеих команд. */
	public record PlayerMesh(List<Draw> red, List<Draw> blu) {
		public List<Draw> forFamily(int family) {
			return family == BLU ? blu : red;
		}
	}

	/**
	 * Читает модель из ресурсов и регистрирует её текстуры.
	 *
	 * @param unitsPerBlock масштаб: сколько единиц TF2 в блоке
	 * @param textures      сюда добавляются имена зарегистрированных текстур, чтобы при следующей
	 *                      перезагрузке их освободить
	 * @return пусто, если модели в ресурсах нет (пак не подключён) или она не читается
	 */
	public static Optional<PlayerMesh> load(ResourceManager resources, TextureManager textureManager, String model, double unitsPerBlock,
			List<Identifier> textures) {
		Optional<byte[]> mdl = read(resources, model + ".mdl");

		if (mdl.isEmpty()) {
			return Optional.empty();
		}

		try {
			byte[] vvd = read(resources, model + ".vvd").orElseThrow(() -> new ModelFormatException("нет файла " + model + ".vvd"));
			byte[] vtx = read(resources, model + ".dx90.vtx").orElseThrow(() -> new ModelFormatException("нет файла " + model + ".dx90.vtx"));
			float scale = (float) (1.0 / unitsPerBlock);

			return Optional.of(new PlayerMesh(
					draws(SourceModel.read(mdl.get(), vvd, vtx, RED), resources, textureManager, scale, textures),
					draws(SourceModel.read(mdl.get(), vvd, vtx, BLU), resources, textureManager, scale, textures)));
		} catch (ModelFormatException e) {
			Tf2Mc.LOGGER.warn("модель {} из расширительного пака не читается: {}", model, e.getMessage());
			return Optional.empty();
		}
	}

	private static List<Draw> draws(SourceModel model, ResourceManager resources, TextureManager textureManager, float scale, List<Identifier> textures) {
		VertexData vertices = model.vertices();
		float[] positions = vertices.positions();
		float[] normals = vertices.normals();
		float[] uv = vertices.texCoords();
		List<Draw> draws = new ArrayList<>();

		for (SourceModel.Part part : model.parts()) {
			Identifier texture = texture(part.materials(), resources, textureManager, textures);
			int[] triangles = part.triangles();
			float[] corners = new float[triangles.length * Draw.STRIDE];

			for (int i = 0; i < triangles.length; i++) {
				int v = triangles[i];
				int at = i * Draw.STRIDE;
				// Начальная поза моделей игроков TF2 хранится с осью y вверх, лицом к +z, левая
				// рука к +x (проверено на Soldier: глаза впереди головы по +z, правый глаз на −x).
				// В Minecraft после поворота тела перёд −z, лево −x: разворот на 180° вокруг y.
				corners[at] = -positions[v * 3] * scale;
				corners[at + 1] = positions[v * 3 + 1] * scale;
				corners[at + 2] = -positions[v * 3 + 2] * scale;
				corners[at + 3] = uv[v * 2];
				corners[at + 4] = uv[v * 2 + 1];
				corners[at + 5] = -normals[v * 3];
				corners[at + 6] = normals[v * 3 + 1];
				corners[at + 7] = -normals[v * 3 + 2];
			}

			draws.add(new Draw(texture, corners));
		}

		return List.copyOf(draws);
	}

	/**
	 * Текстура материала: первый найденный VMT из списка путей, его {@code $basetexture} (с переходом
	 * по {@code include} у заплаток), затем VTF. Не нашлась — белая заглушка игры.
	 */
	private static Identifier texture(List<String> materials, ResourceManager resources, TextureManager textureManager, List<Identifier> textures) {
		for (String material : materials) {
			Optional<String> base = baseTexture(resources, "materials/" + material + ".vmt", 4);

			if (base.isEmpty()) {
				continue;
			}

			Optional<byte[]> vtf = read(resources, "materials/" + base.get() + ".vtf");

			if (vtf.isEmpty()) {
				Tf2Mc.LOGGER.warn("нет текстуры materials/{}.vtf для материала {}", base.get(), material);
				continue;
			}

			try {
				Identifier id = Tf2Mc.id("tf2/" + base.get());
				textureManager.register(id, new DynamicTexture(id::toString, image(VtfImage.read(vtf.get()))));
				textures.add(id);
				return id;
			} catch (ModelFormatException e) {
				Tf2Mc.LOGGER.warn("текстура {} не читается: {}", base.get(), e.getMessage());
			}
		}

		Tf2Mc.LOGGER.warn("нет материала модели среди {}", materials);
		return Identifier.withDefaultNamespace("textures/misc/white.png");
	}

	private static Optional<String> baseTexture(ResourceManager resources, String vmtPath, int depth) {
		Optional<byte[]> vmt = read(resources, vmtPath);

		if (vmt.isEmpty() || depth == 0) {
			return Optional.empty();
		}

		try {
			VmtMaterial material = VmtMaterial.parse(new String(vmt.get(), StandardCharsets.UTF_8));
			Optional<String> base = material.colorTexture();

			if (base.isEmpty() && material.include().isPresent()) {
				return baseTexture(resources, material.include().get(), depth - 1);
			}

			return base;
		} catch (ModelFormatException e) {
			Tf2Mc.LOGGER.warn("материал {} не читается: {}", vmtPath, e.getMessage());
			return Optional.empty();
		}
	}

	private static NativeImage image(VtfImage vtf) {
		NativeImage image = new NativeImage(vtf.width(), vtf.height(), false);
		int[] argb = vtf.argb();

		for (int y = 0; y < vtf.height(); y++) {
			for (int x = 0; x < vtf.width(); x++) {
				image.setPixel(x, y, argb[y * vtf.width() + x]);
			}
		}

		return image;
	}

	/** Файл TF2 из пака по пути внутри игры TF2, например {@code models/player/soldier.mdl}. */
	private static Optional<byte[]> read(ResourceManager resources, String tf2Path) {
		Identifier id = Tf2Mc.id("tf2/" + tf2Path.toLowerCase(Locale.ROOT));

		try (InputStream in = resources.open(id)) {
			return Optional.of(in.readAllBytes());
		} catch (IOException e) {
			return Optional.empty();
		}
	}
}

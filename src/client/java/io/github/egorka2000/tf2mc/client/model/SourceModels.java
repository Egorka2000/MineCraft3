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
import io.github.egorka2000.tf2mc.sourcemodel.AnimationSet;
import io.github.egorka2000.tf2mc.sourcemodel.ModelFormatException;
import io.github.egorka2000.tf2mc.sourcemodel.Pose;
import io.github.egorka2000.tf2mc.sourcemodel.SourceModel;
import io.github.egorka2000.tf2mc.sourcemodel.StudioModel;
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

	/** Треугольники одной текстуры: номера вершин модели тройками. */
	public record Draw(Identifier texture, int[] triangles) {
	}

	/**
	 * Модель, готовая к отрисовке в любой позе: вершины в пространстве привязки (как в VVD), кости,
	 * анимации (если в паке есть файл анимаций) и треугольники для обеих команд.
	 */
	public static final class PlayerMesh {
		/** Сколько чисел на вершину в осях Minecraft: x y z, u v, нормаль x y z. */
		public static final int STRIDE = 8;

		private final VertexData vertices;
		private final List<StudioModel.Bone> bones;
		private final AnimationSet animations;
		private final float scale;
		private final List<Draw> red;
		private final List<Draw> blu;

		PlayerMesh(VertexData vertices, List<StudioModel.Bone> bones, AnimationSet animations, float scale, List<Draw> red, List<Draw> blu) {
			this.vertices = vertices;
			this.bones = bones;
			this.animations = animations;
			this.scale = scale;
			this.red = red;
			this.blu = blu;
		}

		public List<Draw> forFamily(int family) {
			return family == BLU ? blu : red;
		}

		/** Анимации модели; пусто, если файла анимаций нет и модель стоит в начальной позе. */
		public Optional<AnimationSet> animations() {
			return Optional.ofNullable(animations);
		}

		/**
		 * Вершины в осях Minecraft (блоки, ось y вверх, перёд −z, как у ванильных моделей после
		 * поворота тела) для позы.
		 *
		 * @param pose поза из анимаций; {@code null} — начальная поза
		 */
		public float[] skin(Pose pose) {
			int count = vertices.count();
			float[] p = vertices.positions();
			float[] n = vertices.normals();
			float[] uv = vertices.texCoords();
			float[] out = new float[count * STRIDE];

			if (pose == null) {
				// Начальная поза моделей игроков TF2 хранится с осью y вверх, лицом к +z, левая
				// рука к +x (проверено на Soldier: глаза впереди головы по +z, правый глаз на −x).
				// В Minecraft после поворота тела перёд −z, лево −x: разворот на 180° вокруг y.
				for (int v = 0; v < count; v++) {
					put(out, v, -p[v * 3], p[v * 3 + 1], -p[v * 3 + 2], uv, -n[v * 3], n[v * 3 + 1], -n[v * 3 + 2]);
				}

				return out;
			}

			float[] skin = pose.skinMatrices(bones);
			int[] boneIndices = vertices.boneIndices();
			float[] weights = vertices.boneWeights();
			float[] position = new float[3];
			float[] normal = new float[3];

			for (int v = 0; v < count; v++) {
				position[0] = 0;
				position[1] = 0;
				position[2] = 0;
				normal[0] = 0;
				normal[1] = 0;
				normal[2] = 0;

				for (int k = 0; k < VertexData.MAX_BONES_PER_VERTEX; k++) {
					float w = weights[v * 3 + k];

					if (w == 0) {
						continue;
					}

					int m = boneIndices[v * 3 + k] * 12;

					for (int r = 0; r < 3; r++) {
						int row = m + r * 4;
						position[r] += w * (skin[row] * p[v * 3] + skin[row + 1] * p[v * 3 + 1] + skin[row + 2] * p[v * 3 + 2] + skin[row + 3]);
						normal[r] += w * (skin[row] * n[v * 3] + skin[row + 1] * n[v * 3 + 1] + skin[row + 2] * n[v * 3 + 2]);
					}
				}

				// После анимации модель в обычных осях Source: x вперёд, y влево, z вверх.
				// В Minecraft после поворота тела перёд −z, лево −x, верх +y.
				put(out, v, -position[1], position[2], -position[0], uv, -normal[1], normal[2], -normal[0]);
			}

			return out;
		}

		private void put(float[] out, int v, float x, float y, float z, float[] uv, float nx, float ny, float nz) {
			int at = v * STRIDE;
			out[at] = x * scale;
			out[at + 1] = y * scale;
			out[at + 2] = z * scale;
			out[at + 3] = uv[v * 2];
			out[at + 4] = uv[v * 2 + 1];
			out[at + 5] = nx;
			out[at + 6] = ny;
			out[at + 7] = nz;
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
			SourceModel red = SourceModel.read(mdl.get(), vvd, vtx, RED);
			SourceModel blu = SourceModel.read(mdl.get(), vvd, vtx, BLU);

			return Optional.of(new PlayerMesh(red.vertices(), red.studio().bones(), animations(resources, red.studio(), model), (float) (1.0 / unitsPerBlock),
					draws(red, resources, textureManager, textures), draws(blu, resources, textureManager, textures)));
		} catch (ModelFormatException e) {
			Tf2Mc.LOGGER.warn("модель {} из расширительного пака не читается: {}", model, e.getMessage());
			return Optional.empty();
		}
	}

	/**
	 * Анимации из подключённой модели ({@code $includemodel}, у игроков — {@code *_animations.mdl}).
	 * Кости должны совпадать с костями модели по именам и порядку; иначе анимаций нет.
	 */
	private static AnimationSet animations(ResourceManager resources, StudioModel studio, String model) {
		for (String include : studio.includeModels()) {
			String path = include.replace('\\', '/').toLowerCase(Locale.ROOT);
			path = path.startsWith("models/") ? path : "models/" + path;
			Optional<byte[]> data = read(resources, path);

			if (data.isEmpty()) {
				continue;
			}

			try {
				AnimationSet set = AnimationSet.read(data.get());
				List<String> names = set.bones().stream().map(StudioModel.Bone::name).toList();

				if (names.equals(studio.bones().stream().map(StudioModel.Bone::name).toList())) {
					return set;
				}

				Tf2Mc.LOGGER.warn("кости анимаций {} не совпадают с костями модели {}", path, model);
			} catch (ModelFormatException e) {
				Tf2Mc.LOGGER.warn("анимации {} не читаются: {}", path, e.getMessage());
			}
		}

		return null;
	}

	private static List<Draw> draws(SourceModel model, ResourceManager resources, TextureManager textureManager, List<Identifier> textures) {
		List<Draw> draws = new ArrayList<>();

		for (SourceModel.Part part : model.parts()) {
			draws.add(new Draw(texture(part.materials(), resources, textureManager, textures), part.triangles()));
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

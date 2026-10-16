package io.github.egorka2000.tf2mc.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class TextureTest {
	private static final int BGR888 = TinyTexture.BGR888;
	private static final int DXT1 = TinyTexture.DXT1;
	private static final int DXT5 = TinyTexture.DXT5;

	private static byte[] vtf(int minor, int format, int width, int height, byte[]... mipmaps) {
		return TinyTexture.vtf(minor, format, width, height, mipmaps);
	}

	@Test
	void uncompressedPixelsKeepTheirColors() {
		// 2×1, BGR888: синий и красный.
		VtfImage image = VtfImage.read(vtf(2, BGR888, 2, 1, new byte[] {(byte) 255, 0, 0, 0, 0, (byte) 255}));

		assertEquals(2, image.width());
		assertEquals(0xFF0000FF, image.argb()[0]);
		assertEquals(0xFFFF0000, image.argb()[1]);
	}

	@Test
	void largestMipmapIsTakenFromTheEnd() {
		// Мелкий уровень 2×2 (один блок DXT1, чёрный), крупный 4×4 (белый): берётся крупный.
		byte[] black = {0, 0, 0, 0, 0, 0, 0, 0};
		byte[] white = {(byte) 0xFF, (byte) 0xFF, 0, 0, 0, 0, 0, 0};
		VtfImage image = VtfImage.read(vtf(3, DXT1, 4, 4, black, white));

		assertEquals(4, image.width());
		assertEquals(16, image.argb().length);
		for (int pixel : image.argb()) {
			assertEquals(0xFFFFFFFF, pixel);
		}
	}

	@Test
	void dxt1BlockInterpolatesAndCutsOut() {
		// c0 = белый > c1 = чёрный: индексы 0, 1, 2, 3 — белый, чёрный, 2/3 белого, 1/3 белого.
		byte[] opaque = {(byte) 0xFF, (byte) 0xFF, 0, 0, (byte) 0xE4, 0, 0, 0};
		VtfImage image = VtfImage.read(vtf(2, DXT1, 4, 4, opaque));
		assertEquals(0xFFFFFFFF, image.argb()[0]);
		assertEquals(0xFF000000, image.argb()[1]);
		assertEquals(0xFFAAAAAA, image.argb()[2]);
		assertEquals(0xFF555555, image.argb()[3]);

		// c0 ≤ c1: индекс 3 — прозрачная точка.
		byte[] cutout = {0, 0, (byte) 0xFF, (byte) 0xFF, (byte) 0xC0, 0, 0, 0};
		assertEquals(0, VtfImage.read(vtf(2, DXT1, 4, 4, cutout)).argb()[3]);
	}

	@Test
	void dxt5AlphaUsesItsTable() {
		// Прозрачность: a0 = 255, a1 = 0, коды точек 0, 1, 2 ...; цвет — белый.
		byte[] block = {(byte) 255, 0, (byte) 0b10_001_000, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, 0, 0, 0, 0, 0, 0};
		int[] pixels = VtfImage.read(vtf(2, DXT5, 4, 4, block)).argb();

		assertEquals(255, pixels[0] >>> 24);
		assertEquals(0, pixels[1] >>> 24);
		assertEquals((6 * 255) / 7, pixels[2] >>> 24);
		assertEquals(0xFFFFFF, pixels[2] & 0xFFFFFF);
	}

	@Test
	void wrongTexturesAreRejected() {
		assertThrows(ModelFormatException.class, () -> VtfImage.read(TinyModel.mdl()));
		byte[] future = vtf(2, BGR888, 1, 1, new byte[3]);
		future[8] = 6;
		assertThrows(ModelFormatException.class, () -> VtfImage.read(future));
		assertThrows(ModelFormatException.class, () -> VtfImage.read(vtf(2, 99, 1, 1, new byte[4])));
		assertThrows(ModelFormatException.class, () -> VtfImage.read(vtf(2, BGR888, 4, 4, new byte[3])));
	}

	@Test
	void materialGivesBaseTexture() {
		VmtMaterial material = VmtMaterial.parse("""
				"VertexLitGeneric"
				{
					// комментарий
					"$basetexture" "Models\\Player\\Soldier\\soldier_red.vtf"
					"$phong" "1"
					"Proxies"
					{
						"AnimatedTexture" { "animatedtexturevar" "$detail" }
					}
				}
				""");

		assertEquals("vertexlitgeneric", material.shader());
		assertEquals(Optional.of("models/player/soldier/soldier_red"), material.baseTexture());
		assertEquals(Optional.of("1"), material.parameter("$PHONG"));
		assertEquals(Optional.empty(), material.parameter("animatedtexturevar"));
	}

	@Test
	void eyeMaterialFallsBackToIris() {
		VmtMaterial eye = VmtMaterial.parse("""
				"EyeRefract"
				{
					"$Iris" "models/player/shared/eye-iris-blue"
				}
				""");

		assertEquals(Optional.empty(), eye.baseTexture());
		assertEquals(Optional.of("models/player/shared/eye-iris-blue"), eye.colorTexture());
	}

	@Test
	void patchMaterialTakesReplacedParameters() {
		VmtMaterial material = VmtMaterial.parse("""
				patch
				{
					include "materials/models/player/soldier/soldier_red.vmt"
					replace
					{
						$basetexture models/player/soldier/soldier_red_zombie
					}
				}
				""");

		assertEquals("patch", material.shader());
		assertEquals(Optional.of("materials/models/player/soldier/soldier_red.vmt"), material.include());
		assertEquals(Optional.of("models/player/soldier/soldier_red_zombie"), material.baseTexture());
	}

	@Test
	void realSoldierTextureIsDecoded() throws IOException {
		Path file = Path.of(System.getProperty("user.home"), "Documents", "tf2mc-expansion-pack", "pack", "materials", "models", "player", "soldier", "soldier_red.vtf");
		String env = System.getenv("TF2MC_PACK_DIR");
		if (env != null) {
			file = Path.of(env, "materials", "models", "player", "soldier", "soldier_red.vtf");
		}

		assumeTrue(Files.isRegularFile(file), "нет расширительного пака");
		VtfImage image = VtfImage.read(Files.readAllBytes(file));

		assertEquals(1024, image.width());
		assertEquals(512, image.height());

		// Текстура не пустая: разных цветов много, почти все точки непрозрачные.
		long colors = java.util.Arrays.stream(image.argb()).distinct().count();
		long opaque = java.util.Arrays.stream(image.argb()).filter(pixel -> pixel >>> 24 == 255).count();
		assertTrue(colors > 1000, "цветов: " + colors);
		assertTrue(opaque > image.argb().length * 0.9, "непрозрачных: " + opaque);
	}
}

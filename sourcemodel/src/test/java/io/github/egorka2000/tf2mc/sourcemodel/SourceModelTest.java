package io.github.egorka2000.tf2mc.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class SourceModelTest {
	private static final int SUM = TinyModel.CHECKSUM;

	@Test
	void tinyModelIsAssembled() {
		SourceModel model = SourceModel.read(TinyModel.mdl(), TinyModel.vvd(SUM), TinyModel.vtx(SUM), 0);
		StudioModel studio = model.studio();

		assertEquals("test/tiny.mdl", studio.name());
		assertEquals(List.of("root", "child"), studio.bones().stream().map(StudioModel.Bone::name).toList());
		assertEquals(0, studio.bones().get(1).parent());
		assertEquals(10.0F, studio.bones().get(1).position()[2]);
		assertEquals(1.0F, studio.bones().get(1).rotation()[3]);
		assertEquals(-10.0F, studio.bones().get(1).poseToBone()[11]);

		assertEquals(1, model.parts().size());
		assertEquals(List.of("models/test/tex_red", "tex_red"), model.parts().get(0).materials());
		assertArrayEquals(new int[] {0, 1, 2, 2, 1, 3}, model.parts().get(0).triangles());
		assertEquals(2, model.triangleCount());

		assertEquals(List.of("models/test_animations.mdl"), studio.includeModels());
		StudioModel.Hitbox head = studio.hitboxSets().get(0).hitboxes().get(0);
		assertEquals("default", studio.hitboxSets().get(0).name());
		assertEquals(1, head.bone());
		assertEquals(1, head.group());
		assertEquals("head", head.name());
		assertArrayEquals(new float[] {-2.0F, 0.0F, 0.0F}, head.min());
	}

	@Test
	void verticesKeepBonesAndTexCoords() {
		VertexData vertices = VertexData.read(TinyModel.vvd(SUM));

		assertEquals(4, vertices.count());
		assertEquals(3.0F, vertices.positions()[9]);
		assertEquals(1.0F, vertices.normals()[11]);
		assertEquals(0.75F, vertices.texCoords()[6]);
		// Третья вершина на дочерней кости с весом 1; неиспользуемые места — кость 0 с весом 0.
		assertArrayEquals(new int[] {1, 0, 0}, Arrays.copyOfRange(vertices.boneIndices(), 6, 9));
		assertArrayEquals(new float[] {1.0F, 0.0F, 0.0F}, Arrays.copyOfRange(vertices.boneWeights(), 6, 9));
	}

	@Test
	void fixupTableReordersVertices() {
		// Сначала вершины 2–3, потом 0–1. Поправка с уровнем меньше нуля не нужна никакому уровню.
		VertexData vertices = VertexData.read(TinyModel.vvd(SUM, 0, 2, 2, -1, 0, 4, 0, 0, 2));

		float[] x = new float[4];
		for (int i = 0; i < 4; i++) {
			x[i] = vertices.positions()[i * 3];
		}

		assertArrayEquals(new float[] {2.0F, 3.0F, 0.0F, 1.0F}, x);
	}

	@Test
	void secondSkinFamilyIsUsed() {
		SourceModel blu = SourceModel.read(TinyModel.mdl(), TinyModel.vvd(SUM), TinyModel.vtx(SUM), 1);
		assertEquals(2, blu.studio().skinFamilies());
		assertEquals(0, blu.studio().texture(1, 0));
		// Семейство за пределами таблицы — последнее существующее.
		assertEquals(0, blu.studio().texture(5, 0));
	}

	@Test
	void filesFromDifferentBuildsAreRejected() {
		ModelFormatException error = assertThrows(ModelFormatException.class,
				() -> SourceModel.read(TinyModel.mdl(), TinyModel.vvd(SUM + 1), TinyModel.vtx(SUM), 0));
		assertTrue(error.getMessage().contains("контрольные суммы"), error.getMessage());
	}

	@Test
	void wrongFormatsAreRejected() {
		assertThrows(ModelFormatException.class, () -> StudioModel.read(TinyModel.vvd(SUM)));
		assertThrows(ModelFormatException.class, () -> StudioModel.read(TinyModel.mdl(49, SUM)));
		assertThrows(ModelFormatException.class, () -> VertexData.read(TinyModel.mdl()));
		assertThrows(ModelFormatException.class, () -> StripData.read(TinyModel.mdl()));
	}

	@Test
	void truncatedFileIsRejected() {
		byte[] mdl = TinyModel.mdl();
		assertThrows(ModelFormatException.class, () -> StudioModel.read(Arrays.copyOf(mdl, 600)));
		assertThrows(ModelFormatException.class, () -> StripData.read(Arrays.copyOf(TinyModel.vtx(SUM), 120)));
	}
}

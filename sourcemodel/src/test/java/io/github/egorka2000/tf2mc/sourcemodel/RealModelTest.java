package io.github.egorka2000.tf2mc.sourcemodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Чтение настоящей модели Soldier из расширительного пака. Файлов Valve в репозитории нет, поэтому
 * тест идёт только там, где пак собран (по умолчанию «Документы\tf2mc-expansion-pack\pack», или
 * папка из переменной окружения {@code TF2MC_PACK_DIR}); в CI он пропускается.
 */
class RealModelTest {
	private static Path packDir() {
		String env = System.getenv("TF2MC_PACK_DIR");
		return env != null ? Path.of(env) : Path.of(System.getProperty("user.home"), "Documents", "tf2mc-expansion-pack", "pack");
	}

	private static byte[] read(String name) throws IOException {
		return Files.readAllBytes(packDir().resolve("models/player").resolve(name));
	}

	@Test
	void soldierIsRead() throws IOException {
		assumeTrue(Files.isRegularFile(packDir().resolve("models/player/soldier.mdl")), "нет расширительного пака: " + packDir());

		SourceModel model = SourceModel.read(read("soldier.mdl"), read("soldier.vvd"), read("soldier.dx90.vtx"), 0);
		StudioModel studio = model.studio();

		// Числа из заголовка файла, прочитанные вручную при разборе формата.
		assertEquals(48, studio.version());
		assertEquals(86, studio.bones().size());
		assertEquals(9626, model.vertices().count());
		assertEquals(20, studio.textures().size());
		assertTrue(studio.includeModels().stream().anyMatch(name -> name.contains("soldier_animations")), "нет подключённой модели анимаций: " + studio.includeModels());

		// Корень скелета без родителя, у остальных родитель раньше них.
		assertEquals(-1, studio.bones().get(0).parent());
		for (int i = 1; i < studio.bones().size(); i++) {
			assertTrue(studio.bones().get(i).parent() < i, "кость " + i + " раньше родителя");
		}

		assertFalse(model.parts().isEmpty());
		assertTrue(model.triangleCount() > 3000, "треугольников: " + model.triangleCount());
		assertTrue(model.parts().stream().anyMatch(part -> part.materials().contains("models/player/soldier/soldier_red")), "нет материала soldier_red: "
				+ model.parts().stream().map(SourceModel.Part::materials).toList());

		// Хитбоксы: есть голова.
		assertTrue(studio.hitboxSets().get(0).hitboxes().stream().anyMatch(box -> box.group() == 1), "нет хитбокса головы");

		// Сумма весов костей у каждой вершины — единица.
		float[] weights = model.vertices().boneWeights();
		for (int v = 0; v < model.vertices().count(); v++) {
			float sum = weights[v * 3] + weights[v * 3 + 1] + weights[v * 3 + 2];
			assertEquals(1.0, sum, 1e-3, "веса вершины " + v);
		}

		System.out.println("Soldier: треугольников " + model.triangleCount() + ", материалы "
				+ model.parts().stream().map(SourceModel.Part::materials).distinct().toList() + ", папки " + studio.textureDirs()
				+ ", хитбоксов " + studio.hitboxSets().get(0).hitboxes().size());
	}
}

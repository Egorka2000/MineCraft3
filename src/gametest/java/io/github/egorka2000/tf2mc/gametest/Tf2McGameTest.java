package io.github.egorka2000.tf2mc.gametest;

import io.github.egorka2000.tf2mc.Tf2Mc;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Серверные игровые тесты: запускают настоящий сервер с модом.
 */
public class Tf2McGameTest {
	// Дымовой тест: сервер с модом стартует, мод виден загрузчику.
	@GameTest
	public void modIsLoadedOnServer(GameTestHelper helper) {
		if (!FabricLoader.getInstance().isModLoaded(Tf2Mc.MOD_ID)) {
			throw new IllegalStateException("мод " + Tf2Mc.MOD_ID + " не загружен на сервере");
		}

		helper.succeed();
	}
}

package io.github.egorka2000.tf2mc.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import com.mojang.brigadier.tree.CommandNode;
import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.command.Tf2Commands;
import io.github.egorka2000.tf2mc.config.ConfigManager;
import io.github.egorka2000.tf2mc.config.Tf2Config;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

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

	// Настройки прочитаны при старте, команда /tf2 зарегистрирована на сервере.
	@GameTest
	public void configIsLoadedAndCommandIsRegistered(GameTestHelper helper) {
		if (Tf2Mc.config().current().substeps() < Tf2Config.MIN_SUBSTEPS) {
			throw new IllegalStateException("настройки не загружены");
		}

		CommandNode<CommandSourceStack> root = helper.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild(Tf2Commands.ROOT);

		if (root == null || root.getChild("reload") == null || root.getChild("config") == null
				|| root.getChild("movement") == null || root.getChild("speed") == null) {
			throw new IllegalStateException("команда /" + Tf2Commands.ROOT + " не зарегистрирована полностью");
		}

		helper.succeed();
	}

	// Сквозная проверка T1.2: правка файла и /tf2 reload меняют действующие настройки без перезапуска.
	@GameTest
	public void reloadCommandAppliesChangedFile(GameTestHelper helper) throws IOException {
		MinecraftServer server = helper.getLevel().getServer();
		ConfigManager config = Tf2Mc.config();
		String original = Files.readString(config.file(), StandardCharsets.UTF_8);

		try {
			Files.writeString(config.file(), "{\"substeps\": 7}", StandardCharsets.UTF_8);
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), Tf2Commands.ROOT + " reload");

			if (config.current().substeps() != 7) {
				throw new IllegalStateException("после /tf2 reload substeps = " + config.current().substeps() + ", ожидалось 7");
			}

			// Ошибка в файле не должна сбрасывать настройки.
			Files.writeString(config.file(), "{\"substeps\": 700}", StandardCharsets.UTF_8);
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), Tf2Commands.ROOT + " reload");

			if (config.current().substeps() != 7) {
				throw new IllegalStateException("ошибочный файл изменил настройки: substeps = " + config.current().substeps());
			}
		} finally {
			Files.writeString(config.file(), original, StandardCharsets.UTF_8);
			config.load();
		}

		helper.succeed();
	}
}

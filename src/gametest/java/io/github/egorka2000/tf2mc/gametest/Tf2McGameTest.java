package io.github.egorka2000.tf2mc.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.tree.CommandNode;
import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.command.Tf2Commands;
import io.github.egorka2000.tf2mc.config.ConfigManager;
import io.github.egorka2000.tf2mc.config.Tf2Config;
import io.github.egorka2000.tf2mc.movement.MovementToggle;
import io.netty.channel.embedded.EmbeddedChannel;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

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

	// Необязательный ник в конце команды: без ника — себе, с ником — названному игроку, и только оператор.
	@GameTest
	public void commandTargetsSenderOrNamedPlayer(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String name = "tf2mc-target";
		ServerPlayer player = joinTestPlayer(helper, name);

		try {
			// С консоли сервера и с ником: применяется к названному игроку.
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), Tf2Commands.ROOT + " movement on " + name);

			if (!MovementToggle.isMovementEnabled(player.getUUID())) {
				throw new IllegalStateException("команда с ником не применилась к игроку " + name);
			}

			// От имени игрока и без ника: применяется к нему самому.
			server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), Tf2Commands.ROOT + " speed on");

			if (!MovementToggle.isSpeedometerEnabled(player.getUUID())) {
				throw new IllegalStateException("команда без ника не применилась к тому, кто её ввёл");
			}

			// Игрок без прав оператора не может указывать ник, даже свой.
			server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), Tf2Commands.ROOT + " movement off " + name);

			if (!MovementToggle.isMovementEnabled(player.getUUID())) {
				throw new IllegalStateException("игрок без прав оператора применил команду с ником");
			}
		} finally {
			MovementToggle.forget(player.getUUID());
			server.getPlayerList().remove(player);
		}

		helper.succeed();
	}

	/** Подключает к серверу тестового игрока без настоящего клиента: его видно в списке игроков по нику. */
	private static ServerPlayer joinTestPlayer(GameTestHelper helper, String name) {
		ServerLevel level = helper.getLevel();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		return player;
	}
}

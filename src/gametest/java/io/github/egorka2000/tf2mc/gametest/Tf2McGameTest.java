package io.github.egorka2000.tf2mc.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.tree.CommandNode;
import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.clazz.ClassHealth;
import io.github.egorka2000.tf2mc.clazz.PlayerClasses;
import io.github.egorka2000.tf2mc.clazz.TfClass;
import io.github.egorka2000.tf2mc.command.Tf2Commands;
import io.github.egorka2000.tf2mc.config.ConfigManager;
import io.github.egorka2000.tf2mc.config.Tf2Config;
import io.github.egorka2000.tf2mc.movement.SpeedometerToggle;
import io.netty.channel.embedded.EmbeddedChannel;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;

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
				|| root.getChild("class") == null || root.getChild("speed") == null) {
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
			run(server.createCommandSourceStack(), "speed on " + name);
			check(SpeedometerToggle.isEnabled(player.getUUID()), "команда с ником не применилась к игроку " + name);

			// От имени игрока и без ника: применяется к нему самому.
			run(player.createCommandSourceStack(), "speed off");
			check(!SpeedometerToggle.isEnabled(player.getUUID()), "команда без ника не применилась к тому, кто её ввёл");

			// Игрок без прав оператора не может указывать ник, даже свой.
			run(player.createCommandSourceStack(), "speed on " + name);
			check(!SpeedometerToggle.isEnabled(player.getUUID()), "игрок без прав оператора применил команду с ником");

			// Без слова on или off команда переключает состояние у того, кто её ввёл.
			run(player.createCommandSourceStack(), "speed");
			check(SpeedometerToggle.isEnabled(player.getUUID()), "команда без слова не включила спидометр");
			run(player.createCommandSourceStack(), "speed");
			check(!SpeedometerToggle.isEnabled(player.getUUID()), "команда без слова не выключила спидометр");
		} finally {
			SpeedometerToggle.forget(player.getUUID());
			server.getPlayerList().remove(player);
		}

		helper.succeed();
	}

	// Шаг 8а: команда выдаёт класс, класс задаёт здоровье, переживает смерть и снимается командой vanilla.
	@GameTest
	public void classIsAssignedKeptThroughDeathAndRemoved(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String name = "tf2mc-soldier";
		ServerPlayer player = joinTestPlayer(helper, name);

		try {
			check(PlayerClasses.get(player).isEmpty(), "у нового игрока уже есть класс");
			check(player.getMaxHealth() == 20.0f, "у ванильного игрока максимум здоровья " + player.getMaxHealth());

			run(server.createCommandSourceStack(), "class soldier " + name);
			check(PlayerClasses.get(player).orElse(null) == TfClass.SOLDIER, "класс не выдан");
			check(player.getMaxHealth() == 200.0f, "максимум здоровья Soldier: " + player.getMaxHealth());
			check(player.getHealth() == 200.0f, "здоровье после выдачи класса: " + player.getHealth());

			// Игрок без прав оператора класс не меняет.
			run(player.createCommandSourceStack(), "class vanilla");
			check(PlayerClasses.get(player).isPresent(), "игрок без прав оператора снял себе класс");

			// Смерть и перерождение: класс и здоровье класса остаются.
			player.setHealth(50.0f);
			player = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
			check(PlayerClasses.get(player).orElse(null) == TfClass.SOLDIER, "класс потерян после перерождения");
			check(player.getMaxHealth() == 200.0f, "максимум здоровья после перерождения: " + player.getMaxHealth());
			check(player.getHealth() == 200.0f, "здоровье после перерождения: " + player.getHealth());

			run(server.createCommandSourceStack(), "class vanilla " + name);
			check(PlayerClasses.get(player).isEmpty(), "класс не снят");
			check(player.getMaxHealth() == 20.0f, "максимум здоровья после снятия класса: " + player.getMaxHealth());
			check(player.getHealth() == 20.0f, "здоровье после снятия класса: " + player.getHealth());
		} finally {
			server.getPlayerList().remove(player);
		}

		helper.succeed();
	}

	@GameTest
	public void classFollowsTf2HealthRules(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String name = "tf2mc-health";
		ServerPlayer player = joinTestPlayer(helper, name);

		try {
			// У ванильного игрока оверхила нет, бег работает.
			run(server.createCommandSourceStack(), "health set 300 " + name);
			check(ClassHealth.total(player) == 20.0f, "здоровье ванильного игрока: " + ClassHealth.total(player));
			player.setSprinting(true);
			check(player.isSprinting(), "ванильный игрок не бежит");
			player.setSprinting(false);

			run(server.createCommandSourceStack(), "class soldier " + name);

			// Бега нет.
			player.setSprinting(true);
			check(!player.isSprinting(), "игрок с классом бежит");

			// Урон от падения: 5 блоков — это 600 u/s, ниже порога; 10 блоков — 849 u/s,
			// 14,1% от 200 HP с разбросом 20%, то есть от 22 до 33.
			check(ClassHealth.fallDamage(player, 5.0, 1.0f).orElse(-1) == 0, "урон от падения с 5 блоков");

			for (int i = 0; i < 50; i++) {
				int damage = ClassHealth.fallDamage(player, 10.0, 1.0f).orElse(-1);
				check(damage >= 22 && damage <= 33, "урон от падения с 10 блоков: " + damage);
			}

			// То же через игру: падение с 10 блоков снимает столько же, а не ванильные 7.
			// Пока сервер не считает клиент загрузившимся, игрок неуязвим.
			player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
			// В творческом режиме урона от падения нет вовсе.
			player.setGameMode(GameType.SURVIVAL);
			player.causeFallDamage(10.0, 1.0f, player.damageSources().fall());
			check(player.getHealth() >= 200.0f - 33.0f && player.getHealth() <= 200.0f - 22.0f, "здоровье после падения с 10 блоков: " + player.getHealth());

			// Сытость не расходуется, здоровье само не восстанавливается.
			player.setHealth(100.0f);
			player.getFoodData().setFoodLevel(10);
			player.getFoodData().addExhaustion(40.0f);

			for (int i = 0; i < 200; i++) {
				player.getFoodData().tick(player);
			}

			check(player.getFoodData().getFoodLevel() == 10, "сытость изменилась: " + player.getFoodData().getFoodLevel());
			player.getFoodData().setFoodLevel(20);
			player.getFoodData().setSaturation(20.0f);

			for (int i = 0; i < 200; i++) {
				player.getFoodData().tick(player);
			}

			check(player.getHealth() == 100.0f, "здоровье восстановилось само: " + player.getHealth());

			// Оверхил командой: правило 150% на неё не действует.
			run(server.createCommandSourceStack(), "health set 250 " + name);
			check(player.getHealth() == 200.0f && player.getAbsorptionAmount() == 50.0f, "здоровье 250: " + player.getHealth() + " + " + player.getAbsorptionAmount());
			run(server.createCommandSourceStack(), "health add 500 " + name);
			check(ClassHealth.total(player) == 750.0f, "оверхил командой выше 150%: " + ClassHealth.total(player));

			// Лечение оверхил выше 150% не поднимает и не срезает.
			check(ClassHealth.heal(player, 100.0f, true) == 750.0f, "лечение изменило оверхил, выставленный командой: " + ClassHealth.total(player));

			// Лечение по правилам TF2: сначала здоровье, потом оверхил не выше 150% максимума.
			run(server.createCommandSourceStack(), "health set 100 " + name);
			check(ClassHealth.heal(player, 50.0f, false) == 150.0f, "лечение без оверхила: " + ClassHealth.total(player));
			check(ClassHealth.heal(player, 500.0f, false) == 200.0f, "лечение без оверхила подняло здоровье выше максимума: " + ClassHealth.total(player));
			check(ClassHealth.heal(player, 30.0f, true) == 230.0f, "лечение с оверхилом: " + ClassHealth.total(player));
			check(ClassHealth.heal(player, 500.0f, true) == 300.0f, "предел оверхила при лечении: " + ClassHealth.total(player));

			// Полный оверхил спадает за 15 секунд: за секунду уходит 100 / 15.
			for (int i = 0; i < 20; i++) {
				ClassHealth.tickOverheal(server);
			}

			check(Math.abs(player.getAbsorptionAmount() - (100.0f - 100.0f / 15.0f)) < 0.01f, "оверхил через секунду: " + player.getAbsorptionAmount());

			for (int i = 0; i < 20 * 15; i++) {
				ClassHealth.tickOverheal(server);
			}

			check(ClassHealth.total(player) == 200.0f, "здоровье после затухания оверхила: " + ClassHealth.total(player));

			run(server.createCommandSourceStack(), "health add -150 " + name);
			check(ClassHealth.total(player) == 50.0f, "здоровье после вычитания: " + ClassHealth.total(player));

			// Игрок без прав оператора здоровье не меняет.
			run(player.createCommandSourceStack(), "health set 200");
			check(ClassHealth.total(player) == 50.0f, "игрок без прав оператора изменил себе здоровье");

			// Снятие класса убирает и предел оверхила.
			run(server.createCommandSourceStack(), "health set 300 " + name);
			run(server.createCommandSourceStack(), "class vanilla " + name);
			check(ClassHealth.total(player) == 20.0f, "здоровье после снятия класса: " + ClassHealth.total(player));
		} finally {
			server.getPlayerList().remove(player);
		}

		helper.succeed();
	}

	private static void run(CommandSourceStack source, String command) {
		source.getServer().getCommands().performPrefixedCommand(source, Tf2Commands.ROOT + " " + command);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
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

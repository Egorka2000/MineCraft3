package io.github.egorka2000.tf2mc.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.tree.CommandNode;
import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.block.PickupBlock;
import io.github.egorka2000.tf2mc.block.PropBlockEntity;
import io.github.egorka2000.tf2mc.block.Tf2Blocks;
import io.github.egorka2000.tf2mc.clazz.ClassHealth;
import io.github.egorka2000.tf2mc.clazz.PendingChoice;
import io.github.egorka2000.tf2mc.clazz.PlayerClasses;
import io.github.egorka2000.tf2mc.clazz.TfClass;
import io.github.egorka2000.tf2mc.command.Tf2Commands;
import io.github.egorka2000.tf2mc.config.ConfigManager;
import io.github.egorka2000.tf2mc.config.Tf2Config;
import io.github.egorka2000.tf2mc.movement.SpeedometerToggle;
import io.github.egorka2000.tf2mc.team.TeamSpawns;
import io.github.egorka2000.tf2mc.team.Teams;
import io.github.egorka2000.tf2mc.team.TfTeam;
import io.github.egorka2000.tf2mc.weapon.RocketBlast;
import io.github.egorka2000.tf2mc.weapon.RocketEntity;
import io.github.egorka2000.tf2mc.weapon.RocketLauncher;
import io.github.egorka2000.tf2mc.weapon.Tf2Weapons;
import io.netty.channel.embedded.EmbeddedChannel;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Серверные игровые тесты: запускают настоящий сервер с модом.
 */
public class Tf2McGameTest {
	// Аптечки и шкафы — с блочной сущностью: по ней клиент рисует модели TF2. Блок, поставленный
	// до версии 0.20.0 (без сущности), получает её при загрузке чанка.
	@GameTest
	public void propBlocksHaveBlockEntities(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos kit = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos locker = helper.absolutePos(new BlockPos(3, 1, 1));
		level.setBlockAndUpdate(kit, Tf2Blocks.SMALL_HEALTH_KIT.defaultBlockState());
		level.setBlockAndUpdate(locker, Tf2Blocks.RED_RESUPPLY.defaultBlockState());

		check(level.getBlockEntity(kit) instanceof PropBlockEntity, "у аптечки нет блочной сущности");
		check(level.getBlockEntity(locker) instanceof PropBlockEntity, "у шкафа нет блочной сущности");

		// Без сущности, как у блока из старого мира. level.getBlockEntity на сервере создал бы её
		// сам, поэтому чанк спрашивается без создания.
		LevelChunk chunk = level.getChunkAt(kit);
		level.removeBlockEntity(kit);
		check(chunk.getBlockEntity(kit, LevelChunk.EntityCreationType.CHECK) == null, "сущность аптечки не удалилась");
		Tf2Blocks.addMissingProps(chunk);
		check(chunk.getBlockEntity(kit, LevelChunk.EntityCreationType.CHECK) instanceof PropBlockEntity, "сущность аптечки не восстановилась при загрузке чанка");

		helper.succeed();
	}

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
				|| root.getChild("class") == null || root.getChild("team") == null || root.getChild("ammo") == null || root.getChild("rocket") == null || root.getChild("speed") == null) {
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

			// Класс виден в имени игрока: «Ник (soldier)».
			check(player.getDisplayName().getString().equals(name + " (soldier)"), "имя игрока с классом: " + player.getDisplayName().getString());

			// Свой класс игрок без прав оператора меняет сам, а указать ник не может, даже свой.
			run(player.createCommandSourceStack(), "class vanilla " + name);
			check(PlayerClasses.get(player).isPresent(), "игрок без прав оператора применил команду с ником");
			// Свой выбор при уже выбранном классе запоминается до шкафа или возрождения.
			run(player.createCommandSourceStack(), "class vanilla");
			check(PlayerClasses.get(player).isPresent(), "свой выбор класса применился сразу, а не запомнился");
			check(PendingChoice.pendingClass(player).orElse("").equals("vanilla"), "выбор класса не запомнен");
			// Выбор текущего класса отменяет запомненный.
			run(player.createCommandSourceStack(), "class soldier");
			check(PlayerClasses.get(player).orElse(null) == TfClass.SOLDIER, "класс игрока изменился");
			check(PendingChoice.pendingClass(player).isEmpty(), "выбор текущего класса не отменил запомненный");
			check(player.getHealth() == 200.0f, "здоровье после выбора класса игроком: " + player.getHealth());

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

	// Шаг 8в: команды игроков. Свои друг друга не ранят, чужие ранят; наблюдатель и возврат режима.
	@GameTest
	public void teamsBlockFriendlyFire(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		ServerPlayer first = joinTestPlayer(helper, "tf2mc-team-a");
		ServerPlayer second = joinTestPlayer(helper, "tf2mc-team-b");

		try {
			for (ServerPlayer player : new ServerPlayer[] {first, second}) {
				// Пока сервер не считает клиент загрузившимся, игрок неуязвим; в творческом режиме тоже.
				player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
				player.setGameMode(GameType.SURVIVAL);
			}

			// Вошедший в мир игрок без команды — нейтральный: не ранит других игроков, и они не ранят его.
			check(Teams.isNeutral(first) && Teams.isNeutral(second), "вошедший игрок не стал нейтральным");
			check(Teams.get(first).isEmpty(), "у нейтрального игрока есть команда");
			check(!hit(level, first, second, 2.0f), "урон между нейтральными прошёл");

			// Выбрать нейтральную команду нельзя.
			run(first.createCommandSourceStack(), "team neutral");

			// Свою команду выбирает сам игрок, без прав оператора.
			run(first.createCommandSourceStack(), "team red");
			check(Teams.get(first).orElse(null) == TfTeam.RED, "игрок не вступил в красную команду");
			check(!Teams.isNeutral(first), "игрок с командой остался нейтральным");

			// Игрок с командой и нейтральный друг друга не ранят.
			check(!hit(level, first, second, 2.0f), "игрок с командой ранил нейтрального");
			check(!hit(level, second, first, 2.0f), "нейтральный ранил игрока с командой");

			// Составы команд.
			check(Teams.members(server, TfTeam.RED).equals(List.of("tf2mc-team-a")), "состав красных: " + Teams.members(server, TfTeam.RED));
			check(Teams.neutralMembers(server).contains("tf2mc-team-b"), "нейтрального нет в списке: " + Teams.neutralMembers(server));

			// Чужую команду игрок без прав оператора не назначает.
			run(first.createCommandSourceStack(), "team red tf2mc-team-b");
			check(Teams.get(second).isEmpty(), "игрок без прав оператора назначил команду другому");

			// random выбирает команду, где меньше игроков: в красной один, в синей никого.
			run(second.createCommandSourceStack(), "team random");
			check(Teams.get(second).orElse(null) == TfTeam.BLU, "random выбрал не меньшую команду");

			// Разные команды: урон проходит.
			check(hit(level, first, second, 4.0f), "урон по чужой команде не прошёл");

			// Одна команда: урона нет.
			run(server.createCommandSourceStack(), "team red tf2mc-team-b");
			check(Teams.get(second).orElse(null) == TfTeam.RED, "оператор не назначил команду");
			check(!hit(level, first, second, 8.0f), "урон по своей команде прошёл");

			// Наблюдатель выходит из команды; при возвращении получает прежний режим игры.
			run(second.createCommandSourceStack(), "team spectator");
			check(Teams.get(second).isEmpty() && !Teams.isNeutral(second), "наблюдатель остался в команде");
			check(second.gameMode() == GameType.SPECTATOR, "режим наблюдателя не включён: " + second.gameMode());
			run(second.createCommandSourceStack(), "team blu");
			check(Teams.get(second).orElse(null) == TfTeam.BLU, "наблюдатель не вернулся в команду");
			check(second.gameMode() == GameType.SURVIVAL, "прежний режим игры не возвращён: " + second.gameMode());
		} finally {
			for (ServerPlayer player : new ServerPlayer[] {first, second}) {
				server.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
				server.getPlayerList().remove(player);
			}
		}

		helper.succeed();
	}

	// Шаг 12а: точки возрождения команд.
	// Если у команды несколько точек, после смерти игрок появляется на случайной из них, а не всё
	// время на одной (жалоба владельца проекта на карте 2fort).
	@GameTest
	public void respawnPicksRandomTeamSpawn(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = joinTestPlayer(helper, "tf2mc-random-spawn");
		// Две точки рядом и две далеко, в незагруженных чанках — как вторая комната возрождения на 2fort.
		List<BlockPos> spawns = List.of(new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(1, 1, 300), new BlockPos(3, 1, 300));
		Set<BlockPos> used = new HashSet<>();

		try {
			player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
			player.setGameMode(GameType.SURVIVAL);

			for (BlockPos spawn : spawns) {
				helper.setBlock(spawn, Tf2Blocks.RED_SPAWN);
			}

			run(player.createCommandSourceStack(), "team red");

			BlockPos last = null;

			for (int death = 0; death < 30; death++) {
				player = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
				player.connection.player = player;
				BlockPos spawn = helper.relativePos(player.blockPosition().below());
				check(!spawn.equals(last), "после смерти игрок снова появился на той же точке " + spawn);
				used.add(spawn);
				last = spawn;
			}

			// За 30 смертей не увидеть хотя бы одну из четырёх точек почти невозможно.
			check(used.size() == 4, "за 30 смертей игрок появлялся только на точках " + used);
		} finally {
			for (BlockPos spawn : spawns) {
				helper.setBlock(spawn, Blocks.AIR);
			}

			TeamSpawns.of(server, TfTeam.RED);
			server.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
			server.getPlayerList().remove(player);
		}

		helper.succeed();
	}

	@GameTest
	public void teamSpawnPointsPlacePlayers(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer player = joinTestPlayer(helper, "tf2mc-spawn");
		BlockPos red = new BlockPos(1, 1, 1);
		BlockPos blu = new BlockPos(6, 1, 6);

		try {
			player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
			player.setGameMode(GameType.SURVIVAL);
			helper.setBlock(red, Tf2Blocks.RED_SPAWN);
			helper.setBlock(blu, Tf2Blocks.BLU_SPAWN);

			// Нейтральный игрок никуда не переносится.
			Vec3 before = player.position();
			check(!TeamSpawns.sendToSpawn(player), "нейтрального игрока перенесло на точку");
			check(player.position().equals(before), "нейтральный игрок сдвинулся");

			// Вступивший в команду встаёт на её точку.
			run(player.createCommandSourceStack(), "team red");
			checkOnSpawn(helper, player, red, "после вступления в RED");

			// После смерти — снова на точке своей команды.
			player.teleportTo(player.level(), before.x, before.y, before.z, Set.of(), 0.0f, 0.0f, true);
			player = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
			// Игра после возрождения сама переключает соединение на нового игрока; здесь возрождение
			// вызвано напрямую, и без этого перенос нового игрока ушёл бы старому.
			player.connection.player = player;
			checkOnSpawn(helper, player, red, "после перерождения в RED");

			run(server.createCommandSourceStack(), "team blu tf2mc-spawn");
			checkOnSpawn(helper, player, blu, "после перехода в BLU");

			// Сломанная точка вычёркивается: у BLU точек не остаётся, игрок остаётся на месте.
			helper.setBlock(blu, Blocks.AIR);
			check(TeamSpawns.of(server, TfTeam.BLU).isEmpty(), "сломанная точка осталась в списке");
			check(TeamSpawns.of(server, TfTeam.RED).size() == 1, "точек RED: " + TeamSpawns.of(server, TfTeam.RED).size());
			check(!TeamSpawns.sendToSpawn(player), "игрока перенесло на сломанную точку");
		} finally {
			helper.setBlock(red, Blocks.AIR);
			helper.setBlock(blu, Blocks.AIR);
			TeamSpawns.of(server, TfTeam.RED);
			server.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
			server.getPlayerList().remove(player);
		}

		helper.succeed();
	}

	// Шаг 12а: аптечки и патроны. Числа: маленькие 20%, средние 50%, большие 100%; без оверхила.
	@GameTest
	public void pickupsRestoreHealthAndAmmo(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		String name = "tf2mc-pickup";
		ServerPlayer player = joinTestPlayer(helper, name);
		BlockPos small = new BlockPos(1, 1, 1);
		BlockPos large = new BlockPos(3, 1, 1);
		BlockPos ammo = new BlockPos(5, 1, 1);

		try {
			player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
			player.setGameMode(GameType.SURVIVAL);
			helper.setBlock(small, Tf2Blocks.SMALL_HEALTH_KIT);
			helper.setBlock(large, Tf2Blocks.LARGE_HEALTH_KIT);
			helper.setBlock(ammo, Tf2Blocks.MEDIUM_AMMO_PACK);

			// Игрок без класса аптечки не подбирает.
			player.setHealth(10.0f);
			standIn(helper, player, small);
			check(player.getHealth() == 10.0f && available(helper, small), "игрок без класса подобрал аптечку");

			run(server.createCommandSourceStack(), "class soldier " + name);

			// Маленькая аптечка: 20% от 200 = 40. Подставка пустеет, через 10 секунд новая.
			player.setHealth(100.0f);
			standIn(helper, player, small);
			check(player.getHealth() == 140.0f, "после маленькой аптечки здоровье " + player.getHealth());
			check(!available(helper, small), "аптечка осталась на подставке");
			check(level.getBlockTicks().hasScheduledTick(helper.absolutePos(small), Tf2Blocks.SMALL_HEALTH_KIT), "новая аптечка не запланирована");
			standIn(helper, player, small);
			check(player.getHealth() == 140.0f, "пустая подставка вылечила");
			level.getBlockState(helper.absolutePos(small)).tick(level, helper.absolutePos(small), level.getRandom());
			check(available(helper, small), "новая аптечка не появилась");

			// Большая аптечка лечит до максимума, но не выше: оверхила нет.
			standIn(helper, player, large);
			check(player.getHealth() == 200.0f && player.getAbsorptionAmount() == 0.0f, "после большой аптечки " + ClassHealth.total(player));

			// Здоровому аптечка не нужна, а горящему нужна: она тушит огонь.
			level.getBlockState(helper.absolutePos(large)).tick(level, helper.absolutePos(large), level.getRandom());
			standIn(helper, player, large);
			check(available(helper, large), "здоровый игрок подобрал аптечку");
			player.setRemainingFireTicks(100);
			standIn(helper, player, large);
			check(!available(helper, large) && !player.isOnFire(), "аптечка не потушила горящего игрока");

			// Средние патроны: 50% запаса ракетницы (20) = 10; обойму не трогают.
			run(server.createCommandSourceStack(), "ammo set 5 " + name);
			standIn(helper, player, ammo);
			checkAmmo(player, 4, 15, "после средних патронов");
			level.getBlockState(helper.absolutePos(ammo)).tick(level, helper.absolutePos(ammo), level.getRandom());
			standIn(helper, player, ammo);
			checkAmmo(player, 4, 20, "запас выше наибольшего");
			level.getBlockState(helper.absolutePos(ammo)).tick(level, helper.absolutePos(ammo), level.getRandom());
			standIn(helper, player, ammo);
			check(available(helper, ammo), "игрок с полным запасом подобрал патроны");
		} finally {
			for (BlockPos pos : new BlockPos[] {small, large, ammo}) {
				helper.setBlock(pos, Blocks.AIR);
			}
			run(server.createCommandSourceStack(), "class vanilla " + name);
			server.getPlayerList().remove(player);
		}

		helper.succeed();
	}

	// Шаг 12а: шкаф пополнения и отложенный выбор класса и команды.
	@GameTest
	public void resupplyLockerRefillsAndAppliesChoice(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String name = "tf2mc-locker";
		ServerPlayer player = joinTestPlayer(helper, name);
		BlockPos redLocker = new BlockPos(3, 1, 3);
		BlockPos bluLocker = new BlockPos(3, 1, 6);
		BlockPos bluSpawn = new BlockPos(6, 1, 1);

		try {
			player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
			player.setGameMode(GameType.SURVIVAL);
			helper.setBlock(redLocker, Tf2Blocks.RED_RESUPPLY);
			helper.setBlock(bluLocker, Tf2Blocks.BLU_RESUPPLY);
			helper.setBlock(bluSpawn, Tf2Blocks.BLU_SPAWN);

			// Первый выбор — сразу: класс игроку без класса, команда нейтральному.
			run(player.createCommandSourceStack(), "class soldier");
			run(player.createCommandSourceStack(), "team red");
			check(PlayerClasses.get(player).orElse(null) == TfClass.SOLDIER, "первый выбор класса не применён сразу");
			check(Teams.get(player).orElse(null) == TfTeam.RED, "первый выбор команды не применён сразу");

			// Раненый, горящий, без ракет. Вдали от шкафа ничего не меняется.
			player.setHealth(50.0f);
			player.setRemainingFireTicks(100);
			run(server.createCommandSourceStack(), "ammo set 0 " + name);
			check(RocketLauncher.fire(player), "выстрел перед пополнением не прошёл");
			standAt(helper, player, new Vec3(0.5, 1.0, 0.5));
			check(player.getHealth() == 50.0f, "шкаф сработал издалека");

			// Чужой шкаф не служит.
			standAt(helper, player, new Vec3(4.5, 1.0, 6.5));
			check(player.getHealth() == 50.0f, "шкаф BLU пополнил игрока RED");

			// Свой шкаф вплотную: полное здоровье, огонь потушен, боезапас целиком, включая обойму.
			standAt(helper, player, new Vec3(4.5, 1.0, 3.5));
			check(player.getHealth() == 200.0f, "здоровье после шкафа: " + player.getHealth());
			check(!player.isOnFire(), "шкаф не потушил огонь");
			checkAmmo(player, 4, 20, "после шкафа");

			// Свой выбор класса запоминается и применяется у шкафа.
			run(player.createCommandSourceStack(), "class vanilla");
			check(PlayerClasses.get(player).isPresent(), "смена класса применилась вдали от шкафа");
			standAt(helper, player, new Vec3(4.5, 1.0, 3.5));
			check(PlayerClasses.get(player).isEmpty(), "запомненный класс не применён у шкафа");
			check(RocketLauncher.ammo(player) == null, "у ванильного игрока осталась ракетница");

			// Свой выбор команды тоже: у шкафа игрок переходит в BLU и переносится на её точку.
			run(server.createCommandSourceStack(), "class soldier " + name);
			run(player.createCommandSourceStack(), "team blu");
			check(Teams.get(player).orElse(null) == TfTeam.RED, "смена команды применилась вдали от шкафа");
			standAt(helper, player, new Vec3(4.5, 1.0, 3.5));
			check(Teams.get(player).orElse(null) == TfTeam.BLU, "запомненная команда не применена у шкафа");
			checkOnSpawn(helper, player, bluSpawn, "после смены команды у шкафа");

			// Запомненный класс применяется и при возрождении.
			run(player.createCommandSourceStack(), "class vanilla");
			player = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
			player.connection.player = player;
			check(PlayerClasses.get(player).isEmpty(), "запомненный класс не применён при возрождении");
			check(PendingChoice.pendingClass(player).isEmpty(), "запомненный класс остался после возрождения");
			check(!player.getInventory().contains(stack -> stack.is(RocketLauncher.ITEM)), "ванильный игрок возродился с ракетницей");

			// Оператор с ником меняет сразу.
			run(server.createCommandSourceStack(), "class soldier " + name);
			run(server.createCommandSourceStack(), "team red " + name);
			check(PlayerClasses.get(player).isPresent() && Teams.get(player).orElse(null) == TfTeam.RED, "оператор с ником не применил выбор сразу");
		} finally {
			for (BlockPos pos : new BlockPos[] {redLocker, bluLocker, bluSpawn}) {
				helper.setBlock(pos, Blocks.AIR);
			}
			TeamSpawns.of(server, TfTeam.BLU);
			run(server.createCommandSourceStack(), "class vanilla " + name);
			server.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
			server.getPlayerList().remove(player);
		}

		helper.succeed();
	}

	// Шаг 12а: дверь зоны возрождения пропускает только свою команду.
	@GameTest
	public void spawnDoorLetsOnlyOwnTeamThrough(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		ServerPlayer red = joinTestPlayer(helper, "tf2mc-door-red");
		ServerPlayer blu = joinTestPlayer(helper, "tf2mc-door-blu");
		ServerPlayer neutral = joinTestPlayer(helper, "tf2mc-door-neutral");
		BlockPos door = new BlockPos(3, 1, 3);

		try {
			helper.setBlock(door, Tf2Blocks.RED_SPAWN_DOOR);
			helper.setBlock(door.above(), Tf2Blocks.RED_SPAWN_DOOR);
			run(server.createCommandSourceStack(), "team red tf2mc-door-red");
			run(server.createCommandSourceStack(), "team blu tf2mc-door-blu");

			BlockPos absolute = helper.absolutePos(door);
			BlockState state = level.getBlockState(absolute);
			check(state.getCollisionShape(level, absolute, CollisionContext.of(red)).isEmpty(), "дверь RED не пропускает игрока RED");
			check(!state.getCollisionShape(level, absolute, CollisionContext.of(blu)).isEmpty(), "дверь RED пропускает игрока BLU");
			check(!state.getCollisionShape(level, absolute, CollisionContext.of(neutral)).isEmpty(), "дверь RED пропускает нейтрального");
			check(!state.getCollisionShape(level, absolute, CollisionContext.empty()).isEmpty(), "дверь RED пропускает снаряды и прочее");

			// Настоящее движение: RED проходит насквозь, BLU упирается.
			for (ServerPlayer player : new ServerPlayer[] {red, blu}) {
				player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
				player.setGameMode(GameType.SURVIVAL);
				player.setPos(helper.absoluteVec(new Vec3(1.5, 1.0, 3.5)));
				player.move(MoverType.SELF, new Vec3(3.0, 0.0, 0.0));
			}

			double doorFace = helper.absoluteVec(new Vec3(3.0, 1.0, 3.5)).x;
			check(red.getX() > doorFace + 1.0, "игрок RED не прошёл сквозь дверь: x " + (red.getX() - doorFace));
			check(blu.getX() < doorFace, "игрок BLU прошёл сквозь дверь: x " + (blu.getX() - doorFace));
		} finally {
			helper.setBlock(door, Blocks.AIR);
			helper.setBlock(door.above(), Blocks.AIR);
			for (ServerPlayer player : new ServerPlayer[] {red, blu, neutral}) {
				server.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
				server.getPlayerList().remove(player);
			}
		}

		helper.succeed();
	}

	/** Ставит игрока в точку площадки и даёт сработать блокам рядом. */
	private static void standAt(GameTestHelper helper, ServerPlayer player, Vec3 relative) {
		player.setPos(helper.absoluteVec(relative));
		Tf2Blocks.touchBlocks(helper.getLevel().getServer());
	}

	/** Ставит игрока в блок и даёт ему подобрать то, что там лежит. */
	private static void standIn(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(pos)));
		Tf2Blocks.touchBlocks(helper.getLevel().getServer());
	}

	private static boolean available(GameTestHelper helper, BlockPos pos) {
		return helper.getBlockState(pos).getValue(PickupBlock.AVAILABLE);
	}

	private static void checkOnSpawn(GameTestHelper helper, ServerPlayer player, BlockPos spawn, String where) {
		Vec3 expected = Vec3.atBottomCenterOf(helper.absolutePos(spawn).above());
		check(player.position().distanceTo(expected) < 1e-6, where + ": игрок в " + player.position() + ", а не на точке " + expected
				+ "; команда " + Teams.get(player).map(TfTeam::id).orElse("нет") + ", точек BLU " + TeamSpawns.of(player.level().getServer(), TfTeam.BLU).size()
				+ ", игрок в списке сервера: " + (player.level().getServer().getPlayerList().getPlayer(player.getUUID()) == player));
	}

	/**
	 * Первый игрок бьёт второго; возвращает, уменьшилось ли здоровье второго.
	 *
	 * @param damage урон; после удара игра полсекунды пропускает по той же цели только больший урон,
	 *               поэтому каждый следующий удар в тесте сильнее предыдущего
	 */
	private static boolean hit(ServerLevel level, ServerPlayer attacker, ServerPlayer target, float damage) {
		target.setHealth(target.getMaxHealth());
		target.hurtServer(level, attacker.damageSources().playerAttack(attacker), damage);
		return target.getHealth() < target.getMaxHealth();
	}

	// Шаг 9: взрыв ракеты. Числа посчитаны вручную по формулам из docs/02-physics.md.
	// Центр игрока в 40,5 u над ногами. Взрыв под ногами стрелка: урон 90 × (1 − 0,5 × 40,5 / 121) = 74,94.
	// Толчок вверх: 0,948 × 74,94 × 5 = 355,2 u/s на земле и вдвое больше, 710,4 u/s, в воздухе.
	@GameTest
	public void rocketBlastPushesAndDamages(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		ServerPlayer shooter = joinTestPlayer(helper, "tf2mc-rocket-a");
		ServerPlayer other = joinTestPlayer(helper, "tf2mc-rocket-b");
		Vec3 shooterSpot = helper.absoluteVec(new Vec3(1.5, 3.0, 1.5));
		Vec3 otherSpot = helper.absoluteVec(new Vec3(6.5, 3.0, 6.5));

		try {
			for (ServerPlayer player : new ServerPlayer[] {shooter, other}) {
				player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
				player.setGameMode(GameType.SURVIVAL);
			}

			shooter.setPos(shooterSpot);
			other.setPos(otherSpot);

			// Без класса ракета не выпускается.
			check(!Tf2Weapons.fireRocket(shooter), "игрок без класса выпустил ракету");

			run(server.createCommandSourceStack(), "class soldier tf2mc-rocket-a");
			run(server.createCommandSourceStack(), "class soldier tf2mc-rocket-b");
			RocketEntity rocket = new RocketEntity(Tf2Weapons.ROCKET, level);

			// Взрыв под ногами стоящего на земле стрелка: полный урон, слабый толчок.
			shooter.setOnGround(true);
			shooter.setDeltaMovement(Vec3.ZERO);
			RocketBlast.explode(level, shooterSpot, rocket, shooter, null);
			checkNear("здоровье после взрыва на земле", 200.0 - 74.94, shooter.getHealth(), 0.05);
			checkNear("толчок вверх на земле, u/s", 355.2, shooter.getDeltaMovement().y * 45.0 * 20.0, 0.5);
			checkNear("толчок вбок на земле", 0.0, shooter.getDeltaMovement().horizontalDistance(), 1e-6);

			// То же в воздухе: толчок вдвое сильнее, урон 60%.
			shooter.setHealth(200.0f);
			shooter.setOnGround(false);
			shooter.setDeltaMovement(Vec3.ZERO);
			RocketBlast.explode(level, shooterSpot, rocket, shooter, null);
			checkNear("здоровье после взрыва в воздухе", 200.0 - 74.94 * 0.6, shooter.getHealth(), 0.05);
			checkNear("толчок вверх в воздухе, u/s", 710.4, shooter.getDeltaMovement().y * 45.0 * 20.0, 0.5);

			// Толчок добавляется к скорости, а не заменяет её.
			shooter.setHealth(200.0f);
			shooter.setDeltaMovement(new Vec3(0.1, 0.0, 0.0));
			RocketBlast.explode(level, shooterSpot, rocket, shooter, null);
			checkNear("горизонтальная скорость сохранена", 0.1, shooter.getDeltaMovement().x, 1e-6);
			shooter.setHealth(200.0f);

			// Нейтральный игрок взрывом чужой ракеты не задевается.
			check(Teams.isNeutral(other), "второй игрок не нейтральный");
			other.setDeltaMovement(Vec3.ZERO);
			RocketBlast.explode(level, otherSpot, rocket, shooter, null);
			check(other.getHealth() == 200.0f, "взрыв ранил нейтрального игрока: " + other.getHealth());

			// Своя команда не задевается.
			run(server.createCommandSourceStack(), "team red tf2mc-rocket-a");
			run(server.createCommandSourceStack(), "team red tf2mc-rocket-b");
			RocketBlast.explode(level, otherSpot, rocket, shooter, null);
			check(other.getHealth() == 200.0f, "взрыв ранил игрока своей команды: " + other.getHealth());
			checkNear("игрок своей команды отброшен", 0.0, other.getDeltaMovement().length(), 1e-6);

			// Чужая команда. Стрелок в 318,2 u от цели: множитель расстояния 1,25 − 0,25 × 318,2 / 512 = 1,0946,
			// урон в центре 98,52. По радиусу: 98,52 × (1 − 0,5 × 40,5 / 146) = 84,85, толчок 0,948 × 84,85 × 6 = 482,7 u/s.
			run(server.createCommandSourceStack(), "team blu tf2mc-rocket-b");
			RocketBlast.explode(level, otherSpot, rocket, shooter, null);
			checkNear("здоровье противника после взрыва", 200.0 - 84.85, other.getHealth(), 0.05);
			checkNear("толчок противника вверх, u/s", 482.7, other.getDeltaMovement().y * 45.0 * 20.0, 0.5);

			// Прямое попадание: урон центра взрыва, 98,52, независимо от расстояния до центра цели.
			other.setHealth(200.0f);
			RocketBlast.explode(level, otherSpot, rocket, shooter, other);
			checkNear("здоровье противника после прямого попадания", 200.0 - 98.52, other.getHealth(), 0.05);

			// Стрелок был в семи блоках от этих взрывов: его они не задели.
			check(shooter.getHealth() == 200.0f, "далёкий взрыв ранил стрелка: " + shooter.getHealth());
		} finally {
			for (ServerPlayer player : new ServerPlayer[] {shooter, other}) {
				server.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
				server.getPlayerList().remove(player);
			}
		}

		helper.succeed();
	}

	// Шаг 9: полёт ракеты. 1100 u/s — это 1,222 блока за тик; ракета взрывается о первый блок на пути.
	@GameTest
	public void rocketFliesStraightAndExplodesOnBlock(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		ServerPlayer shooter = joinTestPlayer(helper, "tf2mc-rocket-c");

		try {
			shooter.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
			shooter.setGameMode(GameType.SURVIVAL);
			run(server.createCommandSourceStack(), "class soldier tf2mc-rocket-c");

			// Стрелок смотрит вдоль оси z, в свободное пространство площадки.
			shooter.setPos(helper.absoluteVec(new Vec3(1.5, 3.0, 0.5)));
			shooter.setYRot(0.0f);
			shooter.setXRot(0.0f);

			check(Tf2Weapons.fireRocket(shooter), "игрок с классом не выпустил ракету");
			RocketEntity rocket = RocketEntity.fire(level, shooter);
			Vec3 start = rocket.position();

			for (int i = 0; i < 3; i++) {
				rocket.tick();
			}

			check(!rocket.isRemoved(), "ракета исчезла в свободном полёте");
			checkNear("путь ракеты за три тика, блоки", 3.0 * 1100.0 / 45.0 / 20.0, rocket.position().distanceTo(start), 1e-3);
			checkNear("ракета отклонилась от прямой", 0.0, Math.abs(rocket.getX() - start.x) + Math.abs(rocket.getY() - start.y), 1e-6);

			// Срока жизни нет: как в TF2, ракета летит, пока во что-нибудь не попадёт.
			rocket.tickCount = 100_000;
			rocket.tick();
			check(!rocket.isRemoved(), "ракета исчезла от старости");
			rocket.discard();

			// Исчезает, только когда попасть уже не во что: над миром, под ним и в незагруженной области.
			check(RocketEntity.canFlyTo(level, start), "ракете нельзя лететь рядом со стрелком");
			check(!RocketEntity.canFlyTo(level, new Vec3(start.x, level.getMaxY() + 2.0, start.z)), "ракета летит выше мира");
			check(!RocketEntity.canFlyTo(level, new Vec3(start.x, level.getMinY() - 1.0, start.z)), "ракета летит ниже мира");
			check(!RocketEntity.canFlyTo(level, start.add(1_000_000.0, 0.0, 0.0)), "ракета летит в незагруженную область");

			// Блок в пяти блоках перед глазами: ракета взрывается о него не позже чем через пять тиков.
			helper.setBlock(new BlockPos(1, 4, 6), Blocks.STONE);
			RocketEntity second = RocketEntity.fire(level, shooter);
			int ticks = 0;

			while (!second.isRemoved() && ticks < 20) {
				second.tick();
				ticks++;
			}

			check(second.isRemoved(), "ракета не взорвалась о блок");
			check(ticks <= 5, "ракета взорвалась слишком поздно: тик " + ticks);
			check(helper.getBlockState(new BlockPos(1, 4, 6)).is(Blocks.STONE), "взрыв разрушил блок");

			// Взрыв был в пяти блоках, это 225 u: дальше радиуса для стрелка.
			check(shooter.getHealth() == 200.0f, "далёкий взрыв своей ракеты ранил стрелка: " + shooter.getHealth());
		} finally {
			server.getScoreboard().removePlayerFromTeam(shooter.getScoreboardName());
			server.getPlayerList().remove(shooter);
		}

		helper.succeed();
	}

	// Шаг 9: после урона игра рассылает игроку его скорость с сервера. У игрока с классом её считает
	// клиент, и такая рассылка стёрла бы разгон, поэтому для него она отключена.
	@GameTest
	public void damageDoesNotResetClassPlayerVelocity(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = joinTestPlayer(helper, "tf2mc-velocity");

		try {
			player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
			player.setGameMode(GameType.SURVIVAL);

			// Ванильный игрок: всё как в обычной игре, скорость помечена к рассылке.
			player.syncVelocity = false;
			check(player.hurtServer(level, player.damageSources().generic(), 1.0f), "урон по ванильному игроку не прошёл");
			check(player.syncVelocity, "ванильному игроку скорость после урона не рассылается");

			run(server.createCommandSourceStack(), "class soldier tf2mc-velocity");
			player.syncVelocity = false;
			player.setDeltaMovement(new Vec3(0.3, 0.2, 0.1));
			check(player.hurtServer(level, player.damageSources().generic(), 5.0f), "урон по игроку с классом не прошёл");
			check(!player.syncVelocity, "игроку с классом после урона рассылается скорость с сервера");
			checkNear("скорость игрока с классом после урона", 0.0, player.getDeltaMovement().distanceTo(new Vec3(0.3, 0.2, 0.1)), 1e-9);
		} finally {
			server.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
			server.getPlayerList().remove(player);
		}

		helper.succeed();
	}

	// Шаг 10: ракетница. Выдаётся с классом, обойма 4 и запас 20, перезарядка по одной ракете.
	@GameTest
	public void rocketLauncherHasAmmoAndReloads(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		String name = "tf2mc-launcher";
		ServerPlayer player = joinTestPlayer(helper, name);

		try {
			player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
			player.setGameMode(GameType.SURVIVAL);
			player.setPos(helper.absoluteVec(new Vec3(4.5, 3.0, 4.5)));
			// Ракеты уходят вертикально вверх и никого не задевают.
			player.setXRot(-90.0f);

			// Без класса ракетницы нет.
			check(RocketLauncher.ammo(player) == null, "у игрока без класса есть боезапас");
			check(!RocketLauncher.fire(player), "игрок без ракетницы выстрелил");

			run(server.createCommandSourceStack(), "class soldier " + name);
			check(player.getInventory().contains(stack -> stack.is(RocketLauncher.ITEM)), "ракетница не выдана вместе с классом");
			checkAmmo(player, 4, 20, "после выдачи класса");
			// Перезарядка идёт только с ракетницей в руках.
			for (int slot = 0; slot < 9; slot++) {
				if (player.getInventory().getItem(slot).is(RocketLauncher.ITEM)) {
					player.getInventory().setSelectedSlot(slot);
				}
			}
			check(player.getMainHandItem().is(RocketLauncher.ITEM), "ракетница не на панели быстрого доступа");

			// Повторная выдача класса не даёт вторую ракетницу.
			run(server.createCommandSourceStack(), "class soldier " + name);
			check(player.getInventory().countItem(RocketLauncher.ITEM) == 1, "ракетниц в инвентаре: " + player.getInventory().countItem(RocketLauncher.ITEM));

			// Выстрел тратит ракету; до конца паузы в 16 тиков второй выстрел невозможен.
			check(RocketLauncher.fire(player), "первый выстрел не прошёл");
			checkAmmo(player, 3, 20, "после первого выстрела");
			check(!RocketLauncher.fire(player), "второй выстрел прошёл без паузы");
			tickLauncher(server, 15);
			check(!RocketLauncher.fire(player), "выстрел прошёл на тик раньше конца паузы");
			tickLauncher(server, 1);

			for (int shot = 2; shot <= 4; shot++) {
				check(RocketLauncher.fire(player), "выстрел " + shot + " не прошёл");
				tickLauncher(server, 16);
			}

			// Обойма пуста: стрелять нечем.
			checkAmmo(player, 0, 20, "после четырёх выстрелов");
			check(!RocketLauncher.fire(player), "выстрел с пустой обоймой прошёл");

			// Перезарядка: первая ракета за 18 тиков, остальные по 16.
			tickLauncher(server, 17);
			checkAmmo(player, 0, 20, "за тик до первой ракеты");
			tickLauncher(server, 1);
			checkAmmo(player, 1, 19, "после первой ракеты");

			// Ракетница убрана из рук: перезарядка стоит. Снова в руках — начинается заново, с 18 тиков.
			int launcherSlot = player.getInventory().getSelectedSlot();
			player.getInventory().setSelectedSlot((launcherSlot + 1) % 9);
			tickLauncher(server, 100);
			checkAmmo(player, 1, 19, "перезарядка шла с ракетницей не в руках");
			player.getInventory().setSelectedSlot(launcherSlot);
			tickLauncher(server, 17);
			checkAmmo(player, 1, 19, "за тик до ракеты после возвращения в руки");
			tickLauncher(server, 1);
			checkAmmo(player, 2, 18, "после ракеты, заряженной после возвращения в руки");

			tickLauncher(server, 2 * 16);
			checkAmmo(player, 4, 16, "после полной перезарядки");

			// Отладочная команда: запас, прибавка с обрезкой по наибольшему, полное пополнение.
			run(server.createCommandSourceStack(), "ammo set 2 " + name);
			checkAmmo(player, 4, 2, "после ammo set");
			run(server.createCommandSourceStack(), "ammo add 100 " + name);
			checkAmmo(player, 4, 20, "после ammo add");
			check(RocketLauncher.fire(player), "выстрел перед пополнением не прошёл");
			run(server.createCommandSourceStack(), "ammo refill " + name);
			checkAmmo(player, 4, 20, "после ammo refill");

			// Игрок без прав оператора боезапас не меняет.
			run(server.createCommandSourceStack(), "ammo set 5 " + name);
			run(player.createCommandSourceStack(), "ammo refill");
			checkAmmo(player, 4, 5, "после команды без прав оператора");

			// После смерти ракетница и полный боезапас выдаются заново.
			player.getInventory().clearContent();
			player = server.getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
			check(player.getInventory().contains(stack -> stack.is(RocketLauncher.ITEM)), "ракетница не выдана после перерождения");
			checkAmmo(player, 4, 20, "после перерождения");

			// Снятие класса забирает ракетницу и боезапас.
			run(server.createCommandSourceStack(), "class vanilla " + name);
			check(!player.getInventory().contains(stack -> stack.is(RocketLauncher.ITEM)), "ракетница осталась после снятия класса");
			check(RocketLauncher.ammo(player) == null, "боезапас остался после снятия класса");
		} finally {
			server.getScoreboard().removePlayerFromTeam(player.getScoreboardName());
			server.getPlayerList().remove(player);
		}

		helper.succeed();
	}

	private static void tickLauncher(MinecraftServer server, int ticks) {
		for (int i = 0; i < ticks; i++) {
			RocketLauncher.tick(server);
		}
	}

	private static void checkAmmo(ServerPlayer player, int clip, int reserve, String when) {
		RocketLauncher.Ammo ammo = RocketLauncher.ammo(player);

		if (ammo == null || ammo.clip() != clip || ammo.reserve() != reserve) {
			throw new IllegalStateException("боезапас " + when + ": ожидалось " + clip + " / " + reserve + ", получено " + ammo);
		}
	}

	private static void checkNear(String what, double expected, double actual, double tolerance) {
		if (Math.abs(expected - actual) > tolerance) {
			throw new IllegalStateException(what + ": ожидалось " + expected + ", получено " + actual);
		}
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

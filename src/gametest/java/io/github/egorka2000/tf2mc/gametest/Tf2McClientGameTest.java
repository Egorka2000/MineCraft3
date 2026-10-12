package io.github.egorka2000.tf2mc.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.config.ConfigManager;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.world.phys.Vec3;

/**
 * Клиентские игровые тесты: запускают настоящий клиент, нажимают клавиши и измеряют движение игрока.
 * Проверяют подключение движения TF2 к игре (T1.3) в одиночной игре и на выделенном сервере.
 *
 * <p>Запуск: {@code gradlew runClientGameTest}. В обычную сборку не входят: им нужно окно игры.
 */
@SuppressWarnings("UnstableApiUsage") // API клиентских игровых тестов Fabric помечен как экспериментальный.
public class Tf2McClientGameTest implements FabricClientGameTest {
	private static final double UNITS_PER_BLOCK = 45.0;

	/** Ванильная скорость ходьбы, блоков в секунду. */
	private static final double VANILLA_WALK = 4.317;
	/** Временная скорость движения TF2 до появления классов: 300 u/s. */
	private static final double TF2_SPEED = 300.0 / UNITS_PER_BLOCK;
	/** Высота прыжка TF2: 289² / (2 * 800) юнитов. */
	private static final double TF2_JUMP = 289.0 * 289.0 / 1600.0 / UNITS_PER_BLOCK;

	static final int PLATFORM_Y = 200;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			runScenario(context, singleplayer.getServer(), singleplayer.getConnection(), "одиночная игра");
		}

		try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
			try (var connection = server.connect()) {
				runScenario(context, server, connection, "выделенный сервер");
			}
		}
	}

	private static void runScenario(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, String where) {
		connection.waitForChunksDownload();
		preparePlatform(context, server, connection);

		// Ваниль до включения: точка отсчёта и проверка, что мод ничего не меняет без команды.
		assertNear(where + ": ванильная скорость", VANILLA_WALK, walkSpeed(context), 0.15);

		resetPlayer(context, server, connection);
		server.runCommand("execute as @a run tf2 movement on");
		connection.waitForClientboundPackets();

		assertNear(where + ": скорость TF2", TF2_SPEED, walkSpeed(context), 0.1);
		assertStopsByFriction(context, where);
		assertServerAgrees(context, server, connection, where);

		resetPlayer(context, server, connection);
		assertNear(where + ": высота прыжка TF2", TF2_JUMP, jumpHeight(context), 0.03);
		assertServerAgrees(context, server, connection, where);

		// Стрейф в воздухе: ввод вбок с поворотом взгляда вслед за полётом разгоняет выше максимума.
		resetPlayer(context, server, connection);
		double strafeSpeed = airStrafeSpeed(context);

		if (strafeSpeed < TF2_SPEED + 0.2) {
			throw new AssertionError(where + ": стрейф не разогнал игрока: " + strafeSpeed + " блоков/с при максимуме " + TF2_SPEED);
		}

		Tf2Mc.LOGGER.info("[замер] {}: скорость после стрейфа в прыжке: {} блоков/с, {} u/s", where, String.format("%.3f", strafeSpeed), String.format("%.0f", strafeSpeed * UNITS_PER_BLOCK));
		assertServerAgrees(context, server, connection, where);

		// Неровности: ступенька, блок, стена, потолок, край, мелководье, лестница.
		TerrainScenarios.run(context, server, connection, where);

		// Настройки сервера доходят до клиента: вдвое меньшая гравитация даёт вдвое более высокий прыжок.
		withServerConfig(server, "{\"movement\": {\"gravity\": 400}}", () -> {
			connection.waitForClientboundPackets();
			resetPlayer(context, server, connection);
			assertNear(where + ": прыжок при половинной гравитации", TF2_JUMP * 2.0, jumpHeight(context), 0.06);
		});
		connection.waitForClientboundPackets();

		resetPlayer(context, server, connection);
		server.runCommand("execute as @a run tf2 movement off");
		connection.waitForClientboundPackets();
		assertNear(where + ": ванильная скорость после выключения", VANILLA_WALK, walkSpeed(context), 0.15);
	}

	/** Ровная каменная площадка высоко над миром: рельеф и вода на замеры не влияют. */
	private static void preparePlatform(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		server.runCommand("gamemode survival @a");
		server.runCommand("forceload add -48 -48 47 47");
		server.runCommand("fill -40 " + (PLATFORM_Y - 1) + " -40 40 " + (PLATFORM_Y - 1) + " 40 minecraft:stone");
		resetPlayer(context, server, connection);
	}

	private static void resetPlayer(ClientGameTestContext context, TestServerContext server, TestServerConnection connection) {
		context.getInput().releaseKey(options -> options.keyUp);
		server.runCommand("tp @a 0.5 " + PLATFORM_Y + " -30.5 0 0");
		connection.waitForClientboundPackets();
		context.waitTicks(10);
	}

	/** Установившаяся скорость при удержании «вперёд», блоков в секунду. */
	private static double walkSpeed(ClientGameTestContext context) {
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(20);

		Vec3 start = position(context);
		int ticks = 20;
		context.waitTicks(ticks);
		Vec3 end = position(context);

		context.getInput().releaseKey(options -> options.keyUp);
		return horizontalDistance(start, end) / ticks * 20.0;
	}

	private static void assertStopsByFriction(ClientGameTestContext context, String where) {
		context.waitTicks(20);
		Vec3 before = position(context);
		context.waitTicks(5);
		Vec3 after = position(context);

		assertNear(where + ": остановка трением", 0.0, horizontalDistance(before, after), 0.001);
	}

	/** Наибольшая высота над площадкой за время прыжка с места, блоков. */
	private static double jumpHeight(ClientGameTestContext context) {
		double ground = position(context).y;
		double apex = ground;

		context.getInput().holdKeyFor(options -> options.keyJump, 2);

		for (int tick = 0; tick < 40; tick++) {
			context.waitTick();
			apex = Math.max(apex, position(context).y);
		}

		return apex - ground;
	}

	/**
	 * Разбег, прыжок и стрейф влево с поворотом взгляда по направлению полёта.
	 * Возвращает наибольшую горизонтальную скорость за время полёта, блоков в секунду.
	 */
	private static double airStrafeSpeed(ClientGameTestContext context) {
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(20);
		context.getInput().holdKeyFor(options -> options.keyJump, 2);
		context.waitTicks(2);
		context.getInput().releaseKey(options -> options.keyUp);
		context.getInput().holdKey(options -> options.keyLeft);

		double fastest = 0.0;
		Vec3 previous = position(context);

		for (int tick = 0; tick < 11; tick++) {
			context.waitTick();
			Vec3 current = position(context);
			double dx = current.x - previous.x;
			double dz = current.z - previous.z;
			fastest = Math.max(fastest, Math.hypot(dx, dz) * 20.0);

			// Взгляд вдоль полёта: тогда ввод «влево» перпендикулярен скорости.
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			context.runOnClient(client -> client.player.setYRot(yaw));
			previous = current;
		}

		context.getInput().releaseKey(options -> options.keyLeft);
		context.waitTicks(20);
		return fastest;
	}

	/** Сервер принял движение клиента: позиции совпадают, игрока не отбросило назад. */
	static void assertServerAgrees(ClientGameTestContext context, TestServerContext serverContext, TestServerConnection connection, String where) {
		connection.waitForServerboundPackets();
		Vec3 client = position(context);
		Vec3 server = serverContext.computeOnServer(minecraftServer -> minecraftServer.getPlayerList().getPlayers().getFirst().position());

		assertNear(where + ": расхождение позиции клиента и сервера", 0.0, client.distanceTo(server), 0.5);
	}

	/** Временно подменяет файл настроек сервера и перечитывает его командой. */
	private static void withServerConfig(TestServerContext server, String json, Runnable action) {
		ConfigManager config = Tf2Mc.config();

		try {
			String original = Files.readString(config.file(), StandardCharsets.UTF_8);

			try {
				Files.writeString(config.file(), json, StandardCharsets.UTF_8);
				server.runCommand("tf2 reload");
				action.run();
			} finally {
				Files.writeString(config.file(), original, StandardCharsets.UTF_8);
				server.runCommand("tf2 reload");
			}
		} catch (IOException e) {
			throw new IllegalStateException("не удалось подменить файл настроек", e);
		}
	}

	static Vec3 position(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.player.position());
	}

	private static double horizontalDistance(Vec3 from, Vec3 to) {
		return Math.hypot(to.x - from.x, to.z - from.z);
	}

	static void assertNear(String what, double expected, double actual, double tolerance) {
		if (Math.abs(actual - expected) > tolerance) {
			throw new AssertionError(what + ": ожидалось " + expected + " ± " + tolerance + ", получено " + actual);
		}

		Tf2Mc.LOGGER.info("[замер] {}: {} (ожидалось {})", what, String.format("%.3f", actual), String.format("%.3f", expected));
	}
}

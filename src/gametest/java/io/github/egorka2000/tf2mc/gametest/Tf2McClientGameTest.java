package io.github.egorka2000.tf2mc.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.mojang.blaze3d.audio.SoundBuffer;
import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.client.HealthHud;
import io.github.egorka2000.tf2mc.client.WavSounds;
import io.github.egorka2000.tf2mc.client.model.ClassModels;
import io.github.egorka2000.tf2mc.client.model.PlayerAnimations;
import io.github.egorka2000.tf2mc.config.ConfigManager;
import io.github.egorka2000.tf2mc.sourcemodel.TinyModel;
import io.github.egorka2000.tf2mc.sourcemodel.TinyTexture;
import io.github.egorka2000.tf2mc.weapon.RocketLauncher;
import io.github.egorka2000.tf2mc.weapon.WeaponAction;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import net.minecraft.client.CameraType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
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
	/** Скорость Soldier: 240 u/s. */
	private static final double TF2_SPEED = 240.0 / UNITS_PER_BLOCK;
	/** Высота прыжка TF2: 289² / (2 * 800) юнитов. */
	private static final double TF2_JUMP = 289.0 * 289.0 / 1600.0 / UNITS_PER_BLOCK;

	static final int PLATFORM_Y = 200;

	@Override
	public void runTest(ClientGameTestContext context) {
		assertSoundsHavePlaceholders(context);
		assertWavSoundIsLoaded(context);
		assertExpansionPackZipIsRead(context);

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			runScenario(context, singleplayer.getServer(), singleplayer.getConnection(), "одиночная игра");
			// В конце: перезагрузка ресурсов занимает секунды, а проверки сценария чувствительны ко времени.
			assertTinySourceModelIsDrawn(context, singleplayer.getServer());
			screenshotRealModel(context, singleplayer.getServer());
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

		assertDrawnByMod(context, where + ": ванильный игрок", false);

		resetPlayer(context, server, connection);
		// Оператор с ником: класс меняется сразу, без шкафа пополнения.
		server.runCommand("tf2 class soldier @p");
		connection.waitForClientboundPackets();
		context.waitTicks(2);

		// Класс и его здоровье дошли до клиента.
		assertNear(where + ": максимум здоровья Soldier на клиенте", 200.0, clientMaxHealth(context), 0.001);

		// Вместо сердец на экране число здоровья. В творческом режиме игра здоровье не показывает.
		server.runCommand("gamemode survival @a");
		context.waitTicks(5);
		assertShownHealth(context, where + ": здоровье Soldier на экране", "HP 200");
		assertDrawnByMod(context, where + ": игрок с классом", true);

		// Отрисовка от третьего лица проходит без ошибок: стойка и бег.
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(10);
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(10);
		context.getInput().releaseKey(options -> options.keyUp);
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(10);

		// Рядом — боезапас ракетницы, выданной вместе с классом.
		String ammo = context.computeOnClient(client -> HealthHud.shownAmmo());

		if (!"4 / 20".equals(ammo)) {
			throw new AssertionError(where + ": боезапас на экране: ожидалось 4 / 20, получено " + ammo);
		}

		server.runCommand("execute as @a run tf2 health set 300");
		context.waitTicks(5);
		// Оверхил спадает на треть единицы за тик, поэтому к моменту проверки он чуть меньше 300.
		String overheal = context.computeOnClient(client -> HealthHud.shownText());

		if (overheal == null || !overheal.matches("HP (29[5-9]|300)")) {
			throw new AssertionError(where + ": оверхил на экране: ожидалось от HP 295 до HP 300, получено " + overheal);
		}

		server.runCommand("execute as @a run tf2 health set 200");

		assertNear(where + ": скорость Soldier", TF2_SPEED, walkSpeed(context), 0.1);
		assertStopsByFriction(context, where);
		assertServerAgrees(context, server, connection, where);

		resetPlayer(context, server, connection);
		assertNear(where + ": высота прыжка TF2", TF2_JUMP, jumpHeight(context), 0.03);
		assertServerAgrees(context, server, connection, where);

		// Ракетный прыжок: сервер толкает игрока взрывом, а движение считает клиент.
		resetPlayer(context, server, connection);
		double groundedJump = rocketJumpHeight(context, server, connection, false);
		Tf2Mc.LOGGER.info("[замер] {}: ракетный прыжок с земли: {} блоков, {} u", where, String.format("%.3f", groundedJump), String.format("%.1f", groundedJump * UNITS_PER_BLOCK));
		// Взрыв под ногами стоящего игрока: урон 75,1, толчок 0,948 × 75,1 × 5 = 356 u/s, высота 356² / (2 × 800) = 79,2 u.
		assertNear(where + ": высота ракетного прыжка с земли", 79.2 / UNITS_PER_BLOCK, groundedJump, 0.05);
		// Урон по себе на земле полный: 200 − 75,1 = 124,9, на экране округляется вверх.
		assertShownHealth(context, where + ": здоровье после ракетного прыжка с земли", "HP 125");
		assertServerAgrees(context, server, connection, where);
		server.runCommand("execute as @a run tf2 health set 200");

		resetPlayer(context, server, connection);
		double airborneJump = rocketJumpHeight(context, server, connection, true);
		Tf2Mc.LOGGER.info("[замер] {}: ракетный прыжок в прыжке: {} блоков, {} u", where, String.format("%.3f", airborneJump), String.format("%.1f", airborneJump * UNITS_PER_BLOCK));

		// В воздухе толчок вдвое сильнее и складывается со скоростью прыжка. Точная высота зависит
		// от того, на каком тике прыжка взорвалась ракета, поэтому проверяется нижняя граница.
		if (airborneJump < groundedJump * 2.0) {
			throw new AssertionError(where + ": ракетный прыжок в прыжке не выше двух прыжков с земли: " + airborneJump + " блоков");
		}

		assertServerAgrees(context, server, connection, where);
		server.runCommand("execute as @a run tf2 health set 200");

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
		server.runCommand("tf2 class vanilla @p");
		connection.waitForClientboundPackets();
		context.waitTicks(2);
		assertNear(where + ": максимум здоровья после снятия класса", 20.0, clientMaxHealth(context), 0.001);
		assertShownHealth(context, where + ": после снятия класса снова сердца", null);
		assertNear(where + ": ванильная скорость после снятия класса", VANILLA_WALK, walkSpeed(context), 0.15);
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
	 * Ракета под ноги: игрок смотрит вниз, сервер выпускает ракету от его имени.
	 * Возвращает наибольшую высоту над точкой старта, блоки.
	 *
	 * @param jumpFirst сначала подпрыгнуть: взрыв застанет игрока в воздухе
	 */
	private static double rocketJumpHeight(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, boolean jumpFirst) {
		context.runOnClient(client -> client.player.setXRot(90.0f));
		// Поворот взгляда должен дойти до сервера: ракета летит туда, куда игрок смотрит на сервере.
		context.waitTicks(5);

		double ground = position(context).y;
		double apex = ground;

		if (jumpFirst) {
			context.getInput().holdKeyFor(options -> options.keyJump, 2);
			context.waitTicks(3);
		}

		server.runCommand("execute as @a run tf2 rocket");

		for (int tick = 0; tick < 80; tick++) {
			context.waitTick();
			apex = Math.max(apex, position(context).y);
		}

		context.runOnClient(client -> client.player.setXRot(0.0f));
		context.waitTicks(5);
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

	/**
	 * За каждым звуком мода должен стоять настоящий звук: без расширительного пака — ванильная
	 * заглушка. Опечатка в sounds.json дала бы тишину без единой ошибки.
	 */
	private static void assertSoundsHavePlaceholders(ClientGameTestContext context) {
		for (String sound : new String[] {"weapon_rpg.single", "weapon_rpg.reload", "baseexplosioneffect.sound", "healthkit.touch", "ammopack.touch", "item.materialize", "regenerate.touch"}) {
			String file = soundBehind(context, sound);

			if (file == null || file.equals("minecraft:empty") || file.equals("minecraft:intentionally_empty")) {
				throw new AssertionError("за звуком " + sound + " ничего не стоит: " + file);
			}

			Tf2Mc.LOGGER.info("[звук] {} -> {}", sound, file);
		}
	}

	/**
	 * Звук в формате WAV из ресурсов: игра сама его не понимает, читает мод. Проверяется на
	 * собственном тестовом звуке из ресурсов тестового мода — 0,1 секунды, стерео, 22050 Гц.
	 * Так работает расширительный пак, но файлов Valve для проверки не нужно.
	 */
	private static void assertWavSoundIsLoaded(ClientGameTestContext context) {
		Identifier event = Identifier.fromNamespaceAndPath("tf2mc-test", "beep");
		String file = soundBehind(context, event);

		// Игра не выбросила звук при загрузке, хотя файла beep.ogg нет.
		if (!"tf2mc-test:beep".equals(file)) {
			throw new AssertionError("звук из WAV не принят игрой: за " + event + " стоит " + file);
		}

		String loaded = context.computeOnClient(client -> {
			try {
				SoundBuffer buffer = WavSounds.load(client.getResourceManager(), Identifier.fromNamespaceAndPath("tf2mc-test", "sounds/beep.wav"));
				return buffer.size() + " байт, " + (int) buffer.format().getSampleRate() + " Гц, каналов " + buffer.format().getChannels();
			} catch (IOException e) {
				return "ошибка: " + e.getMessage();
			}
		});

		// 2205 отсчётов по два байта: стерео сведено в моно.
		if (!"4410 байт, 22050 Гц, каналов 1".equals(loaded)) {
			throw new AssertionError("звук из WAV прочитан неверно: " + loaded);
		}

		// Проигрывание идёт через загрузчик звуков игры: ошибка чтения попала бы в лог.
		context.runOnClient(client -> client.getSoundManager().play(
				SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(event), 1.0f)));
		context.waitTicks(10);
	}

	/**
	 * Расширительный пак в том виде, в каком его собирает {@code convert-pack.ps1}: архив ZIP
	 * в папке {@code resourcepacks}, звуки под именами из таблиц TF2, файлы WAV и MP3. Архив
	 * собирается здесь же из тестового звука, файлов Valve не нужно.
	 */
	private static void assertExpansionPackZipIsRead(ClientGameTestContext context) {
		String packFile = "tf2mc-expansion-test.zip";
		Path zip = context.computeOnClient(client -> {
			Path path = client.getResourcePackDirectory().resolve(packFile);

			try (InputStream beep = client.getResourceManager().open(Identifier.fromNamespaceAndPath("tf2mc-test", "sounds/beep.wav"));
					ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
				byte[] wav = beep.readAllBytes();
				writeEntry(out, "pack.mcmeta", "{\"pack\": {\"description\": \"test\", \"min_format\": 97, \"max_format\": 97}}");
				writeEntry(out, "assets/tf2mc/sounds.json", "{"
						+ "\"weapon_rpg.single\": {\"replace\": true, \"sounds\": [\"tf2mc:weapons/test_shoot\"]},"
						+ "\"soldier.yes01\": {\"replace\": true, \"sounds\": [\"tf2mc:vo/test_yes\"]}}");
				out.putNextEntry(new ZipEntry("assets/tf2mc/sounds/weapons/test_shoot.wav"));
				out.write(wav);
				out.closeEntry();
				// Содержимое MP3 не важно: мод его пока не читает и должен молча пропустить.
				writeEntry(out, "assets/tf2mc/sounds/vo/test_yes.mp3", "ID3");
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}

			return path;
		});

		reloadWithPack(context, "file/" + packFile, true);

		try {
			String shoot = soundBehind(context, "weapon_rpg.single");
			if (!"tf2mc:weapons/test_shoot".equals(shoot)) {
				throw new AssertionError("звук из архива пака не встал вместо заглушки: " + shoot);
			}

			String loaded = context.computeOnClient(client -> {
				try {
					return WavSounds.load(client.getResourceManager(), Tf2Mc.id("sounds/weapons/test_shoot.wav")).size() + " байт";
				} catch (IOException e) {
					return "ошибка: " + e.getMessage();
				}
			});
			if (!"4410 байт".equals(loaded)) {
				throw new AssertionError("WAV из архива прочитан неверно: " + loaded);
			}

			String voice = soundBehind(context, "soldier.yes01");
			if (voice != null && !voice.equals("minecraft:empty")) {
				throw new AssertionError("звук из MP3 не должен играть, пока мод его не читает: " + voice);
			}

			// Пак ниже мода в списке: заглушки мода читаются после пака и не должны к нему добавиться.
			// Раньше так звучали то звуки TF2, то заглушки.
			String order = movePackBelowMods(context, "file/" + packFile);
			Set<String> sounds = soundsBehind(context, "weapon_rpg.single", 40);
			if (!sounds.equals(Set.of("tf2mc:weapons/test_shoot"))) {
				throw new AssertionError("пак ниже мода: звучат " + sounds + "; порядок паков " + order);
			}
		} finally {
			reloadWithPack(context, "file/" + packFile, false);

			try {
				Files.delete(zip);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		// Без пака снова заглушка.
		assertSoundsHavePlaceholders(context);
	}

	private static void writeEntry(ZipOutputStream out, String name, String text) throws IOException {
		out.putNextEntry(new ZipEntry(name));
		out.write(text.getBytes(StandardCharsets.UTF_8));
		out.closeEntry();
	}

	/**
	 * Ставит пак сразу над ванильными ресурсами, то есть ниже ресурсов модов, и перечитывает
	 * ресурсы.
	 *
	 * @return получившийся порядок паков снизу вверх, для сообщения об ошибке
	 */
	private static String movePackBelowMods(ClientGameTestContext context, String packId) {
		AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();
		AtomicReference<String> order = new AtomicReference<>();

		context.runOnClient(client -> {
			PackRepository packs = client.getResourcePackRepository();
			List<String> selected = new ArrayList<>(packs.getSelectedIds());
			selected.remove(packId);
			// В самый низ, под «Стандартный»: так можно поставить пак в настройках игры, и тогда
			// ресурсы модов, которые идут сразу после ванильных, оказываются над ним.
			selected.add(0, packId);
			packs.setSelected(selected);
			order.set(String.join(", ", packs.getSelectedIds()));

			List<String> actual = new ArrayList<>(packs.getSelectedIds());
			if (actual.indexOf(packId) > actual.indexOf(Tf2Mc.MOD_ID)) {
				throw new AssertionError("не удалось поставить пак ниже мода: " + order.get());
			}

			reload.set(client.reloadResourcePacks());
		});
		context.waitFor(client -> reload.get().isDone());
		reload.get().join();
		return order.get();
	}

	/** Какие файлы клиент выбирает для звука мода за несколько попыток. */
	private static Set<String> soundsBehind(ClientGameTestContext context, String path, int tries) {
		return context.computeOnClient(client -> {
			WeighedSoundEvents events = client.getSoundManager().getSoundEvent(Tf2Mc.id(path));
			Set<String> files = new TreeSet<>();

			for (int i = 0; i < tries && events != null; i++) {
				files.add(events.getSound(RandomSource.create(i)).getLocation().toString());
			}

			return files;
		});
	}

	/** Включает или выключает ресурс-пак и ждёт, пока игра перечитает ресурсы. */
	private static void reloadWithPack(ClientGameTestContext context, String packId, boolean enabled) {
		AtomicReference<CompletableFuture<Void>> reload = new AtomicReference<>();

		context.runOnClient(client -> {
			PackRepository packs = client.getResourcePackRepository();
			packs.reload();

			if (enabled ? !packs.addPack(packId) : !packs.removePack(packId)) {
				throw new AssertionError("не удалось " + (enabled ? "включить" : "выключить") + " пак " + packId + "; есть: " + packs.getAvailableIds());
			}

			reload.set(client.reloadResourcePacks());
		});
		context.waitFor(client -> reload.get().isDone());
		reload.get().join();
		// Пока видна заставка загрузки, игра стоит на паузе и встроенный сервер не обновляется:
		// выданные вещи и события оружия до клиента бы не дошли.
		context.waitFor(client -> client.gui.overlay() == null);
	}

	/**
	 * Модель TF2 из пака: самодельная крошечная модель в форматах Source (заготовки подпроекта
	 * {@code sourcemodel}) кладётся в архив пака под именем модели Soldier. Проверяется весь путь:
	 * чтение файлов, текстура, отрисовщик мода для игрока с классом, отрисовка без ошибок.
	 */
	private static void assertTinySourceModelIsDrawn(ClientGameTestContext context, TestServerContext server) {
		String packFile = "tf2mc-tiny-model.zip";
		Path zip = context.computeOnClient(client -> {
			Path path = client.getResourcePackDirectory().resolve(packFile);
			String tf2 = "assets/tf2mc/tf2/";

			try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(path))) {
				writeEntry(out, "pack.mcmeta", "{\"pack\": {\"description\": \"test\", \"min_format\": 97, \"max_format\": 97}}");
				writeEntry(out, tf2 + "models/player/soldier.mdl", TinyModel.mdl());
				writeEntry(out, tf2 + "models/player/soldier.vvd", TinyModel.vvd(TinyModel.CHECKSUM));
				writeEntry(out, tf2 + "models/player/soldier.dx90.vtx", TinyModel.vtx(TinyModel.CHECKSUM));
				// Та же модель под именем ракеты.
				writeEntry(out, tf2 + "models/weapons/w_models/w_rocket.mdl", TinyModel.mdl());
				writeEntry(out, tf2 + "models/weapons/w_models/w_rocket.vvd", TinyModel.vvd(TinyModel.CHECKSUM));
				writeEntry(out, tf2 + "models/weapons/w_models/w_rocket.dx90.vtx", TinyModel.vtx(TinyModel.CHECKSUM));
				writeEntry(out, tf2 + "materials/models/test/tex_red.vmt", TinyTexture.vmt("models/test/tex_red"));
				writeEntry(out, tf2 + "materials/models/test/tex_red.vtf", TinyTexture.solid(255, 0, 0));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}

			return path;
		});

		reloadWithPack(context, "file/" + packFile, true);
		server.runCommand("tf2 class soldier @p");
		context.waitTicks(10);

		try {
			if (!context.computeOnClient(client -> ClassModels.isSourceModel())) {
				throw new AssertionError("самодельная модель TF2 из пака не подключилась");
			}

			assertDrawnByMod(context, "самодельная модель TF2", true);

			if (context.computeOnClient(client -> ClassModels.rocket().isEmpty())) {
				throw new AssertionError("самодельная модель ракеты из пака не подключилась");
			}

			// Выстрел из ракетницы: сервер сообщает клиенту событие, по которому играется жест.
			// От первого лица свой персонаж не рисуется, и жест остаётся в памяти до проверки.
			selectRocketLauncher(context);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(3);
			WeaponAction gesture = context.computeOnClient(client -> PlayerAnimations.of(client.player).gesture());

			if (gesture != WeaponAction.FIRE) {
				throw new AssertionError("событие выстрела не дошло до клиента: " + gesture);
			}

			// Отрисовка от третьего лица, с ракетой в полёте: ошибка в отрисовщике уронила бы клиент.
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			server.runCommand("execute as @a run tf2 rocket");
			context.waitTicks(10);
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		} finally {
			server.runCommand("tf2 class vanilla @p");
			reloadWithPack(context, "file/" + packFile, false);

			try {
				Files.delete(zip);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		if (context.computeOnClient(client -> ClassModels.isSourceModel() || ClassModels.rocket().isPresent())) {
			throw new AssertionError("модель TF2 осталась после выключения пака");
		}
	}

	/** Берёт в руку ракетницу клавишей её ячейки на панели быстрого доступа. */
	private static void selectRocketLauncher(ClientGameTestContext context) {
		int slot = context.computeOnClient(client -> {
			for (int i = 0; i < 9; i++) {
				if (client.player.getInventory().getItem(i).is(RocketLauncher.ITEM)) {
					return i;
				}
			}

			return -1;
		});

		if (slot < 0) {
			throw new AssertionError("ракетницы нет на панели быстрого доступа");
		}

		context.getInput().pressKey(options -> options.keyHotbarSlots[slot]);
		context.waitTicks(2);
	}

	private static void writeEntry(ZipOutputStream out, String name, byte[] data) throws IOException {
		out.putNextEntry(new ZipEntry(name));
		out.write(data);
		out.closeEntry();
	}

	/**
	 * Модель TF2 из настоящего расширительного пака: только там, где пак собран на этом компьютере
	 * («Документы\tf2mc-expansion-pack\tf2mc-expansion.zip» или файл из {@code TF2MC_PACK_ZIP}).
	 * Файлов Valve в репозитории нет, поэтому в CI шаг пропускается. Снимок экрана спереди
	 * сохраняется в папку снимков тестового клиента для проверки глазами.
	 */
	private static void screenshotRealModel(ClientGameTestContext context, TestServerContext server) {
		String env = System.getenv("TF2MC_PACK_ZIP");
		Path pack = env != null ? Path.of(env) : Path.of(System.getProperty("user.home"), "Documents", "tf2mc-expansion-pack", "tf2mc-expansion.zip");

		if (!Files.isRegularFile(pack)) {
			Tf2Mc.LOGGER.info("[модель] нет расширительного пака {}, проверка настоящей модели пропущена", pack);
			return;
		}

		String packFile = "tf2mc-expansion-real.zip";
		Path copy = context.computeOnClient(client -> client.getResourcePackDirectory().resolve(packFile));

		try {
			Files.copy(pack, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		reloadWithPack(context, "file/" + packFile, true);
		server.runCommand("tf2 class soldier @p");
		context.waitTicks(10);

		try {
			if (!context.computeOnClient(client -> ClassModels.isSourceModel())) {
				throw new AssertionError("модель TF2 из пака не подключилась");
			}

			assertDrawnByMod(context, "модель TF2 из пака", true);
			// Экран загрузки ресурсов гаснет за пару секунд: ждём, пока он уйдёт со снимка.
			context.waitTicks(60);
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(20);
			context.takeScreenshot("tf2mc-real-model-front");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(10);
			context.takeScreenshot("tf2mc-real-model-back");

			// На бегу: анимация бега вместо стойки.
			context.getInput().holdKey(options -> options.keyUp);
			context.waitTicks(8);
			context.takeScreenshot("tf2mc-real-model-run");
			context.getInput().releaseKey(options -> options.keyUp);
			context.waitTicks(10);

			// Взгляд вверх и вниз: корпус и ракетница наклоняются за ним.
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.runOnClient(client -> client.player.setXRot(-60.0F));
			context.waitTicks(5);
			context.takeScreenshot("tf2mc-real-model-look-up");
			context.runOnClient(client -> client.player.setXRot(60.0F));
			context.waitTicks(5);
			context.takeScreenshot("tf2mc-real-model-look-down");
			context.runOnClient(client -> client.player.setXRot(0.0F));

			// Ракета в полёте: камера сзади и сверху, нос ракеты должен смотреть от неё.
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(5);
			server.runCommand("execute as @a run tf2 rocket");
			context.waitTicks(2);
			context.runOnClient(client -> client.player.setXRot(40.0F));
			context.waitTick();
			context.takeScreenshot("tf2mc-real-model-rocket");
			context.runOnClient(client -> client.player.setXRot(0.0F));
			context.waitTicks(40);

			// Жесты оружия: выстрел, затем перезарядка (начинается через 0,8 с после выстрела).
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			selectRocketLauncher(context);
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(4);
			context.takeScreenshot("tf2mc-real-model-fire");
			context.waitTicks(26);
			context.takeScreenshot("tf2mc-real-model-reload");
			context.waitTicks(40);

			// Руки TF2 от первого лица: ожидание, выстрел, перезарядка.
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(30);
			context.takeScreenshot("tf2mc-real-model-first-person");
			context.getInput().pressKey(options -> options.keyUse);
			context.waitTicks(3);
			context.takeScreenshot("tf2mc-real-model-first-person-fire");
			context.waitTicks(26);
			context.takeScreenshot("tf2mc-real-model-first-person-reload");
			context.waitTicks(40);

			// Неподвижная ракета крупно и сбоку: игрок смотрит на юг, ракета повёрнута на восток —
			// нос должен быть слева.
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
			context.runOnClient(client -> client.player.setYRot(0.0F));
			context.waitTicks(5);
			server.runCommand("execute as @p at @s run summon tf2mc:rocket ~ ~1.4 ~2 {Rotation:[-90f,0f]}");
			context.waitTicks(5);
			context.takeScreenshot("tf2mc-real-model-rocket-side");
			server.runCommand("kill @e[type=tf2mc:rocket]");

			// Игрок BLU — синий скин той же модели.
			server.runCommand("tf2 team blu @p");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(10);
			context.takeScreenshot("tf2mc-real-model-blu");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		} finally {
			server.runCommand("tf2 class vanilla @p");
			server.runCommand("team leave @p");
			reloadWithPack(context, "file/" + packFile, false);

			try {
				Files.deleteIfExists(copy);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	/**
	 * Кто рисует игрока: отрисовщик моделей мода или игра. Модель класса мод берёт из ресурсов;
	 * для проверки она лежит в ресурсах тестового мода — простая фигура из кубов.
	 */
	private static void assertDrawnByMod(ClientGameTestContext context, String what, boolean expected) {
		boolean byMod = context.computeOnClient(client -> ClassModels.rendererFor(client.player) != null
				&& client.getEntityRenderDispatcher().getRenderer(client.player) == ClassModels.rendererFor(client.player));

		if (byMod != expected) {
			throw new AssertionError(what + ": рисуется " + (byMod ? "моделью мода" : "игрой") + ", ожидалось наоборот");
		}
	}

	/** Какой файл клиент сыграет для звука мода; {@code null}, если такого звука клиент не знает. */
	private static String soundBehind(ClientGameTestContext context, String path) {
		return soundBehind(context, Tf2Mc.id(path));
	}

	private static String soundBehind(ClientGameTestContext context, Identifier id) {
		return context.computeOnClient(client -> {
			WeighedSoundEvents events = client.getSoundManager().getSoundEvent(id);
			return events == null ? null : events.getSound(RandomSource.create()).getLocation().toString();
		});
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

	/** Проверяет текст здоровья, который клиент нарисовал в последнем кадре; {@code null} — число не рисуется. */
	private static void assertShownHealth(ClientGameTestContext context, String what, String expected) {
		String shown = context.computeOnClient(client -> HealthHud.shownText());

		if (!Objects.equals(expected, shown)) {
			throw new AssertionError(what + ": ожидалось " + expected + ", получено " + shown);
		}
	}

	private static double clientMaxHealth(ClientGameTestContext context) {
		return context.computeOnClient(client -> (double) client.player.getMaxHealth());
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

package io.github.egorka2000.tf2mc.gametest;

import io.github.egorka2000.tf2mc.Tf2Mc;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;

import net.minecraft.world.phys.Vec3;

/**
 * Движение TF2 на неровностях: ступенька, блок, стена, потолок, край, мелководье, лестница.
 * Вызывается из {@link Tf2McClientGameTest} при включённом движении TF2.
 *
 * <p>Сценарии проверяют, что столкновения и ванильные случаи работают, и записывают в лог замеры.
 * Эти замеры — точка отсчёта перед изменением движения на шаге 7 плана.
 */
@SuppressWarnings("UnstableApiUsage") // API клиентских игровых тестов Fabric помечен как экспериментальный.
final class TerrainScenarios {
	private static final int Y = Tf2McClientGameTest.PLATFORM_Y;
	private static final double UNITS_PER_BLOCK = 45.0;
	private static final double TF2_SPEED = 300.0 / UNITS_PER_BLOCK;
	private static final double TF2_JUMP = 289.0 * 289.0 / 1600.0 / UNITS_PER_BLOCK;

	/** Стартовая точка игрока по z и линия препятствий перед ним. */
	private static final double START_Z = -30.5;
	private static final int OBSTACLE_Z = -25;
	/** Половина ширины игрока: ближе к стене он подойти не может. */
	private static final double HALF_WIDTH = 0.3;

	private final ClientGameTestContext context;
	private final TestServerContext server;
	private final TestServerConnection connection;
	private final String where;

	private TerrainScenarios(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, String where) {
		this.context = context;
		this.server = server;
		this.connection = connection;
		this.where = where;
	}

	static void run(ClientGameTestContext context, TestServerContext server, TestServerConnection connection, String where) {
		TerrainScenarios scenarios = new TerrainScenarios(context, server, connection, where);

		scenarios.heldJumpKeyJumpsOnce();
		scenarios.slabIsSteppedOver();
		scenarios.fullBlockStopsWalking();
		scenarios.fullBlockIsJumpedOver();
		scenarios.wallAtAngleIsSlidAlong();
		scenarios.lowCeilingCutsJump();
		scenarios.edgeStartsFallFromZero();
		scenarios.shallowWaterJumpWorks();
		scenarios.ladderIsClimbed();
		scenarios.clear();
	}

	// В TF2 на каждый прыжок нужно новое нажатие: удержание клавиши не даёт прыгать подряд.
	private void heldJumpKeyJumpsOnce() {
		build(null);
		context.getInput().holdKey(options -> options.keyJump);

		int jumps = 0;
		boolean wasOnGround = true;

		// Три секунды: без правила за это время получилось бы четыре прыжка.
		for (int tick = 0; tick < 60; tick++) {
			context.waitTick();
			boolean nowOnGround = onGround();

			if (wasOnGround && !nowOnGround) {
				jumps++;
			}

			wasOnGround = nowOnGround;
		}

		context.getInput().releaseKey(options -> options.keyJump);
		context.waitTicks(5);
		check("удержание прыжка даёт один прыжок", jumps == 1, "прыжков: " + jumps);

		// После отпускания новое нажатие снова прыгает.
		double apex = jump(20);
		near("новое нажатие после удержания", TF2_JUMP, apex, 0.03);
	}

	// Полублок ниже ванильной высоты шага (0,6 блока): игрок заходит на него без прыжка.
	private void slabIsSteppedOver() {
		build("fill -2 " + Y + " " + OBSTACLE_Z + " 2 " + Y + " " + OBSTACLE_Z + " minecraft:stone_slab");
		Vec3 end = walkForward(40);

		check("полублок пройден без прыжка", end.z > OBSTACLE_Z + 2.0, "z = " + end.z);
		agree();
	}

	// Целый блок выше высоты шага: игрок упирается в него и останавливается.
	private void fullBlockStopsWalking() {
		build("fill -2 " + Y + " " + OBSTACLE_Z + " 2 " + Y + " " + OBSTACLE_Z + " minecraft:stone");
		Vec3 end = walkForward(40);

		near("остановка перед целым блоком, z", OBSTACLE_Z - HALF_WIDTH, end.z, 0.02);
		near("высота не изменилась", Y, end.y, 0.001);
		agree();
	}

	// Прыжок TF2 (1,16 блока) выше целого блока: с разбега игрок его перепрыгивает.
	private void fullBlockIsJumpedOver() {
		build("fill -2 " + Y + " " + OBSTACLE_Z + " 2 " + Y + " " + OBSTACLE_Z + " minecraft:stone");
		context.getInput().holdKey(options -> options.keyUp);

		for (int tick = 0; tick < 60 && position().z < OBSTACLE_Z - 2.2; tick++) {
			context.waitTick();
		}

		context.getInput().holdKeyFor(options -> options.keyJump, 2);
		context.waitTicks(25);
		context.getInput().releaseKey(options -> options.keyUp);
		context.waitTicks(15);
		Vec3 end = position();

		check("целый блок перепрыгнут с разбега", end.z > OBSTACLE_Z + 1.5, "z = " + end.z);
		agree();
	}

	// Стена под 45 градусов к движению: игрок не проходит сквозь неё и скользит вдоль.
	private void wallAtAngleIsSlidAlong() {
		build("fill -25 " + Y + " " + OBSTACLE_Z + " 4 " + (Y + 2) + " " + OBSTACLE_Z + " minecraft:stone");
		reset(45.0f);
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(30);

		Vec3 start = position();
		int ticks = 10;
		context.waitTicks(ticks);
		Vec3 end = position();
		context.getInput().releaseKey(options -> options.keyUp);
		context.waitTicks(15);

		double slideSpeed = Math.abs(end.x - start.x) / ticks * 20.0;
		near("стена не пройдена, z", OBSTACLE_Z - HALF_WIDTH, position().z, 0.02);
		check("скольжение вдоль стены", slideSpeed > TF2_SPEED * 0.8, slideSpeed + " блоков/с");
		// На земле скорость не превышает максимум класса, даже если жать в стену под углом (О13).
		check("вдоль стены не быстрее максимума", slideSpeed <= TF2_SPEED + 0.02, slideSpeed + " блоков/с при максимуме " + TF2_SPEED);
		measure("скорость скольжения вдоль стены под 45°", slideSpeed, "блоков/с", slideSpeed * UNITS_PER_BLOCK, "u/s");
		agree();
	}

	// Потолок в двух блоках над полом: прыжок упирается в него и обрывается.
	private void lowCeilingCutsJump() {
		build("fill -2 " + (Y + 2) + " -33 2 " + (Y + 2) + " -28 minecraft:stone");
		double apex = jump(20);

		// Рост игрока 1,8 блока, зазор до потолка 0,2.
		near("прыжок под потолком", 0.2, apex, 0.02);
		near("после удара о потолок игрок вернулся на пол", Y, position().y, 0.001);
		agree();
	}

	// Сход с края: падение начинается с нулевой вертикальной скорости и идёт с гравитацией TF2.
	private void edgeStartsFallFromZero() {
		build(null);
		server.runCommand("tp @a 0.5 " + Y + " 38.5 0 0");
		connection.waitForClientboundPackets();
		context.waitTicks(10);
		context.getInput().holdKey(options -> options.keyUp);

		int guard = 0;

		while (onGround()) {
			context.waitTick();
			check("игрок дошёл до края", ++guard < 60, "не сошёл с площадки за 3 секунды");
		}

		double startY = position().y;
		int ticks = 10;
		context.waitTicks(ticks);
		double drop = startY - position().y;
		context.getInput().releaseKey(options -> options.keyUp);

		// Свободное падение: g * t² / 2 при g = 800 u/s² и t = 0,5 с. Допуск — один тик на момент отрыва.
		double seconds = ticks / 20.0;
		double expected = 800.0 / UNITS_PER_BLOCK * seconds * seconds / 2.0;
		near("падение с края за " + seconds + " с", expected, drop, 0.5);
		reset(0.0f);
	}

	// Мелководье: игра вызывает обычный прыжок, а движение считает как в жидкости.
	// Мод не должен перехватывать такой прыжок, иначе он теряется (ошибка И1 из аудита).
	private void shallowWaterJumpWorks() {
		build("setblock 0 " + Y + " -24 minecraft:water");
		// Вода растекается на блок за пять тиков; в шести блоках от источника её слой тонкий.
		context.waitTicks(60);
		reset(0.0f);

		boolean inWater = context.computeOnClient(client -> client.player.isInWater());
		check("игрок стоит в воде", inWater, "вода не дотекла до игрока");

		double apex = jump(15);
		check("прыжок на мелководье не потерян", apex > 0.3, "подъём " + apex + " блока");
		// Скорость прыжка на мелководье — как у прыжка TF2, а не ванильная (О14).
		check("прыжок на мелководье не выше прыжка TF2", apex <= TF2_JUMP + 0.05, "подъём " + apex + " блока при " + TF2_JUMP);
		measure("высота прыжка на мелководье", apex, "блоков", apex * UNITS_PER_BLOCK, "u");

		server.runCommand("fill -12 " + Y + " -36 12 " + Y + " -12 minecraft:air");
		connection.waitForClientboundPackets();
		context.waitTicks(10);
	}

	// Лестница: остаётся ванильное лазание.
	private void ladderIsClimbed() {
		build("fill 0 " + Y + " " + OBSTACLE_Z + " 0 " + (Y + 5) + " " + OBSTACLE_Z + " minecraft:stone");
		server.runCommand("fill 0 " + Y + " " + (OBSTACLE_Z - 1) + " 0 " + (Y + 5) + " " + (OBSTACLE_Z - 1) + " minecraft:ladder[facing=north]");
		connection.waitForClientboundPackets();
		context.waitTicks(5);

		Vec3 end = walkForward(60);
		check("подъём по лестнице", end.y > Y + 2.0, "высота " + (end.y - Y) + " блока");
		measure("подъём по лестнице за 3 с", end.y - Y, "блоков", (end.y - Y) / 3.0, "блоков/с");
	}

	private void clear() {
		build(null);
	}

	// --- Вспомогательное ---

	/** Убирает прошлое препятствие, ставит новое и возвращает игрока на старт. */
	private void build(String command) {
		context.getInput().releaseKey(options -> options.keyUp);
		server.runCommand("fill -25 " + Y + " -36 12 " + (Y + 6) + " -12 minecraft:air");

		if (command != null) {
			server.runCommand(command);
		}

		reset(0.0f);
	}

	private void reset(float yaw) {
		server.runCommand("tp @a 0.5 " + Y + " " + START_Z + " " + yaw + " 0");
		connection.waitForClientboundPackets();
		context.waitTicks(10);
	}

	private Vec3 walkForward(int ticks) {
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(ticks);
		context.getInput().releaseKey(options -> options.keyUp);
		context.waitTicks(10);
		return position();
	}

	/** Прыжок с места; возвращает наибольший подъём над точкой старта, блоков. */
	private double jump(int ticks) {
		double ground = position().y;
		double apex = ground;
		// Клавиша удерживается два тика, а высота снимается с первого же: под низким потолком
		// вершина прыжка приходится на первый тик.
		context.getInput().holdKey(options -> options.keyJump);

		for (int tick = 0; tick < ticks; tick++) {
			context.waitTick();
			apex = Math.max(apex, position().y);

			if (tick == 1) {
				context.getInput().releaseKey(options -> options.keyJump);
			}
		}

		context.waitTicks(10);
		return apex - ground;
	}

	private Vec3 position() {
		return Tf2McClientGameTest.position(context);
	}

	private boolean onGround() {
		return context.computeOnClient(client -> client.player.onGround());
	}

	private void agree() {
		Tf2McClientGameTest.assertServerAgrees(context, server, connection, where);
	}

	private void near(String what, double expected, double actual, double tolerance) {
		Tf2McClientGameTest.assertNear(where + ": " + what, expected, actual, tolerance);
	}

	private void check(String what, boolean condition, String details) {
		if (!condition) {
			throw new AssertionError(where + ": " + what + ": " + details);
		}
	}

	private void measure(String what, double value, String unit, double secondValue, String secondUnit) {
		Tf2Mc.LOGGER.info("[замер] {}: {}: {} {}, {} {}", where, what,
				String.format("%.3f", value), unit, String.format("%.1f", secondValue), secondUnit);
	}
}

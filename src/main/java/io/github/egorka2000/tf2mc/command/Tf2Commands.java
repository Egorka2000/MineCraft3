package io.github.egorka2000.tf2mc.command;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.config.ConfigManager;
import io.github.egorka2000.tf2mc.config.Tf2Config;
import io.github.egorka2000.tf2mc.clazz.ClassHealth;
import io.github.egorka2000.tf2mc.clazz.PlayerClasses;
import io.github.egorka2000.tf2mc.clazz.TfClass;
import io.github.egorka2000.tf2mc.movement.SpeedometerToggle;
import io.github.egorka2000.tf2mc.net.Tf2Networking;
import io.github.egorka2000.tf2mc.team.Teams;
import io.github.egorka2000.tf2mc.team.TfTeam;
import io.github.egorka2000.tf2mc.weapon.Tf2Weapons;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Команды {@code /tf2 ...}. Всё, что меняет состояние сервера, доступно только операторам.
 *
 * <p>У команд, действующих на игрока, в конце необязательный ник. Без ника команда применяется
 * к тому, кто её ввёл, и доступна всем: она меняет только его собственное состояние.
 * С ником — к названному игроку, и это может только оператор.
 */
public final class Tf2Commands {
	public static final String ROOT = "tf2";

	/** Имя необязательного аргумента с ником игрока. */
	public static final String PLAYER = "player";

	/** Имя аргумента с количеством здоровья. */
	public static final String AMOUNT = "amount";

	private Tf2Commands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal(ROOT)
				.then(Commands.literal("config")
						.executes(context -> showConfig(context.getSource())))
				.then(Commands.literal("reload")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(context -> reloadConfig(context.getSource())))
				.then(classCommand())
				.then(healthCommand())
				.then(teamCommand())
				.then(withOptionalPlayer(
						Commands.literal("rocket").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)),
						(context, target) -> fireRocket(context.getSource(), target)))
				.then(onOff("speed", Tf2Commands::setSpeedometer, target -> SpeedometerToggle.isEnabled(target.getUUID()))));
	}

	@FunctionalInterface
	private interface Switch {
		int apply(CommandSourceStack source, ServerPlayer target, boolean enabled);
	}

	@FunctionalInterface
	private interface PlayerAction {
		int apply(CommandContext<CommandSourceStack> context, ServerPlayer target) throws CommandSyntaxException;
	}

	/**
	 * Переключатель: {@code on} и {@code off} с необязательным ником, а без слова — смена состояния
	 * на противоположное для того, кто ввёл команду.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> onOff(String name, Switch action, Predicate<ServerPlayer> isEnabled) {
		return Commands.literal(name)
				.executes(context -> {
					ServerPlayer self = context.getSource().getPlayerOrException();
					return action.apply(context.getSource(), self, !isEnabled.test(self));
				})
				.then(withOptionalPlayer(Commands.literal("on"), (context, target) -> action.apply(context.getSource(), target, true)))
				.then(withOptionalPlayer(Commands.literal("off"), (context, target) -> action.apply(context.getSource(), target, false)));
	}

	/**
	 * Добавляет в конец команды необязательный ник. Без ника действие применяется к тому, кто ввёл
	 * команду; с ником — к названному игроку, и для этого нужны права оператора.
	 */
	static <T extends ArgumentBuilder<CommandSourceStack, T>> T withOptionalPlayer(T command, PlayerAction action) {
		return command
				.executes(context -> action.apply(context, context.getSource().getPlayerOrException()))
				.then(Commands.argument(PLAYER, EntityArgument.player())
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(context -> action.apply(context, EntityArgument.getPlayer(context, PLAYER))));
	}

	/** Для сообщения об успехе: «себе» или имя игрока, к которому применена команда. */
	private static String describeTarget(CommandSourceStack source, ServerPlayer target) {
		return source.getEntity() == target ? "" : " игроку " + target.getGameProfile().name();
	}

	/**
	 * {@code /tf2 class} показывает свой класс; {@code /tf2 class <имя> [ник]} выдаёт класс,
	 * {@code /tf2 class vanilla [ник]} снимает его. Выдавать и снимать класс может только оператор.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> classCommand() {
		LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("class")
				.executes(context -> showClass(context.getSource()));

		for (TfClass tfClass : TfClass.values()) {
			command.then(withOptionalPlayer(
					Commands.literal(tfClass.id()).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)),
					(context, target) -> setClass(context.getSource(), target, tfClass)));
		}

		return command.then(withOptionalPlayer(
				Commands.literal(TfClass.VANILLA_ID).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)),
				(context, target) -> setClass(context.getSource(), target, null)));
	}

	private static int showClass(CommandSourceStack source) throws CommandSyntaxException {
		String name = PlayerClasses.get(source.getPlayerOrException()).map(TfClass::id).orElse(TfClass.VANILLA_ID);
		source.sendSuccess(() -> Component.literal("tf2-mc: ваш класс — " + name), false);
		return 1;
	}

	/**
	 * @param tfClass новый класс; {@code null} снимает класс
	 */
	private static int setClass(CommandSourceStack source, ServerPlayer target, TfClass tfClass) {
		PlayerClasses.set(target, tfClass);

		String whom = describeTarget(source, target);
		String what = tfClass == null ? "tf2-mc: класс снят" : "tf2-mc: выдан класс " + tfClass.id();
		source.sendSuccess(() -> Component.literal(what + whom), true);
		return 1;
	}

	/**
	 * {@code /tf2 health set <число> [ник]} выставляет здоровье, {@code /tf2 health add <число> [ник]}
	 * прибавляет его; отрицательное число отнимает. Всё, что выше максимума класса, становится
	 * оверхилом. Отладочная команда, только для операторов.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> healthCommand() {
		return Commands.literal("health")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("set").then(withOptionalPlayer(
						Commands.argument(AMOUNT, IntegerArgumentType.integer(1)),
						(context, target) -> setHealth(context.getSource(), target, IntegerArgumentType.getInteger(context, AMOUNT)))))
				.then(Commands.literal("add").then(withOptionalPlayer(
						Commands.argument(AMOUNT, IntegerArgumentType.integer()),
						(context, target) -> setHealth(
								context.getSource(), target, ClassHealth.total(target) + IntegerArgumentType.getInteger(context, AMOUNT)))));
	}

	private static int setHealth(CommandSourceStack source, ServerPlayer target, float total) {
		int result = Math.round(ClassHealth.setTotal(target, total));

		String whom = describeTarget(source, target);
		source.sendSuccess(() -> Component.literal("tf2-mc: здоровье " + result + whom), true);
		return result;
	}

	/**
	 * {@code /tf2 team} показывает свою команду и составы команд;
	 * {@code /tf2 team red|blu|spectator|random [ник]} меняет команду. Свою команду выбирает любой
	 * игрок, чужую назначает оператор. Нейтральную команду выбрать нельзя: в неё попадают при входе.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> teamCommand() {
		LiteralArgumentBuilder<CommandSourceStack> command = Commands.literal("team")
				.executes(context -> showTeam(context.getSource()));

		for (TfTeam team : TfTeam.values()) {
			command.then(withOptionalPlayer(Commands.literal(team.id()), (context, target) -> joinTeam(context.getSource(), target, team)));
		}

		return command
				.then(withOptionalPlayer(Commands.literal(TfTeam.RANDOM_ID), (context, target) -> joinTeam(context.getSource(), target, null)))
				.then(withOptionalPlayer(Commands.literal(TfTeam.SPECTATOR_ID), (context, target) -> spectate(context.getSource(), target)));
	}

	/** Своя команда и составы команд. С консоли сервера — только составы: своей команды у неё нет. */
	private static int showTeam(CommandSourceStack source) {
		List<String> lines = new ArrayList<>();
		ServerPlayer self = source.getPlayer();

		if (self != null) {
			lines.add("tf2-mc: ваша команда — " + describeTeam(self));
		}

		for (TfTeam team : TfTeam.values()) {
			lines.add(roster(team.id(), Teams.members(source.getServer(), team)));
		}

		lines.add(roster("нейтральные", Teams.neutralMembers(source.getServer())));
		source.sendSuccess(() -> Component.literal(String.join("\n", lines)), false);
		return 1;
	}

	private static String describeTeam(ServerPlayer player) {
		if (Teams.isNeutral(player)) {
			return "нейтральная";
		}

		return Teams.get(player).map(TfTeam::id).orElse("наблюдатель");
	}

	private static String roster(String name, List<String> players) {
		return name + " (" + players.size() + ")" + (players.isEmpty() ? "" : ": " + String.join(", ", players));
	}

	/**
	 * @param team команда; {@code null} — та, где меньше игроков
	 */
	private static int joinTeam(CommandSourceStack source, ServerPlayer target, TfTeam team) {
		TfTeam joined = team;

		if (joined == null) {
			joined = Teams.joinRandom(target);
		} else {
			Teams.join(target, joined);
		}

		String what = "tf2-mc: команда " + joined.id();
		String whom = describeTarget(source, target);
		source.sendSuccess(() -> Component.literal(what + whom), true);
		return 1;
	}

	private static int spectate(CommandSourceStack source, ServerPlayer target) {
		Teams.spectate(target);

		String whom = describeTarget(source, target);
		source.sendSuccess(() -> Component.literal("tf2-mc: наблюдатель" + whom), true);
		return 1;
	}

	/**
	 * {@code /tf2 rocket [ник]} выпускает ракету из глаз игрока по направлению взгляда.
	 * Отладочная команда, только для операторов; у игрока должен быть выбран класс.
	 */
	private static int fireRocket(CommandSourceStack source, ServerPlayer target) {
		if (!Tf2Weapons.fireRocket(target)) {
			source.sendFailure(Component.literal("tf2-mc: ракету выпускает только игрок с классом"));
			return 0;
		}

		return 1;
	}

	private static int setSpeedometer(CommandSourceStack source, ServerPlayer target, boolean enabled) {
		SpeedometerToggle.setEnabled(target.getUUID(), enabled);
		Tf2Networking.sync(target);

		String whom = describeTarget(source, target);
		source.sendSuccess(() -> Component.literal((enabled ? "tf2-mc: спидометр включён" : "tf2-mc: спидометр выключен") + whom), false);
		return 1;
	}

	private static int showConfig(CommandSourceStack source) {
		Tf2Config config = Tf2Mc.config().current();

		source.sendSuccess(() -> Component.literal(String.join("\n",
				"Настройки tf2-mc:",
				"  accelerate = " + config.accelerate(),
				"  airAccelerate = " + config.airAccelerate(),
				"  friction = " + config.friction(),
				"  stopSpeed = " + config.stopSpeed(),
				"  gravity = " + config.gravity(),
				"  airSpeedCap = " + config.airSpeedCap(),
				"  jumpSpeed = " + config.jumpSpeed(),
				"  maxVelocity = " + config.maxVelocity(),
				"  unitsPerBlock = " + config.unitsPerBlock(),
				"  substeps = " + config.substeps())), false);
		return 1;
	}

	private static int reloadConfig(CommandSourceStack source) {
		ConfigManager.LoadResult result = Tf2Mc.config().load();
		Tf2Mc.logLoadResult(result);

		for (String warning : result.warnings()) {
			source.sendSystemMessage(Component.literal("tf2-mc: " + warning));
		}

		if (!result.success()) {
			source.sendFailure(Component.literal("tf2-mc: " + result.message()));
			return 0;
		}

		// Клиенты считают движение по настройкам сервера, поэтому им нужны новые значения.
		Tf2Networking.syncAll(source.getServer());
		source.sendSuccess(() -> Component.literal("tf2-mc: " + result.message()), true);
		return 1;
	}
}

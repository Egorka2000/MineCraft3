package io.github.egorka2000.tf2mc.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.config.ConfigManager;
import io.github.egorka2000.tf2mc.config.Tf2Config;
import io.github.egorka2000.tf2mc.clazz.PlayerClasses;
import io.github.egorka2000.tf2mc.clazz.TfClass;
import io.github.egorka2000.tf2mc.movement.SpeedometerToggle;
import io.github.egorka2000.tf2mc.net.Tf2Networking;

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
				.then(onOff("speed", Tf2Commands::setSpeedometer)));
	}

	@FunctionalInterface
	private interface Switch {
		int apply(CommandSourceStack source, ServerPlayer target, boolean enabled);
	}

	@FunctionalInterface
	private interface PlayerAction {
		int apply(CommandSourceStack source, ServerPlayer target) throws CommandSyntaxException;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> onOff(String name, Switch action) {
		return Commands.literal(name)
				.then(withOptionalPlayer(Commands.literal("on"), (source, target) -> action.apply(source, target, true)))
				.then(withOptionalPlayer(Commands.literal("off"), (source, target) -> action.apply(source, target, false)));
	}

	/**
	 * Добавляет в конец команды необязательный ник. Без ника действие применяется к тому, кто ввёл
	 * команду; с ником — к названному игроку, и для этого нужны права оператора.
	 */
	static <T extends ArgumentBuilder<CommandSourceStack, T>> T withOptionalPlayer(T command, PlayerAction action) {
		return command
				.executes(context -> action.apply(context.getSource(), context.getSource().getPlayerOrException()))
				.then(Commands.argument(PLAYER, EntityArgument.player())
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(context -> action.apply(context.getSource(), EntityArgument.getPlayer(context, PLAYER))));
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
					(source, target) -> setClass(source, target, tfClass)));
		}

		return command.then(withOptionalPlayer(
				Commands.literal(TfClass.VANILLA_ID).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)),
				(source, target) -> setClass(source, target, null)));
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

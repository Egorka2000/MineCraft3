package io.github.egorka2000.tf2mc.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.config.ConfigManager;
import io.github.egorka2000.tf2mc.config.Tf2Config;
import io.github.egorka2000.tf2mc.movement.MovementToggle;
import io.github.egorka2000.tf2mc.net.Tf2Networking;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Команды {@code /tf2 ...}. Всё, что меняет состояние сервера, доступно только операторам.
 * Переключатели движения и спидометра меняют только состояние самого игрока, поэтому доступны всем.
 */
public final class Tf2Commands {
	public static final String ROOT = "tf2";

	private Tf2Commands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal(ROOT)
				.then(Commands.literal("config")
						.executes(context -> showConfig(context.getSource())))
				.then(Commands.literal("reload")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(context -> reloadConfig(context.getSource())))
				// Временная команда до появления классов (шаг 8 плана).
				.then(onOff("movement", Tf2Commands::setMovement))
				.then(onOff("speed", Tf2Commands::setSpeedometer)));
	}

	@FunctionalInterface
	private interface Switch {
		int apply(CommandSourceStack source, boolean enabled) throws CommandSyntaxException;
	}

	private static LiteralArgumentBuilder<CommandSourceStack> onOff(String name, Switch action) {
		return Commands.literal(name)
				.then(Commands.literal("on").executes(context -> action.apply(context.getSource(), true)))
				.then(Commands.literal("off").executes(context -> action.apply(context.getSource(), false)));
	}

	private static int setMovement(CommandSourceStack source, boolean enabled) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MovementToggle.setMovementEnabled(player.getUUID(), enabled);
		Tf2Networking.sync(player);

		source.sendSuccess(() -> Component.literal(enabled ? "tf2-mc: движение TF2 включено" : "tf2-mc: движение TF2 выключено"), false);
		return 1;
	}

	private static int setSpeedometer(CommandSourceStack source, boolean enabled) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		MovementToggle.setSpeedometerEnabled(player.getUUID(), enabled);
		Tf2Networking.sync(player);

		source.sendSuccess(() -> Component.literal(enabled ? "tf2-mc: спидометр включён" : "tf2-mc: спидометр выключен"), false);
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

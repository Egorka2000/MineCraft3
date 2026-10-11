package io.github.egorka2000.tf2mc.command;

import com.mojang.brigadier.CommandDispatcher;
import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.config.ConfigManager;
import io.github.egorka2000.tf2mc.config.Tf2Config;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Команды {@code /tf2 ...}. Всё, что меняет состояние, доступно только операторам. */
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
						.executes(context -> reloadConfig(context.getSource()))));
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

		source.sendSuccess(() -> Component.literal("tf2-mc: " + result.message()), true);
		return 1;
	}
}

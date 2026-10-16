package io.github.egorka2000.tf2mc;

import io.github.egorka2000.tf2mc.block.Tf2Blocks;
import io.github.egorka2000.tf2mc.clazz.PlayerClasses;
import io.github.egorka2000.tf2mc.command.Tf2Commands;
import io.github.egorka2000.tf2mc.config.ConfigManager;
import io.github.egorka2000.tf2mc.net.Tf2Networking;
import io.github.egorka2000.tf2mc.team.Teams;
import io.github.egorka2000.tf2mc.weapon.Tf2Weapons;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tf2Mc implements ModInitializer {
	public static final String MOD_ID = "tf2mc";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Создаётся при загрузке мода, а не при загрузке класса: иначе класс нельзя было бы
	// использовать в юнит-тестах, где загрузчика Fabric нет.
	private static volatile ConfigManager config;

	@Override
	public void onInitialize() {
		config = new ConfigManager(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"));
		logLoadResult(config.load());
		Tf2Networking.register();
		PlayerClasses.register();
		Teams.register();
		Tf2Weapons.register();
		Tf2Blocks.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> Tf2Commands.register(dispatcher));

		LOGGER.info("tf2-mc загружен");
	}

	/** Настройки мода; файл лежит в папке конфигов игры. */
	public static ConfigManager config() {
		ConfigManager manager = config;

		if (manager == null) {
			throw new IllegalStateException("мод ещё не загружен: настройки недоступны");
		}

		return manager;
	}

	public static void logLoadResult(ConfigManager.LoadResult result) {
		for (String warning : result.warnings()) {
			LOGGER.warn("{}", warning);
		}

		if (result.success()) {
			LOGGER.info("{}", result.message());
		} else {
			LOGGER.error("{}", result.message());
		}
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}

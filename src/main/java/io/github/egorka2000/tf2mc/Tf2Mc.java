package io.github.egorka2000.tf2mc;

import io.github.egorka2000.tf2mc.command.Tf2Commands;
import io.github.egorka2000.tf2mc.config.ConfigManager;
import io.github.egorka2000.tf2mc.net.Tf2Networking;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Tf2Mc implements ModInitializer {
	public static final String MOD_ID = "tf2mc";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final ConfigManager CONFIG = new ConfigManager(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + ".json"));

	@Override
	public void onInitialize() {
		logLoadResult(CONFIG.load());
		Tf2Networking.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> Tf2Commands.register(dispatcher));

		LOGGER.info("tf2-mc загружен");
	}

	/** Настройки мода; файл лежит в папке конфигов игры. */
	public static ConfigManager config() {
		return CONFIG;
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

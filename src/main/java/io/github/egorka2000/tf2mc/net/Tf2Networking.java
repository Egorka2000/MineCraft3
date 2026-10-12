package io.github.egorka2000.tf2mc.net;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.movement.SpeedometerToggle;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Регистрация пакетов и отправка состояния движения игрокам. */
public final class Tf2Networking {
	private Tf2Networking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(MovementSyncPayload.TYPE, MovementSyncPayload.CODEC);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SpeedometerToggle.forget(handler.getPlayer().getUUID()));
	}

	/** Отправляет игроку действующие настройки сервера и состояние спидометра. */
	public static void sync(ServerPlayer player) {
		// У клиента без мода такого пакета нет: ему отправлять нечего.
		if (!ServerPlayNetworking.canSend(player, MovementSyncPayload.TYPE)) {
			return;
		}

		ServerPlayNetworking.send(player, new MovementSyncPayload(
				SpeedometerToggle.isEnabled(player.getUUID()),
				Tf2Mc.config().current()));
	}

	/** Рассылает настройки всем игрокам, например после {@code /tf2 reload}. */
	public static void syncAll(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sync(player);
		}
	}
}

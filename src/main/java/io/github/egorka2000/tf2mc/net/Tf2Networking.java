package io.github.egorka2000.tf2mc.net;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.movement.SpeedometerToggle;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.server.MinecraftServer;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Регистрация пакетов и отправка состояния движения игрокам. */
public final class Tf2Networking {
	private Tf2Networking() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(MovementSyncPayload.TYPE, MovementSyncPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(VelocityImpulsePayload.TYPE, VelocityImpulsePayload.CODEC);

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> SpeedometerToggle.forget(handler.getPlayer().getUUID()));
	}

	/**
	 * Толкает игрока: прибавляет к его скорости заданную добавку, блоков за тик.
	 *
	 * <p>Скорость игрока считает его клиент, поэтому клиенту с модом уходит добавка, а не новая
	 * скорость. Клиенту без мода остаётся штатный пакет: он заменяет скорость целиком.
	 */
	public static void push(ServerPlayer player, Vec3 impulse) {
		if (ServerPlayNetworking.canSend(player, VelocityImpulsePayload.TYPE)) {
			ServerPlayNetworking.send(player, new VelocityImpulsePayload(impulse.x, impulse.y, impulse.z));
			return;
		}

		player.setDeltaMovement(player.getDeltaMovement().add(impulse));
		player.connection.send(new ClientboundSetEntityMotionPacket(player));
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

package io.github.egorka2000.tf2mc.client;

import io.github.egorka2000.tf2mc.movement.LocalMovementState;
import io.github.egorka2000.tf2mc.net.MovementSyncPayload;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class Tf2McClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(MovementSyncPayload.TYPE, (payload, context) ->
				LocalMovementState.set(new LocalMovementState.Snapshot(payload.showSpeed(), payload.config())));

		// Следующий сервер может быть без мода: без сброса движение TF2 осталось бы включённым.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LocalMovementState.reset());

		ClientTickEvents.END_CLIENT_TICK.register(Speedometer::tick);
		HealthHud.register();
	}
}

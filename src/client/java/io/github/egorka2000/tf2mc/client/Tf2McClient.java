package io.github.egorka2000.tf2mc.client;

import io.github.egorka2000.tf2mc.client.model.RocketRenderer;
import io.github.egorka2000.tf2mc.movement.LocalMovementState;
import io.github.egorka2000.tf2mc.net.MovementSyncPayload;
import io.github.egorka2000.tf2mc.net.VelocityImpulsePayload;
import io.github.egorka2000.tf2mc.weapon.Tf2Weapons;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.renderer.entity.EntityRenderers;

public class Tf2McClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(MovementSyncPayload.TYPE, (payload, context) ->
				LocalMovementState.set(new LocalMovementState.Snapshot(payload.showSpeed(), payload.config())));

		// Толчок от взрыва: прибавляется к скорости, которую клиент считает сам.
		ClientPlayNetworking.registerGlobalReceiver(VelocityImpulsePayload.TYPE, (payload, context) -> {
			if (context.player() != null) {
				context.player().setDeltaMovement(context.player().getDeltaMovement().add(payload.x(), payload.y(), payload.z()));
			}
		});

		// Следующий сервер может быть без мода: без сброса движение TF2 осталось бы включённым.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LocalMovementState.reset());

		ClientTickEvents.END_CLIENT_TICK.register(Speedometer::tick);
		HealthHud.register();
		MemoryCheck.register();
		// Ракета рисуется моделью TF2 из расширительного пака; без пака она невидима.
		EntityRenderers.register(Tf2Weapons.ROCKET, RocketRenderer::new);
	}
}

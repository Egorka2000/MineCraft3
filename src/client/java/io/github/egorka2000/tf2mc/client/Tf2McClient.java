package io.github.egorka2000.tf2mc.client;

import io.github.egorka2000.tf2mc.block.Tf2Blocks;
import io.github.egorka2000.tf2mc.client.model.PlayerAnimations;
import io.github.egorka2000.tf2mc.client.model.PropModels;
import io.github.egorka2000.tf2mc.client.model.PropRenderer;
import io.github.egorka2000.tf2mc.client.model.RocketRenderer;
import io.github.egorka2000.tf2mc.client.model.ViewModels;
import io.github.egorka2000.tf2mc.movement.LocalMovementState;
import io.github.egorka2000.tf2mc.net.MovementSyncPayload;
import io.github.egorka2000.tf2mc.net.VelocityImpulsePayload;
import io.github.egorka2000.tf2mc.net.WeaponActionPayload;
import io.github.egorka2000.tf2mc.weapon.Tf2Weapons;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
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

		// Выстрел и перезарядка игрока: его персонаж проигрывает жест.
		ClientPlayNetworking.registerGlobalReceiver(WeaponActionPayload.TYPE, (payload, context) -> {
			if (context.client().level != null && context.client().level.getEntity(payload.entityId()) instanceof AbstractClientPlayer player) {
				PlayerAnimations.onWeaponAction(player, payload.action());

				if (player == context.client().player) {
					ViewModels.onWeaponAction(payload.action(), player.tickCount / 20.0F);
				}
			}
		});

		// Следующий сервер может быть без мода: без сброса движение TF2 осталось бы включённым.
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> LocalMovementState.reset());

		ClientTickEvents.END_CLIENT_TICK.register(Speedometer::tick);
		HealthHud.register();
		MemoryCheck.register();
		// Ракета рисуется моделью TF2 из расширительного пака; без пака она невидима.
		EntityRenderers.register(Tf2Weapons.ROCKET, RocketRenderer::new);
		// Аптечки, патроны и шкафы пополнения — моделями TF2 из пака, если они там есть.
		BlockEntityRenderers.register(Tf2Blocks.PROP, PropRenderer::new);
		PropModels.register();
	}
}

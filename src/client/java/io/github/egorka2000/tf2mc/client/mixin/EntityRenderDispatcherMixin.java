package io.github.egorka2000.tf2mc.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.egorka2000.tf2mc.client.model.ClassModels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;

/**
 * Игрока с классом рисует отрисовщик мода, а не ванильный. Игра спрашивает отрисовщик дважды:
 * сначала по сущности, потом по её состоянию отрисовки, — подменяются оба ответа.
 */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityRenderDispatcherMixin {
	@Inject(
			method = "getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
			at = @At("HEAD"),
			cancellable = true)
	private void tf2mc$rendererForEntity(Entity entity, CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
		EntityRenderer<?, ?> renderer = ClassModels.rendererFor(entity);

		if (renderer != null) {
			cir.setReturnValue(renderer);
		}
	}

	@Inject(
			method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
			at = @At("HEAD"),
			cancellable = true)
	private void tf2mc$rendererForState(EntityRenderState state, CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
		EntityRenderer<?, ?> renderer = ClassModels.rendererFor(state);

		if (renderer != null) {
			cir.setReturnValue(renderer);
		}
	}

	// Отрисовщик моделей классов создаётся из тех же исходных данных, что и ванильные.
	@Inject(method = "onResourceManagerReload", at = @At("TAIL"))
	private void tf2mc$reload(ResourceManager resourceManager, CallbackInfo ci, @Local EntityRendererProvider.Context context) {
		ClassModels.reload(context, resourceManager);
	}
}

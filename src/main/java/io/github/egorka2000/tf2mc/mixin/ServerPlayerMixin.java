package io.github.egorka2000.tf2mc.mixin;

import io.github.egorka2000.tf2mc.clazz.PlayerClasses;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;

/**
 * У игрока с классом TF2 здоровье само не восстанавливается.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	// Восстановление здоровья на мирной сложности; обычное идёт через сытость (FoodDataMixin).
	@Inject(method = "tickRegeneration", at = @At("HEAD"), cancellable = true)
	private void tf2mc$tickRegeneration(CallbackInfo ci) {
		if (PlayerClasses.get((ServerPlayer) (Object) this).isPresent()) {
			ci.cancel();
		}
	}
}

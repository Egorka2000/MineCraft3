package io.github.egorka2000.tf2mc.mixin;

import io.github.egorka2000.tf2mc.clazz.PlayerClasses;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;

/**
 * У игрока с классом TF2 сытости нет: голод не расходуется, не лечит и не вредит.
 */
@Mixin(FoodData.class)
abstract class FoodDataMixin {
	// В этом методе игра тратит сытость, лечит за её счёт и наносит урон от голода.
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void tf2mc$tick(ServerPlayer player, CallbackInfo ci) {
		if (PlayerClasses.get(player).isPresent()) {
			ci.cancel();
		}
	}
}

package io.github.egorka2000.tf2mc.mixin;

import io.github.egorka2000.tf2mc.clazz.PlayerClasses;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * К имени игрока с классом дописывается класс в скобках: «Ник (soldier)». Так класс виден над
 * головой и в чате, пока у классов нет своих моделей. Цвет ника задаёт команда игрока.
 */
@Mixin(Player.class)
abstract class PlayerMixin {
	@Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
	private void tf2mc$appendClass(CallbackInfoReturnable<Component> cir) {
		PlayerClasses.get((Player) (Object) this).ifPresent(
				tfClass -> cir.setReturnValue(cir.getReturnValue().copy().append(" (" + tfClass.id() + ")")));
	}
}

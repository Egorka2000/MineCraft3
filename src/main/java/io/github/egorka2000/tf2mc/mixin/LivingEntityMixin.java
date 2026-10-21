package io.github.egorka2000.tf2mc.mixin;

import io.github.egorka2000.tf2mc.clazz.ClassHealth;
import io.github.egorka2000.tf2mc.clazz.PlayerClasses;
import io.github.egorka2000.tf2mc.movement.Tf2Travel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Подменяет ванильную ходьбу и полёт в прыжке движением TF2, а у игрока с классом — ещё урон
 * от падения и бег. Вся логика — в {@link Tf2Travel} и {@link ClassHealth}, здесь только точки
 * встраивания (docs/08-architecture.md, раздел 5).
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@Shadow
	protected boolean jumping;

	// В начале тика поле jumping ещё хранит состояние клавиши прошлого тика:
	// ввод текущего тика игра читает позже, внутри aiStep.
	@Inject(method = "aiStep", at = @At("HEAD"))
	private void tf2mc$aiStep(CallbackInfo ci) {
		Tf2Travel.observeJumpKey((LivingEntity) (Object) this, jumping);
	}

	// travelInAir — это и ходьба по земле, и полёт; в воде, лаве и на элитрах игра сюда не заходит.
	@Inject(method = "travelInAir", at = @At("HEAD"), cancellable = true)
	private void tf2mc$travelInAir(Vec3 input, CallbackInfo ci) {
		if (Tf2Travel.travel((LivingEntity) (Object) this, input, jumping)) {
			ci.cancel();
		}
	}

	@Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
	private void tf2mc$jumpFromGround(CallbackInfo ci) {
		LivingEntity entity = (LivingEntity) (Object) this;

		if (Tf2Travel.requestJump(entity)) {
			ci.cancel();
		} else {
			Tf2Travel.beforeVanillaJump(entity);
		}
	}

	@Inject(method = "jumpFromGround", at = @At("TAIL"))
	private void tf2mc$afterJumpFromGround(CallbackInfo ci) {
		Tf2Travel.afterVanillaJump((LivingEntity) (Object) this);
	}

	@Inject(method = "calculateFallDamage", at = @At("HEAD"), cancellable = true)
	private void tf2mc$calculateFallDamage(double fallDistance, float damageModifier, CallbackInfoReturnable<Integer> cir) {
		if ((Object) this instanceof Player player) {
			ClassHealth.fallDamage(player, fallDistance, damageModifier).ifPresent(cir::setReturnValue);
		}
	}

	// В TF2 бега нет: скорость задаёт класс. Без этого бег менял бы поле зрения и тратил бы сытость.
	@ModifyVariable(method = "setSprinting", at = @At("HEAD"), argsOnly = true)
	private boolean tf2mc$setSprinting(boolean sprinting) {
		return sprinting && !((Object) this instanceof Player player && PlayerClasses.get(player).isPresent());
	}
}

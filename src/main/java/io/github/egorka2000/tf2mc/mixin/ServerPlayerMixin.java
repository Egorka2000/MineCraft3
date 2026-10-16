package io.github.egorka2000.tf2mc.mixin;

import io.github.egorka2000.tf2mc.clazz.PlayerClasses;
import io.github.egorka2000.tf2mc.net.Tf2Networking;
import io.github.egorka2000.tf2mc.team.TeamSpawns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * У игрока с классом TF2 здоровье само не восстанавливается, а урон не сбрасывает его скорость. Игрок
 * команды возрождается на её точке.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
	@Unique
	private Vec3 tf2mc$velocityBeforeHurt;

	// Восстановление здоровья на мирной сложности; обычное идёт через сытость (FoodDataMixin).
	@Inject(method = "tickRegeneration", at = @At("HEAD"), cancellable = true)
	private void tf2mc$tickRegeneration(CallbackInfo ci) {
		if (PlayerClasses.get((ServerPlayer) (Object) this).isPresent()) {
			ci.cancel();
		}
	}

	// Игрок команды возрождается на её точке, а не у кровати или в точке мира. Место выбирается
	// здесь, до создания нового игрока: если перенести его уже после возрождения, игра поставит
	// его обратно.
	@Inject(method = "findRespawnPositionAndUseSpawnBlock", at = @At("HEAD"), cancellable = true)
	private void tf2mc$teamSpawn(boolean consumeSpawnBlock, TeleportTransition.PostTeleportTransition after, CallbackInfoReturnable<TeleportTransition> cir) {
		TeamSpawns.respawnTransition((ServerPlayer) (Object) this, after).ifPresent(cir::setReturnValue);
	}

	@Inject(method = "hurtServer", at = @At("HEAD"))
	private void tf2mc$beforeHurt(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		tf2mc$velocityBeforeHurt = PlayerClasses.get(player).isPresent() ? player.getDeltaMovement() : null;
	}

	// После урона игра рассылает скорость игрока, как её видит сервер, — так до клиента доходит
	// отбрасывание. Но скорость игрока с классом считает его клиент, сервер её не знает,
	// и такой пакет стёр бы весь разгон. Поэтому клиенту уходит только само отбрасывание,
	// как добавка к скорости.
	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void tf2mc$afterHurt(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		Vec3 before = tf2mc$velocityBeforeHurt;

		if (before == null) {
			return;
		}

		ServerPlayer player = (ServerPlayer) (Object) this;
		Vec3 knockback = player.getDeltaMovement().subtract(before);

		tf2mc$velocityBeforeHurt = null;
		player.setDeltaMovement(before);
		player.syncVelocity = false;

		if (knockback.lengthSqr() > 0.0) {
			Tf2Networking.push(player, knockback);
		}
	}
}

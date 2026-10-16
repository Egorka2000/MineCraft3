package io.github.egorka2000.tf2mc.mixin;

import io.github.egorka2000.tf2mc.clazz.PendingChoice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;

/**
 * Перед возрождением после смерти применяются запомненные класс и команда: как в TF2, класс,
 * выбранный вне зоны возрождения, достаётся игроку в следующей жизни. Применяются к погибшему
 * игроку до того, как игра выберет место возрождения и создаст нового игрока.
 */
@Mixin(PlayerList.class)
abstract class PlayerListMixin {
	@Inject(method = "respawn", at = @At("HEAD"))
	private void tf2mc$applyPendingChoice(ServerPlayer player, boolean keepAllPlayerData, Entity.RemovalReason reason, CallbackInfoReturnable<ServerPlayer> cir) {
		// keepAllPlayerData — это не смерть, а возвращение из Энда.
		if (!keepAllPlayerData) {
			PendingChoice.applyBeforeRespawn(player);
		}
	}
}

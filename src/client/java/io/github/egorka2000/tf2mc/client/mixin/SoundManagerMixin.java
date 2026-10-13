package io.github.egorka2000.tf2mc.client.mixin;

import io.github.egorka2000.tf2mc.client.WavSounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;

/**
 * При загрузке звуков игра выбрасывает те, для которых нет файла OGG. Звук, у которого вместо
 * него лежит WAV, остаётся: его прочитает {@link WavSounds}.
 */
@Mixin(SoundManager.class)
abstract class SoundManagerMixin {
	@Inject(method = "validateSoundResource", at = @At("HEAD"), cancellable = true)
	private static void tf2mc$acceptWav(Sound sound, Identifier eventLocation, ResourceProvider resourceProvider, CallbackInfoReturnable<Boolean> cir) {
		if (WavSounds.replacementFor(resourceProvider, sound.getPath()) != null) {
			cir.setReturnValue(true);
		}
	}
}

package io.github.egorka2000.tf2mc.client.mixin;

import java.util.HashMap;
import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Игра заранее составляет список звуковых файлов и дальше работает только с ним, а попадают
 * в него одни OGG. Сюда же добавляются WAV: без этого их не увидели бы ни проверка звуков
 * при загрузке, ни сам загрузчик.
 */
@Mixin(targets = "net.minecraft.client.sounds.SoundManager$Preparations")
abstract class SoundPreparationsMixin {
	private static final FileToIdConverter WAV_LISTER = new FileToIdConverter("sounds", ".wav");
	private static final FileToIdConverter MP3_LISTER = new FileToIdConverter("sounds", ".mp3");

	@Shadow
	private Map<Identifier, Resource> soundCache;

	@Inject(method = "listResources", at = @At("TAIL"))
	private void tf2mc$listWav(ResourceManager resourceManager, CallbackInfo ci) {
		Map<Identifier, Resource> withWav = new HashMap<>(soundCache);

		withWav.putAll(WAV_LISTER.listMatchingResources(resourceManager));
		// MP3 мод не читает, но должен их видеть, чтобы молча пропустить: см. SoundManagerMixin.
		withWav.putAll(MP3_LISTER.listMatchingResources(resourceManager));
		soundCache = withWav;
	}
}

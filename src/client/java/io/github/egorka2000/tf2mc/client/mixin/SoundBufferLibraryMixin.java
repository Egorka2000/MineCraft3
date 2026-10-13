package io.github.egorka2000.tf2mc.client.mixin;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.mojang.blaze3d.audio.SoundBuffer;
import io.github.egorka2000.tf2mc.client.WavSounds;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.util.Util;

/**
 * Когда игра загружает звук, для которого нет файла OGG, но есть WAV, звук читается из WAV.
 */
@Mixin(SoundBufferLibrary.class)
abstract class SoundBufferLibraryMixin {
	@Shadow
	@Final
	private ResourceProvider resourceManager;

	@Shadow
	@Final
	private Map<Identifier, CompletableFuture<SoundBuffer>> cache;

	@Inject(method = "getCompleteBuffer", at = @At("HEAD"), cancellable = true)
	private void tf2mc$loadWav(Identifier location, CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
		// Уже загруженные звуки, в том числе из WAV, игра отдаст из своего кеша сама.
		if (cache.containsKey(location)) {
			return;
		}

		Identifier wav = WavSounds.replacementFor(resourceManager, location);

		if (wav == null) {
			return;
		}

		// Как и игра, читаем файл в фоновом потоке и кладём результат в её кеш под прежним именем.
		CompletableFuture<SoundBuffer> loading = CompletableFuture.supplyAsync(() -> {
			try {
				return WavSounds.load(resourceManager, wav);
			} catch (IOException e) {
				throw new CompletionException(e);
			}
		}, Util.nonCriticalIoPool());

		cache.put(location, loading);
		cir.setReturnValue(loading);
	}
}

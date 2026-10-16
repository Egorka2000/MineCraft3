package io.github.egorka2000.tf2mc.client.mixin;

import java.util.HashMap;
import java.util.Map;

import io.github.egorka2000.tf2mc.Tf2Mc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundEventRegistration;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Загрузка звуков из ресурс-паков.
 *
 * <p>Игра заранее составляет список звуковых файлов и дальше работает только с ним, а попадают
 * в него одни OGG. Сюда же добавляются WAV и MP3: без этого их не увидели бы ни проверка звуков
 * при загрузке, ни сам загрузчик.
 *
 * <p>Заглушки мода не смешиваются со звуками пака. Игра читает {@code sounds.json} всех паков
 * снизу вверх и добавляет звуки к уже прочитанным. Если расширительный пак стоит в списке ниже
 * мода, заглушки мода читались бы после звуков TF2 и добавлялись к ним, и игра выбирала бы
 * то одно, то другое. Поэтому заглушка не добавляется к звуку мода, который уже определён.
 */
@Mixin(targets = "net.minecraft.client.sounds.SoundManager$Preparations")
abstract class SoundPreparationsMixin {
	private static final FileToIdConverter WAV_LISTER = new FileToIdConverter("sounds", ".wav");
	private static final FileToIdConverter MP3_LISTER = new FileToIdConverter("sounds", ".mp3");

	@Shadow
	private Map<Identifier, Resource> soundCache;

	@Shadow
	@Final
	private Map<Identifier, WeighedSoundEvents> registry;

	@Inject(method = "listResources", at = @At("TAIL"))
	private void tf2mc$listWav(ResourceManager resourceManager, CallbackInfo ci) {
		Map<Identifier, Resource> withWav = new HashMap<>(soundCache);

		withWav.putAll(WAV_LISTER.listMatchingResources(resourceManager));
		// MP3 мод не читает, но должен их видеть, чтобы молча пропустить: см. SoundManagerMixin.
		withWav.putAll(MP3_LISTER.listMatchingResources(resourceManager));
		soundCache = withWav;
	}

	@Inject(method = "handleRegistration", at = @At("HEAD"), cancellable = true)
	private void tf2mc$keepPackSounds(Identifier eventLocation, SoundEventRegistration registration, CallbackInfo ci) {
		if (eventLocation.getNamespace().equals(Tf2Mc.MOD_ID) && registry.containsKey(eventLocation) && isPlaceholder(registration)) {
			ci.cancel();
		}
	}

	/**
	 * Заглушка мода: без замены и только ссылки на ванильные звуки. Звуки пака — файлы, а сам
	 * пак всегда заменяет определение ({@code "replace": true}).
	 */
	private static boolean isPlaceholder(SoundEventRegistration registration) {
		if (registration.isReplace() || registration.getSounds().isEmpty()) {
			return false;
		}

		for (Sound sound : registration.getSounds()) {
			if (sound.getType() != Sound.Type.SOUND_EVENT || !sound.getLocation().getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
				return false;
			}
		}

		return true;
	}
}

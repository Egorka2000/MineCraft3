package io.github.egorka2000.tf2mc.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFormat;

import com.mojang.blaze3d.audio.SoundBuffer;
import io.github.egorka2000.tf2mc.sound.WavDecoder;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;

/**
 * Звуки в формате WAV из ресурс-паков. Игра сама умеет только OGG, а звуки TF2 лежат в WAV:
 * чтобы расширительный пак не приходилось перекодировать, мод читает их сам.
 *
 * <p>Правило простое: если игра просит {@code sounds/имя.ogg}, такого файла нет, а рядом лежит
 * {@code sounds/имя.wav}, берётся он. Сам разбор файла — в {@link WavDecoder}.
 */
public final class WavSounds {
	private static final String OGG = ".ogg";
	private static final String WAV = ".wav";

	private WavSounds() {
	}

	/**
	 * Файл WAV, который заменяет отсутствующий OGG.
	 *
	 * @param ogg путь, по которому игра ищет звук
	 * @return путь к WAV; {@code null}, если OGG на месте или замены нет
	 */
	public static Identifier replacementFor(ResourceProvider resources, Identifier ogg) {
		if (!ogg.getPath().endsWith(OGG) || resources.getResource(ogg).isPresent()) {
			return null;
		}

		Identifier wav = ogg.withPath(path -> path.substring(0, path.length() - OGG.length()) + WAV);
		return resources.getResource(wav).isPresent() ? wav : null;
	}

	/** Читает WAV и готовит его для звукового движка игры. */
	public static SoundBuffer load(ResourceProvider resources, Identifier wav) throws IOException {
		WavDecoder.Pcm pcm;

		try (InputStream stream = resources.open(wav)) {
			pcm = WavDecoder.decode(stream.readAllBytes());
		} catch (WavDecoder.UnsupportedWavException e) {
			throw new IOException("не удалось прочитать " + wav + ": " + e.getMessage(), e);
		}

		// Звуковому движку нужен буфер вне кучи Java.
		ByteBuffer data = ByteBuffer.allocateDirect(pcm.samples().length).order(ByteOrder.nativeOrder());
		data.put(pcm.samples()).flip();

		// 16 бит, моно, со знаком, младший байт первым — в таком виде отдаёт WavDecoder.
		return new SoundBuffer(data, new AudioFormat(pcm.sampleRate(), 16, 1, true, false));
	}
}

package io.github.egorka2000.tf2mc.sound;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Читает несжатый звук из файла WAV и приводит его к виду, который нужен игре: 16 бит, моно.
 * Чистая Java без классов игры.
 *
 * <p>Нужен расширительному паку: звуки TF2 лежат в WAV, а игра сама умеет только OGG.
 * Понимает несжатый звук 8 и 16 бит, моно и стерео; сжатые разновидности WAV не читает.
 * В моно звук сводится потому, что стереозвук игра играет без учёта расстояния и направления.
 */
public final class WavDecoder {
	private static final int RIFF = chunkId("RIFF");
	private static final int WAVE = chunkId("WAVE");
	private static final int FORMAT = chunkId("fmt ");
	private static final int DATA = chunkId("data");

	/** Признак несжатого звука в заголовке. */
	private static final int UNCOMPRESSED = 1;

	private static final int HEADER_SIZE = 12;
	private static final int CHUNK_HEADER_SIZE = 8;
	private static final int FORMAT_SIZE = 16;

	private WavDecoder() {
	}

	/**
	 * Звук, готовый для игры.
	 *
	 * @param sampleRate частота, отсчётов в секунду
	 * @param samples    отсчёты: 16 бит, моно, младший байт первым
	 */
	public record Pcm(int sampleRate, byte[] samples) {
	}

	/** Файл не является несжатым WAV или повреждён. */
	public static final class UnsupportedWavException extends Exception {
		private static final long serialVersionUID = 1L;

		UnsupportedWavException(String message) {
			super(message);
		}
	}

	/**
	 * Разбирает файл WAV целиком.
	 *
	 * @param file содержимое файла
	 * @throws UnsupportedWavException если это не несжатый WAV 8 или 16 бит
	 */
	public static Pcm decode(byte[] file) throws UnsupportedWavException {
		ByteBuffer buffer = ByteBuffer.wrap(file).order(ByteOrder.LITTLE_ENDIAN);

		if (file.length < HEADER_SIZE || buffer.getInt(0) != RIFF || buffer.getInt(8) != WAVE) {
			throw new UnsupportedWavException("это не файл WAV");
		}

		int channels = 0;
		int sampleRate = 0;
		int bits = 0;
		int position = HEADER_SIZE;

		// Файл состоит из блоков; кроме описания формата и самих данных в нём бывают другие, их пропускаем.
		while (position + CHUNK_HEADER_SIZE <= file.length) {
			int id = buffer.getInt(position);
			int size = buffer.getInt(position + 4);
			int body = position + CHUNK_HEADER_SIZE;

			if (size < 0) {
				throw new UnsupportedWavException("повреждённый блок");
			}

			if (id == FORMAT) {
				if (size < FORMAT_SIZE || body + FORMAT_SIZE > file.length) {
					throw new UnsupportedWavException("повреждённое описание формата");
				}

				if (Short.toUnsignedInt(buffer.getShort(body)) != UNCOMPRESSED) {
					throw new UnsupportedWavException("сжатый звук не поддерживается");
				}

				channels = Short.toUnsignedInt(buffer.getShort(body + 2));
				sampleRate = buffer.getInt(body + 4);
				bits = Short.toUnsignedInt(buffer.getShort(body + 14));
			} else if (id == DATA) {
				if (channels == 0) {
					throw new UnsupportedWavException("данные идут раньше описания формата");
				}

				// Размер в заголовке бывает больше остатка файла; берём то, что есть на самом деле.
				return new Pcm(sampleRate, toMono16(file, body, Math.min(size, file.length - body), channels, bits));
			}

			// Блоки выровнены по чётной границе.
			position = body + size + (size & 1);
		}

		throw new UnsupportedWavException("в файле нет звуковых данных");
	}

	private static byte[] toMono16(byte[] file, int offset, int length, int channels, int bits) throws UnsupportedWavException {
		if (channels < 1 || channels > 2 || bits != 8 && bits != 16) {
			throw new UnsupportedWavException("не поддерживается: каналов " + channels + ", бит " + bits);
		}

		int bytesPerSample = bits / 8;
		int frameSize = bytesPerSample * channels;
		int frames = length / frameSize;
		ByteBuffer source = ByteBuffer.wrap(file, offset, frames * frameSize).order(ByteOrder.LITTLE_ENDIAN);
		ByteBuffer target = ByteBuffer.allocate(frames * 2).order(ByteOrder.LITTLE_ENDIAN);

		for (int frame = 0; frame < frames; frame++) {
			int sum = 0;

			for (int channel = 0; channel < channels; channel++) {
				// 8 бит хранятся без знака со смещением 128, 16 бит — со знаком.
				sum += bits == 8 ? ((source.get() & 0xFF) - 128) << 8 : source.getShort();
			}

			target.putShort((short) (sum / channels));
		}

		return target.array();
	}

	private static int chunkId(String name) {
		return name.charAt(0) | name.charAt(1) << 8 | name.charAt(2) << 16 | name.charAt(3) << 24;
	}
}

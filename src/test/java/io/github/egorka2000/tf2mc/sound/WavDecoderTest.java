package io.github.egorka2000.tf2mc.sound;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class WavDecoderTest {
	private static final int UNCOMPRESSED = 1;
	private static final int ADPCM = 2;

	/** Собирает файл WAV: заголовок, описание формата, необязательный посторонний блок, данные. */
	private static byte[] wav(int formatTag, int channels, int sampleRate, int bits, byte[] extraChunk, byte[] data) {
		ByteArrayOutputStream chunks = new ByteArrayOutputStream();
		ByteBuffer format = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);

		format.putShort((short) formatTag);
		format.putShort((short) channels);
		format.putInt(sampleRate);
		format.putInt(sampleRate * channels * bits / 8);
		format.putShort((short) (channels * bits / 8));
		format.putShort((short) bits);
		chunk(chunks, "fmt ", format.array());

		if (extraChunk != null) {
			chunk(chunks, "LIST", extraChunk);
		}

		chunk(chunks, "data", data);

		ByteArrayOutputStream file = new ByteArrayOutputStream();
		file.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
		file.writeBytes(intBytes(4 + chunks.size()));
		file.writeBytes("WAVE".getBytes(StandardCharsets.US_ASCII));
		file.writeBytes(chunks.toByteArray());
		return file.toByteArray();
	}

	private static void chunk(ByteArrayOutputStream out, String id, byte[] body) {
		out.writeBytes(id.getBytes(StandardCharsets.US_ASCII));
		out.writeBytes(intBytes(body.length));
		out.writeBytes(body);

		// Блоки выровнены по чётной границе.
		if (body.length % 2 == 1) {
			out.write(0);
		}
	}

	private static byte[] intBytes(int value) {
		return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
	}

	private static byte[] shorts(int... values) {
		ByteBuffer buffer = ByteBuffer.allocate(values.length * 2).order(ByteOrder.LITTLE_ENDIAN);

		for (int value : values) {
			buffer.putShort((short) value);
		}

		return buffer.array();
	}

	@Test
	void mono16IsReturnedAsIs() throws Exception {
		byte[] samples = shorts(0, 1000, -1000, 32767, -32768);
		WavDecoder.Pcm pcm = WavDecoder.decode(wav(UNCOMPRESSED, 1, 22050, 16, null, samples));

		assertEquals(22050, pcm.sampleRate());
		assertArrayEquals(samples, pcm.samples());
	}

	@Test
	void stereoIsMixedDownToMono() throws Exception {
		// Пары «левый, правый»: в моно идёт их среднее.
		byte[] stereo = shorts(1000, 3000, -200, 200, 32767, 32767);
		WavDecoder.Pcm pcm = WavDecoder.decode(wav(UNCOMPRESSED, 2, 44100, 16, null, stereo));

		assertEquals(44100, pcm.sampleRate());
		assertArrayEquals(shorts(2000, 0, 32767), pcm.samples());
	}

	@Test
	void eightBitIsWidenedToSixteen() throws Exception {
		// 8 бит хранятся без знака: 128 — тишина, 0 — наименьшее значение, 255 — наибольшее.
		byte[] eightBit = {(byte) 128, 0, (byte) 255};
		WavDecoder.Pcm pcm = WavDecoder.decode(wav(UNCOMPRESSED, 1, 11025, 8, null, eightBit));

		assertArrayEquals(shorts(0, -32768, 127 << 8), pcm.samples());
	}

	@Test
	void unknownChunksAreSkipped() throws Exception {
		// Посторонний блок нечётной длины между форматом и данными: проверяет и пропуск, и выравнивание.
		byte[] samples = shorts(5, -5);
		byte[] file = wav(UNCOMPRESSED, 1, 44100, 16, new byte[] {1, 2, 3}, samples);

		assertArrayEquals(samples, WavDecoder.decode(file).samples());
	}

	@Test
	void dataShorterThanHeaderClaimsIsReadToEndOfFile() throws Exception {
		byte[] file = wav(UNCOMPRESSED, 1, 44100, 16, null, shorts(7, 8, 9));
		// Обрезаем последний отсчёт: в заголовке блока данных по-прежнему заявлено шесть байт.
		byte[] truncated = java.util.Arrays.copyOf(file, file.length - 2);

		assertArrayEquals(shorts(7, 8), WavDecoder.decode(truncated).samples());
	}

	@Test
	void compressedWavIsRejected() {
		byte[] file = wav(ADPCM, 1, 22050, 4, null, new byte[] {1, 2, 3, 4});

		assertThrows(WavDecoder.UnsupportedWavException.class, () -> WavDecoder.decode(file));
	}

	@Test
	void unsupportedLayoutIsRejected() {
		assertThrows(WavDecoder.UnsupportedWavException.class,
				() -> WavDecoder.decode(wav(UNCOMPRESSED, 6, 44100, 16, null, new byte[12])));
		assertThrows(WavDecoder.UnsupportedWavException.class,
				() -> WavDecoder.decode(wav(UNCOMPRESSED, 1, 44100, 24, null, new byte[6])));
	}

	@Test
	void notAWavIsRejected() {
		assertThrows(WavDecoder.UnsupportedWavException.class, () -> WavDecoder.decode(new byte[0]));
		assertThrows(WavDecoder.UnsupportedWavException.class,
				() -> WavDecoder.decode("OggS, а вовсе не WAV".getBytes(StandardCharsets.UTF_8)));
	}

	@Test
	void fileWithoutDataIsRejected() {
		byte[] file = wav(UNCOMPRESSED, 1, 44100, 16, null, new byte[0]);
		// Отрезаем блок данных целиком: остаются заголовок и описание формата.
		byte[] headerOnly = java.util.Arrays.copyOf(file, file.length - 8);

		assertThrows(WavDecoder.UnsupportedWavException.class, () -> WavDecoder.decode(headerOnly));
	}
}

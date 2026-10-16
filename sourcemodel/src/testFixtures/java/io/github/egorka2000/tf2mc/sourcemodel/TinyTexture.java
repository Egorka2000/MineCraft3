package io.github.egorka2000.tf2mc.sourcemodel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Самодельные текстуры VTF и материалы VMT для тестов. Файлов Valve не нужно. */
public final class TinyTexture {
	/** Номера форматов изображения VTF. */
	public static final int BGR888 = 3;
	public static final int DXT1 = 13;
	public static final int DXT5 = 15;

	private TinyTexture() {
	}

	/**
	 * Текстура VTF.
	 *
	 * @param minor   вторая цифра версии: до 3 без ресурсов, с 3 — с ресурсом изображения
	 * @param mipmaps уровни детализации от мелкого к крупному, уже в нужном формате
	 */
	public static byte[] vtf(int minor, int format, int width, int height, byte[]... mipmaps) {
		int header = minor >= 3 ? 80 + 8 : 80;
		int size = header;
		for (byte[] mip : mipmaps) {
			size += mip.length;
		}

		ByteBuffer out = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
		out.put(0, "VTF\0".getBytes(StandardCharsets.US_ASCII));
		out.putInt(4, 7);
		out.putInt(8, minor);
		out.putInt(12, header);
		out.putShort(16, (short) width);
		out.putShort(18, (short) height);
		out.putShort(24, (short) 1);
		out.putInt(52, format);
		out.put(56, (byte) mipmaps.length);
		// Мелкой копии нет.
		out.putInt(57, -1);
		out.putShort(63, (short) 1);

		if (minor >= 3) {
			out.putInt(68, 1);
			out.put(80, (byte) 0x30);
			out.putInt(84, header);
		}

		int at = header;
		for (byte[] mip : mipmaps) {
			out.put(at, mip);
			at += mip.length;
		}

		return out.array();
	}

	/** Сплошная текстура 4×4 одного цвета, BGR888. */
	public static byte[] solid(int red, int green, int blue) {
		byte[] pixels = new byte[4 * 4 * 3];

		for (int i = 0; i < 16; i++) {
			pixels[i * 3] = (byte) blue;
			pixels[i * 3 + 1] = (byte) green;
			pixels[i * 3 + 2] = (byte) red;
		}

		return vtf(2, BGR888, 4, 4, pixels);
	}

	/** Материал VMT с основной текстурой. */
	public static String vmt(String baseTexture) {
		return "\"VertexLitGeneric\"\n{\n\t\"$basetexture\" \"" + baseTexture + "\"\n}\n";
	}
}

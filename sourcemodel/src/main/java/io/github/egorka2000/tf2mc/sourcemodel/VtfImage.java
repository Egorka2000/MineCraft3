package io.github.egorka2000.tf2mc.sourcemodel;

/**
 * Текстура из файла VTF: самый крупный уровень детализации, первый кадр, первая грань,
 * распакованная в цвета ARGB по 8 бит.
 *
 * <p>Устройство файла — по Valve Developer Wiki (страница VTF). Версии 7.0–7.5. Сжатие DXT1, DXT3,
 * DXT5 и основные несжатые форматы; распаковка DXT — по открытому описанию сжатия S3TC.
 */
public final class VtfImage {
	private static final int HEADER_SIZE_V72 = 80;
	private static final int RESOURCE_SIZE = 8;
	private static final int TAG_HIGH_RES = 0x30;

	// Номера форматов изображения из перечисления формата VTF.
	private static final int RGBA8888 = 0;
	private static final int ABGR8888 = 1;
	private static final int RGB888 = 2;
	private static final int BGR888 = 3;
	private static final int RGB565 = 4;
	private static final int I8 = 5;
	private static final int IA88 = 6;
	private static final int A8 = 8;
	private static final int ARGB8888 = 11;
	private static final int BGRA8888 = 12;
	private static final int DXT1 = 13;
	private static final int DXT3 = 14;
	private static final int DXT5 = 15;
	private static final int BGRX8888 = 16;
	private static final int BGR565 = 17;
	private static final int DXT1_ONEBITALPHA = 20;

	private final int width;
	private final int height;
	private final int[] argb;

	private VtfImage(int width, int height, int[] argb) {
		this.width = width;
		this.height = height;
		this.argb = argb;
	}

	public static VtfImage read(byte[] data) {
		Bytes in = new Bytes(data, "VTF");

		if (!in.magic().equals("VTF\0")) {
			throw new ModelFormatException("VTF: не текстура Source, начало " + in.magic());
		}

		int major = in.int32(4);
		int minor = in.int32(8);

		if (major != 7 || minor > 5) {
			throw new ModelFormatException("VTF: версия " + major + "." + minor + " не поддерживается, нужна 7.0–7.5");
		}

		int headerSize = in.int32(12);
		int width = in.uint16(16);
		int height = in.uint16(18);
		int frames = Math.max(1, in.uint16(24));
		int format = in.int32(52);
		int mipmaps = Math.max(1, in.uint8(56));
		int lowFormat = in.int32(57);
		int lowWidth = in.uint8(61);
		int lowHeight = in.uint8(62);
		int depth = minor >= 2 ? Math.max(1, in.uint16(63)) : 1;
		int faces = (in.int32(20) & 0x4000) != 0 ? 6 : 1;

		int start = highResStart(in, minor, headerSize, lowFormat, lowWidth, lowHeight);

		// Уровни детализации идут от самого мелкого к самому крупному; нужен последний.
		int offset = start;
		for (int mip = mipmaps - 1; mip >= 1; mip--) {
			offset += size(format, Math.max(1, width >> mip), Math.max(1, height >> mip)) * frames * faces * Math.max(1, depth >> mip);
		}

		int needed = size(format, width, height);
		in.checkArray(offset, needed, 1, "изображение");
		return new VtfImage(width, height, decode(in, offset, format, width, height));
	}

	/** Где начинаются данные крупного изображения. */
	private static int highResStart(Bytes in, int minor, int headerSize, int lowFormat, int lowWidth, int lowHeight) {
		if (minor >= 3) {
			int resources = in.int32(68);
			in.checkArray(HEADER_SIZE_V72, resources, RESOURCE_SIZE, "ресурсы");

			for (int i = 0; i < resources; i++) {
				int entry = HEADER_SIZE_V72 + i * RESOURCE_SIZE;

				if (in.uint8(entry) == TAG_HIGH_RES && in.uint8(entry + 1) == 0 && in.uint8(entry + 2) == 0) {
					return in.int32(entry + 4);
				}
			}

			throw new ModelFormatException("VTF: нет ресурса с изображением");
		}

		// До 7.3: после заголовка мелкая копия, затем изображение.
		int low = lowWidth == 0 || lowHeight == 0 || lowFormat < 0 ? 0 : size(lowFormat, lowWidth, lowHeight);
		return headerSize + low;
	}

	/** Размер изображения в байтах. */
	static int size(int format, int width, int height) {
		int blocks = Math.max(1, (width + 3) / 4) * Math.max(1, (height + 3) / 4);

		return switch (format) {
			case DXT1, DXT1_ONEBITALPHA -> blocks * 8;
			case DXT3, DXT5 -> blocks * 16;
			case RGBA8888, ABGR8888, ARGB8888, BGRA8888, BGRX8888 -> width * height * 4;
			case RGB888, BGR888 -> width * height * 3;
			case RGB565, BGR565, IA88 -> width * height * 2;
			case I8, A8 -> width * height;
			default -> throw new ModelFormatException("VTF: формат изображения " + format + " не поддерживается");
		};
	}

	private static int[] decode(Bytes in, int offset, int format, int width, int height) {
		int[] pixels = new int[width * height];

		switch (format) {
			case DXT1, DXT1_ONEBITALPHA, DXT3, DXT5 -> decodeDxt(in, offset, format, width, height, pixels);
			default -> {
				int bytes = size(format, 1, 1);

				for (int i = 0; i < pixels.length; i++) {
					pixels[i] = pixel(in, offset + i * bytes, format);
				}
			}
		}

		return pixels;
	}

	/** Один несжатый пиксель в ARGB. */
	private static int pixel(Bytes in, int at, int format) {
		return switch (format) {
			case RGBA8888 -> argb(in.uint8(at + 3), in.uint8(at), in.uint8(at + 1), in.uint8(at + 2));
			case ABGR8888 -> argb(in.uint8(at), in.uint8(at + 3), in.uint8(at + 2), in.uint8(at + 1));
			case ARGB8888 -> argb(in.uint8(at), in.uint8(at + 1), in.uint8(at + 2), in.uint8(at + 3));
			case BGRA8888 -> argb(in.uint8(at + 3), in.uint8(at + 2), in.uint8(at + 1), in.uint8(at));
			case BGRX8888 -> argb(255, in.uint8(at + 2), in.uint8(at + 1), in.uint8(at));
			case RGB888 -> argb(255, in.uint8(at), in.uint8(at + 1), in.uint8(at + 2));
			case BGR888 -> argb(255, in.uint8(at + 2), in.uint8(at + 1), in.uint8(at));
			case RGB565 -> rgb565(in.uint16(at), false);
			case BGR565 -> rgb565(in.uint16(at), true);
			case I8 -> argb(255, in.uint8(at), in.uint8(at), in.uint8(at));
			case IA88 -> argb(in.uint8(at + 1), in.uint8(at), in.uint8(at), in.uint8(at));
			case A8 -> argb(in.uint8(at), 255, 255, 255);
			default -> throw new ModelFormatException("VTF: формат изображения " + format + " не поддерживается");
		};
	}

	/** 16 бит: 5 бит красного или синего, 6 зелёного, 5 синего или красного. */
	private static int rgb565(int value, boolean blueFirst) {
		int high = expand5((value >> 11) & 0x1F);
		int green = expand6((value >> 5) & 0x3F);
		int low = expand5(value & 0x1F);
		return blueFirst ? argb(255, low, green, high) : argb(255, high, green, low);
	}

	private static void decodeDxt(Bytes in, int offset, int format, int width, int height, int[] pixels) {
		int blockBytes = format == DXT3 || format == DXT5 ? 16 : 8;
		int blocksWide = Math.max(1, (width + 3) / 4);
		int blocksHigh = Math.max(1, (height + 3) / 4);
		int[] colors = new int[4];
		int[] alphas = new int[16];

		for (int by = 0; by < blocksHigh; by++) {
			for (int bx = 0; bx < blocksWide; bx++) {
				int block = offset + (by * blocksWide + bx) * blockBytes;
				int colorBlock = block + (blockBytes == 16 ? 8 : 0);
				boolean alphaFromColor = format == DXT1 || format == DXT1_ONEBITALPHA;

				blockColors(in.uint16(colorBlock), in.uint16(colorBlock + 2), alphaFromColor, colors);
				blockAlphas(in, block, format, alphas);
				int indices = in.int32(colorBlock + 4);

				for (int py = 0; py < 4; py++) {
					for (int px = 0; px < 4; px++) {
						int x = bx * 4 + px;
						int y = by * 4 + py;

						if (x >= width || y >= height) {
							continue;
						}

						int texel = py * 4 + px;
						int color = colors[(indices >>> (texel * 2)) & 0x3];
						int alpha = alphaFromColor ? color >>> 24 : alphas[texel];
						pixels[y * width + x] = (alpha << 24) | (color & 0xFFFFFF);
					}
				}
			}
		}
	}

	/** Четыре цвета блока: два записанных и два промежуточных (у DXT1 при c0 ≤ c1 — один и прозрачный). */
	private static void blockColors(int c0, int c1, boolean dxt1, int[] colors) {
		colors[0] = rgb565(c0, false);
		colors[1] = rgb565(c1, false);
		int r0 = (colors[0] >> 16) & 0xFF;
		int g0 = (colors[0] >> 8) & 0xFF;
		int b0 = colors[0] & 0xFF;
		int r1 = (colors[1] >> 16) & 0xFF;
		int g1 = (colors[1] >> 8) & 0xFF;
		int b1 = colors[1] & 0xFF;

		if (!dxt1 || c0 > c1) {
			colors[2] = argb(255, (2 * r0 + r1) / 3, (2 * g0 + g1) / 3, (2 * b0 + b1) / 3);
			colors[3] = argb(255, (r0 + 2 * r1) / 3, (g0 + 2 * g1) / 3, (b0 + 2 * b1) / 3);
		} else {
			colors[2] = argb(255, (r0 + r1) / 2, (g0 + g1) / 2, (b0 + b1) / 2);
			colors[3] = 0;
		}
	}

	/** Прозрачность 16 точек блока: у DXT3 — явная по 4 бита, у DXT5 — по таблице из двух значений. */
	private static void blockAlphas(Bytes in, int block, int format, int[] alphas) {
		if (format == DXT3) {
			for (int i = 0; i < 16; i++) {
				int nibble = (in.uint8(block + i / 2) >> ((i % 2) * 4)) & 0xF;
				alphas[i] = nibble * 17;
			}
		} else if (format == DXT5) {
			int a0 = in.uint8(block);
			int a1 = in.uint8(block + 1);
			long bits = 0;

			for (int i = 0; i < 6; i++) {
				bits |= (long) in.uint8(block + 2 + i) << (8 * i);
			}

			for (int i = 0; i < 16; i++) {
				int code = (int) ((bits >>> (3 * i)) & 0x7);
				alphas[i] = dxt5Alpha(a0, a1, code);
			}
		}
	}

	private static int dxt5Alpha(int a0, int a1, int code) {
		if (code == 0) {
			return a0;
		}

		if (code == 1) {
			return a1;
		}

		if (a0 > a1) {
			return ((8 - code) * a0 + (code - 1) * a1) / 7;
		}

		if (code == 6) {
			return 0;
		}

		if (code == 7) {
			return 255;
		}

		return ((6 - code) * a0 + (code - 1) * a1) / 5;
	}

	private static int expand5(int value) {
		return (value << 3) | (value >> 2);
	}

	private static int expand6(int value) {
		return (value << 2) | (value >> 4);
	}

	private static int argb(int a, int r, int g, int b) {
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	/** Цвета построчно сверху вниз, по 8 бит: A R G B. */
	public int[] argb() {
		return argb;
	}
}

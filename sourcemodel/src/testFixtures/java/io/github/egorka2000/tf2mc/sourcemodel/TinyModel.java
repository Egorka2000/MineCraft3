package io.github.egorka2000.tf2mc.sourcemodel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Самодельная крошечная модель в форматах MDL, VVD и VTX для тестов: две кости, одна часть тела,
 * одна сетка из четырёх вершин и двух треугольников, одна текстура, два семейства скинов, один
 * хитбокс головы, одна подключённая модель. Файлов Valve не нужно.
 */
public final class TinyModel {
	public static final int CHECKSUM = 12345;

	private TinyModel() {
	}

	public static byte[] mdl() {
		return mdl(48, CHECKSUM);
	}

	public static byte[] mdl(int version, int checksum) {
		ByteBuffer out = buffer(1400);
		out.put(0, "IDST".getBytes(StandardCharsets.US_ASCII));
		out.putInt(4, version);
		out.putInt(8, checksum);
		putString(out, 12, "test/tiny.mdl");

		int strings = 1280;
		int root = strings;
		int child = putString(out, root, "root");
		int bodyName = putString(out, child, "child");
		int texName = putString(out, bodyName, "body");
		int dirName = putString(out, texName, "tex_red");
		int setName = putString(out, dirName, "models\\test\\");
		int boxName = putString(out, setName, "default");
		int includeName = putString(out, boxName, "head");
		putString(out, includeName, "models/test_animations.mdl");

		// Кости: корень и дочерняя, сдвинутая на 10 по z.
		out.putInt(156, 2);
		out.putInt(160, 408);
		bone(out, 408, root, -1, 0.0F);
		bone(out, 408 + 216, child, 0, 10.0F);

		// Часть тела с одной моделью и одной сеткой.
		out.putInt(232, 1);
		out.putInt(236, 840);
		out.putInt(840, bodyName - 840);
		out.putInt(844, 1);
		out.putInt(852, 856 - 840);
		putString(out, 856, "tiny");
		out.putInt(856 + 72, 1);
		out.putInt(856 + 76, 1004 - 856);
		out.putInt(856 + 80, 4);
		out.putInt(856 + 84, 0);
		out.putInt(1004, 0);
		out.putInt(1004 + 8, 4);
		out.putInt(1004 + 12, 0);

		// Текстура, папка, таблица скинов: два семейства, одно место под материал.
		out.putInt(204, 1);
		out.putInt(208, 1120);
		out.putInt(1120, texName - 1120);
		out.putInt(212, 1);
		out.putInt(216, 1184);
		out.putInt(1184, dirName);
		out.putInt(220, 1);
		out.putInt(224, 2);
		out.putInt(228, 1188);

		// Набор хитбоксов с хитбоксом головы на дочерней кости.
		out.putInt(172, 1);
		out.putInt(176, 1192);
		out.putInt(1192, setName - 1192);
		out.putInt(1196, 1);
		out.putInt(1200, 1204 - 1192);
		out.putInt(1204, 1);
		out.putInt(1208, 1);
		out.putFloat(1212, -2.0F);
		out.putFloat(1224, 2.0F);
		out.putInt(1236, boxName - 1204);

		// Подключённая модель с анимациями.
		out.putInt(336, 1);
		out.putInt(340, 1272);
		out.putInt(1276, includeName - 1272);
		return out.array();
	}

	/**
	 * @param fixups по три числа на поправку: уровень, первая вершина, сколько вершин; пусто — без таблицы
	 */
	public static byte[] vvd(int checksum, int... fixups) {
		int vertexStart = 64 + fixups.length * 4;
		ByteBuffer out = buffer(vertexStart + 4 * 48);
		out.put(0, "IDSV".getBytes(StandardCharsets.US_ASCII));
		out.putInt(4, 4);
		out.putInt(8, checksum);
		out.putInt(12, 1);
		out.putInt(16, 4);
		out.putInt(48, fixups.length / 3);
		out.putInt(52, 64);
		out.putInt(56, vertexStart);

		for (int i = 0; i < fixups.length; i++) {
			out.putInt(64 + i * 4, fixups[i]);
		}

		// Вершина i: в x записан её номер, первые две на корневой кости, вторые две на дочерней.
		for (int i = 0; i < 4; i++) {
			int vertex = vertexStart + i * 48;
			out.putFloat(vertex, 1.0F);
			out.put(vertex + 12, (byte) (i < 2 ? 0 : 1));
			out.put(vertex + 15, (byte) 1);
			out.putFloat(vertex + 16, i);
			out.putFloat(vertex + 36, 1.0F);
			out.putFloat(vertex + 40, 0.25F * i);
		}

		return out.array();
	}

	/** Два треугольника тройками: 0 1 2 и 2 1 3. */
	public static byte[] vtx(int checksum) {
		ByteBuffer out = buffer(173);
		out.putInt(0, 7);
		out.putInt(16, checksum);
		out.putInt(20, 1);
		out.putInt(28, 1);
		out.putInt(32, 36);
		out.putInt(36, 1);
		out.putInt(40, 8);
		out.putInt(44, 1);
		out.putInt(48, 8);
		out.putInt(52, 1);
		out.putInt(56, 12);
		out.putInt(64, 1);
		out.putInt(68, 9);

		int group = 73;
		out.putInt(group, 4);
		out.putInt(group + 4, 25);
		out.putInt(group + 8, 6);
		out.putInt(group + 12, 61);
		out.putInt(group + 16, 1);
		out.putInt(group + 20, 73);

		for (int i = 0; i < 4; i++) {
			out.putShort(98 + i * 9 + 4, (short) i);
		}

		short[] indices = {0, 1, 2, 2, 1, 3};
		for (int i = 0; i < indices.length; i++) {
			out.putShort(134 + i * 2, indices[i]);
		}

		out.putInt(146, 6);
		out.putInt(146 + 8, 4);
		out.put(146 + 18, (byte) 1);
		return out.array();
	}

	private static void bone(ByteBuffer out, int offset, int name, int parent, float z) {
		out.putInt(offset, name - offset);
		out.putInt(offset + 4, parent);
		out.putFloat(offset + 40, z);
		out.putFloat(offset + 56, 1.0F);
		// Матрица из пространства модели в пространство кости: единичная со сдвигом на −z.
		out.putFloat(offset + 96, 1.0F);
		out.putFloat(offset + 116, 1.0F);
		out.putFloat(offset + 136, 1.0F);
		out.putFloat(offset + 140, -z);
	}

	/** @return смещение сразу за строкой */
	private static int putString(ByteBuffer out, int offset, String text) {
		byte[] bytes = text.getBytes(StandardCharsets.US_ASCII);
		out.put(offset, bytes);
		return offset + bytes.length + 1;
	}

	private static ByteBuffer buffer(int size) {
		return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
	}
}

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

	/**
	 * Файл анимаций для крошечной модели: те же две кости, параметр позы {@code move_x},
	 * последовательность {@code idle} (по кругу) с одной анимацией на 3 кадра. В анимации
	 * дочерняя кость поворачивается вокруг z на 0, 1 и 2 радиана и сдвинута по x на 2,5.
	 */
	public static byte[] animations() {
		ByteBuffer out = buffer(1400);
		out.put(0, "IDST".getBytes(StandardCharsets.US_ASCII));
		out.putInt(4, 48);
		putString(out, 12, "test/tiny_animations.mdl");

		// Строки — в конце файла, за всеми структурами.
		int root = 1330;
		int child = putString(out, root, "root");
		int seqName = putString(out, child, "child");
		int animName = putString(out, seqName, "idle");
		int paramName = putString(out, animName, "a_idle");
		putString(out, paramName, "move_x");

		out.putInt(156, 2);
		out.putInt(160, 408);
		bone(out, 408, root, -1, 0.0F);
		bone(out, 408 + 216, child, 0, 10.0F);
		// Масштабы сжатия дочерней кости: углы по 0,01, смещения по 0,5.
		out.putFloat(408 + 216 + 72, 0.5F);
		out.putFloat(408 + 216 + 92, 0.01F);

		// Параметр позы.
		out.putInt(300, 1);
		out.putInt(304, 840);
		out.putInt(840, paramName - 840);
		out.putFloat(848, -1.0F);
		out.putFloat(852, 1.0F);

		// Описание анимации: 30 кадров в секунду, 3 кадра, данные — с 1000.
		int anim = 860;
		out.putInt(180, 1);
		out.putInt(184, anim);
		out.putInt(anim + 4, animName - anim);
		out.putFloat(anim + 8, 30.0F);
		out.putInt(anim + 16, 3);
		out.putInt(anim + 56, 1000 - anim);

		// Данные дочерней кости: анимированные углы и смещения.
		int data = 1000;
		out.put(data, (byte) 1);
		out.put(data + 1, (byte) 0x0C);
		// Указатели дорожек углов (x y z) и смещений (x y z) — от начала своей тройки.
		out.putShort(data + 4 + 4, (short) 12);
		out.putShort(data + 10, (short) 14);
		// Дорожка угла z: три значения на три кадра.
		int rotation = data + 4 + 12;
		out.put(rotation, (byte) 3);
		out.put(rotation + 1, (byte) 3);
		out.putShort(rotation + 2, (short) 0);
		out.putShort(rotation + 4, (short) 100);
		out.putShort(rotation + 6, (short) 200);
		// Дорожка смещения x: одно значение на все три кадра.
		int position = data + 10 + 14;
		out.put(position, (byte) 1);
		out.put(position + 1, (byte) 3);
		out.putShort(position + 2, (short) 5);

		// Последовательность «idle» по кругу, одна анимация.
		int seq = 1100;
		out.putInt(188, 1);
		out.putInt(192, seq);
		out.putInt(seq + 4, seqName - seq);
		out.putInt(seq + 12, 1);
		out.putInt(seq + 56, 1);
		out.putInt(seq + 60, 1320 - seq);
		out.putInt(seq + 68, 1);
		out.putInt(seq + 72, 1);
		out.putInt(seq + 76, -1);
		out.putInt(seq + 80, -1);
		out.putShort(1320, (short) 0);
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

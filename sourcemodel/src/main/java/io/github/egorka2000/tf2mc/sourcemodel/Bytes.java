package io.github.egorka2000.tf2mc.sourcemodel;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Чтение чисел и строк из файла формата Source по смещению. Все числа в этих файлах записаны
 * младшим байтом вперёд. Выход за конец файла — ошибка формата, а не исключение Java.
 */
final class Bytes {
	private final ByteBuffer buffer;
	private final String file;

	Bytes(byte[] data, String file) {
		this.buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		this.file = file;
	}

	int size() {
		return buffer.capacity();
	}

	int int32(int offset) {
		check(offset, 4);
		return buffer.getInt(offset);
	}

	float float32(int offset) {
		check(offset, 4);
		return buffer.getFloat(offset);
	}

	short int16(int offset) {
		check(offset, 2);
		return buffer.getShort(offset);
	}

	int uint16(int offset) {
		return int16(offset) & 0xFFFF;
	}

	int uint8(int offset) {
		check(offset, 1);
		return buffer.get(offset) & 0xFF;
	}

	/** Четыре буквы в начале файла, по которым узнаётся формат. */
	String magic() {
		check(0, 4);
		byte[] id = new byte[4];
		buffer.get(0, id);
		return new String(id, StandardCharsets.US_ASCII);
	}

	/** Строка, оканчивающаяся нулевым байтом. */
	String string(int offset) {
		check(offset, 1);
		int end = offset;

		while (end < buffer.capacity() && buffer.get(end) != 0) {
			end++;
		}

		byte[] text = new byte[end - offset];
		buffer.get(offset, text);
		return new String(text, StandardCharsets.US_ASCII);
	}

	/** Строка фиксированной длины, дополненная нулями. */
	String fixedString(int offset, int length) {
		check(offset, length);
		String value = string(offset);
		return value.length() > length ? value.substring(0, length) : value;
	}

	/** Смещение и длина массива из заголовка: проверяет, что массив целиком лежит в файле. */
	void checkArray(int offset, int count, int stride, String what) {
		if (count < 0 || offset < 0 || (long) offset + (long) count * stride > buffer.capacity()) {
			throw new ModelFormatException(file + ": " + what + " за пределами файла: смещение " + offset + ", элементов " + count);
		}
	}

	private void check(int offset, int length) {
		if (offset < 0 || offset + length > buffer.capacity()) {
			throw new ModelFormatException(file + ": чтение за пределами файла, смещение " + offset);
		}
	}
}

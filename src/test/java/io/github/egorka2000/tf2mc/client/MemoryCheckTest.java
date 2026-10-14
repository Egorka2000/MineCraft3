package io.github.egorka2000.tf2mc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MemoryCheckTest {
	private static final long GB = 1024L * 1024 * 1024;

	@Test
	void warnsWhenLessThanFourGigabytes() {
		assertEquals("tf2-mc: игре выделено 2,0 ГБ памяти, советуем не меньше 4 ГБ. "
				+ "В лаунчере: Установки → ваша установка → Дополнительно → Аргументы JVM, замените -Xmx… на -Xmx4G.",
				MemoryCheck.warning(2 * GB));
	}

	@Test
	void silentWithFourGigabytes() {
		// С -Xmx4G Java сообщает чуть меньше: служебные области сборщика мусора.
		assertNull(MemoryCheck.warning(4 * GB - 100L * 1024 * 1024));
		assertNull(MemoryCheck.warning(8 * GB));
	}

	@Test
	void silentWithoutLimit() {
		assertNull(MemoryCheck.warning(Long.MAX_VALUE));
	}
}

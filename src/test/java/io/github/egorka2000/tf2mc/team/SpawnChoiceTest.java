package io.github.egorka2000.tf2mc.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import net.minecraft.util.RandomSource;

class SpawnChoiceTest {
	private static final List<String> ROOMS = List.of("a1", "a2", "b1", "b2");

	@Test
	void nextSpawnDiffersFromLast() {
		RandomSource random = RandomSource.create(1);
		String last = null;
		Set<String> used = new HashSet<>();

		for (int death = 0; death < 200; death++) {
			String spawn = SpawnChoice.choose(ROOMS, s -> false, last, random).orElseThrow();
			assertNotEquals(last, spawn, "игрок появился на той же точке, что в прошлый раз");
			used.add(spawn);
			last = spawn;
		}

		assertEquals(Set.copyOf(ROOMS), used, "встретились не все точки");
	}

	@Test
	void occupiedSpawnIsSkipped() {
		RandomSource random = RandomSource.create(2);

		for (int i = 0; i < 100; i++) {
			assertEquals("b2", SpawnChoice.choose(ROOMS, s -> !s.equals("b2"), null, random).orElseThrow());
		}
	}

	@Test
	void singleOrAllOccupiedSpawnsStillWork() {
		RandomSource random = RandomSource.create(3);

		assertEquals(Optional.of("a1"), SpawnChoice.choose(List.of("a1"), s -> false, "a1", random));
		assertTrue(SpawnChoice.choose(ROOMS, s -> true, null, random).isPresent(), "все точки заняты — игрок всё равно должен появиться");
		assertEquals(Optional.empty(), SpawnChoice.choose(List.<String>of(), s -> false, null, random));
	}
}

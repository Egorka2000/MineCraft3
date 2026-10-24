package io.github.egorka2000.tf2mc.clazz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import io.github.egorka2000.tf2mc.physics.MoveParams;
import org.junit.jupiter.api.Test;

class TfClassTest {
	@Test
	void soldierMatchesVerifiedNumbers() {
		// docs/05-sources.md: Soldier — 200 HP, скорость 80% от базовых 300 u/s.
		assertEquals(200, TfClass.SOLDIER.maxHealth());
		assertEquals(MoveParams.BASE_SPEED * 0.8, TfClass.SOLDIER.maxSpeed(), 1e-9);
		assertEquals(0, TfClass.SOLDIER.airJumps());
	}

	@Test
	void scoutMatchesVerifiedNumbers() {
		// docs/05-sources.md: Scout — 125 HP, скорость 133% от базовых 300 u/s (400 u/s), один прыжок в воздухе.
		assertEquals(125, TfClass.SCOUT.maxHealth());
		assertEquals(400.0, TfClass.SCOUT.maxSpeed(), 1e-9);
		assertEquals(1, TfClass.SCOUT.airJumps());
	}

	@Test
	void classIsFoundByItsId() {
		for (TfClass tfClass : TfClass.values()) {
			assertEquals(Optional.of(tfClass), TfClass.byId(tfClass.id()));
		}
	}

	@Test
	void unknownIdAndVanillaAreNotClasses() {
		assertTrue(TfClass.byId("heavy-weapons-guy").isEmpty());
		assertTrue(TfClass.byId("").isEmpty());
		assertTrue(TfClass.byId(TfClass.VANILLA_ID).isEmpty());
	}

	@Test
	void idsAreUniqueAndSuitableForCommands() {
		Set<String> ids = new HashSet<>();

		for (TfClass tfClass : TfClass.values()) {
			assertTrue(ids.add(tfClass.id()), "повтор имени: " + tfClass.id());
			assertTrue(tfClass.id().matches("[a-z]+"), "имя не годится для команды: " + tfClass.id());
		}
	}
}

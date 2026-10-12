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

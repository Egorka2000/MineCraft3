package io.github.egorka2000.tf2mc.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AirJumpsTest {
	@Test
	void oneAirJumpPerFlight() {
		AirJumps jumps = new AirJumps();

		assertTrue(jumps.tick(false, true, false, 1));
		assertFalse(jumps.tick(false, false, true, 1));
		assertFalse(jumps.tick(false, true, false, 1), "второй прыжок в воздухе у Scout");
	}

	@Test
	void holdingJumpFromGroundDoesNotAirJump() {
		AirJumps jumps = new AirJumps();

		assertFalse(jumps.tick(true, true, false, 1));
		// Прыгнул с земли и держит клавишу: в воздухе это не новое нажатие.
		assertFalse(jumps.tick(false, true, true, 1));
		assertFalse(jumps.tick(false, true, true, 1));
		// Отпустил и нажал снова.
		assertFalse(jumps.tick(false, false, true, 1));
		assertTrue(jumps.tick(false, true, false, 1));
	}

	@Test
	void landingRestoresAirJump() {
		AirJumps jumps = new AirJumps();
		jumps.tick(false, true, false, 1);

		jumps.tick(true, false, false, 1);

		assertTrue(jumps.tick(false, true, false, 1));
	}

	@Test
	void classWithoutAirJumpsNeverAirJumps() {
		assertFalse(new AirJumps().tick(false, true, false, 0));
	}
}

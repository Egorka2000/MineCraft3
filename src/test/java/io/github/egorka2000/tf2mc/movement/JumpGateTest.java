package io.github.egorka2000.tf2mc.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JumpGateTest {
	@Test
	void freshPressIsAccepted() {
		JumpGate gate = new JumpGate(0);
		gate.observePreviousTick(false);

		assertTrue(gate.acceptPress());
	}

	@Test
	void heldKeyDoesNotJumpAgain() {
		JumpGate gate = new JumpGate(2);
		gate.observePreviousTick(false);
		assertTrue(gate.acceptPress());

		// Клавишу держат весь полёт; на приземлении игра снова просит прыжок.
		for (int tick = 0; tick < 15; tick++) {
			gate.observePreviousTick(true);
		}

		assertFalse(gate.acceptPress());
	}

	@Test
	void releaseAllowsNextJump() {
		JumpGate gate = new JumpGate(0);
		gate.observePreviousTick(false);
		assertTrue(gate.acceptPress());

		gate.observePreviousTick(true);
		gate.observePreviousTick(false);

		assertTrue(gate.acceptPress());
	}

	@Test
	void pressShortlyBeforeLandingIsAcceptedWithinBuffer() {
		JumpGate gate = new JumpGate(2);
		gate.observePreviousTick(false);
		// Клавишу зажали за два тика до приземления.
		gate.observePreviousTick(true);
		gate.observePreviousTick(true);

		assertTrue(gate.acceptPress());
	}

	@Test
	void pressTooEarlyIsRejected() {
		JumpGate gate = new JumpGate(2);
		gate.observePreviousTick(false);
		gate.observePreviousTick(true);
		gate.observePreviousTick(true);
		gate.observePreviousTick(true);

		assertFalse(gate.acceptPress());
	}

	@Test
	void onePressGivesOneJumpEvenInsideBuffer() {
		JumpGate gate = new JumpGate(5);
		gate.observePreviousTick(false);
		assertTrue(gate.acceptPress());

		gate.observePreviousTick(true);
		assertFalse(gate.acceptPress());
	}

	@Test
	void negativeBufferIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new JumpGate(-1));
	}
}

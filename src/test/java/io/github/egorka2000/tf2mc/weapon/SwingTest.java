package io.github.egorka2000.tf2mc.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SwingTest {
	private static final Swing.Rules RULES = Swing.Rules.SHOVEL;

	@Test
	void shovelMatchesVerifiedNumbers() {
		// docs/05-sources.md: удар раз в 0,8 с, через 0,2 с после замаха; смена оружия 0,5 с.
		assertEquals(16, RULES.interval());
		assertEquals(4, RULES.hitDelay());
		assertEquals(10, RULES.deployTicks());
	}

	@Test
	void hitLandsAfterDelayOnce() {
		Swing state = Swing.READY.swing(RULES);
		int hits = 0;
		int hitTick = -1;

		for (int tick = 1; tick <= RULES.interval(); tick++) {
			Swing.Tick result = state.tick();
			state = result.next();

			if (result.hit()) {
				hits++;
				hitTick = tick;
			}
		}

		assertEquals(1, hits);
		assertEquals(RULES.hitDelay(), hitTick);
		assertTrue(state.canSwing());
	}

	@Test
	void cannotSwingDuringInterval() {
		Swing state = Swing.READY.swing(RULES);

		for (int tick = 1; tick < RULES.interval(); tick++) {
			state = state.tick().next();
			assertFalse(state.canSwing(), "замах на тике " + tick);
		}
	}

	@Test
	void holsterCancelsPendingHit() {
		Swing state = Swing.READY.swing(RULES).tick().next().holster();

		for (int tick = 0; tick < RULES.interval(); tick++) {
			Swing.Tick result = state.tick();
			assertFalse(result.hit(), "удар после того, как оружие убрали");
			state = result.next();
		}
	}

	@Test
	void deployBlocksSwingForHalfSecond() {
		Swing state = Swing.READY.deploy(RULES);

		for (int tick = 0; tick < RULES.deployTicks(); tick++) {
			assertFalse(state.canSwing());
			state = state.tick().next();
		}

		assertTrue(state.canSwing());
	}
}

package io.github.egorka2000.tf2mc.clazz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HealthRulesTest {
	private static final double GRAVITY = 800.0;
	private static final double SOLDIER_HEALTH = 200.0;

	@Test
	void fallSpeedFollowsFreeFall() {
		// v² = 2gh: с 225 u при гравитации 800 получается 600 u/s.
		assertEquals(600.0, HealthRules.fallSpeed(225.0, GRAVITY), 1e-9);
		assertEquals(0.0, HealthRules.fallSpeed(0.0, GRAVITY), 1e-9);
		assertEquals(0.0, HealthRules.fallSpeed(-10.0, GRAVITY), 1e-9);
	}

	@Test
	void noFallDamageUpToSafeSpeed() {
		assertEquals(0.0, HealthRules.fallDamage(0.0, SOLDIER_HEALTH, 1.0), 1e-9);
		assertEquals(0.0, HealthRules.fallDamage(HealthRules.SAFE_FALL_SPEED, SOLDIER_HEALTH, 1.0), 1e-9);
	}

	@Test
	void fallDamageMatchesWikiTable() {
		// docs/05-sources.md: 651 u/s — 10,85% максимального здоровья, 3500 u/s — 58,33%.
		assertEquals(10.85, HealthRules.fallDamage(651.0, 100.0, 1.0), 1e-9);
		assertEquals(58.33, HealthRules.fallDamage(3500.0, 100.0, 1.0), 0.005);
	}

	@Test
	void fallDamageScalesWithMaxHealthAndSpread() {
		double base = HealthRules.fallDamage(900.0, 100.0, 1.0);

		assertEquals(base * 2.0, HealthRules.fallDamage(900.0, SOLDIER_HEALTH, 1.0), 1e-9);
		assertEquals(base * 0.8, HealthRules.fallDamage(900.0, 100.0, 1.0 - HealthRules.FALL_DAMAGE_SPREAD), 1e-9);
		assertEquals(base * 1.2, HealthRules.fallDamage(900.0, 100.0, 1.0 + HealthRules.FALL_DAMAGE_SPREAD), 1e-9);
	}

	@Test
	void overhealIsOneAndHalfRoundedDownToFive() {
		// docs/05-sources.md: Soldier — 300; 175 HP дают 262,5, что округляется до 260.
		assertEquals(300.0, HealthRules.maxOverheal(SOLDIER_HEALTH), 1e-9);
		assertEquals(260.0, HealthRules.maxOverheal(175.0), 1e-9);
		assertEquals(185.0, HealthRules.maxOverheal(125.0), 1e-9);
		assertEquals(100.0, HealthRules.overhealCapacity(SOLDIER_HEALTH), 1e-9);
	}

	@Test
	void fullOverhealDecaysInFifteenSeconds() {
		double perSecond = HealthRules.overhealDecayPerSecond(SOLDIER_HEALTH);

		assertEquals(HealthRules.overhealCapacity(SOLDIER_HEALTH), perSecond * HealthRules.OVERHEAL_DECAY_SECONDS, 1e-9);
	}
}

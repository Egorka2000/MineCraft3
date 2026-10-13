package io.github.egorka2000.tf2mc.physics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.egorka2000.tf2mc.physics.ExplosionMath.Vector;
import org.junit.jupiter.api.Test;

class ExplosionMathTest {
	private static final double ROCKET_DAMAGE = 90.0;
	private static final double SELF_RADIUS = 121.0;

	/** Отношение объёма эталонного игрока к объёму халла стоящего игрока. */
	private static final double STANDING_RATIO = 48.0 * 48.0 * 82.0 / (49.0 * 49.0 * 83.0);

	@Test
	void splashIsFullInCentreAndHalfAtEdge() {
		assertEquals(ROCKET_DAMAGE, ExplosionMath.splashDamage(ROCKET_DAMAGE, 0.0, SELF_RADIUS), 1e-9);
		assertEquals(ROCKET_DAMAGE * 0.5, ExplosionMath.splashDamage(ROCKET_DAMAGE, SELF_RADIUS, SELF_RADIUS), 1e-9);
		assertEquals(ROCKET_DAMAGE * 0.75, ExplosionMath.splashDamage(ROCKET_DAMAGE, SELF_RADIUS / 2.0, SELF_RADIUS), 1e-9);
	}

	@Test
	void noSplashOutsideRadius() {
		assertEquals(0.0, ExplosionMath.splashDamage(ROCKET_DAMAGE, SELF_RADIUS + 0.001, SELF_RADIUS), 1e-9);
		assertEquals(0.0, ExplosionMath.splashDamage(ROCKET_DAMAGE, 10.0, 0.0), 1e-9);
	}

	@Test
	void rangeMultiplierMatchesVerifiedPoints() {
		// docs/05-sources.md: 125% в упор, 100% на 512 u, 52,8% на 1024 u и дальше.
		assertEquals(1.25, ExplosionMath.rangeMultiplier(0.0), 1e-9);
		assertEquals(1.0, ExplosionMath.rangeMultiplier(512.0), 1e-9);
		assertEquals(0.528, ExplosionMath.rangeMultiplier(1024.0), 1e-9);
		assertEquals(0.528, ExplosionMath.rangeMultiplier(5000.0), 1e-9);
		assertEquals(1.25, ExplosionMath.rangeMultiplier(-10.0), 1e-9);
	}

	@Test
	void rangeMultiplierIsLinearBetweenPoints() {
		assertEquals(1.125, ExplosionMath.rangeMultiplier(256.0), 1e-9);
		assertEquals(0.764, ExplosionMath.rangeMultiplier(768.0), 1e-9);
	}

	@Test
	void rangeMultiplierNeverGrowsWithDistance() {
		double previous = ExplosionMath.rangeMultiplier(0.0);

		for (double distance = 16.0; distance <= 1200.0; distance += 16.0) {
			double current = ExplosionMath.rangeMultiplier(distance);
			assertTrue(current <= previous + 1e-12, "множитель вырос на расстоянии " + distance);
			previous = current;
		}
	}

	@Test
	void pushGrowsWithDamageAndScale() {
		double base = ExplosionMath.pushSpeed(40.0, ExplosionMath.SCALE_OTHER, ExplosionMath.HULL_HEIGHT);

		assertEquals(STANDING_RATIO * 40.0 * 6.0, base, 1e-9);
		assertEquals(base * 2.0, ExplosionMath.pushSpeed(80.0, ExplosionMath.SCALE_OTHER, ExplosionMath.HULL_HEIGHT), 1e-9);
		assertEquals(0.0, ExplosionMath.pushSpeed(0.0, ExplosionMath.SCALE_OTHER, ExplosionMath.HULL_HEIGHT), 1e-9);
	}

	@Test
	void pushIsCappedAtMaximum() {
		assertEquals(ExplosionMath.MAX_PUSH, ExplosionMath.pushSpeed(500.0, ExplosionMath.SCALE_SELF_AIRBORNE, ExplosionMath.HULL_HEIGHT), 1e-9);
	}

	@Test
	void airborneSelfPushIsTwiceGrounded() {
		Vector blast = new Vector(0.0, 0.0, 0.0);
		Vector centre = new Vector(0.0, 0.0, 40.0);
		double grounded = ExplosionMath.selfPush(blast, centre, 50.0, false, false).length();
		double airborne = ExplosionMath.selfPush(blast, centre, 50.0, true, false).length();

		assertEquals(grounded * 2.0, airborne, 1e-9);
	}

	@Test
	void crouchingMakesSelfPushStronger() {
		Vector blast = new Vector(0.0, 0.0, 0.0);
		Vector centre = new Vector(0.0, 0.0, 30.0);
		double standing = ExplosionMath.selfPush(blast, centre, 40.0, true, false).length();
		double crouching = ExplosionMath.selfPush(blast, centre, 40.0, true, true).length();

		assertEquals(standing * ExplosionMath.HULL_HEIGHT / ExplosionMath.HULL_HEIGHT_CROUCHED_SELF, crouching, 1e-9);
	}

	@Test
	void pushPointsAwayFromBlast() {
		// Взрыв сзади и снизу: толчок вперёд и вверх.
		Vector push = ExplosionMath.selfPush(new Vector(-50.0, 0.0, 0.0), new Vector(0.0, 0.0, 40.0), 50.0, true, false);

		assertTrue(push.x() > 0.0, "толчок направлен к взрыву");
		assertEquals(0.0, push.y(), 1e-9);
		assertTrue(push.z() > 0.0, "толчок направлен вниз");
	}

	@Test
	void loweredBlastOriginAddsLift() {
		// Взрыв на высоте центра игрока: без опускания точки взрыва толчок был бы строго горизонтальным.
		Vector direction = ExplosionMath.pushDirection(new Vector(-100.0, 0.0, 40.0), new Vector(0.0, 0.0, 40.0));

		assertEquals(1.0, direction.length(), 1e-9);
		assertEquals(ExplosionMath.BLAST_ORIGIN_DROP / Math.hypot(100.0, ExplosionMath.BLAST_ORIGIN_DROP), direction.z(), 1e-9);
	}

	@Test
	void blastUnderFeetPushesStraightUp() {
		Vector push = ExplosionMath.selfPush(new Vector(0.0, 0.0, 0.0), new Vector(0.0, 0.0, 41.5), 60.0, true, false);

		assertEquals(0.0, push.x(), 1e-9);
		assertEquals(0.0, push.y(), 1e-9);
		assertEquals(STANDING_RATIO * 60.0 * 10.0, push.z(), 1e-9);
	}

	@Test
	void otherPushUsesItsOwnScale() {
		Vector blast = new Vector(0.0, 0.0, 0.0);
		Vector centre = new Vector(0.0, 0.0, 40.0);

		assertEquals(STANDING_RATIO * 50.0 * 6.0, ExplosionMath.otherPush(blast, centre, 50.0).length(), 1e-9);
	}

	@Test
	void selfDamageIsReducedOnlyInAir() {
		assertEquals(50.0, ExplosionMath.selfDamage(50.0, false), 1e-9);
		assertEquals(30.0, ExplosionMath.selfDamage(50.0, true), 1e-9);
	}
}

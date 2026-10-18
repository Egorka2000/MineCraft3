package io.github.egorka2000.tf2mc.physics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class PelletSpreadTest {
	private static final double SPREAD = 0.0675;

	@Test
	void firstPelletGoesToCrosshair() {
		double[][] pellets = PelletSpread.offsets(10, SPREAD, new Random(1)::nextDouble);

		assertEquals(10, pellets.length);
		assertArrayEquals(new double[] {0.0, 0.0}, pellets[0], 0.0);
	}

	@Test
	void pelletsStayWithinSpread() {
		Random random = new Random(2);

		for (int shot = 0; shot < 1000; shot++) {
			for (double[] pellet : PelletSpread.offsets(10, SPREAD, random::nextDouble)) {
				assertTrue(Math.abs(pellet[0]) <= SPREAD && Math.abs(pellet[1]) <= SPREAD, "дробина вне разброса");
			}
		}
	}

	@Test
	void extremesGiveFullSpread() {
		// Обе случайные величины на краю: отклонение равно разбросу.
		assertArrayEquals(new double[] {SPREAD, SPREAD}, PelletSpread.offsets(2, SPREAD, () -> 1.0)[1], 1e-12);
		assertArrayEquals(new double[] {-SPREAD, -SPREAD}, PelletSpread.offsets(2, SPREAD, () -> 0.0)[1], 1e-12);
	}
}

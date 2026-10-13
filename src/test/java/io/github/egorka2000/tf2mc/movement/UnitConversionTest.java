package io.github.egorka2000.tf2mc.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.egorka2000.tf2mc.physics.MoveInput;
import io.github.egorka2000.tf2mc.physics.MoveState;
import org.junit.jupiter.api.Test;

class UnitConversionTest {
	private static final double SCALE = 45.0;
	private static final double EPSILON = 1e-9;

	@Test
	void speedConversionUsesTicksAndScale() {
		// 300 u/s при 45 u/блок — это 6,667 блока в секунду, или треть блока за тик.
		assertEquals(1.0 / 3.0, UnitConversion.toBlocksPerTick(300.0, SCALE), EPSILON);
		assertEquals(300.0, UnitConversion.toUnitsPerSecond(1.0 / 3.0, SCALE), EPSILON);
	}

	@Test
	void speedConversionRoundTrips() {
		for (double speed : new double[] {-3500.0, -1.5, 0.0, 30.0, 289.0, 1100.0}) {
			assertEquals(speed, UnitConversion.toUnitsPerSecond(UnitConversion.toBlocksPerTick(speed, SCALE), SCALE), EPSILON);
		}
	}

	@Test
	void distanceConversionUsesScale() {
		assertEquals(52.2, UnitConversion.toUnits(1.16, SCALE), EPSILON);
		// Радиус взрыва ракеты 146 u — это 3,24 блока.
		assertEquals(146.0 / 45.0, UnitConversion.toBlocks(146.0, SCALE), EPSILON);
		assertEquals(146.0, UnitConversion.toUnits(UnitConversion.toBlocks(146.0, SCALE), SCALE), EPSILON);
	}

	@Test
	void moveStateSwapsVerticalAxis() {
		// В Minecraft вверх направлена y, в TF2 — z.
		MoveState state = UnitConversion.toMoveState(0.1, 0.2, 0.3, false, SCALE);

		assertEquals(90.0, state.vx(), EPSILON);
		assertEquals(270.0, state.vy(), EPSILON);
		assertEquals(180.0, state.vz(), EPSILON);
		assertFalse(state.onGround());
		assertTrue(UnitConversion.toMoveState(0.0, 0.0, 0.0, true, SCALE).onGround());
	}

	@Test
	void forwardAtZeroYawPointsAlongPositiveZ() {
		// x TF2 — это x Minecraft, y TF2 — это z Minecraft. При угле 0 игрок смотрит вдоль +z.
		MoveInput input = UnitConversion.toMoveInput(0.0, 1.0, 0.0, false);

		assertEquals(0.0, input.wishX(), EPSILON);
		assertEquals(1.0, input.wishY(), EPSILON);
	}

	@Test
	void strafeLeftAtZeroYawPointsAlongPositiveX() {
		MoveInput input = UnitConversion.toMoveInput(1.0, 0.0, 0.0, false);

		assertEquals(1.0, input.wishX(), EPSILON);
		assertEquals(0.0, input.wishY(), EPSILON);
	}

	@Test
	void forwardAtNinetyDegreesPointsAlongNegativeX() {
		// Поворот на 90 градусов в Minecraft — это взгляд на запад, вдоль -x.
		MoveInput input = UnitConversion.toMoveInput(0.0, 1.0, 90.0, false);

		assertEquals(-1.0, input.wishX(), EPSILON);
		assertEquals(0.0, input.wishY(), EPSILON);
	}

	@Test
	void rotationKeepsInputLength() {
		MoveInput input = UnitConversion.toMoveInput(0.3, -0.4, 137.0, true);

		assertEquals(0.5, Math.hypot(input.wishX(), input.wishY()), EPSILON);
		assertTrue(input.jump());
	}
}

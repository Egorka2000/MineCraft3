package io.github.egorka2000.tf2mc.physics;

import java.util.function.DoubleSupplier;

/**
 * Разброс дроби, как в TF2 со случайным разбросом (обычные матчи): первая дробина летит точно в
 * прицел, у остальных отклонение вправо и вверх — сумма двух случайных чисел от −0,5 до 0,5,
 * умноженная на разброс оружия. Направление дробины — вперёд + x·вправо + y·вверх.
 *
 * <p>Правило — по открытому Source SDK 2013 ({@code tf_fx_shared.cpp}); код написан заново. Что
 * первая дробина летит в прицел, подтверждает и TF2 Wiki (страница Shotgun).
 */
public final class PelletSpread {
	private PelletSpread() {
	}

	/**
	 * Отклонения дробин одного выстрела.
	 *
	 * @param spread разброс оружия, у дробовика 0,0675
	 * @param random случайные числа от 0 до 1
	 * @return по паре (x, y) на дробину; первая — (0, 0)
	 */
	public static double[][] offsets(int pellets, double spread, DoubleSupplier random) {
		double[][] out = new double[pellets][2];

		for (int i = 1; i < pellets; i++) {
			out[i][0] = (random.getAsDouble() - 0.5 + random.getAsDouble() - 0.5) * spread;
			out[i][1] = (random.getAsDouble() - 0.5 + random.getAsDouble() - 0.5) * spread;
		}

		return out;
	}
}

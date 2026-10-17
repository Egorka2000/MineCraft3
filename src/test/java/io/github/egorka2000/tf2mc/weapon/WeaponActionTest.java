package io.github.egorka2000.tf2mc.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class WeaponActionTest {
	private static final Magazine.Rules RULES = Magazine.Rules.ROCKET_LAUNCHER;

	/** События перезарядки за {@code ticks} тиков в руках. */
	private static List<WeaponAction> reloadEvents(Magazine start, int ticks) {
		List<WeaponAction> events = new ArrayList<>();
		Magazine state = start;

		for (int i = 0; i < ticks; i++) {
			Magazine next = state.tick(RULES);
			WeaponAction action = WeaponAction.reload(state, next);

			if (action != null) {
				events.add(action);
			}

			state = next;
		}

		return events;
	}

	@Test
	void twoRocketsGiveStartLoopEnd() {
		Magazine afterTwoShots = new Magazine(2, 20, 0, 0, 0);

		assertEquals(List.of(WeaponAction.RELOAD_START, WeaponAction.RELOAD_LOOP, WeaponAction.RELOAD_END), reloadEvents(afterTwoShots, 60));
	}

	@Test
	void fullMagazineGivesNothing() {
		assertEquals(List.of(), reloadEvents(RULES.full(), 60));
	}

	@Test
	void shotInterruptsWithoutEnd() {
		Magazine reloading = new Magazine(2, 20, 0, 5, 0);
		Magazine fired = reloading.fire(RULES);

		// Выстрел — свой жест; конца перезарядки он не даёт, а пауза после него ничего не начинает.
		assertEquals(List.of(), reloadEvents(fired, RULES.fireInterval()));
	}

	@Test
	void holsteringEndsReload() {
		Magazine reloading = new Magazine(2, 20, 0, 5, 1);

		assertEquals(WeaponAction.RELOAD_END, WeaponAction.reload(reloading, reloading.holsteredTick()));
	}
}

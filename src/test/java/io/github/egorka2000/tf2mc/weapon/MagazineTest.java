package io.github.egorka2000.tf2mc.weapon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MagazineTest {
	private static final Magazine.Rules RULES = Magazine.Rules.ROCKET_LAUNCHER;

	private static Magazine ticks(Magazine magazine, int count) {
		Magazine state = magazine;

		for (int i = 0; i < count; i++) {
			state = state.tick(RULES);
		}

		return state;
	}

	@Test
	void rocketLauncherMatchesVerifiedNumbers() {
		// docs/05-sources.md: обойма 4, запас 20, выстрел раз в 0,8 с, перезарядка 0,92 с и 0,8 с.
		assertEquals(4, RULES.clipSize());
		assertEquals(20, RULES.reserveSize());
		assertEquals(16, RULES.fireInterval());
		assertEquals(18, RULES.firstReloadTicks());
		assertEquals(16, RULES.nextReloadTicks());
	}

	@Test
	void fullMagazineStaysFull() {
		assertEquals(RULES.full(), ticks(RULES.full(), 100));
	}

	@Test
	void fireSpendsRoundAndStartsCooldown() {
		Magazine after = RULES.full().fire(RULES);

		assertEquals(3, after.clip());
		assertEquals(20, after.reserve());
		assertFalse(after.canFire());
		// Пауза длится ровно интервал выстрела.
		assertFalse(ticks(after, RULES.fireInterval() - 1).canFire());
		assertTrue(ticks(after, RULES.fireInterval()).canFire());
	}

	@Test
	void fourShotsEmptyTheClip() {
		Magazine state = RULES.full();

		for (int shot = 0; shot < 4; shot++) {
			state = ticks(state.fire(RULES), RULES.fireInterval());
		}

		// Между выстрелами перезарядка не успевает зарядить ни одной ракеты.
		assertEquals(0, state.clip());
		assertEquals(20, state.reserve());
		assertFalse(state.canFire());
	}

	@Test
	void firingEmptyClipIsRejected() {
		Magazine empty = new Magazine(0, 20, 0, 0, 0);

		assertThrows(IllegalStateException.class, () -> empty.fire(RULES));
	}

	@Test
	void firstRoundReloadsSlowerThanTheRest() {
		Magazine empty = new Magazine(0, 20, 0, 0, 0);

		assertEquals(0, ticks(empty, RULES.firstReloadTicks() - 1).clip());
		assertEquals(1, ticks(empty, RULES.firstReloadTicks()).clip());
		assertEquals(1, ticks(empty, RULES.firstReloadTicks() + RULES.nextReloadTicks() - 1).clip());
		assertEquals(2, ticks(empty, RULES.firstReloadTicks() + RULES.nextReloadTicks()).clip());
	}

	@Test
	void fullReloadTakesRoundsFromReserve() {
		Magazine empty = new Magazine(0, 20, 0, 0, 0);
		Magazine reloaded = ticks(empty, RULES.firstReloadTicks() + 3 * RULES.nextReloadTicks());

		assertEquals(4, reloaded.clip());
		assertEquals(16, reloaded.reserve());
		// Полная обойма больше не заряжается.
		assertEquals(reloaded.clip(), ticks(reloaded, 100).clip());
		assertEquals(16, ticks(reloaded, 100).reserve());
	}

	@Test
	void reloadStopsWhenReserveIsEmpty() {
		Magazine low = new Magazine(0, 2, 0, 0, 0);
		Magazine after = ticks(low, 200);

		assertEquals(2, after.clip());
		assertEquals(0, after.reserve());
	}

	@Test
	void shotInterruptsReloadAndNextOneIsFirstAgain() {
		// Одна ракета в обойме, вторая почти заряжена.
		Magazine reloading = ticks(new Magazine(1, 20, 0, 0, 0), RULES.firstReloadTicks() - 1);
		assertEquals(1, reloading.clip());

		Magazine fired = reloading.fire(RULES);
		assertEquals(0, fired.clip());
		assertEquals(20, fired.reserve());

		// После паузы заряжание начинается заново и длится как для первой ракеты.
		Magazine afterCooldown = ticks(fired, RULES.fireInterval());
		assertEquals(0, ticks(afterCooldown, RULES.firstReloadTicks() - 1).clip());
		assertEquals(1, ticks(afterCooldown, RULES.firstReloadTicks()).clip());
	}

	@Test
	void holsteredWeaponDoesNotReloadAndRestartsFromFirstRound() {
		// Пустая обойма, первая ракета почти заряжена.
		Magazine reloading = ticks(new Magazine(0, 20, 0, 0, 0), RULES.firstReloadTicks() - 1);
		assertEquals(0, reloading.clip());

		// Оружие убрано: сколько ни жди, ракет не прибавляется, прогресс сбрасывается.
		Magazine holstered = reloading;
		for (int i = 0; i < 200; i++) {
			holstered = holstered.holsteredTick();
		}
		assertEquals(new Magazine(0, 20, 0, 0, 0), holstered);

		// В руках снова: первая ракета опять заряжается полный срок.
		assertEquals(0, ticks(holstered, RULES.firstReloadTicks() - 1).clip());
		assertEquals(1, ticks(holstered, RULES.firstReloadTicks()).clip());
	}

	@Test
	void cooldownRunsWhileHolstered() {
		Magazine fired = RULES.full().fire(RULES);
		Magazine holstered = fired;

		for (int i = 0; i < RULES.fireInterval(); i++) {
			holstered = holstered.holsteredTick();
		}

		assertTrue(holstered.canFire(), "пауза после выстрела не прошла, пока оружие убрано");
	}

	@Test
	void reserveIsClampedToRules() {
		Magazine state = new Magazine(4, 10, 0, 0, 0);

		assertEquals(20, state.addReserve(500, RULES).reserve());
		assertEquals(0, state.addReserve(-500, RULES).reserve());
		assertEquals(7, state.withReserve(7, RULES).reserve());
	}

	@Test
	void negativeStateIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new Magazine(-1, 0, 0, 0, 0));
	}
}

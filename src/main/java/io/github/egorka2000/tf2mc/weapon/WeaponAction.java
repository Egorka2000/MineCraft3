package io.github.egorka2000.tf2mc.weapon;

/**
 * Событие оружия, которое видят другие игроки: по нему клиент проигрывает жест персонажа TF2.
 * Перезарядка у ракетницы идёт по одной ракете: начало, следующая ракета, конец.
 */
public enum WeaponAction {
	FIRE,
	RELOAD_START,
	RELOAD_LOOP,
	RELOAD_END;

	/**
	 * Событие перезарядки между двумя тиками.
	 *
	 * @return {@code null}, если перезарядка не началась, не перешла к следующему патрону и не
	 *         закончилась. Выстрел, прервавший перезарядку, конца не даёт: его жест — выстрел.
	 */
	public static WeaponAction reload(Magazine before, Magazine after) {
		if (before.reloadTicks() == 0 && after.reloadTicks() > 0) {
			return after.reloadedInRow() == 0 ? RELOAD_START : RELOAD_LOOP;
		}

		boolean wasReloading = before.reloadTicks() > 0 || before.reloadedInRow() > 0;
		boolean reloading = after.reloadTicks() > 0 || after.reloadedInRow() > 0;
		return wasReloading && !reloading ? RELOAD_END : null;
	}
}

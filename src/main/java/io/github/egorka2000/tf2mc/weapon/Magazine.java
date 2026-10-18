package io.github.egorka2000.tf2mc.weapon;

/**
 * Обойма и запас оружия с перезарядкой по одному патрону. Чистая логика без классов игры:
 * время считается в тиках, один вызов {@link #tick} — один тик сервера.
 *
 * <p>Перезарядка начинается сама, когда обойма неполная, запас не пуст и пауза после выстрела
 * прошла. Выстрел её прерывает: начатый патрон теряется, следующая перезарядка снова «первая».
 *
 * @param clip          патронов в обойме
 * @param reserve       патронов в запасе
 * @param fireCooldown  сколько тиков осталось до следующего выстрела
 * @param reloadTicks   сколько тиков осталось до конца заряжания текущего патрона; 0 — не заряжается
 * @param reloadedInRow сколько патронов заряжено подряд в этой перезарядке
 */
public record Magazine(int clip, int reserve, int fireCooldown, int reloadTicks, int reloadedInRow) {
	public Magazine {
		if (clip < 0 || reserve < 0 || fireCooldown < 0 || reloadTicks < 0 || reloadedInRow < 0) {
			throw new IllegalArgumentException("отрицательное значение в состоянии обоймы");
		}
	}

	/**
	 * Правила оружия.
	 *
	 * @param clipSize         размер обоймы
	 * @param reserveSize      наибольший запас
	 * @param fireInterval     пауза между выстрелами, тики
	 * @param firstReloadTicks время заряжания первого патрона, тики
	 * @param nextReloadTicks  время заряжания каждого следующего, тики
	 * @param deployTicks      сколько тиков оружие достаётся: до конца нельзя ни стрелять, ни заряжать
	 */
	public record Rules(int clipSize, int reserveSize, int fireInterval, int firstReloadTicks, int nextReloadTicks, int deployTicks) {
		/**
		 * Ракетница Soldier: обойма 4, запас 20, выстрел раз в 0,8 с, первая ракета заряжается
		 * 0,92 с, следующие по 0,8 с; достаётся, как всё оружие TF2, за 0,5 с. Источник — docs/05-sources.md.
		 */
		public static final Rules ROCKET_LAUNCHER = new Rules(4, 20, 16, 18, 16, 10);

		/** Полная обойма и полный запас. */
		public Magazine full() {
			return new Magazine(clipSize, reserveSize, 0, 0, 0);
		}
	}

	/** Можно ли выстрелить прямо сейчас. */
	public boolean canFire() {
		return clip > 0 && fireCooldown == 0;
	}

	/**
	 * Выстрел: тратит патрон, запускает паузу и прерывает перезарядку.
	 * Вызывать только после проверки {@link #canFire}.
	 */
	public Magazine fire(Rules rules) {
		if (!canFire()) {
			throw new IllegalStateException("выстрел невозможен: обойма пуста или не прошла пауза");
		}

		return new Magazine(clip - 1, reserve, rules.fireInterval(), 0, 0);
	}

	/** Один тик: идёт пауза после выстрела, затем перезарядка по одному патрону. */
	public Magazine tick(Rules rules) {
		if (fireCooldown > 0) {
			return new Magazine(clip, reserve, fireCooldown - 1, 0, 0);
		}

		if (clip >= rules.clipSize() || reserve == 0) {
			return reloadTicks == 0 && reloadedInRow == 0 ? this : new Magazine(clip, reserve, 0, 0, 0);
		}

		if (reloadTicks == 0) {
			int duration = reloadedInRow == 0 ? rules.firstReloadTicks() : rules.nextReloadTicks();
			return new Magazine(clip, reserve, 0, duration - 1, reloadedInRow);
		}

		if (reloadTicks > 1) {
			return new Magazine(clip, reserve, 0, reloadTicks - 1, reloadedInRow);
		}

		return new Magazine(clip + 1, reserve - 1, 0, 0, reloadedInRow + 1);
	}

	/**
	 * Один тик, когда оружие убрано. Перезарядка идёт только в руках: убранное оружие её
	 * прерывает, и после возвращения в руки она начинается заново, с первого патрона.
	 * Пауза после выстрела идёт как обычно.
	 */
	public Magazine holsteredTick() {
		if (fireCooldown == 0 && reloadTicks == 0 && reloadedInRow == 0) {
			return this;
		}

		return new Magazine(clip, reserve, Math.max(0, fireCooldown - 1), 0, 0);
	}

	/**
	 * Оружие взяли в руку: пока оно достаётся, нельзя ни стрелять, ни заряжать. Пауза после
	 * выстрела, если она дольше, сохраняется.
	 */
	public Magazine deploy(Rules rules) {
		return new Magazine(clip, reserve, Math.max(fireCooldown, rules.deployTicks()), 0, 0);
	}

	/** Запас изменён на заданную величину и обрезан границами от нуля до наибольшего. */
	public Magazine addReserve(int amount, Rules rules) {
		return withReserve(reserve + amount, rules);
	}

	/** Запас выставлен в заданное значение и обрезан границами от нуля до наибольшего. */
	public Magazine withReserve(int amount, Rules rules) {
		return new Magazine(clip, Math.clamp(amount, 0, rules.reserveSize()), fireCooldown, reloadTicks, reloadedInRow);
	}
}

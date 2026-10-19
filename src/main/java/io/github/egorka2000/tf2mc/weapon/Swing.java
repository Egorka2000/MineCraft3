package io.github.egorka2000.tf2mc.weapon;

/**
 * Удар оружием ближнего боя по тикам сервера: замах, через задержку — сам удар, пауза до
 * следующего замаха. Как в TF2, удар приходится не в момент нажатия, а через 0,2 с: цель
 * выбирается по тому, куда игрок смотрит в момент удара.
 *
 * <p>Чистая Java без игры: проверяется юнит-тестами.
 *
 * @param cooldown тиков до следующего замаха
 * @param hitIn    тиков до удара; 0 — удара не ждём
 */
public record Swing(int cooldown, int hitIn) {
	/** Готово к замаху. */
	public static final Swing READY = new Swing(0, 0);

	/**
	 * Числа оружия ближнего боя, в тиках.
	 *
	 * @param interval   от замаха до следующего замаха
	 * @param hitDelay   от замаха до удара
	 * @param deployTicks сколько оружие достаётся, когда его берут в руку
	 */
	public record Rules(int interval, int hitDelay, int deployTicks) {
		/** Лопата: удар раз в 0,8 с, через 0,2 с после замаха; достаётся 0,5 с. Источник — docs/05-sources.md. */
		public static final Rules SHOVEL = new Rules(16, 4, 10);
	}

	/** Итог тика: новое состояние и пришёлся ли на этот тик удар. */
	public record Tick(Swing next, boolean hit) {
	}

	public boolean canSwing() {
		return cooldown == 0;
	}

	/** Замах: удар через задержку, следующий замах — через интервал. */
	public Swing swing(Rules rules) {
		return new Swing(rules.interval(), rules.hitDelay());
	}

	/** Оружие взяли в руку: оно достаётся, замахнуться нельзя. Ожидаемый удар отменяется. */
	public Swing deploy(Rules rules) {
		return new Swing(Math.max(cooldown, rules.deployTicks()), 0);
	}

	/** Оружие убрали из руки: удар, который ещё не пришёлся, отменяется. Пауза идёт дальше. */
	public Swing holster() {
		return new Swing(cooldown, 0);
	}

	/** Один тик: пауза и задержка удара уменьшаются; удар — когда задержка дошла до нуля. */
	public Tick tick() {
		Swing next = new Swing(Math.max(0, cooldown - 1), Math.max(0, hitIn - 1));
		return new Tick(next, hitIn == 1);
	}
}

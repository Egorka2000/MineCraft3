package io.github.egorka2000.tf2mc.client.model;

import java.util.function.ToDoubleFunction;

import io.github.egorka2000.tf2mc.weapon.WeaponAction;

/**
 * Анимация рук своего игрока от первого лица, как у ракетницы в TF2: достать оружие, ожидание,
 * выстрел, перезарядка (начало, по ракете, конец). События приходят с сервера
 * ({@link #onWeaponAction}); оружие достаётся, когда ракетница оказывается в руке.
 *
 * <p>Время — в секундах игры своего игрока, с долями тика.
 */
public final class ViewModels {
	static final String IDLE = "dh_idle";
	static final String DRAW = "dh_draw";
	static final String FIRE = "dh_fire";
	static final String RELOAD_START = "dh_reload_start";
	static final String RELOAD_LOOP = "dh_reload_loop";
	static final String RELOAD_FINISH = "dh_reload_finish";

	/** Последовательность рук и доля её проигрывания. */
	public record Frame(String sequence, float cycle) {
	}

	private static WeaponAction action;
	private static float actionAt;
	private static boolean drawing;
	private static float drawAt;
	private static boolean held;

	private ViewModels() {
	}

	/** Сервер прислал событие оружия своего игрока. */
	public static void onWeaponAction(WeaponAction event, float now) {
		action = event;
		actionAt = now;

		// Выстрел прерывает доставание; перезарядка ждёт его конца (см. choose).
		if (event == WeaponAction.FIRE) {
			drawing = false;
		}
	}

	/**
	 * Кадр рук сейчас. Взятая в руку ракетница сначала достаётся, затем играет последнее событие
	 * оружия, а без события — ожидание.
	 *
	 * @param holding  в руке ракетница
	 * @param now      время, секунды
	 * @param duration длительность последовательности по имени, секунды
	 */
	public static Frame frame(boolean holding, float now, ToDoubleFunction<String> duration) {
		if (holding && !held) {
			drawing = true;
			drawAt = now;
			action = null;
		}

		held = holding;
		return choose(action, now - actionAt, drawing, now - drawAt, now, duration);
	}

	/**
	 * Выбор кадра без состояния: что играть при последнем событии и прошедшем с него времени.
	 *
	 * <p>Сервер начинает перезарядку в тот же тик, когда ракетница снова оказалась в руке, — если
	 * её прервали, убрав оружие. Тогда доставание не успело бы проиграться: руки сразу прыгнули бы
	 * в позу перезарядки. Поэтому событие перезарядки, пришедшее во время доставания, играется с
	 * его конца, как в TF2, где перезарядка ждёт, пока оружие достанут.
	 *
	 * @param drawing    ракетницу доставали после последнего выстрела
	 * @param sinceDraw  сколько секунд прошло с начала доставания
	 */
	static Frame choose(WeaponAction action, float sinceAction, boolean drawing, float sinceDraw, float now, ToDoubleFunction<String> duration) {
		float draw = (float) duration.applyAsDouble(DRAW);

		if (drawing && sinceDraw < draw) {
			return once(DRAW, sinceDraw, duration);
		}

		if (action != null) {
			float since = drawing ? Math.min(sinceAction, sinceDraw - draw) : sinceAction;

			switch (action) {
				case FIRE -> {
					if (since < duration.applyAsDouble(FIRE)) {
						return once(FIRE, since, duration);
					}
				}
				case RELOAD_START -> {
					// Начало короче заряжания первой ракеты: дальше руки продолжают как с любой ракетой.
					float start = (float) duration.applyAsDouble(RELOAD_START);
					return since < start ? once(RELOAD_START, since, duration) : loop(RELOAD_LOOP, since - start, duration);
				}
				case RELOAD_LOOP -> {
					return loop(RELOAD_LOOP, since, duration);
				}
				case RELOAD_END -> {
					if (since < duration.applyAsDouble(RELOAD_FINISH)) {
						return once(RELOAD_FINISH, since, duration);
					}
				}
				default -> {
				}
			}
		}

		return loop(IDLE, now, duration);
	}

	private static Frame once(String sequence, float time, ToDoubleFunction<String> duration) {
		return new Frame(sequence, Math.clamp(time / (float) duration.applyAsDouble(sequence), 0.0F, 1.0F));
	}

	private static Frame loop(String sequence, float time, ToDoubleFunction<String> duration) {
		float position = time / (float) duration.applyAsDouble(sequence);
		return new Frame(sequence, position - (float) Math.floor(position));
	}
}

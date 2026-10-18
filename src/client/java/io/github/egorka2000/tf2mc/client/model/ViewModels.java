package io.github.egorka2000.tf2mc.client.model;

import java.util.function.ToDoubleFunction;

import io.github.egorka2000.tf2mc.weapon.WeaponAction;

/**
 * Анимация рук своего игрока от первого лица, как у оружия с обоймой в TF2: достать оружие,
 * ожидание, выстрел, перезарядка (начало, по патрону, конец). Имена анимаций — у оружия в руке
 * ({@link WeaponView}). События приходят с сервера ({@link #onWeaponAction}); оружие достаётся,
 * когда его берут в руку, в том числе вместо другого оружия.
 *
 * <p>Время — в секундах игры своего игрока, с долями тика.
 */
public final class ViewModels {
	/** Последовательность рук и доля её проигрывания. */
	public record Frame(String sequence, float cycle) {
	}

	private static WeaponAction action;
	private static float actionAt;
	private static boolean drawing;
	private static float drawAt;
	private static WeaponView held;

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
	 * Кадр рук сейчас. Взятое в руку оружие сначала достаётся, затем играет последнее событие
	 * оружия, а без события — ожидание.
	 *
	 * @param view     оружие в руке; {@code null} — не оружие мода, кадра нет
	 * @param now      время, секунды
	 * @param duration длительность последовательности по имени, секунды
	 * @return {@code null}, если в руке не оружие мода
	 */
	public static Frame frame(WeaponView view, float now, ToDoubleFunction<String> duration) {
		if (view != null && view != held) {
			drawing = true;
			drawAt = now;
			action = null;
		}

		held = view;
		return view == null ? null : choose(view, action, now - actionAt, drawing, now - drawAt, now, duration);
	}

	/**
	 * Выбор кадра без состояния: что играть при последнем событии и прошедшем с него времени.
	 *
	 * <p>Сервер не заряжает и не стреляет, пока оружие достаётся (0,5 с), а анимация доставания
	 * бывает длиннее. Событие перезарядки, пришедшее во время доставания, играется с его конца,
	 * как в TF2: иначе руки сразу прыгнули бы в позу перезарядки.
	 *
	 * @param drawing   оружие доставали после последнего выстрела
	 * @param sinceDraw сколько секунд прошло с начала доставания
	 */
	static Frame choose(WeaponView view, WeaponAction action, float sinceAction, boolean drawing, float sinceDraw, float now,
			ToDoubleFunction<String> duration) {
		float draw = (float) duration.applyAsDouble(view.draw());

		if (drawing && sinceDraw < draw) {
			return once(view.draw(), sinceDraw, duration);
		}

		if (action != null) {
			float since = drawing ? Math.min(sinceAction, sinceDraw - draw) : sinceAction;

			switch (action) {
				case FIRE -> {
					if (since < duration.applyAsDouble(view.fire())) {
						return once(view.fire(), since, duration);
					}
				}
				case RELOAD_START -> {
					// Начало короче заряжания первого патрона: дальше руки продолжают как с любым патроном.
					float start = (float) duration.applyAsDouble(view.reloadStart());
					return since < start ? once(view.reloadStart(), since, duration) : loop(view.reloadLoop(), since - start, duration);
				}
				case RELOAD_LOOP -> {
					return loop(view.reloadLoop(), since, duration);
				}
				case RELOAD_END -> {
					if (since < duration.applyAsDouble(view.reloadFinish())) {
						return once(view.reloadFinish(), since, duration);
					}
				}
				default -> {
				}
			}
		}

		return loop(view.idle(), now, duration);
	}

	private static Frame once(String sequence, float time, ToDoubleFunction<String> duration) {
		return new Frame(sequence, Math.clamp(time / (float) duration.applyAsDouble(sequence), 0.0F, 1.0F));
	}

	private static Frame loop(String sequence, float time, ToDoubleFunction<String> duration) {
		float position = time / (float) duration.applyAsDouble(sequence);
		return new Frame(sequence, position - (float) Math.floor(position));
	}
}

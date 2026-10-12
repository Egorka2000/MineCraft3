package io.github.egorka2000.tf2mc.movement;

/**
 * Решает, считать ли нажатую клавишу прыжка новым прыжком.
 *
 * <p>В TF2 на каждый прыжок нужно новое нажатие: пока клавиша удерживается с прошлого прыжка,
 * игрок не прыгает. Здесь то же правило с небольшим запасом: нажатие засчитывается, если клавишу
 * зажали не раньше чем за {@code bufferTicks} тиков до приземления. Без запаса при 20 тиках
 * в секунду пришлось бы попадать нажатием в окно в 50 мс.
 */
public final class JumpGate {
	private final int bufferTicks;

	/** Сколько тиков подряд клавиша была зажата до текущего тика. */
	private int heldTicks;
	/** Прыжок по текущему удержанию уже был: до отпускания клавиши новых не будет. */
	private boolean consumed;

	/**
	 * @param bufferTicks за сколько тиков до приземления можно зажать клавишу; 0 — только в тик приземления
	 */
	public JumpGate(int bufferTicks) {
		if (bufferTicks < 0) {
			throw new IllegalArgumentException("запас не может быть отрицательным: " + bufferTicks);
		}

		this.bufferTicks = bufferTicks;
	}

	/** Вызывается в начале каждого тика: была ли клавиша зажата в прошлом тике. */
	public void observePreviousTick(boolean wasHeld) {
		if (wasHeld) {
			heldTicks++;
		} else {
			heldTicks = 0;
			consumed = false;
		}
	}

	/**
	 * Игра хочет выполнить прыжок: игрок на земле и клавиша сейчас зажата.
	 *
	 * @return true, если это новый прыжок
	 */
	public boolean acceptPress() {
		if (consumed || heldTicks > bufferTicks) {
			return false;
		}

		consumed = true;
		return true;
	}
}

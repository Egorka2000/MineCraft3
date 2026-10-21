package io.github.egorka2000.tf2mc.movement;

/**
 * Считает прыжки в воздухе (двойной прыжок Scout) за один полёт.
 *
 * <p>Как в TF2: прыжок в воздухе — только по новому нажатию клавиши (удержание с прыжка с земли
 * не считается), не больше, чем положено классу, и счётчик сбрасывается, когда игрок встал на
 * землю. Источник правил — docs/05-sources.md (SDK).
 */
public final class AirJumps {
	private int used;

	/**
	 * Вызывается каждый тик движения.
	 *
	 * @param onGround   стоит ли игрок на земле в начале тика
	 * @param pressed    зажата ли клавиша прыжка в этом тике
	 * @param wasHeld    была ли она зажата в прошлом тике
	 * @param allowed    сколько прыжков в воздухе положено классу
	 * @return прыгнуть ли в воздухе в этом тике
	 */
	public boolean tick(boolean onGround, boolean pressed, boolean wasHeld, int allowed) {
		if (onGround) {
			used = 0;
			return false;
		}

		if (!pressed || wasHeld || used >= allowed) {
			return false;
		}

		used++;
		return true;
	}
}

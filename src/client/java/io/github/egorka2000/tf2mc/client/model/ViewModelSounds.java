package io.github.egorka2000.tf2mc.client.model;

import java.util.List;

import io.github.egorka2000.tf2mc.sourcemodel.AnimationSet;

/**
 * Звуки рук от первого лица: TF2 играет их событиями внутри анимаций рук, например помпу
 * дробовика после выстрела и в конце перезарядки. Здесь — какие события анимация прошла с
 * прошлого кадра; играет их {@link ViewModelRenderer}.
 */
public final class ViewModelSounds {
	private String sequence;
	private float cycle;

	/**
	 * Звуковые события, пройденные с прошлого кадра. Новая последовательность звучит с начала;
	 * у повторяющейся при переходе через конец звучат и хвост, и начало; неповторяющаяся, начатая
	 * заново (второй выстрел), звучит с начала.
	 *
	 * @param current последовательность в этом кадре; {@code null} — руки не рисуются
	 * @param now     доля последовательности в этом кадре
	 */
	public List<AnimationSet.Event> advance(AnimationSet.Sequence current, float now) {
		if (current == null) {
			sequence = null;
			return List.of();
		}

		boolean same = current.name().equals(sequence);
		float from = cycle;
		sequence = current.name();
		cycle = now;

		if (!same) {
			return current.sounds().stream().filter(event -> event.cycle() <= now).toList();
		}

		if (now >= from) {
			return current.sounds().stream().filter(event -> event.cycle() > from && event.cycle() <= now).toList();
		}

		if (current.looping()) {
			return current.sounds().stream().filter(event -> event.cycle() > from || event.cycle() <= now).toList();
		}

		return current.sounds().stream().filter(event -> event.cycle() <= now).toList();
	}
}

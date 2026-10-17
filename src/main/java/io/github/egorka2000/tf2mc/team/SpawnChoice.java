package io.github.egorka2000.tf2mc.team;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.util.RandomSource;

/** Выбор точки возрождения без классов игры, чтобы его можно было проверить юнит-тестом. */
public final class SpawnChoice {
	private SpawnChoice() {
	}

	/**
	 * Случайная точка из свободных, и не та, на которой игрок появлялся в прошлый раз, если есть
	 * другие. Так подряд игрок появляется в разных местах, а не на одной точке, и не встаёт на
	 * голову другому игроку. Если свободных нет, выбор идёт из всех.
	 *
	 * @param occupied занята ли точка другим игроком
	 * @param last     где игрок появлялся в прошлый раз; {@code null} — нигде
	 */
	public static <T> Optional<T> choose(List<T> spawns, Predicate<T> occupied, T last, RandomSource random) {
		List<T> candidates = new ArrayList<>(spawns.stream().filter(occupied.negate()).toList());

		if (candidates.isEmpty()) {
			candidates.addAll(spawns);
		}

		if (candidates.size() > 1) {
			candidates.remove(last);
		}

		return candidates.isEmpty() ? Optional.empty() : Optional.of(candidates.get(random.nextInt(candidates.size())));
	}
}

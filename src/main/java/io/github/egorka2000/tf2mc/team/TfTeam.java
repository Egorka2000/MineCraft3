package io.github.egorka2000.tf2mc.team;

import java.util.Optional;

/**
 * Команда TF2. Классы игры не используются.
 */
public enum TfTeam {
	RED("red"),
	BLU("blu");

	/** Слово в командах, которое выводит игрока из команды в наблюдатели. Командой не является. */
	public static final String SPECTATOR_ID = "spectator";

	/** Слово в командах, которое выбирает команду с меньшим числом игроков. Командой не является. */
	public static final String RANDOM_ID = "random";

	private final String id;

	TfTeam(String id) {
		this.id = id;
	}

	/** Имя команды в командах игры. */
	public String id() {
		return id;
	}

	/** Противник. */
	public TfTeam opponent() {
		return this == RED ? BLU : RED;
	}

	/** Команда по имени. Неизвестное имя, «spectator» и «random» дают пустой результат. */
	public static Optional<TfTeam> byId(String id) {
		for (TfTeam team : values()) {
			if (team.id.equals(id)) {
				return Optional.of(team);
			}
		}

		return Optional.empty();
	}
}

package io.github.egorka2000.tf2mc.team;

/**
 * Выбор команды для {@code /tf2 team random}. Чистая логика без классов игры.
 */
public final class TeamBalance {
	private TeamBalance() {
	}

	/**
	 * Команда, в которой меньше игроков; при равенстве решает жребий.
	 *
	 * @param redCount сколько игроков в красной команде, не считая выбирающего
	 * @param bluCount сколько игроков в синей команде, не считая выбирающего
	 * @param coin     жребий на случай равенства: {@code true} — красные
	 */
	public static TfTeam pick(int redCount, int bluCount, boolean coin) {
		if (redCount != bluCount) {
			return redCount < bluCount ? TfTeam.RED : TfTeam.BLU;
		}

		return coin ? TfTeam.RED : TfTeam.BLU;
	}
}

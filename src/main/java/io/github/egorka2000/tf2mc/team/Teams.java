package io.github.egorka2000.tf2mc.team;

import java.util.Optional;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

/**
 * Команды игроков. Хранятся как ванильные команды таблицы счёта: игра сама сохраняет их в мире,
 * рассылает клиентам, красит ники и не пропускает урон между своими.
 */
public final class Teams {
	private static final String SCOREBOARD_PREFIX = "tf2mc_";

	private Teams() {
	}

	/** Команда игрока; пусто, если он ни в одной из команд мода. Работает и на сервере, и на клиенте. */
	public static Optional<TfTeam> get(Player player) {
		PlayerTeam team = player.getTeam();

		if (team == null || !team.getName().startsWith(SCOREBOARD_PREFIX)) {
			return Optional.empty();
		}

		return TfTeam.byId(team.getName().substring(SCOREBOARD_PREFIX.length()));
	}

	/** Зачисляет игрока в команду. Наблюдателю возвращается режим игры, который был до наблюдения. */
	public static void join(ServerPlayer player, TfTeam team) {
		Scoreboard scoreboard = player.level().getServer().getScoreboard();

		scoreboard.addPlayerToTeam(player.getScoreboardName(), scoreboardTeam(scoreboard, team));

		if (player.gameMode() == GameType.SPECTATOR) {
			player.setGameMode(modeBeforeSpectating(player));
		}
	}

	/** Зачисляет игрока в команду, где меньше игроков; при равенстве — в случайную. */
	public static TfTeam joinRandom(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		TfTeam team = TeamBalance.pick(countOthers(server, player, TfTeam.RED), countOthers(server, player, TfTeam.BLU), player.getRandom().nextBoolean());

		join(player, team);
		return team;
	}

	/** Выводит игрока из команды и переводит в ванильный режим наблюдателя. */
	public static void spectate(ServerPlayer player) {
		Scoreboard scoreboard = player.level().getServer().getScoreboard();

		if (get(player).isPresent()) {
			scoreboard.removePlayerFromTeam(player.getScoreboardName());
		}

		player.setGameMode(GameType.SPECTATOR);
	}

	/** Сколько игроков, которые сейчас на сервере, состоит в команде, не считая указанного. */
	private static int countOthers(MinecraftServer server, ServerPlayer except, TfTeam team) {
		int count = 0;

		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other != except && get(other).orElse(null) == team) {
				count++;
			}
		}

		return count;
	}

	/** Режим игры до наблюдения. Игра хранит его сама; если его нет — режим мира по умолчанию. */
	private static GameType modeBeforeSpectating(ServerPlayer player) {
		GameType previous = player.gameMode.getPreviousGameModeForPlayer();

		if (previous == null || previous == GameType.SPECTATOR) {
			return player.level().getServer().getDefaultGameType();
		}

		return previous;
	}

	/** Команда таблицы счёта для команды мода; создаётся при первом обращении. */
	private static PlayerTeam scoreboardTeam(Scoreboard scoreboard, TfTeam team) {
		String name = SCOREBOARD_PREFIX + team.id();
		PlayerTeam existing = scoreboard.getPlayerTeam(name);

		if (existing != null) {
			return existing;
		}

		PlayerTeam created = scoreboard.addPlayerTeam(name);
		created.setAllowFriendlyFire(false);
		created.setColor(Optional.of(team == TfTeam.RED ? TeamColor.RED : TeamColor.BLUE));
		return created;
	}
}

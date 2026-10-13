package io.github.egorka2000.tf2mc.team;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

/**
 * Команды игроков. Хранятся как ванильные команды таблицы счёта: игра сама сохраняет их в мире,
 * рассылает клиентам, красит ники и не пропускает урон между своими.
 *
 * <p>Кроме красной и синей есть нейтральная команда: в неё попадает игрок, который вошёл в мир
 * и ещё не выбрал команду. Выбрать её нельзя. Нейтральный игрок в бою не участвует: не наносит
 * урон другим игрокам и не получает его от них.
 */
public final class Teams {
	private static final String SCOREBOARD_PREFIX = "tf2mc_";
	private static final String NEUTRAL_NAME = SCOREBOARD_PREFIX + "neutral";

	private Teams() {
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> placeOnJoin(handler.getPlayer()));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Teams::allowDamage);
	}

	/** Команда игрока; пусто, если он нейтральный или наблюдатель. Работает и на сервере, и на клиенте. */
	public static Optional<TfTeam> get(Player player) {
		PlayerTeam team = player.getTeam();

		if (team == null || !team.getName().startsWith(SCOREBOARD_PREFIX)) {
			return Optional.empty();
		}

		return TfTeam.byId(team.getName().substring(SCOREBOARD_PREFIX.length()));
	}

	/** Вошёл в мир и ещё не выбрал команду. */
	public static boolean isNeutral(Player player) {
		PlayerTeam team = player.getTeam();
		return team != null && team.getName().equals(NEUTRAL_NAME);
	}

	/**
	 * Могут ли два игрока ранить друг друга: не могут игроки одной команды и не может никто,
	 * если хотя бы один из них нейтральный. Оружие мода должно спрашивать именно здесь.
	 */
	public static boolean canFight(Player first, Player second) {
		if (isNeutral(first) || isNeutral(second)) {
			return false;
		}

		return first.canHarmPlayer(second) && second.canHarmPlayer(first);
	}

	/** Зачисляет игрока в команду. Наблюдателю возвращается режим игры, который был до наблюдения. */
	public static void join(ServerPlayer player, TfTeam team) {
		Scoreboard scoreboard = player.level().getServer().getScoreboard();

		// Игрок состоит только в одной команде таблицы счёта: из прежней игра убирает его сама.
		scoreboard.addPlayerToTeam(player.getScoreboardName(), scoreboardTeam(scoreboard, SCOREBOARD_PREFIX + team.id(), colorOf(team)));

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

		if (get(player).isPresent() || isNeutral(player)) {
			scoreboard.removePlayerFromTeam(player.getScoreboardName());
		}

		player.setGameMode(GameType.SPECTATOR);
	}

	/** Ники игроков команды, которые сейчас на сервере. */
	public static List<String> members(MinecraftServer server, TfTeam team) {
		List<String> names = new ArrayList<>();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (get(player).orElse(null) == team) {
				names.add(player.getScoreboardName());
			}
		}

		return names;
	}

	/** Ники нейтральных игроков, которые сейчас на сервере. */
	public static List<String> neutralMembers(MinecraftServer server) {
		List<String> names = new ArrayList<>();

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (isNeutral(player)) {
				names.add(player.getScoreboardName());
			}
		}

		return names;
	}

	/**
	 * При входе в мир игрок без команды становится нейтральным. Наблюдателя это не касается:
	 * он вышел из команды сам.
	 */
	private static void placeOnJoin(ServerPlayer player) {
		if (player.getTeam() != null || player.gameMode() == GameType.SPECTATOR) {
			return;
		}

		Scoreboard scoreboard = player.level().getServer().getScoreboard();
		scoreboard.addPlayerToTeam(player.getScoreboardName(), scoreboardTeam(scoreboard, NEUTRAL_NAME, TeamColor.GRAY));
	}

	/** Нейтральный игрок не ранит других игроков, и они не ранят его. Остальной урон не тронут. */
	private static boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
		if (!(target instanceof Player victim) || !(source.getEntity() instanceof Player attacker) || attacker == victim) {
			return true;
		}

		return !isNeutral(victim) && !isNeutral(attacker);
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

	private static TeamColor colorOf(TfTeam team) {
		return team == TfTeam.RED ? TeamColor.RED : TeamColor.BLUE;
	}

	/** Команда таблицы счёта с выключенным огнём по своим; создаётся при первом обращении. */
	private static PlayerTeam scoreboardTeam(Scoreboard scoreboard, String name, TeamColor color) {
		PlayerTeam existing = scoreboard.getPlayerTeam(name);

		if (existing != null) {
			return existing;
		}

		PlayerTeam created = scoreboard.addPlayerTeam(name);
		created.setAllowFriendlyFire(false);
		created.setColor(Optional.of(color));
		return created;
	}
}

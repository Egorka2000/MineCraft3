package io.github.egorka2000.tf2mc.clazz;

import java.util.Optional;

import com.mojang.serialization.Codec;
import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.team.Teams;
import io.github.egorka2000.tf2mc.team.TfTeam;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Отложенный выбор класса и команды. Как в TF2, класс и команда меняются не где угодно:
 * выбор, сделанный командой самого игрока, запоминается и применяется у шкафа пополнения своей
 * команды или при следующем возрождении.
 *
 * <p>Сразу применяется первый выбор: класс игроку без класса, команда нейтральному или
 * наблюдателю — иначе им не с чего начать. Оператор, указавший ник, тоже меняет сразу.
 */
public final class PendingChoice {
	/** Запомненный класс: его имя или {@code vanilla}. */
	public static final AttachmentType<String> CLASS = AttachmentRegistry.create(
			Tf2Mc.id("pending_class"),
			builder -> builder.persistent(Codec.STRING).copyOnDeath());

	/** Запомненная команда: её имя или {@code random}. */
	public static final AttachmentType<String> TEAM = AttachmentRegistry.create(
			Tf2Mc.id("pending_team"),
			builder -> builder.persistent(Codec.STRING).copyOnDeath());

	private PendingChoice() {
	}

	/**
	 * Вызывается при запуске мода: типы данных {@link #CLASS} и {@link #TEAM} должны быть известны
	 * Fabric до загрузки игроков, иначе запомненный выбор при загрузке выбрасывается.
	 */
	public static void register() {
		// Регистрацию делают статические поля; вызов загружает класс.
	}

	/**
	 * Выбор класса самим игроком.
	 *
	 * @param tfClass новый класс; {@code null} — ванильный игрок
	 * @return применён ли выбор сразу; {@code false} — запомнен
	 */
	public static boolean chooseClass(ServerPlayer player, TfClass tfClass) {
		Optional<TfClass> current = PlayerClasses.get(player);

		if (current.isEmpty() || current.equals(Optional.ofNullable(tfClass))) {
			player.removeAttached(CLASS);
			PlayerClasses.set(player, tfClass);
			return true;
		}

		player.setAttached(CLASS, tfClass == null ? TfClass.VANILLA_ID : tfClass.id());
		return false;
	}

	/**
	 * Выбор команды самим игроком.
	 *
	 * @param team новая команда; {@code null} — та, где меньше игроков, выбирается при применении
	 * @return применён ли выбор сразу; {@code false} — запомнен
	 */
	public static boolean chooseTeam(ServerPlayer player, TfTeam team) {
		Optional<TfTeam> current = Teams.get(player);

		if (current.isEmpty() || (team != null && current.get() == team)) {
			player.removeAttached(TEAM);

			if (team == null) {
				Teams.joinRandom(player);
			} else if (current.isEmpty()) {
				Teams.join(player, team);
			}

			return true;
		}

		player.setAttached(TEAM, team == null ? TfTeam.RANDOM_ID : team.id());
		return false;
	}

	/** Запомненный класс; пусто, если выбора нет. В значении {@code vanilla} — ванильный игрок. */
	public static Optional<String> pendingClass(Player player) {
		return Optional.ofNullable(player.getAttached(CLASS));
	}

	/** Запомненная команда; пусто, если выбора нет. */
	public static Optional<String> pendingTeam(Player player) {
		return Optional.ofNullable(player.getAttached(TEAM));
	}

	/**
	 * У шкафа пополнения: применяет запомненные команду и класс. Сменивший команду переносится
	 * на её точку возрождения.
	 *
	 * @return было ли что применять
	 */
	public static boolean applyNow(ServerPlayer player) {
		boolean changed = applyTeam(player, true);
		String classId = player.getAttached(CLASS);

		if (classId != null) {
			player.removeAttached(CLASS);
			PlayerClasses.set(player, TfClass.byId(classId).orElse(null));
			changed = true;
		}

		return changed;
	}

	/**
	 * Перед возрождением: применяет запомненные команду и класс к погибшему игроку, чтобы игра
	 * выбрала место возрождения уже по новой команде и перенесла новый класс на нового игрока.
	 * Класс здесь только записывается: здоровье и оружие класса выдаёт возрождение.
	 */
	public static void applyBeforeRespawn(ServerPlayer player) {
		applyTeam(player, false);
		String classId = player.getAttached(CLASS);

		if (classId != null) {
			player.removeAttached(CLASS);
			Optional<TfClass> tfClass = TfClass.byId(classId);

			if (tfClass.isPresent()) {
				player.setAttached(PlayerClasses.CLASS_ID, tfClass.get().id());
			} else {
				player.removeAttached(PlayerClasses.CLASS_ID);
			}
		}
	}

	private static boolean applyTeam(ServerPlayer player, boolean toSpawn) {
		String teamId = player.getAttached(TEAM);

		if (teamId == null) {
			return false;
		}

		player.removeAttached(TEAM);

		if (teamId.equals(TfTeam.RANDOM_ID)) {
			Teams.joinRandom(player, toSpawn);
		} else {
			TfTeam.byId(teamId).ifPresent(team -> Teams.join(player, team, toSpawn));
		}

		return true;
	}
}

package io.github.egorka2000.tf2mc.weapon;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * Ракетница Soldier, основное оружие: обойма 4, запас 20, стреляет ракетами ({@link RocketEntity}).
 * Обойма, перезарядка и доставание — общие для оружия с обоймой ({@link ClipWeapon}).
 */
public final class RocketLauncher {
	/** Оружие: предмет, боезапас, стрельба. */
	public static final ClipWeapon WEAPON = new ClipWeapon("rocket_launcher", "rocket_ammo", Magazine.Rules.ROCKET_LAUNCHER,
			Tf2Sounds.ROCKET_LAUNCHER_SHOOT, Tf2Sounds.ROCKET_LAUNCHER_RELOAD, player -> RocketEntity.fire(player.level(), player));

	/** Предмет-ракетница. Выдаётся вместе с классом. */
	public static final Item ITEM = WEAPON.item();

	private RocketLauncher() {
	}

	public static void register() {
		WEAPON.register();
	}

	/** Выдаёт игроку ракетницу с полным боезапасом; вторую не выдаёт. */
	public static void equip(ServerPlayer player) {
		WEAPON.equip(player);
	}

	/** Забирает у игрока ракетницу и боезапас. */
	public static void unequip(ServerPlayer player) {
		WEAPON.unequip(player);
	}

	/** Обойма и запас игрока; {@code null}, если ракетницы у него нет. */
	public static ClipWeapon.Ammo ammo(Player player) {
		return WEAPON.ammo(player);
	}

	/** Выстрел ракетой; {@code false}, если выстрелить нельзя. */
	public static boolean fire(ServerPlayer player) {
		return WEAPON.fire(player);
	}

	/** Полная обойма и полный запас; {@code false}, если ракетницы у игрока нет. */
	public static boolean refill(ServerPlayer player) {
		return WEAPON.refill(player);
	}

	/** Меняет запас ракет; {@code null}, если ракетницы у игрока нет. */
	public static ClipWeapon.Ammo changeReserve(ServerPlayer player, int amount, boolean add) {
		return WEAPON.changeReserve(player, amount, add);
	}

	/** Один тик пауз и перезарядки; для тестов, в игре вызывается сам. */
	public static void tick(MinecraftServer server) {
		WEAPON.tick(server);
	}
}

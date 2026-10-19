package io.github.egorka2000.tf2mc.weapon;

import java.util.List;
import java.util.Optional;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.clazz.PlayerClasses;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;

/**
 * Оружие мода: регистрация и запуск, выдача классу.
 *
 * <p>Оружие Soldier: ракетница {@link RocketLauncher}, дробовик {@link Shotgun}, лопата {@link Shovel}. Отладочная команда {@code /tf2 rocket} выпускает
 * ракету без неё и не тратит боезапас.
 */
public final class Tf2Weapons {
	/** Тип урона от взрыва: в обход брони, без ванильного отбрасывания и без паузы между попаданиями. */
	public static final ResourceKey<DamageType> BLAST = ResourceKey.create(Registries.DAMAGE_TYPE, Tf2Mc.id("blast"));

	/** Тип урона от пули и дроби: так же в обход брони, без ванильного отбрасывания и без паузы между попаданиями. */
	public static final ResourceKey<DamageType> BULLET = ResourceKey.create(Registries.DAMAGE_TYPE, Tf2Mc.id("bullet"));

	/** Тип урона от оружия ближнего боя: так же в обход брони, без ванильного отбрасывания и без паузы между попаданиями. */
	public static final ResourceKey<DamageType> MELEE = ResourceKey.create(Registries.DAMAGE_TYPE, Tf2Mc.id("melee"));

	/** Оружие с обоймой, которое выдаётся классу: основное, затем второе. */
	public static final List<ClipWeapon> CLIP_WEAPONS = List.of(RocketLauncher.WEAPON, Shotgun.WEAPON);

	private static final ResourceKey<EntityType<?>> ROCKET_KEY = ResourceKey.create(Registries.ENTITY_TYPE, Tf2Mc.id("rocket"));

	/** Ракета Soldier. */
	public static final EntityType<RocketEntity> ROCKET = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			ROCKET_KEY,
			EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC)
					.noLootTable()
					.noSave()
					.sized(0.25F, 0.25F)
					.clientTrackingRange(8)
					.updateInterval(1)
					.build(ROCKET_KEY));

	private Tf2Weapons() {
	}

	public static void register() {
		Tf2Sounds.register();
		CLIP_WEAPONS.forEach(ClipWeapon::register);
		Shovel.WEAPON.register();
	}

	/** Выдаёт игроку оружие класса с полным боезапасом; уже выданное не повторяет. */
	public static void equip(ServerPlayer player) {
		CLIP_WEAPONS.forEach(weapon -> weapon.equip(player));
		Shovel.WEAPON.equip(player);
	}

	/** Забирает у игрока оружие класса и боезапас. */
	public static void unequip(ServerPlayer player) {
		CLIP_WEAPONS.forEach(weapon -> weapon.unequip(player));
		Shovel.WEAPON.unequip(player);
	}

	/**
	 * Полный боезапас всему оружию игрока.
	 *
	 * @return было ли у игрока оружие
	 */
	public static boolean refill(ServerPlayer player) {
		boolean any = false;

		for (ClipWeapon weapon : CLIP_WEAPONS) {
			any |= weapon.refill(player);
		}

		return any;
	}

	/** К какому оружию относятся команды боезапаса: в руке, а если в руке не оружие с обоймой — основное. */
	public static ClipWeapon ammoTarget(Player player) {
		return held(player).orElse(RocketLauncher.WEAPON);
	}

	/** Оружие с обоймой в основной руке игрока; пусто, если там другое. */
	public static Optional<ClipWeapon> held(Player player) {
		return CLIP_WEAPONS.stream().filter(weapon -> player.getMainHandItem().is(weapon.item())).findFirst();
	}

	/**
	 * Выпускает ракету от имени игрока, если у него выбран класс. Боезапас не тратится:
	 * это путь отладочной команды.
	 *
	 * @return выпущена ли ракета
	 */
	public static boolean fireRocket(ServerPlayer player) {
		if (PlayerClasses.get(player).isEmpty()) {
			return false;
		}

		RocketEntity.fire(player.level(), player);
		return true;
	}
}

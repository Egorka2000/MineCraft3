package io.github.egorka2000.tf2mc.weapon;

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

/**
 * Оружие мода: регистрация и запуск. Пока здесь только ракета Soldier.
 *
 * <p>Стреляет ракетница {@link RocketLauncher}; отладочная команда {@code /tf2 rocket} выпускает
 * ракету без неё и не тратит боезапас.
 */
public final class Tf2Weapons {
	/** Тип урона от взрыва: в обход брони, без ванильного отбрасывания и без паузы между попаданиями. */
	public static final ResourceKey<DamageType> BLAST = ResourceKey.create(Registries.DAMAGE_TYPE, Tf2Mc.id("blast"));

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
		RocketLauncher.register();
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

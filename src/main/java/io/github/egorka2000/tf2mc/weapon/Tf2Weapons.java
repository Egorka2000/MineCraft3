package io.github.egorka2000.tf2mc.weapon;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.clazz.PlayerClasses;

import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Оружие мода: регистрация и запуск. Пока здесь только ракета Soldier.
 *
 * <p>Своего предмета-ракетницы ещё нет. До него ракету выпускает правая кнопка мыши с огненным
 * стержнем в руке у игрока с классом и команда {@code /tf2 rocket}.
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

	/** Интервал между выстрелами ракетницы: 0,8 секунды. */
	public static final int FIRE_INTERVAL_TICKS = 16;

	private Tf2Weapons() {
	}

	public static void register() {
		UseItemCallback.EVENT.register(Tf2Weapons::useItem);
	}

	/**
	 * Выпускает ракету от имени игрока, если у него выбран класс.
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

	/** Временная ракетница: огненный стержень в руке у игрока с классом. */
	private static InteractionResult useItem(Player player, Level level, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (!stack.is(Items.BLAZE_ROD) || PlayerClasses.get(player).isEmpty() || player.isSpectator()) {
			return InteractionResult.PASS;
		}

		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}

		if (level instanceof ServerLevel && player instanceof ServerPlayer shooter) {
			fireRocket(shooter);
		}

		// Пауза между выстрелами считается и на клиенте, и на сервере: клиент не шлёт лишних нажатий.
		player.getCooldowns().addCooldown(stack, FIRE_INTERVAL_TICKS);
		return InteractionResult.SUCCESS;
	}
}

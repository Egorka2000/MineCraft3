package io.github.egorka2000.tf2mc.clazz;

import java.util.Optional;

import com.mojang.serialization.Codec;
import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.weapon.Tf2Weapons;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Класс TF2 у игрока: хранение, выбор и применение.
 *
 * <p>Имя класса лежит в данных игрока через Attachment API: оно сохраняется между перезапусками,
 * переносится через смерть и рассылается клиентам. Клиенту оно нужно, чтобы считать движение:
 * движение TF2 включено ровно тогда, когда у игрока выбран класс.
 */
public final class PlayerClasses {
	/** Имя выбранного класса. Отсутствие значения означает ванильного игрока. */
	public static final AttachmentType<String> CLASS_ID = AttachmentRegistry.create(
			Tf2Mc.id("class"),
			builder -> builder
					.persistent(Codec.STRING)
					.copyOnDeath()
					.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));

	private static final Identifier HEALTH_MODIFIER = Tf2Mc.id("class_health");
	private static final Identifier OVERHEAL_MODIFIER = Tf2Mc.id("class_overheal");

	private PlayerClasses() {
	}

	public static void register() {
		// Модификаторы атрибутов игра через смерть не переносит: после перерождения здоровье класса
		// выставляется заново. Класс берётся у прежнего игрока: к моменту этого события данные
		// Attachment API на нового игрока ещё не перенесены.
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			applyHealth(newPlayer, get(oldPlayer), true);

			// Вещи после смерти теряются: оружие класса выдаётся заново, с полным боезапасом.
			// Ванильному игроку оружие класса не положено, даже если вещи сохраняются при смерти:
			// он мог стать ванильным перед самым возрождением (PendingChoice).
			if (get(oldPlayer).isPresent()) {
				Tf2Weapons.equip(newPlayer);
			} else {
				Tf2Weapons.unequip(newPlayer);
			}
		});
		// При входе — на случай, если сохранённые данные игрока разошлись с описанием класса
		// в новой версии мода.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> applyHealth(handler.getPlayer(), get(handler.getPlayer()), false));
		ServerTickEvents.END_SERVER_TICK.register(ClassHealth::tickOverheal);
	}

	/** Класс игрока; пусто, если игрок ванильный. Работает и на сервере, и на клиенте. */
	public static Optional<TfClass> get(Player player) {
		String id = player.getAttached(CLASS_ID);
		return id == null ? Optional.empty() : TfClass.byId(id);
	}

	/**
	 * Выдаёт игроку класс или снимает его.
	 *
	 * @param tfClass новый класс; {@code null} возвращает ванильного игрока
	 */
	public static void set(ServerPlayer player, TfClass tfClass) {
		if (tfClass == null) {
			player.removeAttached(CLASS_ID);
		} else {
			player.setAttached(CLASS_ID, tfClass.id());
		}

		applyHealth(player, Optional.ofNullable(tfClass), true);

		if (tfClass == null) {
			Tf2Weapons.unequip(player);
		} else {
			Tf2Weapons.equip(player);
		}
	}

	/**
	 * Приводит максимальное здоровье и предел оверхила игрока к классу.
	 *
	 * @param tfClass класс игрока; пусто для ванильного
	 * @param refill  выставить ли здоровье на максимум: при выборе класса и после перерождения
	 */
	private static void applyHealth(ServerPlayer player, Optional<TfClass> tfClass, boolean refill) {
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		AttributeInstance maxOverheal = player.getAttribute(Attributes.MAX_ABSORPTION);

		if (maxHealth == null || maxOverheal == null) {
			return;
		}

		if (tfClass.isPresent()) {
			// Здоровье в числах TF2 один к одному: модификатор добавляет разницу с ванильной базой.
			maxHealth.addOrReplacePermanentModifier(new AttributeModifier(
					HEALTH_MODIFIER, tfClass.get().maxHealth() - maxHealth.getBaseValue(), AttributeModifier.Operation.ADD_VALUE));
			// Оверхил хранится как ванильное поглощение; его предел — тоже атрибут. Он открыт до конца:
			// правило 150% соблюдают источники лечения, а отладочная команда может выставить больше.
			maxOverheal.addOrReplacePermanentModifier(new AttributeModifier(
					OVERHEAL_MODIFIER, ClassHealth.STORAGE_LIMIT, AttributeModifier.Operation.ADD_VALUE));

			if (refill) {
				player.setHealth(player.getMaxHealth());
			}
		} else {
			maxHealth.removeModifier(HEALTH_MODIFIER);
			maxOverheal.removeModifier(OVERHEAL_MODIFIER);
		}

		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}

		// Игра подрезает оверхил под новый предел только на следующем тике; здесь — сразу.
		player.setAbsorptionAmount(refill ? 0.0F : player.getAbsorptionAmount());
	}
}

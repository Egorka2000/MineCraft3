package io.github.egorka2000.tf2mc.clazz;

import java.util.Optional;
import java.util.OptionalInt;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.movement.UnitConversion;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Здоровье игрока с классом TF2 по правилам TF2: урон от падения и оверхил.
 * Математика — в {@link HealthRules}, здесь только связь с игрой.
 *
 * <p>Оверхил хранится как ванильное поглощение: оно уже сохраняется, рассылается клиентам
 * и первым принимает урон.
 */
public final class ClassHealth {
	private static final double TICKS_PER_SECOND = 20.0;

	private ClassHealth() {
	}

	/**
	 * Урон от падения по правилам TF2.
	 *
	 * <p>TF2 считает урон по скорости приземления. Сервер скорости игрока не знает: движение
	 * считает клиент. Поэтому скорость восстанавливается по высоте падения, которую сервер
	 * ведёт сам, и гравитации из настроек.
	 *
	 * @param fallBlocks     высота падения, блоки
	 * @param damageModifier ванильный множитель поверхности: сено, слизь и подобное
	 * @return урон в единицах здоровья; пусто, если игрок ванильный
	 */
	public static OptionalInt fallDamage(Player player, double fallBlocks, float damageModifier) {
		Optional<TfClass> tfClass = PlayerClasses.get(player);

		if (tfClass.isEmpty()) {
			return OptionalInt.empty();
		}

		var config = Tf2Mc.config().current();
		double speed = HealthRules.fallSpeed(UnitConversion.toUnits(fallBlocks, config.unitsPerBlock()), config.gravity());
		double spread = 1.0 + (player.getRandom().nextDouble() * 2.0 - 1.0) * HealthRules.FALL_DAMAGE_SPREAD;

		return OptionalInt.of(Mth.floor(HealthRules.fallDamage(speed, tfClass.get().maxHealth(), spread) * damageModifier));
	}

	/** Здоровье вместе с оверхилом. */
	public static float total(Player player) {
		return player.getHealth() + player.getAbsorptionAmount();
	}

	/**
	 * Выставляет здоровье вместе с оверхилом. Всё, что выше максимума, уходит в оверхил
	 * и обрезается его пределом; у ванильного игрока оверхила нет.
	 *
	 * @return здоровье, которое получилось на самом деле
	 */
	public static float setTotal(ServerPlayer player, float total) {
		float health = Mth.clamp(total, 1.0F, player.getMaxHealth());

		player.setHealth(health);
		// Предел оверхила — атрибут MAX_ABSORPTION, игра обрезает по нему сама.
		player.setAbsorptionAmount(Math.max(total - health, 0.0F));
		return total(player);
	}

	/** Раз в тик сервера: оверхил игроков с классом понемногу спадает. */
	public static void tickOverheal(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			float overheal = player.getAbsorptionAmount();

			if (overheal <= 0.0F) {
				continue;
			}

			PlayerClasses.get(player).ifPresent(tfClass -> {
				double decay = HealthRules.overhealDecayPerSecond(tfClass.maxHealth()) / TICKS_PER_SECOND;
				player.setAbsorptionAmount((float) Math.max(overheal - decay, 0.0));
			});
		}
	}
}

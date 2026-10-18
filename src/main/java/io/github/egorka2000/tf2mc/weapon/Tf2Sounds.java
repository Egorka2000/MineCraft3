package io.github.egorka2000.tf2mc.weapon;

import io.github.egorka2000.tf2mc.Tf2Mc;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Звуки мода. Мод обращается к ним по именам и не знает, какой файл за ними стоит.
 *
 * <p>Имена совпадают с именами звуков в таблицах самой TF2 ({@code scripts/game_sounds*.txt}),
 * только строчными буквами: {@code Weapon_RPG.Single} — это {@code tf2mc:weapon_rpg.single}.
 * Расширительный пак переводит в звуки Minecraft сразу все таблицы TF2, поэтому новому звуку
 * мода не нужен новый пак: достаточно взять имя из таблицы.
 *
 * <p>В самом моде за каждым именем — заглушка из ванильных звуков ({@code assets/tf2mc/sounds.json}),
 * пак подменяет её звуком TF2.
 */
public final class Tf2Sounds {
	/** Выстрел из ракетницы. */
	public static final SoundEvent ROCKET_LAUNCHER_SHOOT = register("weapon_rpg.single");

	/** В обойму ракетницы заряжена ракета. */
	public static final SoundEvent ROCKET_LAUNCHER_RELOAD = register("weapon_rpg.reload");

	/** Выстрел из дробовика. */
	public static final SoundEvent SHOTGUN_SHOOT = register("weapon_shotgun.single");

	/** В дробовик заряжен патрон. */
	public static final SoundEvent SHOTGUN_RELOAD = register("weapon_shotgun.reload");

	/** Взрыв ракеты. */
	public static final SoundEvent ROCKET_EXPLODE = register("baseexplosioneffect.sound");

	/** Подобрана аптечка. */
	public static final SoundEvent HEALTH_KIT_TOUCH = register("healthkit.touch");

	/** Подобраны патроны. */
	public static final SoundEvent AMMO_PACK_TOUCH = register("ammopack.touch");

	/** На подставке появилась новая аптечка или патроны. */
	public static final SoundEvent ITEM_MATERIALIZE = register("item.materialize");

	/** Сработал шкаф пополнения. */
	public static final SoundEvent REGENERATE_TOUCH = register("regenerate.touch");

	private Tf2Sounds() {
	}

	/** Вызывается при запуске мода: сам вызов загружает класс и регистрирует звуки. */
	public static void register() {
	}

	private static SoundEvent register(String path) {
		Identifier id = Tf2Mc.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}
}

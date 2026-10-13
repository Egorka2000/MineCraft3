package io.github.egorka2000.tf2mc.weapon;

import io.github.egorka2000.tf2mc.Tf2Mc;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Звуки мода. Мод обращается к ним по своим именам и не знает, какой файл за ними стоит.
 *
 * <p>В самом моде за каждым именем — заглушка из ванильных звуков ({@code assets/tf2mc/sounds.json}).
 * Расширительный пак подменяет их настоящими звуками: его собирает конвертер из
 * {@code tools/expansion-pack}, соответствие имён и файлов TF2 лежит там же.
 */
public final class Tf2Sounds {
	/** Выстрел из ракетницы. */
	public static final SoundEvent ROCKET_LAUNCHER_SHOOT = register("weapon.rocket_launcher.shoot");

	/** В обойму ракетницы заряжена ракета. */
	public static final SoundEvent ROCKET_LAUNCHER_RELOAD = register("weapon.rocket_launcher.reload");

	/** Взрыв ракеты. */
	public static final SoundEvent ROCKET_EXPLODE = register("weapon.rocket.explode");

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

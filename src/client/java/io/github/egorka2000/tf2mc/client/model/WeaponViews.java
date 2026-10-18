package io.github.egorka2000.tf2mc.client.model;

import java.util.List;
import java.util.Optional;

import io.github.egorka2000.tf2mc.weapon.RocketLauncher;
import io.github.egorka2000.tf2mc.weapon.Shotgun;

import net.minecraft.world.item.ItemStack;

/** Оружие мода, которое рисуется моделью TF2, и его анимации ({@link WeaponView}). */
public final class WeaponViews {
	public static final WeaponView ROCKET_LAUNCHER = new WeaponView(RocketLauncher.ITEM, WeaponView.PRIMARY,
			"models/weapons/c_models/c_rocketlauncher/c_rocketlauncher",
			"dh_draw", "dh_idle", "dh_fire", "dh_reload_start", "dh_reload_loop", "dh_reload_finish");

	public static final WeaponView SHOTGUN = new WeaponView(Shotgun.ITEM, WeaponView.SECONDARY,
			"models/weapons/c_models/c_shotgun/c_shotgun",
			"draw", "idle", "fire", "reload_start", "reload_loop", "reload_end");

	/** Всё оружие, которое рисуется моделью TF2. */
	public static final List<WeaponView> ALL = List.of(ROCKET_LAUNCHER, SHOTGUN);

	private WeaponViews() {
	}

	/** Оружие TF2 в этой руке; пусто, если там не оружие мода. */
	public static Optional<WeaponView> of(ItemStack stack) {
		return ALL.stream().filter(view -> stack.is(view.item())).findFirst();
	}
}

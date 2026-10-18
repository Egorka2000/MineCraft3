package io.github.egorka2000.tf2mc.client.model;

import net.minecraft.world.item.Item;

/**
 * Как выглядит оружие TF2 в руках: модель из пака, набор анимаций тела и анимации рук от первого
 * лица. Само оружие мода перечислено в {@link WeaponViews}.
 *
 * @param slot   набор анимаций тела Soldier: {@link #PRIMARY} — основное оружие, {@link #SECONDARY} — второе
 * @param model  модель оружия в паке, без расширения
 * @param draw   анимации рук от первого лица: достать, ожидание, выстрел, начало, повтор и конец перезарядки
 */
public record WeaponView(Item item, String slot, String model, String draw, String idle, String fire, String reloadStart, String reloadLoop,
		String reloadFinish) {
	/** Основное оружие: анимации тела с ним — набор по умолчанию, даже когда в руке ничего нет. */
	public static final String PRIMARY = "PRIMARY";

	public static final String SECONDARY = "SECONDARY";

	/** Оружие ближнего боя: перезарядки нет, имена анимаций перезарядки — {@code null}. */
	public static final String MELEE = "MELEE";
}

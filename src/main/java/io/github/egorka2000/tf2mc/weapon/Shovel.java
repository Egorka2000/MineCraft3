package io.github.egorka2000.tf2mc.weapon;

import net.minecraft.world.item.Item;

/**
 * Лопата Soldier, оружие ближнего боя: 65 урона, удар раз в 0,8 с через 0,2 с после замаха,
 * длина удара 48 u от глаз, после промаха лучом — куб 36 u. Числа и источники — docs/05-sources.md.
 */
public final class Shovel {
	/** Урон удара. */
	public static final double DAMAGE = 65.0;

	/** Длина удара от глаз, u. */
	public static final double RANGE = 48.0;

	/** Половина стороны куба, которым удар проверяется после промаха лучом, u. */
	public static final double HULL = 18.0;

	/** Оружие: предмет и удар. */
	public static final MeleeWeapon WEAPON = new MeleeWeapon("shovel", Swing.Rules.SHOVEL, new MeleeWeapon.Reach(DAMAGE, RANGE, HULL), Tf2Weapons.MELEE,
			Tf2Sounds.SHOVEL_MISS, Tf2Sounds.SHOVEL_HIT_WORLD, Tf2Sounds.SHOVEL_HIT_FLESH);

	/** Предмет-лопата. Выдаётся вместе с классом. */
	public static final Item ITEM = WEAPON.item();

	private Shovel() {
	}
}

package io.github.egorka2000.tf2mc.client.model;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * Состояние отрисовки игрока с классом. Отдельный тип нужен, чтобы игра на втором шаге отрисовки
 * узнала такого игрока и снова взяла отрисовщик мода, а не ванильный.
 *
 * <p>Наследует состояние ванильного игрока: для локального игрока игра требует именно его.
 */
public final class ClassRenderState extends AvatarRenderState {
	/** Модель класса игрока из пака; {@code null} — модели этого класса нет. */
	public SourceModels.PlayerMesh mesh;

	/** Семейство скинов модели TF2: команда игрока ({@link SourceModels#RED} или {@link SourceModels#BLU}). */
	public int skinFamily;

	/** Последовательность анимации TF2, например {@code run_PRIMARY}; пусто — начальная поза. */
	public String sequence = "";

	/** Доля проигрывания последовательности, 0..1. */
	public float cycle;

	/** Движение относительно тела: вперёд и влево, доли наибольшей скорости класса, −1..1. */
	public float moveForward;
	public float moveLeft;

	/** В основной руке оружие мода: его модель рисуется в руках. */
	public boolean holdingWeapon;

	/** Оружие мода в основной руке; {@code null} — другое или ничего. */
	public WeaponView weapon;

	/** Последовательность, с которой идёт плавный переход; {@code null} — перехода нет. */
	public String previousSequence;

	/** Доля проигрывания прежней последовательности. */
	public float previousCycle;

	/** Ход перехода: 0 — ещё прежняя последовательность, 1 — уже новая. */
	public float transition = 1.0F;

	/** Жест оружия поверх основной анимации, например {@code AttackStand_PRIMARY}; {@code null} — жеста нет. */
	public String gestureSequence;

	/** Доля проигрывания жеста. */
	public float gestureCycle;
}

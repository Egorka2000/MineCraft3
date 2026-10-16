package io.github.egorka2000.tf2mc.client.model;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * Состояние отрисовки игрока с классом. Отдельный тип нужен, чтобы игра на втором шаге отрисовки
 * узнала такого игрока и снова взяла отрисовщик мода, а не ванильный.
 *
 * <p>Наследует состояние ванильного игрока: для локального игрока игра требует именно его.
 */
public final class ClassRenderState extends AvatarRenderState {
	/** Семейство скинов модели TF2: команда игрока ({@link SourceModels#RED} или {@link SourceModels#BLU}). */
	public int skinFamily;

	/** Последовательность анимации TF2, например {@code run_PRIMARY}; пусто — начальная поза. */
	public String sequence = "";

	/** Доля проигрывания последовательности, 0..1. */
	public float cycle;

	/** Движение относительно тела: вперёд и влево, доли наибольшей скорости класса, −1..1. */
	public float moveForward;
	public float moveLeft;

	/** В основной руке ракетница: её модель рисуется в руках. */
	public boolean holdingWeapon;
}

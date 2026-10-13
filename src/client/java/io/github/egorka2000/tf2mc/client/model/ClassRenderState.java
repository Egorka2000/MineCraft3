package io.github.egorka2000.tf2mc.client.model;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * Состояние отрисовки игрока с классом. Отдельный тип нужен, чтобы игра на втором шаге отрисовки
 * узнала такого игрока и снова взяла отрисовщик мода, а не ванильный.
 *
 * <p>Наследует состояние ванильного игрока: для локального игрока игра требует именно его.
 */
public final class ClassRenderState extends AvatarRenderState {
}

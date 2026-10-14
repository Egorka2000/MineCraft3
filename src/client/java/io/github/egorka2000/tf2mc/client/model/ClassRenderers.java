package io.github.egorka2000.tf2mc.client.model;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Создание отрисовщика моделей классов. Вынесено из {@link ClassModels} в отдельный класс:
 * проверяя код {@code ClassModels}, Java заранее загрузила бы {@link ClassPlayerRenderer}, а с ним
 * и библиотеку анимации, которой может не быть. Этот класс загружается, только когда она есть.
 */
final class ClassRenderers {
	private ClassRenderers() {
	}

	static EntityRenderer<?, ?> create(EntityRendererProvider.Context context) {
		return new ClassPlayerRenderer(context);
	}
}

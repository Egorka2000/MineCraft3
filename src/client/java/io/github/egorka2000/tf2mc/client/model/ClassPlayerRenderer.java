package io.github.egorka2000.tf2mc.client.model;

import com.geckolib.renderer.GeoReplacedEntityRenderer;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/**
 * Рисует игрока с классом моделью со скелетной анимацией вместо ванильной модели игрока.
 */
final class ClassPlayerRenderer extends GeoReplacedEntityRenderer<ClassAnimatable, AbstractClientPlayer, ClassRenderState> {
	ClassPlayerRenderer(EntityRendererProvider.Context context) {
		super(context, new ClassPlayerModel(), new ClassAnimatable());
	}

	@Override
	public ClassRenderState createRenderState(ClassAnimatable animatable, AbstractClientPlayer player) {
		return new ClassRenderState();
	}
}

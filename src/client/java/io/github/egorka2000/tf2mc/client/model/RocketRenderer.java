package io.github.egorka2000.tf2mc.client.model;

import java.util.Optional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.egorka2000.tf2mc.weapon.RocketEntity;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Рисует ракету моделью TF2 из расширительного пака ({@link SourceModels#ROCKET}), носом по
 * направлению полёта. Без пака ракета невидима.
 */
public final class RocketRenderer extends EntityRenderer<RocketEntity, RocketRenderer.State> {
	/**
	 * Ближе этого расстояния до камеры, блоки, ракета не рисуется. Она вылетает из глаз стрелка и
	 * в первый кадр закрыла бы ему пол-экрана; за тик она пролетает больше блока.
	 */
	private static final double HIDDEN_NEAR_CAMERA = 1.0;

	public RocketRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	/** Состояние отрисовки ракеты: поворот, с которым её выпустили. */
	public static final class State extends EntityRenderState {
		float yRot;
		float xRot;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(RocketEntity rocket, State state, float partialTicks) {
		super.extractRenderState(rocket, state, partialTicks);
		state.yRot = rocket.getYRot(partialTicks);
		state.xRot = rocket.getXRot(partialTicks);
	}

	@Override
	public boolean shouldRender(RocketEntity rocket, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
		return ClassModels.rocket().isPresent()
				&& rocket.distanceToSqr(camX, camY, camZ) >= HIDDEN_NEAR_CAMERA * HIDDEN_NEAR_CAMERA
				&& super.shouldRender(rocket, culler, camX, camY, camZ, partialTicks);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		Optional<SourceModels.Prop> rocket = ClassModels.rocket();

		if (rocket.isPresent()) {
			poseStack.pushPose();
			// Модель смотрит носом в +z. Поворот ракеты — как у игрока, который её выпустил:
			// рысканье θ уводит перёд в (−sin θ, 0, cos θ), положительный наклон — вниз.
			poseStack.rotateDegrees(Axis.YP, -state.yRot);
			poseStack.rotateDegrees(Axis.XP, state.xRot);
			SourcePlayerRenderer.submit(collector, poseStack, rocket.get().mesh(), rocket.get().vertices(), SourceModels.RED, state.lightCoords,
					OverlayTexture.NO_OVERLAY);
			poseStack.popPose();
		}

		super.submit(state, poseStack, collector, camera);
	}
}

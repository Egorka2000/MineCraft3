package io.github.egorka2000.tf2mc.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.egorka2000.tf2mc.team.Teams;
import io.github.egorka2000.tf2mc.team.TfTeam;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * Рисует игрока с классом моделью TF2 из расширительного пака вместо модели Стива.
 *
 * <p>Всё, кроме самой модели, остаётся от ванильного отрисовщика игрока: поворот тела, имя над
 * головой, тень, покраснение от урона. Модель Стива, броня и предметы в руках не рисуются.
 * Пока модель в начальной позе, без анимаций.
 */
final class SourcePlayerRenderer extends AvatarRenderer<AbstractClientPlayer> {
	private final SourceModels.PlayerMesh mesh;

	SourcePlayerRenderer(EntityRendererProvider.Context context, SourceModels.PlayerMesh mesh) {
		super(context, false);
		this.mesh = mesh;
	}

	@Override
	public AvatarRenderState createRenderState() {
		return new ClassRenderState();
	}

	@Override
	public void extractRenderState(AbstractClientPlayer player, AvatarRenderState state, float partialTicks) {
		super.extractRenderState(player, state, partialTicks);

		if (state instanceof ClassRenderState classState) {
			classState.skinFamily = Teams.get(player).orElse(TfTeam.RED) == TfTeam.BLU ? SourceModels.BLU : SourceModels.RED;
		}
	}

	@Override
	public void submit(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.isInvisible) {
			int family = state instanceof ClassRenderState classState ? classState.skinFamily : SourceModels.RED;
			int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);

			poseStack.pushPose();
			poseStack.scale(state.scale, state.scale, state.scale);
			setupRotations(state, poseStack, state.bodyRot, state.scale);

			for (SourceModels.Draw draw : mesh.forFamily(family)) {
				RenderType type = RenderTypes.entityCutout(draw.texture());
				collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> triangles(pose, buffer, draw.corners(), state.lightCoords, overlay));
			}

			poseStack.popPose();
		}

		// Имя, тень и прочее; модель Стива отключена в getRenderType.
		super.submit(state, poseStack, collector, camera);
	}

	/** Треугольники отрисовщик игры рисует четырёхугольниками: последний угол повторяется. */
	private static void triangles(PoseStack.Pose pose, VertexConsumer buffer, float[] corners, int light, int overlay) {
		int stride = SourceModels.Draw.STRIDE;

		for (int at = 0; at < corners.length; at += stride * 3) {
			corner(pose, buffer, corners, at, light, overlay);
			corner(pose, buffer, corners, at + stride, light, overlay);
			corner(pose, buffer, corners, at + stride * 2, light, overlay);
			corner(pose, buffer, corners, at + stride * 2, light, overlay);
		}
	}

	private static void corner(PoseStack.Pose pose, VertexConsumer buffer, float[] c, int at, int light, int overlay) {
		buffer.addVertex(pose, c[at], c[at + 1], c[at + 2])
				.setColor(-1)
				.setUv(c[at + 3], c[at + 4])
				.setOverlay(overlay)
				.setLight(light)
				.setNormal(pose, c[at + 5], c[at + 6], c[at + 7]);
	}

	// Модель Стива не рисуется: её место заняла модель TF2.
	@Override
	protected RenderType getRenderType(AvatarRenderState state, boolean isBodyVisible, boolean forceTransparent, boolean appearGlowing) {
		return null;
	}

	// Броня, плащ и предметы в руках Стива к модели TF2 не подходят.
	@Override
	protected boolean shouldRenderLayers(AvatarRenderState state) {
		return false;
	}
}

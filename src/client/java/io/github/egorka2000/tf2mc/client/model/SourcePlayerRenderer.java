package io.github.egorka2000.tf2mc.client.model;

import java.util.Optional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.clazz.PlayerClasses;
import io.github.egorka2000.tf2mc.clazz.TfClass;
import io.github.egorka2000.tf2mc.sourcemodel.AnimationSet;
import io.github.egorka2000.tf2mc.sourcemodel.Pose;
import io.github.egorka2000.tf2mc.team.Teams;
import io.github.egorka2000.tf2mc.team.TfTeam;
import io.github.egorka2000.tf2mc.weapon.RocketLauncher;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;

/**
 * Рисует игрока с классом моделью TF2 из расширительного пака вместо модели Стива.
 *
 * <p>Всё, кроме самой модели, остаётся от ванильного отрисовщика игрока: поворот тела, имя над
 * головой, тень, покраснение от урона. Модель Стива, броня и предметы в руках Стива не рисуются;
 * ракетница в основной руке рисуется моделью TF2 в руках персонажа.
 *
 * <p>Анимация выбирается по движению игрока, как в TF2 с основным оружием: стойка, бег (смешивается
 * по направлению движения), присед и ходьба в приседе, полёт в прыжке. Вершины пересчитываются
 * через кости каждый кадр.
 */
final class SourcePlayerRenderer extends AvatarRenderer<AbstractClientPlayer> {
	/** Быстрее этой доли наибольшей скорости игрок считается идущим. */
	private static final float MOVING = 0.1F;

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

		if (!(state instanceof ClassRenderState classState)) {
			return;
		}

		classState.skinFamily = Teams.get(player).orElse(TfTeam.RED) == TfTeam.BLU ? SourceModels.BLU : SourceModels.RED;

		// Скорость — по смещению за тик: у чужих игроков на клиенте своей скорости нет.
		double dx = player.getX() - player.xo;
		double dz = player.getZ() - player.zo;
		double maxPerTick = PlayerClasses.get(player).map(TfClass::maxSpeed).orElse(240.0) / Tf2Mc.config().current().unitsPerBlock() / 20.0;
		float yaw = state.bodyRot * Mth.DEG_TO_RAD;
		// Перёд при повороте тела θ — (−sin θ, cos θ), лево — (cos θ, sin θ).
		classState.moveForward = (float) Math.clamp((-Math.sin(yaw) * dx + Math.cos(yaw) * dz) / maxPerTick, -1.0, 1.0);
		classState.moveLeft = (float) Math.clamp((Math.cos(yaw) * dx + Math.sin(yaw) * dz) / maxPerTick, -1.0, 1.0);
		boolean moving = Math.hypot(classState.moveForward, classState.moveLeft) > MOVING;

		String sequence;

		if (!player.onGround() && !player.isInWater()) {
			sequence = "jump_float_PRIMARY";
		} else if (state.isCrouching) {
			sequence = moving ? "crouch_walk_PRIMARY" : "crouch_PRIMARY";
		} else {
			sequence = moving ? "run_PRIMARY" : "stand_PRIMARY";
		}

		classState.sequence = sequence;
		classState.holdingWeapon = player.getMainHandItem().is(RocketLauncher.ITEM);
		classState.cycle = mesh.animations().flatMap(set -> set.sequence(sequence).map(seq -> cycle(set, seq, state.ageInTicks))).orElse(0.0F);
	}

	/** Доля проигрывания по времени: длительность — по первой анимации последовательности. */
	private static float cycle(AnimationSet set, AnimationSet.Sequence sequence, float ageInTicks) {
		AnimationSet.Animation animation = set.animations().get(sequence.animation(0, 0));
		float seconds = Math.max(1, animation.frames() - 1) / Math.max(1.0F, animation.fps());
		float position = ageInTicks / 20.0F / seconds;
		return sequence.looping() ? position - (float) Math.floor(position) : Math.min(position, 1.0F);
	}

	@Override
	public void submit(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.isInvisible) {
			ClassRenderState classState = state instanceof ClassRenderState s ? s : null;
			int family = classState != null ? classState.skinFamily : SourceModels.RED;
			int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
			SourceModels.SkinnedMesh body = mesh.body();
			Pose pose = pose(classState);
			float[] world = pose == null ? null : pose.worldMatrices(body.bones());

			poseStack.pushPose();
			poseStack.scale(state.scale, state.scale, state.scale);
			setupRotations(state, poseStack, state.bodyRot, state.scale);
			submit(collector, poseStack, body, body.skin(pose == null ? null : pose.skinMatrices(body.bones())), family, state.lightCoords, overlay);

			// Оружие повторяет кость weapon_bone тела.
			if (world != null && classState.holdingWeapon && mesh.weapon().isPresent()) {
				SourceModels.SkinnedMesh weapon = mesh.weapon().get();
				float[] skin = Pose.attachedSkinMatrices(weapon.bones(), body.bones(), world);
				submit(collector, poseStack, weapon, weapon.skin(skin), family, state.lightCoords, overlay);
			}

			poseStack.popPose();
		}

		// Имя, тень и прочее; модель Стива отключена в getRenderType.
		super.submit(state, poseStack, collector, camera);
	}

	private static void submit(SubmitNodeCollector collector, PoseStack poseStack, SourceModels.SkinnedMesh mesh, float[] vertices, int family, int light, int overlay) {
		for (SourceModels.Draw draw : mesh.forFamily(family)) {
			RenderType type = RenderTypes.entityCutout(draw.texture());
			collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> triangles(pose, buffer, vertices, draw.triangles(), light, overlay));
		}
	}

	/** Поза по выбранной анимации; {@code null} — начальная поза, если анимаций нет. */
	private Pose pose(ClassRenderState state) {
		Optional<AnimationSet> animations = mesh.animations();

		if (state == null || animations.isEmpty()) {
			return null;
		}

		AnimationSet set = animations.get();
		Optional<AnimationSet.Sequence> sequence = set.sequence(state.sequence);

		if (sequence.isEmpty()) {
			return null;
		}

		float[] values = new float[set.parameters().size()];
		setParameter(set, values, "move_x", state.moveForward);
		setParameter(set, values, "move_y", state.moveLeft);
		// Наклон корпуса за взглядом. Измерено по кости weapon_bone: при body_yaw > 0 ствол уходит
		// вправо, при body_pitch > 0 — вверх. В Minecraft поворот головы относительно тела вправо
		// положительный, как body_yaw, а наклон вниз положительный — обратно body_pitch.
		setParameter(set, values, "body_yaw", state.yRot);
		setParameter(set, values, "body_pitch", -state.xRot);
		return set.sampleWithLayers(sequence.get(), state.cycle, values);
	}

	private static void setParameter(AnimationSet set, float[] values, String name, float value) {
		int index = set.parameter(name);

		if (index >= 0) {
			values[index] = value;
		}
	}

	/** Треугольники отрисовщик игры рисует четырёхугольниками: последний угол повторяется. */
	private static void triangles(PoseStack.Pose pose, VertexConsumer buffer, float[] vertices, int[] triangles, int light, int overlay) {
		for (int i = 0; i + 2 < triangles.length; i += 3) {
			corner(pose, buffer, vertices, triangles[i], light, overlay);
			corner(pose, buffer, vertices, triangles[i + 1], light, overlay);
			corner(pose, buffer, vertices, triangles[i + 2], light, overlay);
			corner(pose, buffer, vertices, triangles[i + 2], light, overlay);
		}
	}

	private static void corner(PoseStack.Pose pose, VertexConsumer buffer, float[] v, int vertex, int light, int overlay) {
		int at = vertex * SourceModels.SkinnedMesh.STRIDE;
		buffer.addVertex(pose, v[at], v[at + 1], v[at + 2])
				.setColor(-1)
				.setUv(v[at + 3], v[at + 4])
				.setOverlay(overlay)
				.setLight(light)
				.setNormal(pose, v[at + 5], v[at + 6], v[at + 7]);
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

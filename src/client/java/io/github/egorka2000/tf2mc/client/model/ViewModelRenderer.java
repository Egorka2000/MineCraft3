package io.github.egorka2000.tf2mc.client.model;

import java.util.Optional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.egorka2000.tf2mc.clazz.PlayerClasses;
import io.github.egorka2000.tf2mc.sourcemodel.AnimationSet;
import io.github.egorka2000.tf2mc.sourcemodel.Pose;
import io.github.egorka2000.tf2mc.team.Teams;
import io.github.egorka2000.tf2mc.team.TfTeam;
import io.github.egorka2000.tf2mc.weapon.RocketLauncher;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Руки Soldier с ракетницей от первого лица из расширительного пака вместо руки Стива.
 *
 * <p>Модель рук TF2 стоит началом координат в глазах игрока и смотрит по оси x Source — так же,
 * как модель персонажа после анимации, поэтому годится тот же перевод в оси Minecraft. TF2 рисует
 * руки с углом обзора {@code viewmodel_fov} 54° по горизонтали экрана 4:3, игра — с вертикальным
 * углом 70°; поперёк взгляда модель растягивается, чтобы на экране она была того же размера.
 */
public final class ViewModelRenderer {
	/** Угол обзора рук TF2 по умолчанию, градусы. Источник — docs/05-sources.md. */
	static final double VIEWMODEL_FOV = 54.0;

	private ViewModelRenderer() {
	}

	/**
	 * Рисует руки TF2, если игрок с классом держит ракетницу и в паке есть руки с анимациями.
	 *
	 * @return нарисованы ли руки: тогда руку Стива и предмет игра не рисует
	 */
	public static boolean submit(float partialTicks, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState,
			FirstPersonHandsAndItemsRenderState hands) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Optional<SourceModels.PlayerMesh> model = ClassModels.viewModel();

		if (player == null || model.isEmpty() || PlayerClasses.get(player).isEmpty() || playerState.avatarRenderState == null || hands.isScoping) {
			return false;
		}

		boolean holding = hands.mainHandItem.is(RocketLauncher.ITEM);
		float now = (player.tickCount + partialTicks) / 20.0F;
		AnimationSet set = model.get().animations().orElseThrow();
		ViewModels.Frame frame = ViewModels.frame(holding, now, name -> duration(set, name));

		if (!holding) {
			return false;
		}

		Optional<AnimationSet.Sequence> sequence = set.sequence(frame.sequence());

		if (sequence.isEmpty()) {
			return false;
		}

		SourceModels.SkinnedMesh arms = model.get().body();
		SourceModels.SkinnedMesh weapon = model.get().weapon().orElseThrow();
		Pose pose = set.sampleWithLayers(sequence.get(), frame.cycle(), new float[set.parameters().size()]);
		float[] world = pose.worldMatrices(arms.bones());
		int family = Teams.get(player).orElse(TfTeam.RED) == TfTeam.BLU ? SourceModels.BLU : SourceModels.RED;
		int light = playerState.avatarRenderState.lightCoords;
		float stretch = stretch();

		poseStack.pushPose();
		// Покачивание при повороте взгляда — как у руки Стива.
		poseStack.rotateDegrees(Axis.XP, (hands.viewXRot - hands.xBob) * 0.1F);
		poseStack.rotateDegrees(Axis.YP, (hands.viewYRot - hands.yBob) * 0.1F);
		poseStack.scale(stretch, stretch, 1.0F);
		SourcePlayerRenderer.submit(collector, poseStack, arms, arms.skin(pose.skinMatrices(arms.bones())), family, light, OverlayTexture.NO_OVERLAY);
		SourcePlayerRenderer.submit(collector, poseStack, weapon, weapon.skin(Pose.attachedSkinMatrices(weapon.bones(), arms.bones(), world)), family, light,
				OverlayTexture.NO_OVERLAY);
		poseStack.popPose();
		return true;
	}

	/**
	 * Во сколько раз растянуть модель поперёк взгляда. Угол TF2 задан по горизонтали экрана 4:3;
	 * по вертикали он меньше: {@code 2·atan(tan(54°/2)·3/4)} ≈ 41,9°.
	 */
	static float stretch() {
		double tf2Vertical = Math.atan(Math.tan(Math.toRadians(VIEWMODEL_FOV / 2.0)) * 3.0 / 4.0);
		return (float) (Math.tan(Math.toRadians(Camera.BASE_HUD_FOV / 2.0)) / Math.tan(tf2Vertical));
	}

	private static double duration(AnimationSet set, String name) {
		return set.sequence(name).map(sequence -> {
			AnimationSet.Animation animation = set.animations().get(sequence.animation(0, 0));
			return Math.max(1, animation.frames() - 1) / (double) Math.max(1.0F, animation.fps());
		}).orElse(1.0);
	}
}

package io.github.egorka2000.tf2mc.client.model;

import java.util.Optional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.egorka2000.tf2mc.block.PickupBlock;
import io.github.egorka2000.tf2mc.block.PropBlockEntity;
import io.github.egorka2000.tf2mc.block.ResupplyBlock;
import io.github.egorka2000.tf2mc.sourcemodel.AnimationSet;
import io.github.egorka2000.tf2mc.sourcemodel.Pose;
import io.github.egorka2000.tf2mc.team.Teams;
import io.github.egorka2000.tf2mc.team.TfTeam;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Рисует спецблок моделью TF2 из расширительного пака ({@link PropModels}): аптечки и патроны
 * — пока они лежат на подставке, шкаф пополнения — с дверцами, которые открываются, когда рядом
 * игрок его команды, как в TF2. Без модели в паке ничего не рисует: блок выглядит как раньше.
 */
public final class PropRenderer implements BlockEntityRenderer<PropBlockEntity, PropRenderer.State> {
	/** Ближе этого расстояния до шкафа, блоки, его дверцы открываются для игрока его команды. */
	static final double LOCKER_OPEN_DISTANCE = 2.0;

	public PropRenderer(BlockEntityRendererProvider.Context context) {
	}

	/** Что рисовать: модель блока, последовательность и доля её проигрывания, поворот, скин. */
	public static final class State extends BlockEntityRenderState {
		Block block;
		boolean visible;
		String sequence;
		float cycle;
		Direction facing = Direction.NORTH;
		int family = SourceModels.RED;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(PropBlockEntity entity, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
		BlockState blockState = entity.getBlockState();
		state.block = blockState.getBlock();
		Optional<SourceModels.AnimatedProp> model = PropModels.forBlock(state.block);
		state.visible = model.isPresent() && entity.getLevel() != null
				&& !(state.block instanceof PickupBlock && !blockState.getValue(PickupBlock.AVAILABLE));

		if (!state.visible) {
			return;
		}

		AnimationSet animations = model.get().animations();
		float now = (entity.getLevel().getGameTime() + partialTicks) / 20.0F;

		if (state.block instanceof ResupplyBlock locker) {
			state.facing = blockState.getValue(ResupplyBlock.FACING);
			state.family = locker.team() == TfTeam.BLU ? SourceModels.BLU : SourceModels.RED;
			entity.setOpen(teammateNear(entity, locker.team()), now);

			if (entity.changedAt() == Float.NEGATIVE_INFINITY) {
				state.sequence = "idle";
				state.cycle = 0.0F;
			} else {
				state.sequence = entity.open() ? "open" : "close";
				state.cycle = Math.clamp((now - entity.changedAt()) / duration(animations, state.sequence), 0.0F, 1.0F);
			}
		} else {
			// У аптечек и патронов в модели одна анимация, ожидание; она идёт по кругу.
			state.sequence = "idle";
			float position = now / duration(animations, state.sequence);
			state.cycle = position - (float) Math.floor(position);
		}
	}

	/** Есть ли рядом со шкафом живой игрок его команды. */
	private static boolean teammateNear(PropBlockEntity entity, TfTeam team) {
		Vec3 center = Vec3.atBottomCenterOf(entity.getBlockPos());

		for (Player player : entity.getLevel().players()) {
			if (player.isAlive() && !player.isSpectator() && Teams.get(player).orElse(null) == team
					&& player.position().closerThan(center, LOCKER_OPEN_DISTANCE)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		Optional<SourceModels.AnimatedProp> model = state.visible ? PropModels.forBlock(state.block) : Optional.empty();

		if (model.isEmpty()) {
			return;
		}

		SourceModels.SkinnedMesh mesh = model.get().mesh();
		AnimationSet animations = model.get().animations();
		Optional<AnimationSet.Sequence> sequence = animations.sequence(state.sequence);
		Pose pose = sequence.map(seq -> animations.sampleWithLayers(seq, state.cycle, new float[animations.parameters().size()]))
				.orElseGet(() -> Pose.rest(mesh.bones()));

		poseStack.pushPose();
		poseStack.translate(0.5F, 0.0F, 0.5F);
		// После анимации предмет TF2, как и персонаж, смотрит по оси x Source, а в осях Minecraft —
		// в −z, на север (измерено на шкафу: дверцы открываются в +x). Поворот блока разворачивает
		// его к своей стороне: north — 0°, south — 180°.
		poseStack.rotateDegrees(Axis.YP, 180.0F - state.facing.toYRot());
		SourcePlayerRenderer.submit(collector, poseStack, mesh, mesh.skin(pose.skinMatrices(mesh.bones())), state.family, state.lightCoords,
				OverlayTexture.NO_OVERLAY);
		poseStack.popPose();
	}

	/** Шкаф выше своего блока: его нельзя отбрасывать, когда сам блок вне экрана. */
	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	private static float duration(AnimationSet animations, String name) {
		return animations.sequence(name).map(sequence -> {
			AnimationSet.Animation animation = animations.animations().get(sequence.animation(0, 0));
			return Math.max(1, animation.frames() - 1) / Math.max(1.0F, animation.fps());
		}).orElse(1.0F);
	}
}

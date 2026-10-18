package io.github.egorka2000.tf2mc.block;

import java.util.Optional;

import io.github.egorka2000.tf2mc.clazz.PendingChoice;
import io.github.egorka2000.tf2mc.clazz.PlayerClasses;
import io.github.egorka2000.tf2mc.clazz.TfClass;
import io.github.egorka2000.tf2mc.team.Teams;
import io.github.egorka2000.tf2mc.team.TfTeam;
import io.github.egorka2000.tf2mc.weapon.ClipWeapon;
import io.github.egorka2000.tf2mc.weapon.RocketLauncher;
import io.github.egorka2000.tf2mc.weapon.Tf2Sounds;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Шкаф пополнения команды. Срабатывает, когда игрок своей команды подходит к нему вплотную
 * (как в TF2, где шкаф открывается сам), или по нажатию.
 *
 * <p>Применяет запомненные класс и команду ({@link PendingChoice}), восполняет здоровье до
 * максимума класса, тушит огонь и заполняет боезапас целиком, включая обойму. Оверхил не
 * трогает. Чужой команде и нейтральным не служит.
 */
public final class ResupplyBlock extends Block implements EntityBlock {
	/** Куда смотрят дверцы шкафа: к игроку, который его поставил. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	private final TfTeam team;

	public ResupplyBlock(TfTeam team, Properties properties) {
		super(properties);
		this.team = team;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	/** Команда, чей это шкаф. */
	public TfTeam team() {
		return team;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	/** Сущность нужна клиенту, чтобы рисовать шкаф моделью TF2 из пака и открывать его дверцы. */
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new PropBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (player instanceof ServerPlayer serverPlayer) {
			resupply(serverPlayer.level(), pos, serverPlayer);
		}

		return InteractionResult.SUCCESS;
	}

	/**
	 * Пополняет игрока, если он из команды шкафа.
	 *
	 * @return изменилось ли что-нибудь
	 */
	public boolean resupply(ServerLevel level, BlockPos pos, ServerPlayer player) {
		if (Teams.get(player).orElse(null) != team) {
			return false;
		}

		boolean changed = PendingChoice.applyNow(player);
		Optional<TfClass> tfClass = PlayerClasses.get(player);

		if (tfClass.isPresent()) {
			if (player.getHealth() < player.getMaxHealth()) {
				player.setHealth(player.getMaxHealth());
				changed = true;
			}

			if (player.isOnFire()) {
				player.clearFire();
				changed = true;
			}

			ClipWeapon.Ammo before = RocketLauncher.ammo(player);
			RocketLauncher.refill(player);
			changed |= before != null && !before.equals(RocketLauncher.ammo(player));
		}

		// Звук — только когда шкаф действительно что-то сделал: игрок может стоять рядом долго.
		if (changed) {
			level.playSound(null, pos, Tf2Sounds.REGENERATE_TOUCH, SoundSource.BLOCKS, 1.0F, 1.0F);
		}

		return changed;
	}
}

package io.github.egorka2000.tf2mc.block;

import java.util.Optional;

import io.github.egorka2000.tf2mc.clazz.PendingChoice;
import io.github.egorka2000.tf2mc.clazz.PlayerClasses;
import io.github.egorka2000.tf2mc.clazz.TfClass;
import io.github.egorka2000.tf2mc.team.Teams;
import io.github.egorka2000.tf2mc.team.TfTeam;
import io.github.egorka2000.tf2mc.weapon.RocketLauncher;
import io.github.egorka2000.tf2mc.weapon.Tf2Sounds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Шкаф пополнения команды. Срабатывает, когда игрок своей команды подходит к нему вплотную
 * (как в TF2, где шкаф открывается сам), или по нажатию.
 *
 * <p>Применяет запомненные класс и команду ({@link PendingChoice}), восполняет здоровье до
 * максимума класса, тушит огонь и заполняет боезапас целиком, включая обойму. Оверхил не
 * трогает. Чужой команде и нейтральным не служит.
 */
public final class ResupplyBlock extends Block {
	private final TfTeam team;

	public ResupplyBlock(TfTeam team, Properties properties) {
		super(properties);
		this.team = team;
	}

	/** Команда, чей это шкаф. */
	public TfTeam team() {
		return team;
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

			RocketLauncher.Ammo before = RocketLauncher.ammo(player);
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

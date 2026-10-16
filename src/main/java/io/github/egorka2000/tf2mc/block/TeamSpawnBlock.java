package io.github.egorka2000.tf2mc.block;

import io.github.egorka2000.tf2mc.team.TeamSpawns;
import io.github.egorka2000.tf2mc.team.TfTeam;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Точка возрождения команды. Игрок команды после смерти и при вступлении в команду появляется
 * на одной из её точек, стоя на блоке. Список точек ведёт {@link TeamSpawns}.
 */
public final class TeamSpawnBlock extends Block {
	private final TfTeam team;

	public TeamSpawnBlock(TfTeam team, Properties properties) {
		super(properties);
		this.team = team;
	}

	/** Команда, чья это точка. */
	public TfTeam team() {
		return team;
	}

	/** Поставленный блок записывается в список точек. Убранный вычёркивается при следующем выборе точки. */
	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);

		if (level instanceof ServerLevel serverLevel && !oldState.is(this)) {
			TeamSpawns.add(serverLevel, pos);
		}
	}
}

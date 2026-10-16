package io.github.egorka2000.tf2mc.block;

import io.github.egorka2000.tf2mc.team.Teams;
import io.github.egorka2000.tf2mc.team.TfTeam;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Дверь зоны возрождения: игроки своей команды проходят сквозь неё, для остальных это стена.
 * Из блоков-дверей строитель собирает проход нужного размера. Временный вид — цветное стекло.
 *
 * <p>Столкновения считаются и на клиенте: движение своего игрока считает клиент. Команда игрока
 * клиенту известна — игра рассылает таблицу счёта, — поэтому клиент и сервер решают одинаково.
 * Снаряды дверь не пропускает.
 */
public final class SpawnDoorBlock extends TransparentBlock {
	private final TfTeam team;

	public SpawnDoorBlock(TfTeam team, Properties properties) {
		super(properties);
		this.team = team;
	}

	/** Команда, чью зону возрождения закрывает дверь. */
	public TfTeam team() {
		return team;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		if (context instanceof EntityCollisionContext entityContext
				&& entityContext.getEntity() instanceof Player player
				&& Teams.get(player).orElse(null) == team) {
			return Shapes.empty();
		}

		return Shapes.block();
	}
}

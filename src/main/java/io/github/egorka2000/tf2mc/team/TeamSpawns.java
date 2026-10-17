package io.github.egorka2000.tf2mc.team;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.block.TeamSpawnBlock;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Точки возрождения команд: где они стоят и как туда попадает игрок.
 *
 * <p>Список точек хранится в данных каждого мира через Attachment API и сохраняется вместе
 * с миром. Блок вносит себя в список, когда его ставят; сломанные блоки вычёркиваются при
 * выборе точки, поэтому отдельно следить за разрушением не нужно.
 */
public final class TeamSpawns {
	/** Места блоков точек возрождения в этом мире, обеих команд вперемешку. */
	public static final AttachmentType<List<BlockPos>> POSITIONS = AttachmentRegistry.create(
			Tf2Mc.id("team_spawns"),
			builder -> builder.persistent(BlockPos.CODEC.listOf()));

	private TeamSpawns() {
	}

	/**
	 * Вызывается при запуске мода. Тип данных {@link #POSITIONS} должен быть известен Fabric до
	 * загрузки мира: иначе сохранённый список точек при загрузке выбрасывается («unknown attachment
	 * type»), и игроки возрождаются не на своих точках. При загрузке чанка точки, которых нет в
	 * списке, дописываются: так список восстанавливается после такой потери и на картах, где блоки
	 * поставлены не руками (структуры, схематики).
	 */
	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> rediscover(level, chunk));
	}

	/** Дописывает в список точки возрождения из чанка, которых там нет. Открыт для тестов. */
	public static void rediscover(ServerLevel level, LevelChunk chunk) {
		LevelChunkSection[] sections = chunk.getSections();

		for (int index = 0; index < sections.length; index++) {
			LevelChunkSection section = sections[index];

			if (section.hasOnlyAir() || !section.maybeHas(state -> state.getBlock() instanceof TeamSpawnBlock)) {
				continue;
			}

			int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));

			for (int y = 0; y < SectionPos.SECTION_SIZE; y++) {
				for (int z = 0; z < SectionPos.SECTION_SIZE; z++) {
					for (int x = 0; x < SectionPos.SECTION_SIZE; x++) {
						if (section.getBlockState(x, y, z).getBlock() instanceof TeamSpawnBlock) {
							add(level, new BlockPos(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z));
						}
					}
				}
			}
		}
	}

	/** Точка возрождения: мир и блок, на котором появляется игрок. */
	public record Spawn(ServerLevel level, BlockPos block) {
		/** Где стоит появившийся игрок: на середине верхней грани блока. */
		public Vec3 standingPosition() {
			return Vec3.atBottomCenterOf(block.above());
		}
	}

	/** Вносит блок в список точек мира. */
	public static void add(ServerLevel level, BlockPos pos) {
		List<BlockPos> positions = new ArrayList<>(level.getAttachedOrElse(POSITIONS, List.of()));

		if (!positions.contains(pos)) {
			positions.add(pos.immutable());
			level.setAttached(POSITIONS, positions);
		}
	}

	/**
	 * Все точки команды во всех мирах сервера. Места, где блока точки этой команды больше нет,
	 * вычёркиваются из списков.
	 */
	public static List<Spawn> of(MinecraftServer server, TfTeam team) {
		List<Spawn> spawns = new ArrayList<>();

		for (ServerLevel level : server.getAllLevels()) {
			List<BlockPos> positions = level.getAttachedOrElse(POSITIONS, List.of());
			List<BlockPos> alive = new ArrayList<>();

			for (BlockPos pos : positions) {
				if (level.getBlockState(pos).getBlock() instanceof TeamSpawnBlock block) {
					alive.add(pos);

					if (block.team() == team) {
						spawns.add(new Spawn(level, pos));
					}
				}
			}

			if (alive.size() != positions.size()) {
				level.setAttached(POSITIONS, alive);
			}
		}

		return spawns;
	}

	/** Случайная точка команды; пусто, если у команды точек нет. */
	public static Optional<Spawn> pick(MinecraftServer server, TfTeam team, RandomSource random) {
		List<Spawn> spawns = of(server, team);
		return spawns.isEmpty() ? Optional.empty() : Optional.of(spawns.get(random.nextInt(spawns.size())));
	}

	/**
	 * Переносит игрока команды на случайную её точку: он встаёт на блок точки. Игрока без
	 * команды или команду без точек не трогает.
	 *
	 * @return перенесён ли игрок
	 */
	public static boolean sendToSpawn(ServerPlayer player) {
		Optional<Spawn> spawn = spawnFor(player);

		if (spawn.isEmpty()) {
			return false;
		}

		Vec3 position = spawn.get().standingPosition();
		player.teleportTo(spawn.get().level(), position.x, position.y, position.z, Set.of(), player.getYRot(), player.getXRot(), true);
		player.setDeltaMovement(Vec3.ZERO);
		player.fallDistance = 0.0;
		return true;
	}

	/**
	 * Куда возрождается игрок команды: на случайную её точку вместо кровати или точки мира.
	 * Вызывается игрой при выборе места возрождения ({@code ServerPlayerMixin}).
	 *
	 * @return пусто, если у игрока нет команды или у команды нет точек: тогда место выбирает игра
	 */
	public static Optional<TeleportTransition> respawnTransition(ServerPlayer player, TeleportTransition.PostTeleportTransition after) {
		return spawnFor(player).map(spawn -> new TeleportTransition(spawn.level(), spawn.standingPosition(), Vec3.ZERO, player.getYRot(), player.getXRot(), after));
	}

	private static Optional<Spawn> spawnFor(ServerPlayer player) {
		return Teams.get(player).flatMap(team -> pick(player.level().getServer(), team, player.getRandom()));
	}
}

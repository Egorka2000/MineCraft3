package io.github.egorka2000.tf2mc.block;

import java.util.function.Function;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.team.TfTeam;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;

/**
 * Блоки для игры: из них строитель карты собирает базы команд. Текстуры пока ванильные.
 *
 * <p>Все блоки мода — служебные, как барьер: в режиме выживания их не сломать, ломаются
 * только в творческом режиме. Лежат на отдельной вкладке творческого инвентаря «tf2-mc».
 */
public final class Tf2Blocks {
	/** Точка возрождения команды RED. */
	public static final Block RED_SPAWN = register("red_spawn", properties -> new TeamSpawnBlock(TfTeam.RED, properties), MapColor.COLOR_RED);

	/** Точка возрождения команды BLU. */
	public static final Block BLU_SPAWN = register("blu_spawn", properties -> new TeamSpawnBlock(TfTeam.BLU, properties), MapColor.COLOR_BLUE);

	/** Аптечки: 20%, 50% и 100% максимума здоровья. */
	public static final Block SMALL_HEALTH_KIT = pickup("small_health_kit", PickupBlock.Kind.HEALTH, 0.2, 4);
	public static final Block MEDIUM_HEALTH_KIT = pickup("medium_health_kit", PickupBlock.Kind.HEALTH, 0.5, 6);
	public static final Block LARGE_HEALTH_KIT = pickup("large_health_kit", PickupBlock.Kind.HEALTH, 1.0, 9);

	/** Патроны: 20%, 50% и 100% запаса. */
	public static final Block SMALL_AMMO_PACK = pickup("small_ammo_pack", PickupBlock.Kind.AMMO, 0.2, 4);
	public static final Block MEDIUM_AMMO_PACK = pickup("medium_ammo_pack", PickupBlock.Kind.AMMO, 0.5, 6);
	public static final Block LARGE_AMMO_PACK = pickup("large_ammo_pack", PickupBlock.Kind.AMMO, 1.0, 9);

	/** Вкладка творческого инвентаря со всеми блоками мода. */
	public static final CreativeModeTab TAB = Registry.register(
			BuiltInRegistries.CREATIVE_MODE_TAB,
			Tf2Mc.id("blocks"),
			FabricCreativeModeTab.builder()
					.title(Component.translatable("itemGroup.tf2mc.blocks"))
					.icon(() -> new ItemStack(RED_SPAWN))
					.displayItems((parameters, output) -> {
						output.accept(RED_SPAWN);
						output.accept(BLU_SPAWN);
						output.accept(SMALL_HEALTH_KIT);
						output.accept(MEDIUM_HEALTH_KIT);
						output.accept(LARGE_HEALTH_KIT);
						output.accept(SMALL_AMMO_PACK);
						output.accept(MEDIUM_AMMO_PACK);
						output.accept(LARGE_AMMO_PACK);
					})
					.build());

	private Tf2Blocks() {
	}

	/** Вызывается при запуске мода: регистрирует блоки и подбор аптечек и патронов. */
	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Tf2Blocks::touchPickups);
	}

	/** Раз в тик: игроки подбирают аптечки и патроны, в блоки которых вошли. Открыт для тестов. */
	public static void touchPickups(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator() || !player.isAlive()) {
				continue;
			}

			AABB box = player.getBoundingBox();
			ServerLevel level = player.level();

			for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
				BlockState state = level.getBlockState(pos);

				if (state.getBlock() instanceof PickupBlock pickup) {
					pickup.tryPickUp(state, level, pos.immutable(), player);
				}
			}
		}
	}

	private static Block pickup(String name, PickupBlock.Kind kind, double fraction, int height) {
		MapColor color = kind == PickupBlock.Kind.HEALTH ? MapColor.SNOW : MapColor.COLOR_GREEN;
		return register(name, properties -> new PickupBlock(kind, fraction, height, properties.noCollision().noOcclusion()), color);
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, MapColor color) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Tf2Mc.id(name));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Tf2Mc.id(name));

		// Как барьер: не ломается в выживании и не выпадает предметом.
		Block block = factory.apply(BlockBehaviour.Properties.of()
				.setId(blockKey)
				.mapColor(color)
				.strength(-1.0F, 3600000.0F)
				.noLootTable());

		Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		return block;
	}
}

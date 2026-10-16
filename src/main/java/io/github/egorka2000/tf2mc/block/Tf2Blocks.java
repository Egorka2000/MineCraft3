package io.github.egorka2000.tf2mc.block;

import java.util.function.Function;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.team.TfTeam;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

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
					})
					.build());

	private Tf2Blocks() {
	}

	/** Вызывается при запуске мода: сам вызов загружает класс и регистрирует блоки. */
	public static void register() {
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

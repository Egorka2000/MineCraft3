package io.github.egorka2000.tf2mc.client.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.block.Tf2Blocks;

import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;

import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;

/**
 * Модели TF2 спецблоков из расширительного пака: аптечки, патроны, шкафы пополнения.
 *
 * <p>Если модель есть в паке, клиент рисует её блочной сущностью ({@link PropRenderer}), а
 * временная модель блока заменяется: у аптечек и патронов остаётся только подставка, у шкафа —
 * ничего. Без пака блоки выглядят как раньше. Для двери и точки возрождения моделей в TF2 нет:
 * дверь там — часть карты, а точка возрождения невидима.
 */
public final class PropModels {
	/** Блок → модель TF2 в паке. */
	private static final Map<Block, String> MODELS = Map.of(
			Tf2Blocks.SMALL_HEALTH_KIT, "models/items/medkit_small",
			Tf2Blocks.MEDIUM_HEALTH_KIT, "models/items/medkit_medium",
			Tf2Blocks.LARGE_HEALTH_KIT, "models/items/medkit_large",
			Tf2Blocks.SMALL_AMMO_PACK, "models/items/ammopack_small",
			Tf2Blocks.MEDIUM_AMMO_PACK, "models/items/ammopack_medium",
			Tf2Blocks.LARGE_AMMO_PACK, "models/items/ammopack_large",
			Tf2Blocks.RED_RESUPPLY, "models/props_gameplay/resupply_locker",
			Tf2Blocks.BLU_RESUPPLY, "models/props_gameplay/resupply_locker");

	/** Модели блоков из мода, которые остаются вместо временных, когда рисуется модель TF2. */
	private static final Map<Block, Identifier> REPLACEMENTS = Map.of(
			Tf2Blocks.SMALL_HEALTH_KIT, Tf2Mc.id("block/pickup_base"),
			Tf2Blocks.MEDIUM_HEALTH_KIT, Tf2Mc.id("block/pickup_base"),
			Tf2Blocks.LARGE_HEALTH_KIT, Tf2Mc.id("block/pickup_base"),
			Tf2Blocks.SMALL_AMMO_PACK, Tf2Mc.id("block/pickup_base"),
			Tf2Blocks.MEDIUM_AMMO_PACK, Tf2Mc.id("block/pickup_base"),
			Tf2Blocks.LARGE_AMMO_PACK, Tf2Mc.id("block/pickup_base"),
			Tf2Blocks.RED_RESUPPLY, Tf2Mc.id("block/red_resupply_tf2"),
			Tf2Blocks.BLU_RESUPPLY, Tf2Mc.id("block/blu_resupply_tf2"));

	private static final Map<Block, SourceModels.AnimatedProp> LOADED = new HashMap<>();

	private PropModels() {
	}

	/** Подключает замену моделей блоков при загрузке ресурсов. */
	public static void register() {
		PreparableModelLoadingPlugin.register(
				(state, executor) -> CompletableFuture.supplyAsync(() -> blocksWithModels(state.resourceManager()), executor),
				(blocks, context) -> {
					for (Block block : blocks) {
						Identifier replacement = REPLACEMENTS.get(block);
						context.registerBlockStateResolver(block, resolver -> block.getStateDefinition().getPossibleStates()
								.forEach(state -> resolver.setModel(state, new SingleVariant.Unbaked(new Variant(replacement)).asRoot())));
					}
				});
	}

	/** Блоки, для которых в ресурсах есть модель TF2. */
	static Set<Block> blocksWithModels(ResourceManager resources) {
		Set<Block> blocks = new HashSet<>();

		for (Map.Entry<Block, String> entry : MODELS.entrySet()) {
			if (resources.getResource(Tf2Mc.id("tf2/" + entry.getValue().toLowerCase(Locale.ROOT) + ".mdl")).isPresent()) {
				blocks.add(entry.getKey());
			}
		}

		return blocks;
	}

	/** Перечитывает модели при перезагрузке ресурсов; текстуры добавляются в {@code textures}. */
	static void reload(ResourceManager resources, TextureManager textureManager, double unitsPerBlock, List<Identifier> textures) {
		LOADED.clear();
		Map<String, SourceModels.AnimatedProp> byPath = new HashMap<>();

		for (Map.Entry<Block, String> entry : MODELS.entrySet()) {
			Optional<SourceModels.AnimatedProp> prop = Optional.ofNullable(byPath.get(entry.getValue()))
					.or(() -> SourceModels.loadAnimatedProp(resources, textureManager, entry.getValue(), unitsPerBlock, textures));
			prop.ifPresent(model -> {
				byPath.put(entry.getValue(), model);
				LOADED.put(entry.getKey(), model);
			});
		}
	}

	/** Модель TF2 блока, если она есть в паке. */
	public static Optional<SourceModels.AnimatedProp> forBlock(Block block) {
		return Optional.ofNullable(LOADED.get(block));
	}
}

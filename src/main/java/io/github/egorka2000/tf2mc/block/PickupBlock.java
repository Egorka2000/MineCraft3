package io.github.egorka2000.tf2mc.block;

import io.github.egorka2000.tf2mc.clazz.ClassHealth;
import io.github.egorka2000.tf2mc.clazz.PlayerClasses;
import io.github.egorka2000.tf2mc.weapon.Magazine;
import io.github.egorka2000.tf2mc.weapon.RocketLauncher;
import io.github.egorka2000.tf2mc.weapon.Tf2Sounds;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Аптечка или патроны на подставке. Игрок с классом подбирает их, войдя в блок, если ему это
 * нужно; через 10 секунд на подставке появляется новая. Сквозь блок можно ходить.
 *
 * <p>Числа по TF2 Wiki (05-sources.md): маленькая, средняя и большая восполняют 20%, 50% и 100%
 * максимума здоровья класса или запаса оружия. Аптечка не лечит выше максимума и тушит огонь;
 * патроны обойму не пополняют.
 */
public final class PickupBlock extends Block {
	/** Лежит ли сейчас на подставке аптечка или патроны. */
	public static final BooleanProperty AVAILABLE = BlockStateProperties.ENABLED;

	/** Через сколько тиков появляется новая: 10 секунд. */
	public static final int RESPAWN_TICKS = 200;

	/** Что лежит на подставке. */
	public enum Kind {
		HEALTH,
		AMMO
	}

	private final Kind kind;
	private final double fraction;
	private final VoxelShape availableShape;
	private final VoxelShape emptyShape;

	/**
	 * @param fraction доля максимума здоровья или запаса, которую восполняет подбор
	 * @param height   высота коробки, шестнадцатые доли блока
	 */
	public PickupBlock(Kind kind, double fraction, int height, Properties properties) {
		super(properties);
		this.kind = kind;
		this.fraction = fraction;
		this.availableShape = Block.box(3.0, 0.0, 3.0, 13.0, height, 13.0);
		this.emptyShape = Block.box(3.0, 0.0, 3.0, 13.0, 1.0, 13.0);
		registerDefaultState(stateDefinition.any().setValue(AVAILABLE, true));
	}

	public Kind kind() {
		return kind;
	}

	public double fraction() {
		return fraction;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(AVAILABLE);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return state.getValue(AVAILABLE) ? availableShape : emptyShape;
	}

	/**
	 * Игрок вошёл в блок: подбирает аптечку или патроны, если они лежат и нужны ему.
	 *
	 * @return подобрано ли
	 */
	public boolean tryPickUp(BlockState state, ServerLevel level, BlockPos pos, ServerPlayer player) {
		if (!state.getValue(AVAILABLE) || PlayerClasses.get(player).isEmpty() || !apply(player)) {
			return false;
		}

		level.setBlock(pos, state.setValue(AVAILABLE, false), Block.UPDATE_ALL);
		level.scheduleTick(pos, this, RESPAWN_TICKS);
		play(level, pos, kind == Kind.HEALTH ? Tf2Sounds.HEALTH_KIT_TOUCH : Tf2Sounds.AMMO_PACK_TOUCH);
		return true;
	}

	/** Восполняет игроку здоровье или запас. @return {@code false}, если восполнять нечего */
	private boolean apply(ServerPlayer player) {
		if (kind == Kind.HEALTH) {
			// Аптечка нужна раненому; горящему — даже с полным здоровьем: она тушит огонь.
			if (player.getHealth() >= player.getMaxHealth() && !player.isOnFire()) {
				return false;
			}

			ClassHealth.heal(player, (float) (player.getMaxHealth() * fraction), false);
			player.clearFire();
			return true;
		}

		// Пока оружие с запасом одно — ракетница. С новым оружием патроны пополнят запас каждого.
		RocketLauncher.Ammo ammo = RocketLauncher.ammo(player);

		if (ammo == null || ammo.reserve() >= Magazine.Rules.ROCKET_LAUNCHER.reserveSize()) {
			return false;
		}

		RocketLauncher.changeReserve(player, (int) Math.round(Magazine.Rules.ROCKET_LAUNCHER.reserveSize() * fraction), true);
		return true;
	}

	/** Прошло 10 секунд: на подставке появляется новая. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!state.getValue(AVAILABLE)) {
			level.setBlock(pos, state.setValue(AVAILABLE, true), Block.UPDATE_ALL);
			play(level, pos, Tf2Sounds.ITEM_MATERIALIZE);
		}
	}

	private static void play(ServerLevel level, BlockPos pos, SoundEvent sound) {
		level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
	}
}

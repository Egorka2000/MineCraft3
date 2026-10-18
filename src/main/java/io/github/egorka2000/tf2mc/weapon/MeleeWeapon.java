package io.github.egorka2000.tf2mc.weapon;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.clazz.PlayerClasses;
import io.github.egorka2000.tf2mc.net.Tf2Networking;
import io.github.egorka2000.tf2mc.team.Teams;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Оружие ближнего боя: лопата. Замах — правая кнопка мыши, удар — через задержку, по тому, куда
 * игрок смотрит в момент удара ({@link Swing}). Сначала луч от глаз на длину удара; если он ни во
 * что не попал — тот же путь объёмом (кубом со стороной {@code 2 × hull}), чтобы удар задевал
 * цель, на которую прицел смотрит чуть мимо. Урон без поправки на расстояние.
 *
 * <p>Боезапаса нет; паузы живут только в памяти сервера.
 */
public final class MeleeWeapon {
	/**
	 * Числа удара.
	 *
	 * @param damage урон
	 * @param range  длина удара от глаз, u
	 * @param hull   половина стороны куба, которым проверяется удар после промаха лучом, u
	 */
	public record Reach(double damage, double range, double hull) {
	}

	private final Item item;
	private final Swing.Rules rules;
	private final Reach reach;
	private final ResourceKey<DamageType> damageType;
	private final SoundEvent missSound;
	private final SoundEvent hitWorldSound;
	private final SoundEvent hitFleshSound;

	private final Map<UUID, Swing> states = new ConcurrentHashMap<>();

	/** У кого оружие уже в руке. Взятое в руку заново, оно сначала достаётся. */
	private final Set<UUID> held = ConcurrentHashMap.newKeySet();

	/** Регистрирует предмет. Создавать при загрузке мода. */
	public MeleeWeapon(String itemId, Swing.Rules rules, Reach reach, ResourceKey<DamageType> damageType, SoundEvent missSound,
			SoundEvent hitWorldSound, SoundEvent hitFleshSound) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Tf2Mc.id(itemId));
		this.item = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)));
		this.rules = rules;
		this.reach = reach;
		this.damageType = damageType;
		this.missSound = missSound;
		this.hitWorldSound = hitWorldSound;
		this.hitFleshSound = hitFleshSound;
	}

	public Item item() {
		return item;
	}

	public void register() {
		UseItemCallback.EVENT.register(this::useItem);
		ServerTickEvents.END_SERVER_TICK.register(this::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			states.remove(handler.getPlayer().getUUID());
			held.remove(handler.getPlayer().getUUID());
		});
	}

	/** Выдаёт оружие; второе не выдаёт. */
	public void equip(ServerPlayer player) {
		if (!player.getInventory().contains(stack -> stack.is(item))) {
			player.getInventory().add(new ItemStack(item));
		}
	}

	/** Забирает оружие. */
	public void unequip(ServerPlayer player) {
		player.getInventory().clearOrCountMatchingItems(stack -> stack.is(item), false, -1, player.inventoryMenu.getCraftSlots());
		states.remove(player.getUUID());
		held.remove(player.getUUID());
	}

	/**
	 * Замах. Удар придёт через задержку, в {@link #tick}.
	 *
	 * @return прошёл ли замах: оружие в руке, достано, пауза кончилась, у игрока есть класс
	 */
	public boolean swing(ServerPlayer player) {
		if (!player.getMainHandItem().is(item) || PlayerClasses.get(player).isEmpty()) {
			return false;
		}

		Swing state = deployIfDrawn(player, states.getOrDefault(player.getUUID(), Swing.READY));
		states.put(player.getUUID(), state);

		if (!state.canSwing()) {
			return false;
		}

		states.put(player.getUUID(), state.swing(rules));
		play(player, missSound);
		Tf2Networking.weaponAction(player, WeaponAction.FIRE);
		return true;
	}

	/** Раз в тик сервера: паузы, доставание и удары после замаха. */
	public void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Swing state = states.get(player.getUUID());
			boolean inHand = player.getMainHandItem().is(item);

			if (state == null) {
				if (inHand) {
					states.put(player.getUUID(), deployIfDrawn(player, Swing.READY));
				}

				continue;
			}

			state = inHand ? deployIfDrawn(player, state) : state.holster();

			if (!inHand) {
				held.remove(player.getUUID());
			}

			Swing.Tick result = state.tick();
			states.put(player.getUUID(), result.next());

			if (result.hit() && PlayerClasses.get(player).isPresent()) {
				smack(player);
			}
		}
	}

	/** Удар: первая цель на пути, иначе блок, иначе промах (звук промаха уже был при замахе). */
	void smack(ServerPlayer player) {
		ServerLevel level = player.level();
		double unitsPerBlock = Tf2Mc.config().current().unitsPerBlock();
		Vec3 from = player.getEyePosition();
		Vec3 to = from.add(player.getLookAngle().scale(reach.range() / unitsPerBlock));
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		LivingEntity target = target(player, from, end, 0.0);

		if (target == null && block.getType() == HitResult.Type.MISS) {
			target = target(player, from, end, reach.hull() / unitsPerBlock);
		}

		if (target != null) {
			target.hurtServer(level, level.damageSources().source(damageType, player, player), (float) reach.damage());
			play(player, hitFleshSound);
		} else if (block.getType() == HitResult.Type.BLOCK) {
			play(player, hitWorldSound);
		}
	}

	/** Ближайшая цель, чей хитбокс, раздутый на {@code grow} блоков, пересекает путь удара. */
	private static LivingEntity target(ServerPlayer player, Vec3 from, Vec3 to, double grow) {
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;

		for (Entity entity : player.level().getEntities(player, new AABB(from, to).inflate(grow + 1.0), entity -> canHit(player, entity))) {
			AABB box = entity.getBoundingBox().inflate(grow);
			double distance = box.contains(from) ? 0.0 : box.clip(from, to).map(from::distanceToSqr).orElse(Double.MAX_VALUE);

			if (distance < bestDistance) {
				bestDistance = distance;
				best = (LivingEntity) entity;
			}
		}

		return best;
	}

	/** Удар не задевает самого игрока, наблюдателей и тех, кого он ранить не может. */
	private static boolean canHit(ServerPlayer player, Entity entity) {
		if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity.isSpectator() || !entity.isPickable()) {
			return false;
		}

		return !(entity instanceof Player target) || Teams.canFight(player, target);
	}

	private InteractionResult useItem(Player player, Level level, InteractionHand hand) {
		if (!player.getItemInHand(hand).is(item) || player.isSpectator()) {
			return InteractionResult.PASS;
		}

		if (player instanceof ServerPlayer swinger) {
			return swing(swinger) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}

		return InteractionResult.SUCCESS;
	}

	private Swing deployIfDrawn(ServerPlayer player, Swing state) {
		return held.add(player.getUUID()) ? state.deploy(rules) : state;
	}

	/** Звук слышат все вокруг; привязан к игроку, как у оружия с обоймой. */
	private static void play(ServerPlayer player, SoundEvent sound) {
		player.level().playSound(null, player, sound, SoundSource.PLAYERS, 1.0F, 1.0F);
	}
}

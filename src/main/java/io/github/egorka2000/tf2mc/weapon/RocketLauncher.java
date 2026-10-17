package io.github.egorka2000.tf2mc.weapon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.clazz.PlayerClasses;
import io.github.egorka2000.tf2mc.net.Tf2Networking;
import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Ракетница Soldier: предмет, боезапас и стрельба. Правила обоймы и перезарядки — в {@link Magazine}.
 *
 * <p>Боезапас принадлежит игроку, а не предмету: обойма и запас лежат в данных игрока,
 * сохраняются и рассылаются его клиенту для показа. Паузы и ход перезарядки живут только
 * в памяти сервера.
 */
public final class RocketLauncher {
	private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, Tf2Mc.id("rocket_launcher"));

	/** Предмет-ракетница. Выдаётся вместе с классом. */
	public static final Item ITEM = Registry.register(
			BuiltInRegistries.ITEM, ITEM_KEY, new Item(new Item.Properties().setId(ITEM_KEY).stacksTo(1)));

	/**
	 * Боезапас, который видит клиент.
	 *
	 * @param clip    патронов в обойме
	 * @param reserve патронов в запасе
	 */
	public record Ammo(int clip, int reserve) {
		static final Codec<Ammo> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.INT.fieldOf("clip").forGetter(Ammo::clip),
				Codec.INT.fieldOf("reserve").forGetter(Ammo::reserve)).apply(instance, Ammo::new));

		static final StreamCodec<ByteBuf, Ammo> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Ammo::clip, ByteBufCodecs.VAR_INT, Ammo::reserve, Ammo::new);
	}

	/**
	 * Обойма и запас игрока. Отсутствие значения означает, что ракетницы у игрока нет.
	 *
	 * <p>Через смерть значение не переносится: после перерождения оружие выдаётся заново с полным
	 * боезапасом, а перенос старого значения затёр бы его.
	 */
	public static final AttachmentType<Ammo> AMMO = AttachmentRegistry.create(
			Tf2Mc.id("rocket_ammo"),
			builder -> builder
					.persistent(Ammo.CODEC)
					.syncWith(Ammo.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	private static final Magazine.Rules RULES = Magazine.Rules.ROCKET_LAUNCHER;

	/** Паузы и ход перезарядки по игрокам. */
	private static final Map<UUID, Magazine> STATES = new ConcurrentHashMap<>();

	private RocketLauncher() {
	}

	public static void register() {
		UseItemCallback.EVENT.register(RocketLauncher::useItem);
		ServerTickEvents.END_SERVER_TICK.register(RocketLauncher::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> STATES.remove(handler.getPlayer().getUUID()));
	}

	/** Выдаёт игроку ракетницу с полным боезапасом; вторую не выдаёт. */
	public static void equip(ServerPlayer player) {
		if (!player.getInventory().contains(stack -> stack.is(ITEM))) {
			player.getInventory().add(new ItemStack(ITEM));
		}

		store(player, RULES.full());
	}

	/** Забирает у игрока ракетницу и боезапас. */
	public static void unequip(ServerPlayer player) {
		player.getInventory().clearOrCountMatchingItems(stack -> stack.is(ITEM), false, -1, player.inventoryMenu.getCraftSlots());
		player.removeAttached(AMMO);
		STATES.remove(player.getUUID());
	}

	/** Обойма и запас игрока; {@code null}, если ракетницы у него нет. */
	public static Ammo ammo(Player player) {
		return player.getAttached(AMMO);
	}

	/**
	 * Выстрел из ракетницы: тратит ракету из обоймы.
	 *
	 * @return выпущена ли ракета
	 */
	public static boolean fire(ServerPlayer player) {
		Magazine magazine = state(player);

		if (magazine == null || !magazine.canFire() || PlayerClasses.get(player).isEmpty()) {
			return false;
		}

		store(player, magazine.fire(RULES));
		RocketEntity.fire(player.level(), player);
		play(player, Tf2Sounds.ROCKET_LAUNCHER_SHOOT);
		Tf2Networking.weaponAction(player, WeaponAction.FIRE);
		return true;
	}

	/**
	 * Полная обойма и полный запас.
	 *
	 * @return {@code false}, если ракетницы у игрока нет
	 */
	public static boolean refill(ServerPlayer player) {
		if (state(player) == null) {
			return false;
		}

		store(player, RULES.full());
		return true;
	}

	/**
	 * Меняет запас: выставляет значение или прибавляет к текущему. Обойма не меняется.
	 *
	 * @param add прибавить к текущему запасу, а не заменить его
	 * @return новый боезапас; {@code null}, если ракетницы у игрока нет
	 */
	public static Ammo changeReserve(ServerPlayer player, int amount, boolean add) {
		Magazine magazine = state(player);

		if (magazine == null) {
			return null;
		}

		store(player, add ? magazine.addReserve(amount, RULES) : magazine.withReserve(amount, RULES));
		return ammo(player);
	}

	/** Раз в тик сервера: паузы после выстрела и перезарядка, если ракетница в руках. */
	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Magazine magazine = state(player);

			if (magazine != null) {
				Magazine next = player.getMainHandItem().is(ITEM) ? magazine.tick(RULES) : magazine.holsteredTick();

				store(player, next);

				// Обойма выросла: в неё только что зарядили ракету.
				if (next.clip() > magazine.clip()) {
					play(player, Tf2Sounds.ROCKET_LAUNCHER_RELOAD);
				}

				WeaponAction reload = WeaponAction.reload(magazine, next);

				if (reload != null) {
					Tf2Networking.weaponAction(player, reload);
				}
			}
		}
	}

	private static InteractionResult useItem(Player player, Level level, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (!stack.is(ITEM) || player.isSpectator()) {
			return InteractionResult.PASS;
		}

		if (player instanceof ServerPlayer shooter) {
			return fire(shooter) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}

		// На клиенте решение принимает сервер; здесь только не даём руке махать без патронов.
		Ammo ammo = ammo(player);
		return ammo != null && ammo.clip() > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}

	/**
	 * Звук оружия слышат все вокруг, включая самого игрока. Звук привязан к игроку и движется
	 * вместе с ним: звук в неподвижной точке выстрела быстрый игрок оставлял бы позади, и свой
	 * выстрел слышался бы сбоку или сзади.
	 */
	private static void play(ServerPlayer player, SoundEvent sound) {
		player.level().playSound(null, player, sound, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	/** Состояние обоймы игрока; после перезахода восстанавливается по сохранённому боезапасу. */
	private static Magazine state(ServerPlayer player) {
		Magazine known = STATES.get(player.getUUID());

		if (known != null) {
			return known;
		}

		Ammo saved = ammo(player);
		return saved == null ? null : new Magazine(saved.clip(), saved.reserve(), 0, 0, 0);
	}

	/** Запоминает состояние; клиенту боезапас уходит, только когда изменились обойма или запас. */
	private static void store(ServerPlayer player, Magazine magazine) {
		STATES.put(player.getUUID(), magazine);

		Ammo shown = ammo(player);

		if (shown == null || shown.clip() != magazine.clip() || shown.reserve() != magazine.reserve()) {
			player.setAttached(AMMO, new Ammo(magazine.clip(), magazine.reserve()));
		}
	}
}

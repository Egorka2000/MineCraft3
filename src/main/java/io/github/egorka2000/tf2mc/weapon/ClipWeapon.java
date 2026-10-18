package io.github.egorka2000.tf2mc.weapon;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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
 * Оружие с обоймой и запасом: ракетница, дробовик. Предмет, боезапас, паузы, перезарядка по одному
 * патрону и доставание — общие; чем стреляет оружие, задаёт {@code shoot}. Правила обоймы — в
 * {@link Magazine}.
 *
 * <p>Боезапас принадлежит игроку, а не предмету: обойма и запас лежат в данных игрока,
 * сохраняются и рассылаются его клиенту для показа. Паузы и ход перезарядки живут только
 * в памяти сервера.
 */
public final class ClipWeapon {
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

	private final Item item;
	private final AttachmentType<Ammo> ammo;
	private final Magazine.Rules rules;
	private final SoundEvent shootSound;
	private final SoundEvent reloadSound;
	private final Consumer<ServerPlayer> shoot;

	/** Паузы и ход перезарядки по игрокам. */
	private final Map<UUID, Magazine> states = new ConcurrentHashMap<>();

	/** У кого оружие уже в руке. Взятое в руку заново, оно сначала достаётся ({@link Magazine#deploy}). */
	private final Set<UUID> held = ConcurrentHashMap.newKeySet();

	/**
	 * Регистрирует предмет и тип данных боезапаса. Создавать при загрузке мода: тип данных должен
	 * быть известен Fabric до загрузки мира и игроков.
	 *
	 * <p>Через смерть боезапас не переносится: после перерождения оружие выдаётся заново с полным
	 * боезапасом, а перенос старого значения затёр бы его.
	 *
	 * @param itemId  имя предмета
	 * @param ammoId  имя данных боезапаса в данных игрока
	 * @param shoot   выстрел: ракета, дробь
	 */
	public ClipWeapon(String itemId, String ammoId, Magazine.Rules rules, SoundEvent shootSound, SoundEvent reloadSound, Consumer<ServerPlayer> shoot) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Tf2Mc.id(itemId));
		this.item = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1)));
		this.ammo = AttachmentRegistry.create(Tf2Mc.id(ammoId), builder -> builder
				.persistent(Ammo.CODEC)
				.syncWith(Ammo.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
		this.rules = rules;
		this.shootSound = shootSound;
		this.reloadSound = reloadSound;
		this.shoot = shoot;
	}

	public Item item() {
		return item;
	}

	public Magazine.Rules rules() {
		return rules;
	}

	/** Подключает стрельбу правой кнопкой и ход пауз и перезарядки. */
	public void register() {
		UseItemCallback.EVENT.register(this::useItem);
		ServerTickEvents.END_SERVER_TICK.register(this::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			states.remove(handler.getPlayer().getUUID());
			held.remove(handler.getPlayer().getUUID());
		});
	}

	/** Выдаёт игроку оружие с полным боезапасом; второе не выдаёт. */
	public void equip(ServerPlayer player) {
		if (!player.getInventory().contains(stack -> stack.is(item))) {
			player.getInventory().add(new ItemStack(item));
		}

		store(player, rules.full());
	}

	/** Забирает у игрока оружие и боезапас. */
	public void unequip(ServerPlayer player) {
		player.getInventory().clearOrCountMatchingItems(stack -> stack.is(item), false, -1, player.inventoryMenu.getCraftSlots());
		player.removeAttached(ammo);
		states.remove(player.getUUID());
		held.remove(player.getUUID());
	}

	/** Обойма и запас игрока; {@code null}, если этого оружия у него нет. */
	public Ammo ammo(Player player) {
		return player.getAttached(ammo);
	}

	/**
	 * Выстрел: тратит патрон из обоймы.
	 *
	 * @return прошёл ли выстрел
	 */
	public boolean fire(ServerPlayer player) {
		Magazine magazine = state(player);

		if (magazine != null) {
			magazine = deployIfDrawn(player, magazine);
			store(player, magazine);
		}

		if (magazine == null || !magazine.canFire() || PlayerClasses.get(player).isEmpty()) {
			return false;
		}

		store(player, magazine.fire(rules));
		shoot.accept(player);
		play(player, shootSound);
		Tf2Networking.weaponAction(player, WeaponAction.FIRE);
		return true;
	}

	/**
	 * Полная обойма и полный запас.
	 *
	 * @return {@code false}, если этого оружия у игрока нет
	 */
	public boolean refill(ServerPlayer player) {
		if (state(player) == null) {
			return false;
		}

		store(player, rules.full());
		return true;
	}

	/**
	 * Меняет запас: выставляет значение или прибавляет к текущему. Обойма не меняется.
	 *
	 * @param add прибавить к текущему запасу, а не заменить его
	 * @return новый боезапас; {@code null}, если этого оружия у игрока нет
	 */
	public Ammo changeReserve(ServerPlayer player, int amount, boolean add) {
		Magazine magazine = state(player);

		if (magazine == null) {
			return null;
		}

		store(player, add ? magazine.addReserve(amount, rules) : magazine.withReserve(amount, rules));
		return ammo(player);
	}

	/** Раз в тик сервера: паузы после выстрела и перезарядка, если оружие в руках. */
	public void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Magazine magazine = state(player);

			if (magazine != null) {
				Magazine drawn = deployIfDrawn(player, magazine);
				Magazine next = player.getMainHandItem().is(item) ? drawn.tick(rules) : drawn.holsteredTick();

				store(player, next);

				// Обойма выросла: в неё только что зарядили патрон.
				if (next.clip() > magazine.clip()) {
					play(player, reloadSound);
				}

				WeaponAction reload = WeaponAction.reload(magazine, next);

				if (reload != null) {
					Tf2Networking.weaponAction(player, reload);
				}
			}
		}
	}

	private InteractionResult useItem(Player player, Level level, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);

		if (!stack.is(item) || player.isSpectator()) {
			return InteractionResult.PASS;
		}

		if (player instanceof ServerPlayer shooter) {
			return fire(shooter) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}

		// На клиенте решение принимает сервер; здесь только не даём руке махать без патронов.
		Ammo shown = ammo(player);
		return shown != null && shown.clip() > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}

	/**
	 * Звук оружия слышат все вокруг, включая самого игрока. Звук привязан к игроку и движется
	 * вместе с ним: звук в неподвижной точке выстрела быстрый игрок оставлял бы позади, и свой
	 * выстрел слышался бы сбоку или сзади.
	 */
	private static void play(ServerPlayer player, SoundEvent sound) {
		player.level().playSound(null, player, sound, SoundSource.PLAYERS, 1.0F, 1.0F);
	}

	/**
	 * Если оружие только что взяли в руку, оно сначала достаётся: 0,5 с нельзя ни стрелять, ни
	 * заряжать, как в TF2. Иначе перезарядка, прерванная тем, что оружие убрали, продолжалась бы
	 * прямо во время доставания, и патрон появлялся бы в обойме раньше, чем руки его зарядят.
	 */
	private Magazine deployIfDrawn(ServerPlayer player, Magazine magazine) {
		if (!player.getMainHandItem().is(item)) {
			held.remove(player.getUUID());
			return magazine;
		}

		return held.add(player.getUUID()) ? magazine.deploy(rules) : magazine;
	}

	/** Состояние обоймы игрока; после перезахода восстанавливается по сохранённому боезапасу. */
	private Magazine state(ServerPlayer player) {
		Magazine known = states.get(player.getUUID());

		if (known != null) {
			return known;
		}

		Ammo saved = ammo(player);
		return saved == null ? null : new Magazine(saved.clip(), saved.reserve(), 0, 0, 0);
	}

	/** Запоминает состояние; клиенту боезапас уходит, только когда изменились обойма или запас. */
	private void store(ServerPlayer player, Magazine magazine) {
		states.put(player.getUUID(), magazine);

		Ammo shown = ammo(player);

		if (shown == null || shown.clip() != magazine.clip() || shown.reserve() != magazine.reserve()) {
			player.setAttached(ammo, new Ammo(magazine.clip(), magazine.reserve()));
		}
	}
}

package io.github.egorka2000.tf2mc.net;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.weapon.WeaponAction;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Сервер сообщает клиентам, что игрок выстрелил или перезаряжает оружие: клиент проигрывает
 * жест его персонажа. Уходит самому игроку и всем, кто его видит.
 *
 * @param entityId номер сущности игрока
 * @param action   событие оружия
 */
public record WeaponActionPayload(int entityId, WeaponAction action) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<WeaponActionPayload> TYPE = new CustomPacketPayload.Type<>(Tf2Mc.id("weapon_action"));

	public static final StreamCodec<FriendlyByteBuf, WeaponActionPayload> CODEC = CustomPacketPayload.codec(WeaponActionPayload::write, WeaponActionPayload::read);

	@Override
	public CustomPacketPayload.Type<WeaponActionPayload> type() {
		return TYPE;
	}

	private void write(FriendlyByteBuf buffer) {
		buffer.writeVarInt(entityId);
		buffer.writeVarInt(action.ordinal());
	}

	// Порядок чтения обязан совпадать с порядком записи выше.
	private static WeaponActionPayload read(FriendlyByteBuf buffer) {
		int entityId = buffer.readVarInt();
		int action = buffer.readVarInt();

		if (action < 0 || action >= WeaponAction.values().length) {
			throw new IllegalArgumentException("неизвестное событие оружия " + action);
		}

		return new WeaponActionPayload(entityId, WeaponAction.values()[action]);
	}
}

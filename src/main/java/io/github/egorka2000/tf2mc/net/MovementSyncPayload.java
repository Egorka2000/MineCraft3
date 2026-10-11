package io.github.egorka2000.tf2mc.net;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.config.Tf2Config;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Сервер сообщает клиенту, включено ли у его игрока движение TF2, и передаёт свои настройки.
 * Отправляется при входе игрока, при переключении и после {@code /tf2 reload}.
 *
 * @param enabled   включено ли движение TF2
 * @param showSpeed показывать ли спидометр
 * @param config    настройки сервера
 */
public record MovementSyncPayload(boolean enabled, boolean showSpeed, Tf2Config config) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MovementSyncPayload> TYPE = new CustomPacketPayload.Type<>(Tf2Mc.id("movement_sync"));

	public static final StreamCodec<FriendlyByteBuf, MovementSyncPayload> CODEC = CustomPacketPayload.codec(MovementSyncPayload::write, MovementSyncPayload::read);

	@Override
	public CustomPacketPayload.Type<MovementSyncPayload> type() {
		return TYPE;
	}

	private void write(FriendlyByteBuf buffer) {
		buffer.writeBoolean(enabled);
		buffer.writeBoolean(showSpeed);
		buffer.writeDouble(config.accelerate());
		buffer.writeDouble(config.airAccelerate());
		buffer.writeDouble(config.friction());
		buffer.writeDouble(config.stopSpeed());
		buffer.writeDouble(config.gravity());
		buffer.writeDouble(config.airSpeedCap());
		buffer.writeDouble(config.jumpSpeed());
		buffer.writeDouble(config.maxVelocity());
		buffer.writeDouble(config.unitsPerBlock());
		buffer.writeVarInt(config.substeps());
	}

	// Порядок чтения обязан совпадать с порядком записи выше.
	// Значения вне допустимых границ отклоняет конструктор Tf2Config.
	private static MovementSyncPayload read(FriendlyByteBuf buffer) {
		boolean enabled = buffer.readBoolean();
		boolean showSpeed = buffer.readBoolean();
		Tf2Config config = new Tf2Config(
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readDouble(),
				buffer.readVarInt());

		return new MovementSyncPayload(enabled, showSpeed, config);
	}
}

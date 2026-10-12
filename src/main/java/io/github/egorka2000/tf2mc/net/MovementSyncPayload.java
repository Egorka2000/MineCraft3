package io.github.egorka2000.tf2mc.net;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.config.Tf2Config;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Сервер передаёт клиенту свои настройки движения и состояние спидометра. Отправляется при входе
 * игрока, при переключении спидометра и после {@code /tf2 reload}. Включено ли движение TF2,
 * клиент узнаёт не отсюда, а по классу игрока.
 *
 * @param showSpeed показывать ли спидометр
 * @param config    настройки сервера
 */
public record MovementSyncPayload(boolean showSpeed, Tf2Config config) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MovementSyncPayload> TYPE = new CustomPacketPayload.Type<>(Tf2Mc.id("movement_sync"));

	public static final StreamCodec<FriendlyByteBuf, MovementSyncPayload> CODEC = CustomPacketPayload.codec(MovementSyncPayload::write, MovementSyncPayload::read);

	@Override
	public CustomPacketPayload.Type<MovementSyncPayload> type() {
		return TYPE;
	}

	private void write(FriendlyByteBuf buffer) {
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

		return new MovementSyncPayload(showSpeed, config);
	}
}

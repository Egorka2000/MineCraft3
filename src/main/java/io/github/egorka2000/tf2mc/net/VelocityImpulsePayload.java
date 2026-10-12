package io.github.egorka2000.tf2mc.net;

import io.github.egorka2000.tf2mc.Tf2Mc;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Сервер сообщает клиенту толчок, который нужно прибавить к скорости его игрока.
 *
 * <p>Движение своего игрока считает клиент, и его настоящей скорости сервер не знает. Штатный
 * пакет скорости заменил бы её целиком: игрок терял бы разгон и скорость прыжка. Поэтому
 * отправляется не новая скорость, а добавка к ней.
 *
 * @param x добавка к скорости по x Minecraft, блоков за тик
 * @param y добавка к скорости по y Minecraft (вверх), блоков за тик
 * @param z добавка к скорости по z Minecraft, блоков за тик
 */
public record VelocityImpulsePayload(double x, double y, double z) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<VelocityImpulsePayload> TYPE = new CustomPacketPayload.Type<>(Tf2Mc.id("velocity_impulse"));

	public static final StreamCodec<FriendlyByteBuf, VelocityImpulsePayload> CODEC = CustomPacketPayload.codec(VelocityImpulsePayload::write, VelocityImpulsePayload::read);

	@Override
	public CustomPacketPayload.Type<VelocityImpulsePayload> type() {
		return TYPE;
	}

	private void write(FriendlyByteBuf buffer) {
		buffer.writeDouble(x);
		buffer.writeDouble(y);
		buffer.writeDouble(z);
	}

	// Порядок чтения обязан совпадать с порядком записи выше.
	private static VelocityImpulsePayload read(FriendlyByteBuf buffer) {
		return new VelocityImpulsePayload(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
	}
}

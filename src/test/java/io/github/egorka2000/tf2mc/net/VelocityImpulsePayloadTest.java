package io.github.egorka2000.tf2mc.net;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Перепутанные оси в пакете толкнули бы игрока вбок вместо «вверх» без единой ошибки.
 */
class VelocityImpulsePayloadTest {
	@Test
	void payloadSurvivesEncodingAndDecoding() {
		VelocityImpulsePayload payload = new VelocityImpulsePayload(0.125, 0.789, -0.5);
		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

		VelocityImpulsePayload.CODEC.encode(buffer, payload);

		assertEquals(payload, VelocityImpulsePayload.CODEC.decode(buffer));
		assertEquals(0, buffer.readableBytes(), "после чтения в пакете остались лишние байты");
	}
}

package io.github.egorka2000.tf2mc.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.egorka2000.tf2mc.weapon.WeaponAction;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import net.minecraft.network.FriendlyByteBuf;

class WeaponActionPayloadTest {
	@Test
	void payloadSurvivesEncodingAndDecoding() {
		for (WeaponAction action : WeaponAction.values()) {
			WeaponActionPayload payload = new WeaponActionPayload(1234, action);
			FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

			WeaponActionPayload.CODEC.encode(buffer, payload);

			assertEquals(payload, WeaponActionPayload.CODEC.decode(buffer));
			assertEquals(0, buffer.readableBytes(), "после чтения в пакете остались лишние байты");
		}
	}

	@Test
	void unknownActionIsRejected() {
		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		buffer.writeVarInt(1);
		buffer.writeVarInt(WeaponAction.values().length);

		assertThrows(IllegalArgumentException.class, () -> WeaponActionPayload.CODEC.decode(buffer));
	}
}

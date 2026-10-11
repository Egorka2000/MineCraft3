package io.github.egorka2000.tf2mc.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.egorka2000.tf2mc.config.ConfigException;
import io.github.egorka2000.tf2mc.config.Tf2Config;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Пакет читается в том же порядке, в каком пишется: перепутанные поля дали бы клиенту
 * чужие настройки без единой ошибки.
 */
class MovementSyncPayloadTest {
	private static final Tf2Config CUSTOM = new Tf2Config(7.0, 12.0, 5.0, 80.0, 600.0, 25.0, 300.0, 2000.0, 40.0, 5);

	@Test
	void payloadSurvivesEncodingAndDecoding() {
		for (MovementSyncPayload payload : new MovementSyncPayload[] {
			new MovementSyncPayload(true, false, CUSTOM),
			new MovementSyncPayload(false, true, Tf2Config.DEFAULT),
		}) {
			FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
			MovementSyncPayload.CODEC.encode(buffer, payload);

			assertEquals(payload, MovementSyncPayload.CODEC.decode(buffer));
			assertEquals(0, buffer.readableBytes(), "после чтения в пакете остались лишние байты");
		}
	}

	@Test
	void invalidSettingsFromNetworkAreRejected() {
		FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
		buffer.writeBoolean(true);
		buffer.writeBoolean(false);

		for (int i = 0; i < 8; i++) {
			buffer.writeDouble(1.0);
		}

		// unitsPerBlock = 0 и substeps = 1000: такие значения клиент применять не должен.
		buffer.writeDouble(0.0);
		buffer.writeVarInt(1000);

		assertThrows(ConfigException.class, () -> MovementSyncPayload.CODEC.decode(buffer));
	}
}

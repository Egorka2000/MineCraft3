package io.github.egorka2000.tf2mc.client;

import java.util.Locale;

import io.github.egorka2000.tf2mc.movement.LocalMovementState;
import io.github.egorka2000.tf2mc.movement.UnitConversion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Показывает над панелью быстрого доступа горизонтальную скорость и высоту последнего прыжка
 * в единицах TF2. Нужен для замеров и сравнения с TF2; включается командой {@code /tf2 speed on}.
 */
final class Speedometer {
	private static double groundHeight;
	private static double jumpHeight;
	private static double lastSpeed;
	private static boolean wasOnGround = true;

	private Speedometer() {
	}

	/** Скорость за последний тик, u/s. Для тестов и замеров. */
	static double lastSpeed() {
		return lastSpeed;
	}

	/** Высота последнего прыжка над точкой отрыва, u. */
	static double lastJumpHeight() {
		return jumpHeight;
	}

	static void tick(Minecraft client) {
		LocalPlayer player = client.player;

		if (player == null) {
			return;
		}

		LocalMovementState.Snapshot state = LocalMovementState.get();
		double unitsPerBlock = state.config().unitsPerBlock();

		// Скорость считается по фактическому смещению за тик, а не по полю скорости:
		// так видно то, что реально произошло после столкновений.
		lastSpeed = UnitConversion.toUnitsPerSecond(Math.hypot(player.getX() - player.xo, player.getZ() - player.zo), unitsPerBlock);

		if (player.onGround()) {
			groundHeight = player.getY();
		} else {
			if (wasOnGround) {
				jumpHeight = 0.0;
			}

			jumpHeight = Math.max(jumpHeight, UnitConversion.toUnits(player.getY() - groundHeight, unitsPerBlock));
		}

		wasOnGround = player.onGround();

		if (state.showSpeed()) {
			player.sendOverlayMessage(Component.literal(String.format(Locale.ROOT, "%.0f u/s   прыжок %.1f u", lastSpeed, jumpHeight)));
		}
	}
}

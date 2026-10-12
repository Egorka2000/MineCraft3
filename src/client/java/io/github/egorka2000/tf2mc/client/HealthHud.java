package io.github.egorka2000.tf2mc.client;

import io.github.egorka2000.tf2mc.clazz.PlayerClasses;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * У игрока с классом TF2 вместо сердец показывается число здоровья, а полоски голода и брони
 * скрыты: сытости и брони в правилах TF2 нет. Простой текст; полноценный HUD — задача T5.1.
 */
public final class HealthHud {
	// Там же, где игра рисует левое сердце.
	private static final int OFFSET_FROM_CENTER = 91;
	private static final int OFFSET_FROM_BOTTOM = 39;

	private static final int COLOR_NORMAL = 0xFFFFFFFF;
	private static final int COLOR_OVERHEAL = 0xFF55FFFF;
	private static final int COLOR_LOW = 0xFFFF5555;

	/** Доля максимального здоровья, ниже которой число краснеет. */
	private static final float LOW_HEALTH = 0.5F;

	/** Что показано в последнем кадре; {@code null}, если число здоровья не рисовалось. */
	private static volatile String shownText;

	private HealthHud() {
	}

	/** Текст здоровья из последнего кадра. Нужен клиентскому игровому тесту: экран он читать не умеет. */
	public static String shownText() {
		return shownText;
	}

	static void register() {
		HudElementRegistry.replaceElement(VanillaHudElements.HEALTH_BAR, vanilla -> (graphics, delta) -> {
			shownText = null;

			if (hasClass()) {
				extractHealth(graphics);
			} else {
				vanilla.extractRenderState(graphics, delta);
			}
		});

		hideForClass(VanillaHudElements.FOOD_BAR);
		// Игра ставит броню над сердцами, а при 200 HP сердец было бы десять рядов.
		hideForClass(VanillaHudElements.ARMOR_BAR);
	}

	private static void hideForClass(Identifier element) {
		HudElementRegistry.replaceElement(element, vanilla -> (graphics, delta) -> {
			if (!hasClass()) {
				vanilla.extractRenderState(graphics, delta);
			}
		});
	}

	private static boolean hasClass() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && PlayerClasses.get(player).isPresent();
	}

	private static void extractHealth(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;

		// В творческом режиме и у наблюдателя игра здоровье не показывает.
		if (player == null || client.gameMode == null || !client.gameMode.canHurtPlayer()) {
			return;
		}

		int color = COLOR_NORMAL;

		if (player.getAbsorptionAmount() > 0.0F) {
			color = COLOR_OVERHEAL;
		} else if (player.getHealth() < player.getMaxHealth() * LOW_HEALTH) {
			color = COLOR_LOW;
		}

		// Округление вверх: пока игрок жив, ноль не показывается.
		String text = "HP " + Mth.ceil(player.getHealth() + player.getAbsorptionAmount());
		shownText = text;
		graphics.text(client.font, text, graphics.guiWidth() / 2 - OFFSET_FROM_CENTER, graphics.guiHeight() - OFFSET_FROM_BOTTOM, color);
	}
}

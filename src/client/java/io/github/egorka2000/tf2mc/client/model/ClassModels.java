package io.github.egorka2000.tf2mc.client.model;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.clazz.PlayerClasses;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;

/**
 * Модели классов: вход для остального мода.
 *
 * <p>Своей модели персонажа в моде нет: она приходит из ресурс-пака. Игрок с классом рисуется
 * моделью со скелетной анимацией, только если установлена библиотека анимации и в ресурсах есть
 * модель; иначе остаётся обычная модель игрока со скином.
 *
 * <p>Здесь нет типов библиотеки анимации в сигнатурах, а к классам, которые от неё зависят,
 * обращение идёт только когда она установлена.
 */
public final class ClassModels {
	/** Файл модели, по которому мод узнаёт, что ресурс-пак принёс модель класса. */
	public static final Identifier MODEL_FILE = Tf2Mc.id("geckolib/models/entity/class/soldier.geo.json");

	private static final boolean LIBRARY_LOADED = FabricLoader.getInstance().isModLoaded("geckolib");

	private static EntityRenderer<?, ?> renderer;

	private ClassModels() {
	}

	/** Рисуется ли сейчас игрок с классом моделью из ресурс-пака. */
	public static boolean isActive() {
		return renderer != null;
	}

	/** Игра пересоздала отрисовщики сущностей: пересоздаётся и отрисовщик моделей классов. */
	public static void reload(EntityRendererProvider.Context context, ResourceManager resources) {
		renderer = LIBRARY_LOADED && resources.getResource(MODEL_FILE).isPresent() ? new ClassPlayerRenderer(context) : null;
	}

	/** Отрисовщик для сущности; {@code null}, если её рисует игра. */
	public static EntityRenderer<?, ?> rendererFor(Entity entity) {
		return renderer != null && entity instanceof AbstractClientPlayer player && PlayerClasses.get(player).isPresent() ? renderer : null;
	}

	/** Отрисовщик для состояния отрисовки; {@code null}, если его рисует игра. */
	public static EntityRenderer<?, ?> rendererFor(EntityRenderState state) {
		return renderer != null && state instanceof ClassRenderState ? renderer : null;
	}
}

package io.github.egorka2000.tf2mc.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.clazz.PlayerClasses;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.TextureManager;
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
 * <p>Здесь нет типов библиотеки анимации даже внутри методов: к классам, которые от неё зависят,
 * обращение идёт только через {@link ClassRenderers} и только когда она установлена.
 */
public final class ClassModels {
	/** Файл модели, по которому мод узнаёт, что ресурс-пак принёс модель класса. */
	public static final Identifier MODEL_FILE = Tf2Mc.id("geckolib/models/entity/class/soldier.geo.json");

	private static final boolean LIBRARY_LOADED = FabricLoader.getInstance().isModLoaded("geckolib");

	private static EntityRenderer<?, ?> renderer;

	/** Текстуры модели TF2, зарегистрированные при прошлой перезагрузке ресурсов. */
	private static final List<Identifier> sourceTextures = new ArrayList<>();

	/** Модель ракеты TF2 из расширительного пака; пусто — ракета невидима. */
	private static Optional<SourceModels.Prop> rocket = Optional.empty();

	private ClassModels() {
	}

	/** Модель ракеты TF2, если расширительный пак подключён. */
	public static Optional<SourceModels.Prop> rocket() {
		return rocket;
	}

	/** Рисуется ли сейчас игрок с классом моделью из ресурс-пака. */
	public static boolean isActive() {
		return renderer != null;
	}

	/**
	 * Игра пересоздала отрисовщики сущностей: пересоздаётся и отрисовщик моделей классов.
	 * Первой берётся модель TF2 из расширительного пака; если её нет — модель GeckoLib из
	 * ресурс-пака; если нет и её — игрока рисует игра.
	 */
	public static void reload(EntityRendererProvider.Context context, ResourceManager resources) {
		TextureManager textureManager = Minecraft.getInstance().getTextureManager();

		for (Identifier texture : sourceTextures) {
			textureManager.release(texture);
		}

		sourceTextures.clear();
		rocket = SourceModels.loadMesh(resources, textureManager, SourceModels.ROCKET, Tf2Mc.config().current().unitsPerBlock(), sourceTextures)
				.map(SourceModels.Prop::new);
		Optional<SourceModels.PlayerMesh> soldier = SourceModels.load(
				resources, textureManager, SourceModels.SOLDIER, SourceModels.ROCKET_LAUNCHER, Tf2Mc.config().current().unitsPerBlock(), sourceTextures);

		if (soldier.isPresent()) {
			renderer = new SourcePlayerRenderer(context, soldier.get());
		} else {
			renderer = LIBRARY_LOADED && resources.getResource(MODEL_FILE).isPresent() ? ClassRenderers.create(context) : null;
		}
	}

	/** Рисуется ли игрок с классом моделью TF2 из расширительного пака. */
	public static boolean isSourceModel() {
		return renderer instanceof SourcePlayerRenderer;
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

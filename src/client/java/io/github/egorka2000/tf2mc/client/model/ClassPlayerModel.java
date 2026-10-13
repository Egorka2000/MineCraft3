package io.github.egorka2000.tf2mc.client.model;

import com.geckolib.model.DefaultedEntityGeoModel;
import io.github.egorka2000.tf2mc.Tf2Mc;

/**
 * Модель игрока с классом. Мод знает только имя ресурса, {@code tf2mc:class/soldier}; какие файлы
 * за ним стоят, решают ресурс-паки. В самом моде этих файлов нет:
 *
 * <ul>
 * <li>{@code assets/tf2mc/geckolib/models/entity/class/soldier.geo.json} — модель;
 * <li>{@code assets/tf2mc/geckolib/animations/entity/class/soldier.animation.json} — анимации;
 * <li>{@code assets/tf2mc/textures/entity/class/soldier.png} — текстура.
 * </ul>
 */
final class ClassPlayerModel extends DefaultedEntityGeoModel<ClassAnimatable> {
	ClassPlayerModel() {
		super(Tf2Mc.id("class/soldier"));
	}
}

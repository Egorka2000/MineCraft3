package io.github.egorka2000.tf2mc.client.model;

import com.geckolib.animatable.GeoReplacedEntity;
import com.geckolib.animatable.instance.AnimatableInstanceCache;
import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.util.GeckoLibUtil;

/**
 * Анимации модели класса: стойка и бег. Имена анимаций — часть договора с ресурс-паком:
 * модель из пака должна называть их так же.
 */
final class ClassAnimatable implements GeoReplacedEntity {
	/** Стойка на месте. */
	static final String IDLE = "idle";

	/** Бег. */
	static final String RUN = "run";

	private static final RawAnimation IDLE_LOOP = RawAnimation.begin().thenLoop(IDLE);
	private static final RawAnimation RUN_LOOP = RawAnimation.begin().thenLoop(RUN);

	/** Сколько тиков длится плавный переход между анимациями. */
	private static final int TRANSITION_TICKS = 3;

	private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<ClassAnimatable>("movement", TRANSITION_TICKS,
				test -> test.setAndContinue(test.isMoving() ? RUN_LOOP : IDLE_LOOP)));
	}

	@Override
	public AnimatableInstanceCache getAnimatableInstanceCache() {
		return cache;
	}
}

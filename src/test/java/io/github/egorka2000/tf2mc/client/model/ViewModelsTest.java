package io.github.egorka2000.tf2mc.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.function.ToDoubleFunction;

import io.github.egorka2000.tf2mc.weapon.WeaponAction;
import org.junit.jupiter.api.Test;

class ViewModelsTest {
	private static final float EPSILON = 1e-4F;

	/** Длительности, секунды: как у ракетницы Soldier, округлённо. */
	private static final ToDoubleFunction<String> DURATION = Map.of(
			ViewModels.IDLE, 4.0, ViewModels.DRAW, 0.8, ViewModels.FIRE, 1.0,
			ViewModels.RELOAD_START, 0.5, ViewModels.RELOAD_LOOP, 0.8, ViewModels.RELOAD_FINISH, 0.75)::get;

	private static void assertFrame(String sequence, float cycle, ViewModels.Frame frame) {
		assertEquals(sequence, frame.sequence());
		assertEquals(cycle, frame.cycle(), EPSILON);
	}

	@Test
	void drawThenIdle() {
		assertFrame(ViewModels.DRAW, 0.5F, ViewModels.choose(null, 0, true, 0.4F, 10.4F, DURATION));
		assertFrame(ViewModels.IDLE, 0.6F, ViewModels.choose(null, 0, true, 1.0F, 10.4F, DURATION));
	}

	@Test
	void fireOnceThenIdle() {
		assertFrame(ViewModels.FIRE, 0.25F, ViewModels.choose(WeaponAction.FIRE, 0.25F, false, 0, 2.0F, DURATION));
		assertFrame(ViewModels.IDLE, 0.5F, ViewModels.choose(WeaponAction.FIRE, 1.5F, false, 0, 2.0F, DURATION));
	}

	@Test
	void reloadStartFlowsIntoLoop() {
		assertFrame(ViewModels.RELOAD_START, 0.5F, ViewModels.choose(WeaponAction.RELOAD_START, 0.25F, false, 0, 0, DURATION));
		// Начало 0,5 с, дальше повтор: 0,9 с — это 0,4 с повтора, половина.
		assertFrame(ViewModels.RELOAD_LOOP, 0.5F, ViewModels.choose(WeaponAction.RELOAD_START, 0.9F, false, 0, 0, DURATION));
		assertFrame(ViewModels.RELOAD_LOOP, 0.25F, ViewModels.choose(WeaponAction.RELOAD_LOOP, 1.0F, false, 0, 0, DURATION));
	}

	@Test
	void reloadFinishThenIdle() {
		assertFrame(ViewModels.RELOAD_FINISH, 0.0F, ViewModels.choose(WeaponAction.RELOAD_END, 0, false, 0, 0, DURATION));
		assertFrame(ViewModels.IDLE, 0.25F, ViewModels.choose(WeaponAction.RELOAD_END, 1.0F, false, 0, 1.0F, DURATION));
	}

	@Test
	void stretchMatchesViewmodelFov() {
		// tan(35°) / tan(atan(tan(27°)·0,75)) ≈ 0,7002 / 0,3821.
		assertEquals(1.8325F, ViewModelRenderer.stretch(), 1e-3F);
	}
}

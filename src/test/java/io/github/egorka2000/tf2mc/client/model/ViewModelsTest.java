package io.github.egorka2000.tf2mc.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import java.util.function.ToDoubleFunction;

import io.github.egorka2000.tf2mc.weapon.WeaponAction;
import org.junit.jupiter.api.Test;

class ViewModelsTest {
	private static final float EPSILON = 1e-4F;

	/** Анимации рук как у ракетницы; предмет тесту не нужен. */
	private static final WeaponView LAUNCHER = new WeaponView(null, WeaponView.PRIMARY, "launcher",
			"dh_draw", "dh_idle", "dh_fire", "dh_reload_start", "dh_reload_loop", "dh_reload_finish");

	/** Анимации рук как у дробовика. */
	private static final WeaponView SHOTGUN = new WeaponView(null, WeaponView.SECONDARY, "shotgun",
			"draw", "idle", "fire", "reload_start", "reload_loop", "reload_end");

	/** Длительности, секунды: как у ракетницы Soldier, округлённо; у дробовика доставание короче. */
	private static final ToDoubleFunction<String> DURATION = Map.of(
			"dh_idle", 4.0, "dh_draw", 0.8, "dh_fire", 1.0, "dh_reload_start", 0.5, "dh_reload_loop", 0.8, "dh_reload_finish", 0.75,
			"draw", 0.6, "idle", 1.0, "fire", 0.8)::get;

	private static void assertFrame(String sequence, float cycle, ViewModels.Frame frame) {
		assertEquals(sequence, frame.sequence());
		assertEquals(cycle, frame.cycle(), EPSILON);
	}

	@Test
	void drawThenIdle() {
		assertFrame("dh_draw", 0.5F, ViewModels.choose(LAUNCHER, null, 0, true, 0.4F, 10.4F, DURATION));
		assertFrame("dh_idle", 0.6F, ViewModels.choose(LAUNCHER, null, 0, true, 1.0F, 10.4F, DURATION));
	}

	@Test
	void fireOnceThenIdle() {
		assertFrame("dh_fire", 0.25F, ViewModels.choose(LAUNCHER, WeaponAction.FIRE, 0.25F, false, 0, 2.0F, DURATION));
		assertFrame("dh_idle", 0.5F, ViewModels.choose(LAUNCHER, WeaponAction.FIRE, 1.5F, false, 0, 2.0F, DURATION));
	}

	@Test
	void reloadStartFlowsIntoLoop() {
		assertFrame("dh_reload_start", 0.5F, ViewModels.choose(LAUNCHER, WeaponAction.RELOAD_START, 0.25F, false, 0, 0, DURATION));
		// Начало 0,5 с, дальше повтор: 0,9 с — это 0,4 с повтора, половина.
		assertFrame("dh_reload_loop", 0.5F, ViewModels.choose(LAUNCHER, WeaponAction.RELOAD_START, 0.9F, false, 0, 0, DURATION));
		assertFrame("dh_reload_loop", 0.25F, ViewModels.choose(LAUNCHER, WeaponAction.RELOAD_LOOP, 1.0F, false, 0, 0, DURATION));
	}

	@Test
	void reloadFinishThenIdle() {
		assertFrame("dh_reload_finish", 0.0F, ViewModels.choose(LAUNCHER, WeaponAction.RELOAD_END, 0, false, 0, 0, DURATION));
		assertFrame("dh_idle", 0.25F, ViewModels.choose(LAUNCHER, WeaponAction.RELOAD_END, 1.0F, false, 0, 1.0F, DURATION));
	}

	@Test
	void reloadStartedDuringDrawWaitsForIt() {
		// Ракетницу достали, и сервер в тот же миг начал перезарядку: сначала доигрывается доставание.
		assertFrame("dh_draw", 0.375F, ViewModels.choose(LAUNCHER, WeaponAction.RELOAD_START, 0.3F, true, 0.3F, 0, DURATION));
		// Доставание 0,8 с кончилось 0,2 с назад — перезарядка идёт с этого момента: 0,2 из 0,5.
		assertFrame("dh_reload_start", 0.4F, ViewModels.choose(LAUNCHER, WeaponAction.RELOAD_START, 1.0F, true, 1.0F, 0, DURATION));
		// Событие уже после доставания играется как обычно.
		assertFrame("dh_reload_loop", 0.25F, ViewModels.choose(LAUNCHER, WeaponAction.RELOAD_LOOP, 0.2F, true, 5.0F, 0, DURATION));
	}

	@Test
	void fireInterruptsDraw() {
		ViewModels.frame(null, 9.0F, DURATION);
		ViewModels.frame(LAUNCHER, 10.0F, DURATION);
		ViewModels.onWeaponAction(WeaponAction.FIRE, 10.1F);

		assertFrame("dh_fire", 0.1F, ViewModels.frame(LAUNCHER, 10.2F, DURATION));
	}

	@Test
	void switchingWeaponsDrawsTheNewOne() {
		ViewModels.frame(LAUNCHER, 20.0F, DURATION);
		ViewModels.frame(LAUNCHER, 25.0F, DURATION);

		// Ракетницу сменили на дробовик: он достаётся своими анимациями.
		assertFrame("draw", 0.0F, ViewModels.frame(SHOTGUN, 30.0F, DURATION));
		assertFrame("draw", 0.5F, ViewModels.frame(SHOTGUN, 30.3F, DURATION));
		assertFrame("idle", 0.7F, ViewModels.frame(SHOTGUN, 30.7F, DURATION));
		assertNull(ViewModels.frame(null, 31.0F, DURATION), "без оружия мода в руке кадра нет");
	}

	@Test
	void stretchMatchesViewmodelFov() {
		// tan(35°) / tan(atan(tan(27°)·0,75)) ≈ 0,7002 / 0,3821.
		assertEquals(1.8325F, ViewModelRenderer.stretch(), 1e-3F);
	}
}

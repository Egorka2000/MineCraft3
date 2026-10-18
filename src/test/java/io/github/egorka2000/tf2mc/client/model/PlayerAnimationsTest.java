package io.github.egorka2000.tf2mc.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.egorka2000.tf2mc.client.model.PlayerAnimations.Posture;
import io.github.egorka2000.tf2mc.weapon.WeaponAction;
import org.junit.jupiter.api.Test;

class PlayerAnimationsTest {
	@Test
	void gestureDependsOnPosture() {
		assertEquals("AttackStand_PRIMARY", PlayerAnimations.gestureName(WeaponAction.FIRE, Posture.AIR));
		assertEquals("AttackCrouch_PRIMARY", PlayerAnimations.gestureName(WeaponAction.FIRE, Posture.CROUCH));
		assertEquals("ReloadStand_PRIMARY", PlayerAnimations.gestureName(WeaponAction.RELOAD_START, Posture.STAND));
		assertEquals("ReloadCrouch_PRIMARY_loop", PlayerAnimations.gestureName(WeaponAction.RELOAD_LOOP, Posture.CROUCH));
		assertEquals("ReloadAirwalk_PRIMARY_end", PlayerAnimations.gestureName(WeaponAction.RELOAD_END, Posture.AIR));
	}

	@Test
	void swimmingHasItsOwnGestures() {
		assertEquals("AttackSwim_PRIMARY", PlayerAnimations.gestureName(WeaponAction.FIRE, Posture.SWIM));
		assertEquals("ReloadSwim_PRIMARY", PlayerAnimations.gestureName(WeaponAction.RELOAD_START, Posture.SWIM));
		assertEquals("ReloadSwim_PRIMARY", PlayerAnimations.gestureName(WeaponAction.RELOAD_LOOP, Posture.SWIM));
		// Конца перезарядки в воде у TF2 нет.
		assertNull(PlayerAnimations.gestureName(WeaponAction.RELOAD_END, Posture.SWIM));
	}

	@Test
	void secondaryWeaponUsesItsOwnNames() {
		assertEquals("AttackStand_SECONDARY", PlayerAnimations.gestureName(WeaponAction.FIRE, Posture.STAND, WeaponView.SECONDARY));
		assertEquals("AttackSwim_SECONDARY", PlayerAnimations.gestureName(WeaponAction.FIRE, Posture.SWIM, WeaponView.SECONDARY));
		assertEquals("ReloadCrouch_SECONDARY_loop", PlayerAnimations.gestureName(WeaponAction.RELOAD_LOOP, Posture.CROUCH, WeaponView.SECONDARY));
		assertEquals("SECONDARY_reload_airwalk", PlayerAnimations.gestureName(WeaponAction.RELOAD_START, Posture.AIR, WeaponView.SECONDARY));
		// У второго оружия в воде есть и повтор, и конец.
		assertEquals("SECONDARY_reload_swim_end", PlayerAnimations.gestureName(WeaponAction.RELOAD_END, Posture.SWIM, WeaponView.SECONDARY));
	}
}

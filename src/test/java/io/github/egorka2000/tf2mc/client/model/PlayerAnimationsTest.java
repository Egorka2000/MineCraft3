package io.github.egorka2000.tf2mc.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.egorka2000.tf2mc.weapon.WeaponAction;
import org.junit.jupiter.api.Test;

class PlayerAnimationsTest {
	@Test
	void gestureDependsOnPosture() {
		assertEquals("AttackStand_PRIMARY", PlayerAnimations.gestureName(WeaponAction.FIRE, false, true));
		assertEquals("AttackCrouch_PRIMARY", PlayerAnimations.gestureName(WeaponAction.FIRE, true, false));
		assertEquals("ReloadStand_PRIMARY", PlayerAnimations.gestureName(WeaponAction.RELOAD_START, false, false));
		assertEquals("ReloadCrouch_PRIMARY_loop", PlayerAnimations.gestureName(WeaponAction.RELOAD_LOOP, true, false));
		assertEquals("ReloadAirwalk_PRIMARY_end", PlayerAnimations.gestureName(WeaponAction.RELOAD_END, false, true));
	}
}

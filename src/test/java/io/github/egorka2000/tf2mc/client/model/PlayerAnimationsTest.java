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
	void meleeHasItsOwnNamesAndNoReload() {
		assertEquals("Attack_Stand_MELEE", PlayerAnimations.gestureName(WeaponAction.FIRE, Posture.AIR, WeaponView.MELEE));
		assertEquals("Attack_Crouch_MELEE", PlayerAnimations.gestureName(WeaponAction.FIRE, Posture.CROUCH, WeaponView.MELEE));
		assertEquals("Melee_Swim_Swing", PlayerAnimations.gestureName(WeaponAction.FIRE, Posture.SWIM, WeaponView.MELEE));
		assertNull(PlayerAnimations.gestureName(WeaponAction.RELOAD_START, Posture.STAND, WeaponView.MELEE));
	}

	@Test
	void baseSequenceDependsOnPostureMovementAndSlot() {
		assertEquals("stand_PRIMARY", PlayerAnimations.sequenceName(Posture.STAND, false, WeaponView.PRIMARY));
		assertEquals("run_SECONDARY", PlayerAnimations.sequenceName(Posture.STAND, true, WeaponView.SECONDARY));
		assertEquals("crouch_walk_MELEE", PlayerAnimations.sequenceName(Posture.CROUCH, true, WeaponView.MELEE));
		assertEquals("swim_MELEE", PlayerAnimations.sequenceName(Posture.SWIM, false, WeaponView.MELEE));
		assertEquals("jump_float_SECONDARY", PlayerAnimations.sequenceName(Posture.AIR, false, WeaponView.SECONDARY));
		// Полёт с оружием ближнего боя у TF2 называется иначе.
		assertEquals("a_jumpfloat_MELEE", PlayerAnimations.sequenceName(Posture.AIR, false, WeaponView.MELEE));
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

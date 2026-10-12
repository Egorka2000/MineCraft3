package io.github.egorka2000.tf2mc.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class TfTeamTest {
	@Test
	void teamIsFoundByItsId() {
		for (TfTeam team : TfTeam.values()) {
			assertEquals(Optional.of(team), TfTeam.byId(team.id()));
		}
	}

	@Test
	void spectatorRandomAndUnknownAreNotTeams() {
		assertTrue(TfTeam.byId(TfTeam.SPECTATOR_ID).isEmpty());
		assertTrue(TfTeam.byId(TfTeam.RANDOM_ID).isEmpty());
		assertTrue(TfTeam.byId("green").isEmpty());
		assertTrue(TfTeam.byId("").isEmpty());
	}

	@Test
	void opponentsAreEachOther() {
		assertEquals(TfTeam.BLU, TfTeam.RED.opponent());
		assertEquals(TfTeam.RED, TfTeam.BLU.opponent());
	}

	@Test
	void randomPicksSmallerTeam() {
		assertEquals(TfTeam.RED, TeamBalance.pick(1, 3, false));
		assertEquals(TfTeam.BLU, TeamBalance.pick(4, 2, true));
	}

	@Test
	void randomUsesCoinWhenTeamsAreEqual() {
		assertEquals(TfTeam.RED, TeamBalance.pick(2, 2, true));
		assertEquals(TfTeam.BLU, TeamBalance.pick(2, 2, false));
		assertEquals(TfTeam.RED, TeamBalance.pick(0, 0, true));
	}
}

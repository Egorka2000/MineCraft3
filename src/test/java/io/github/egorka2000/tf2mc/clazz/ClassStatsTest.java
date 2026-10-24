package io.github.egorka2000.tf2mc.clazz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ClassStatsTest {
	private static final Set<String> IDS = Set.of("scout");

	@Test
	void entryIsRead() {
		Map<String, ClassStats> stats = ClassStats.parse("{\"scout\": {\"health\": 125, \"speed\": 400, \"airJumps\": 1}}", IDS);

		assertEquals(Map.of("scout", new ClassStats(125, 400.0, 1)), stats);
	}

	@Test
	void everyClassOfTheModMustBePresentAndNoOther() {
		assertThrows(IllegalArgumentException.class, () -> ClassStats.parse("{}", IDS));
		assertThrows(IllegalArgumentException.class, () -> ClassStats.parse(
				"{\"scout\": {\"health\": 125, \"speed\": 400, \"airJumps\": 1}, \"pyro\": {\"health\": 175, \"speed\": 300, \"airJumps\": 0}}", IDS));
	}

	@Test
	void missingUnknownOrWrongValuesAreErrors() {
		// Ключа не хватает, лишний ключ, не число, дробное там, где нужно целое, вне границ, не JSON.
		for (String json : new String[] {
				"{\"scout\": {\"health\": 125, \"speed\": 400}}",
				"{\"scout\": {\"health\": 125, \"speed\": 400, \"airJumps\": 1, \"jumps\": 2}}",
				"{\"scout\": {\"health\": \"125\", \"speed\": 400, \"airJumps\": 1}}",
				"{\"scout\": {\"health\": 125.5, \"speed\": 400, \"airJumps\": 1}}",
				"{\"scout\": {\"health\": 0, \"speed\": 400, \"airJumps\": 1}}",
				"{\"scout\": {\"health\": 125, \"speed\": 400, \"airJumps\": -1}}",
				"{\"scout\": 5}",
				"[1]",
				"{"}) {
			assertThrows(IllegalArgumentException.class, () -> ClassStats.parse(json, IDS), json);
		}
	}
}

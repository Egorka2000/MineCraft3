package io.github.egorka2000.tf2mc.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.egorka2000.tf2mc.physics.MoveParams;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConfigParserTest {
	@Test
	void defaultsMatchTf2AndProjectDecisions() {
		Tf2Config config = Tf2Config.DEFAULT;

		assertEquals(MoveParams.tf2Defaults(240.0), config.toMoveParams(240.0));
		assertEquals(45.0, config.unitsPerBlock());
		assertEquals(3, config.substeps());
	}

	@Test
	void writtenFileReadsBackUnchanged() {
		Tf2Config custom = new Tf2Config(7.0, 12.0, 5.0, 80.0, 600.0, 25.0, 300.0, 2000.0, 40.0, 5);
		ConfigParser.Result result = ConfigParser.parse(ConfigParser.toJson(custom));

		assertEquals(custom, result.config());
		assertTrue(result.warnings().isEmpty());
	}

	@Test
	void emptyObjectGivesDefaults() {
		assertEquals(Tf2Config.DEFAULT, ConfigParser.parse("{}").config());
	}

	@Test
	void missingKeysKeepDefaults() {
		Tf2Config config = ConfigParser.parse("{\"movement\": {\"friction\": 6}, \"substeps\": 1}").config();

		assertEquals(6.0, config.friction());
		assertEquals(1, config.substeps());
		assertEquals(Tf2Config.DEFAULT.accelerate(), config.accelerate());
		assertEquals(Tf2Config.DEFAULT.unitsPerBlock(), config.unitsPerBlock());
	}

	@Test
	void unknownKeysAreReportedButDoNotFail() {
		ConfigParser.Result result = ConfigParser.parse("{\"movement\": {\"frction\": 6}, \"scale\": 40}");

		assertEquals(Tf2Config.DEFAULT, result.config());
		assertEquals(2, result.warnings().size());
		assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("movement.frction")));
		assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("scale")));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"",
		"не json",
		"[1, 2]",
		"{\"movement\": 5}",
		"{\"movement\": {\"friction\": \"много\"}}",
		"{\"movement\": {\"friction\": -1}}",
		"{\"movement\": {\"gravity\": null}}",
		"{\"unitsPerBlock\": 0}",
		"{\"unitsPerBlock\": -45}",
		"{\"substeps\": 0}",
		"{\"substeps\": 11}",
		"{\"substeps\": 2.5}",
		"{\"substeps\": true}",
	})
	void invalidFilesAreRejected(String json) {
		assertThrows(ConfigException.class, () -> ConfigParser.parse(json));
	}

	@Test
	void errorMessageNamesTheParameter() {
		ConfigException error = assertThrows(ConfigException.class, () -> ConfigParser.parse("{\"movement\": {\"stopSpeed\": []}}"));

		assertTrue(error.getMessage().contains("movement.stopSpeed"), error.getMessage());
	}
}

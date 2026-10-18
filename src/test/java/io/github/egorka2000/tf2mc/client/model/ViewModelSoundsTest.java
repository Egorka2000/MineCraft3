package io.github.egorka2000.tf2mc.client.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import io.github.egorka2000.tf2mc.sourcemodel.AnimationSet;
import org.junit.jupiter.api.Test;

class ViewModelSoundsTest {
	private static final int CLIENT_SOUND = 5004;

	private static AnimationSet.Sequence sequence(String name, boolean looping, AnimationSet.Event... events) {
		return new AnimationSet.Sequence(name, "", looping ? AnimationSet.SEQUENCE_LOOPING : 0, new int[] {1, 1}, new int[] {-1, -1},
				new float[2], new float[2], new int[] {0}, List.of(), new float[0], 0.2F, 0.2F, List.of(events));
	}

	private static AnimationSet.Event sound(float cycle, String name) {
		return new AnimationSet.Event(cycle, CLIENT_SOUND, "", name);
	}

	private static List<String> names(List<AnimationSet.Event> events) {
		return events.stream().map(AnimationSet.Event::options).toList();
	}

	/** Помпа дробовика после выстрела, как в анимации рук TF2, и событие, которое не звук. */
	private static final AnimationSet.Sequence FIRE = sequence("fire", false, sound(0.28F, "Back"), sound(0.48F, "Forward"),
			new AnimationSet.Event(0.52F, 6002, "", "2"));

	private static final AnimationSet.Sequence LOOP = sequence("reload_loop", true, sound(0.27F, "Shell"));

	@Test
	void eventsSoundOnceWhenPassed() {
		ViewModelSounds sounds = new ViewModelSounds();

		assertEquals(List.of(), names(sounds.advance(FIRE, 0.1F)));
		assertEquals(List.of("Back"), names(sounds.advance(FIRE, 0.3F)));
		assertEquals(List.of(), names(sounds.advance(FIRE, 0.3F)));
		// Кадр перескочил помпу вперёд и событие 6002: звучит только помпа, 6002 — не звук.
		assertEquals(List.of("Forward"), names(sounds.advance(FIRE, 1.0F)));
	}

	@Test
	void newSequenceSoundsFromItsStart() {
		ViewModelSounds sounds = new ViewModelSounds();
		sounds.advance(LOOP, 0.9F);

		assertEquals(List.of("Back", "Forward"), names(sounds.advance(FIRE, 0.5F)));
	}

	@Test
	void loopSoundsAgainAfterWrapping() {
		ViewModelSounds sounds = new ViewModelSounds();
		sounds.advance(LOOP, 0.5F);

		assertEquals(List.of("Shell"), names(sounds.advance(LOOP, 0.3F)));
	}

	@Test
	void secondShotRestartsSounds() {
		ViewModelSounds sounds = new ViewModelSounds();
		sounds.advance(FIRE, 0.9F);

		assertEquals(List.of("Back"), names(sounds.advance(FIRE, 0.3F)));
	}

	@Test
	void hiddenArmsForgetSequence() {
		ViewModelSounds sounds = new ViewModelSounds();
		sounds.advance(FIRE, 0.9F);
		sounds.advance(null, 0.0F);

		assertEquals(List.of("Back", "Forward"), names(sounds.advance(FIRE, 0.9F)));
	}
}

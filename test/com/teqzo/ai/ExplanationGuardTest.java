package com.teqzo.ai;

import com.teqzo.testkit.Test;

import static com.teqzo.testkit.Assertions.*;

class ExplanationGuardTest {

	private static final String FACTS = "{\"top_devices\":[{\"name\":\"Samsung Galaxy A52\",\"highlights\":[\"Battery: 4500 mAh\"],"
			+ "\"score_out_of_100\":61.3}],\"devices_matching_in_database\":12}";

	@Test
	void acceptsTextWhoseNumbersComeFromTheFacts() {
		assertTrue(ExplanationGuard.isGrounded("The Samsung Galaxy A52 stands out with a 4,500 mAh battery and a score of 61.3.", FACTS));
		assertTrue(ExplanationGuard.isGrounded("Here are 3 good options.", FACTS));
	}

	@Test
	void rejectsInventedSpecifications() {
		assertFalse(ExplanationGuard.isGrounded("It has a 5000 mAh battery.", FACTS));
		assertFalse(ExplanationGuard.isGrounded("It charges at 65W.", FACTS));
		assertFalse(ExplanationGuard.isGrounded("A great 108 MP camera.", FACTS));
	}

	@Test
	void rejectsLinksCodeLongOrEmptyText() {
		assertFalse(ExplanationGuard.isGrounded("See http://example.com", FACTS));
		assertFalse(ExplanationGuard.isGrounded("```x```", FACTS));
		assertFalse(ExplanationGuard.isGrounded("a".repeat(ExplanationGuard.MAX_LENGTH + 1), FACTS));
		assertFalse(ExplanationGuard.isGrounded("  ", FACTS));
	}
}

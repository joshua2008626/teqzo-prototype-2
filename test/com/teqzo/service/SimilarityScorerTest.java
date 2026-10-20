package com.teqzo.service;

import com.teqzo.TestData;
import com.teqzo.domain.DeviceRow;
import com.teqzo.testkit.Test;

import static com.teqzo.testkit.Assertions.*;

class SimilarityScorerTest {

	@Test
	void identicalDevicesScoreNearHundred() {
		SimilarityScorer.Result r = SimilarityScorer.score(TestData.flagship(), TestData.flagship());
		assertNotNull(r);
		assertEquals(100.0, r.similarity(), 0.01);
		assertTrue(r.reasons().contains("same RAM (12 GB)"));
	}

	@Test
	void flagshipAndBudgetPhoneAreFarApart() {
		SimilarityScorer.Result r = SimilarityScorer.score(TestData.flagship(), TestData.budget());
		assertNotNull(r);
		assertTrue(r.similarity() < 50, "was " + r.similarity());
	}

	@Test
	void closerPriceAndSpecsScoreHigher() {
		DeviceRow base = TestData.flagship();
		DeviceRow near = TestData.flagship().withId("near").withName("Near");
		near.priceInr = 84999.0;
		near.ramGb = 12.0;
		near.batteryMah = 4900.0;
		DeviceRow far = TestData.budget();
		assertTrue(SimilarityScorer.score(base, near).similarity() > SimilarityScorer.score(base, far).similarity());
	}

	@Test
	void tooLittleInCommonGivesNoScore() {
		DeviceRow a = TestData.device("a", "X", "A");
		DeviceRow b = TestData.device("b", "Y", "B");
		a.priceInr = 10000.0;
		b.priceInr = 10000.0; // price + year + os only: weight 5 but just... three attributes
		a.announcedYear = null;
		b.announcedYear = null;
		a.os = null;
		assertNull(SimilarityScorer.score(a, b));
	}

	@Test
	void missingValuesDoNotPenalise() {
		DeviceRow a = TestData.flagship();
		DeviceRow b = TestData.flagship().withId("b");
		b.batteryMah = null;
		b.wiredChargingW = null;
		assertEquals(100.0, SimilarityScorer.score(a, b).similarity(), 0.01);
	}

	@Test
	void closenessIsLinearBetweenTheLimits() {
		assertEquals(1.0, SimilarityScorer.closeness(0, 0, 2), 1e-9);
		assertEquals(0.5, SimilarityScorer.closeness(1, 0, 2), 1e-9);
		assertEquals(0.0, SimilarityScorer.closeness(5, 0, 2), 1e-9);
	}
}

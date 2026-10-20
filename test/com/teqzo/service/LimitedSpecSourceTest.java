package com.teqzo.service;

import com.teqzo.TestData;
import com.teqzo.domain.DeviceRow;
import com.teqzo.recommendation.Dimension;
import com.teqzo.dto.RecommendationResponse.Check;
import com.teqzo.recommendation.RequirementChecker;
import com.teqzo.recommendation.Requirements;
import com.teqzo.recommendation.ScoringEngine;
import com.teqzo.recommendation.UseCase;
import com.teqzo.recommendation.Weights;
import com.teqzo.testkit.Test;

import java.util.List;

import static com.teqzo.testkit.Assertions.*;

/** Devices added from the Flipkart dataset carry few fields; "not listed" must never become "no". */
class LimitedSpecSourceTest {

	private static DeviceRow flipkartPhone(boolean fiveGInName) {
		DeviceRow d = TestData.device("flipkart-apple-iphone-17", "Apple", "iPhone 17");
		d.announcedYear = null;
		d.listedYear = 2026;
		d.dataSource = "FLIPKART_2026";
		d.has5g = fiveGInName;
		d.priceInr = 82900.0;
		return d;
	}

	@Test
	void fiveGIsUnknownNotNoForLimitedSources() {
		assertNull(DeviceMapper.fiveG(flipkartPhone(false)));
		assertEquals(Boolean.TRUE, DeviceMapper.fiveG(flipkartPhone(true)));
		DeviceRow gsmarena = TestData.device("x-1", "Samsung", "Old");
		assertEquals(Boolean.FALSE, DeviceMapper.fiveG(gsmarena));
		gsmarena.hasSpecData = false;
		assertNull(DeviceMapper.fiveG(gsmarena));
	}

	@Test
	void fiveGRequirementIsUnknownForLimitedSourcesAndNotMetForGsmarena() {
		Requirements r = new Requirements();
		r.requires5g = true;
		String limited = RequirementChecker.check(r, flipkartPhone(false), null).get(0).status();
		assertEquals(RequirementChecker.UNKNOWN, limited);
		DeviceRow old = TestData.device("x-1", "Samsung", "Old");
		assertEquals(RequirementChecker.NOT_MET, RequirementChecker.check(r, old, null).get(0).status());
		assertEquals(RequirementChecker.MET, RequirementChecker.check(r, flipkartPhone(true), null).get(0).status());
	}

	@Test
	void releaseYearRequirementExplainsWhenOnlyTheListingYearIsKnown() {
		Requirements r = new Requirements();
		r.minReleaseYear = 2023;
		Check check = RequirementChecker.check(r, flipkartPhone(true), null).get(0);
		assertEquals(RequirementChecker.UNKNOWN, check.status());
		assertTrue(check.actual().contains("on sale in 2026"));
	}

	@Test
	void freshnessUsesTheListingYearAndConnectivityIgnoresUnknownFiveG() {
		Weights w = Weights.forRequirements(List.of(), List.of());
		ScoringEngine.Result result = ScoringEngine.score(flipkartPhone(false), w, 2022);
		var freshness = result.components().stream().filter(c -> c.dimension() == Dimension.FRESHNESS).findFirst().get();
		assertEquals(1.0, freshness.score(), 1e-9);
		var connectivity = result.components().stream().filter(c -> c.dimension() == Dimension.CONNECTIVITY).findFirst().get();
		assertNull(connectivity.score(), "no 5G flag on a limited source is not a 0 score");
		assertEquals("not listed", connectivity.detail());
	}

	@Test
	void useCasesStillScoreWithoutAnyListedChargingOrWeight() {
		Weights w = Weights.forRequirements(List.of(UseCase.GAMING), List.of());
		double score = ScoringEngine.score(flipkartPhone(true), w, 2022).total();
		assertTrue(score >= 0 && score <= 100);
	}
}

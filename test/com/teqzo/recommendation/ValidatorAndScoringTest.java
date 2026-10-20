package com.teqzo.recommendation;

import com.teqzo.TestData;
import com.teqzo.domain.DeviceRow;
import com.teqzo.testkit.Test;

import java.util.List;

import static com.teqzo.testkit.Assertions.*;

class ValidatorAndScoringTest {

	@Test
	void implausibleValuesAreDroppedWithANote() {
		Requirements r = new Requirements();
		r.ramMinGb = 5000.0;
		r.batteryMinMah = 5.0;
		r.budgetMaxInr = 30.0;
		r.os = "windows phone";
		r.displayType = "magic";
		r.refreshRateMinHz = 120.0;
		RequirementsValidator.Validated v = RequirementsValidator.validate(r);
		assertNull(v.requirements().ramMinGb);
		assertNull(v.requirements().batteryMinMah);
		assertNull(v.requirements().budgetMaxInr);
		assertNull(v.requirements().os);
		assertNull(v.requirements().displayType);
		assertEquals(120.0, v.requirements().refreshRateMinHz);
		assertEquals(5, v.notes().size());
	}

	@Test
	void osAndDisplayTypeAreNormalised() {
		Requirements r = new Requirements();
		r.os = "Android 14";
		r.displayType = "AMOLED";
		Requirements out = RequirementsValidator.validate(r).requirements();
		assertEquals("android", out.os);
		assertEquals("amoled", out.displayType);
	}

	@Test
	void weightsSumToOneAndHonourPrioritiesAndIgnores() {
		Weights balanced = Weights.forRequirements(List.of(), List.of());
		Weights gaming = Weights.forRequirements(List.of(UseCase.GAMING), List.of());
		Weights noCamera = Weights.forRequirements(List.of(UseCase.GAMING), List.of(Dimension.CAMERA));
		assertEquals(1.0, sum(balanced), 1e-9);
		assertEquals(1.0, sum(gaming), 1e-9);
		assertEquals(1.0, sum(noCamera), 1e-9);
		assertTrue(gaming.of(Dimension.PERFORMANCE) > balanced.of(Dimension.PERFORMANCE));
		assertEquals(0.0, noCamera.of(Dimension.CAMERA), 1e-12);
	}

	private static double sum(Weights w) {
		double s = 0;
		for (Dimension d : Dimension.values()) s += w.of(d);
		return s;
	}

	@Test
	void flagshipOutscoresBudgetPhoneOverall() {
		Weights w = Weights.forRequirements(List.of(), List.of());
		double flagship = ScoringEngine.score(TestData.flagship(), w, 2022).total();
		double budget = ScoringEngine.score(TestData.budget(), w, 2022).total();
		assertTrue(flagship > budget);
		assertTrue(flagship <= 100.0);
	}

	@Test
	void batteryFocusShiftsPointsTowardTheBiggerBattery() {
		DeviceRow big = TestData.budget();
		big.batteryMah = 7000.0;
		big.wiredChargingW = 120.0;
		Weights balanced = Weights.forRequirements(List.of(), List.of());
		Weights battery = Weights.forRequirements(List.of(UseCase.BATTERY), List.of());
		double leadBalanced = ScoringEngine.score(TestData.flagship(), balanced, 2022).total()
				- ScoringEngine.score(big, balanced, 2022).total();
		double leadBattery = ScoringEngine.score(TestData.flagship(), battery, 2022).total()
				- ScoringEngine.score(big, battery, 2022).total();
		assertTrue(leadBattery < leadBalanced, "a battery focus must narrow the flagship's lead");
		assertTrue(points(ScoringEngine.score(big, battery, 2022), Dimension.BATTERY)
				> points(ScoringEngine.score(TestData.flagship(), battery, 2022), Dimension.BATTERY));
	}

	private static double points(ScoringEngine.Result r, Dimension d) {
		return r.components().stream().filter(c -> c.dimension() == d).findFirst().get().points();
	}

	@Test
	void unlistedDataScoresNothingInsteadOfZeroValues() {
		DeviceRow bare = TestData.device("x-1.php", "Nobody", "Bare");
		ScoringEngine.Result r = ScoringEngine.score(bare, Weights.forRequirements(List.of(), List.of()), 2022);
		ScoreComponent camera = r.components().stream().filter(c -> c.dimension() == Dimension.CAMERA).findFirst().get();
		assertNull(camera.score());
		assertEquals("not listed", camera.detail());
		assertEquals(0.0, camera.points(), 1e-12);
	}

	@Test
	void ignoredDimensionContributesNoPoints() {
		Weights w = Weights.forRequirements(List.of(), List.of(Dimension.CAMERA));
		ScoringEngine.Result r = ScoringEngine.score(TestData.flagship(), w, 2022);
		ScoreComponent camera = r.components().stream().filter(c -> c.dimension() == Dimension.CAMERA).findFirst().get();
		assertEquals(0.0, camera.points(), 1e-12);
	}

	@Test
	void descriptionsOnlyContainListedValues() {
		assertEquals("5000 mAh", ScoringEngine.describe(Dimension.BATTERY, TestData.flagship()));
		DeviceRow d = TestData.budget();
		assertEquals("18 W wired", ScoringEngine.describe(Dimension.CHARGING, d));
		assertEquals("no 5G listed", ScoringEngine.describe(Dimension.CONNECTIVITY, d));
	}
}

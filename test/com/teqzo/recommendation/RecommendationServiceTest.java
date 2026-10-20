package com.teqzo.recommendation;

import com.teqzo.TestData;
import com.teqzo.domain.DeviceRow;
import com.teqzo.dto.RecommendationResponse;
import com.teqzo.service.BrandService;
import com.teqzo.service.FacetsService;
import com.teqzo.service.PricePolicy;
import com.teqzo.testkit.Test;

import java.util.List;

import static com.teqzo.testkit.Assertions.*;

class RecommendationServiceTest {

	private final TestData.FakeDeviceRepository repo = new TestData.FakeDeviceRepository(TestData.flagship(), TestData.budget());

	private RecommendationService service(Double fx) {
		return new RecommendationService(repo, new BrandService(new TestData.FakeBrandRepository()),
				new FacetsService(repo), new PricePolicy(fx));
	}

	@Test
	void onlyDevicesThatMeetEveryHardRequirementAreReturned() {
		Requirements r = new Requirements();
		r.ramMinGb = 8.0;
		r.requires5g = true;
		RecommendationResponse out = service(null).recommend(r, 5);
		assertTrue(out.exactMatch());
		assertEquals(1, out.recommendations().size());
		assertEquals("Flagship One", out.recommendations().get(0).device().name());
		assertTrue(out.recommendations().get(0).checks().stream().allMatch(c -> c.status().equals("MET")));
	}

	@Test
	void rankingIsDeterministicAndFollowsPriorities() {
		Requirements r = new Requirements();
		r.priorities.add(UseCase.BATTERY);
		RecommendationResponse a = service(null).recommend(r, 5);
		RecommendationResponse b = service(null).recommend(r, 5);
		assertEquals(a.recommendations().get(0).device().id(), b.recommendations().get(0).device().id());
		assertEquals(2, a.recommendations().size());
		assertEquals(1, a.recommendations().get(0).rank());
		assertTrue(a.recommendations().get(0).score() >= a.recommendations().get(1).score());
	}

	@Test
	void emptyResultRelaxesOptionalRequirementsAndSaysSo() {
		Requirements r = new Requirements();
		r.ramMinGb = 8.0;
		r.requiresNfc = true;
		r.requiresWirelessCharging = true;
		r.refreshRateMinHz = 165.0; // nobody has this
		RecommendationResponse out = service(null).recommend(r, 5);
		assertFalse(out.exactMatch());
		assertFalse(out.relaxed().isEmpty());
		assertFalse(out.recommendations().isEmpty());
		assertEquals("Flagship One", out.recommendations().get(0).device().name());
		assertTrue(out.recommendations().get(0).checks().stream().anyMatch(c -> c.status().equals("NOT_MET")));
		assertFalse(out.recommendations().get(0).caveats().isEmpty());
	}

	@Test
	void relaxationNeverDropsBrandOrBudget() {
		Requirements r = new Requirements();
		r.brands.add("Apple"); // nothing by Apple in the fake data
		RecommendationResponse out = service(null).recommend(r, 5);
		assertTrue(out.recommendations().isEmpty());
	}

	@Test
	void budgetFiltersListedPricesAndKeepsUnknownOnlyAfterRelaxing() {
		DeviceRow noPrice = TestData.device("oppo_x-3.php", "Oppo", "No Price");
		noPrice.ramGb = 8.0;
		repo.rows.add(noPrice);
		Requirements r = new Requirements();
		r.budgetMaxInr = 20000.0;
		RecommendationResponse out = service(null).recommend(r, 5);
		assertTrue(out.exactMatch());
		assertEquals(List.of("Budget Plus"), out.recommendations().stream().map(d -> d.device().name()).toList());

		repo.rows.removeIf(d -> d.id.equals("xiaomi_budget-2.php"));
		RecommendationResponse relaxed = service(null).recommend(r, 5);
		assertFalse(relaxed.exactMatch());
		RecommendationResponse.RecommendedDevice only = relaxed.recommendations().get(0);
		assertEquals("No Price", only.device().name());
		assertTrue(only.checks().stream().anyMatch(c -> c.status().equals("UNKNOWN")));
	}

	@Test
	void unknownBrandsAreReportedNotInvented() {
		Requirements r = new Requirements();
		r.brands.add("Foobar");
		r.priorities.add(UseCase.BATTERY);
		RecommendationResponse out = service(null).recommend(r, 5);
		assertTrue(out.notes().stream().anyMatch(n -> n.contains("Foobar")));
		assertEquals(2, out.recommendations().size());
	}

	@Test
	void limitIsClamped() {
		assertEquals(2, service(null).recommend(new Requirements(), 100).recommendations().size());
		assertEquals(1, service(null).recommend(new Requirements(), 0).recommendations().size());
	}

	@Test
	void requirementCheckerReportsRealValues() {
		Requirements r = new Requirements();
		r.ramMinGb = 8.0;
		r.requiresNfc = true;
		DeviceRow d = TestData.budget();
		var checks = RequirementChecker.check(r, d, null);
		assertEquals("NOT_MET", checks.get(0).status());
		assertEquals("4 GB", checks.get(0).actual());
		assertEquals("UNKNOWN", checks.get(1).status());
	}

	@Test
	void recommendationsDefaultToPhoneSizedDisplays() {
		var f = service(null).toFilter(new Requirements(), false);
		assertEquals(3.0, f.displaySizeMinInches);
		assertEquals(7.2, f.displaySizeMaxInches);
		Requirements tablet = new Requirements();
		tablet.displaySizeMinInches = 7.5;
		var g = service(null).toFilter(tablet, false);
		assertEquals(7.5, g.displaySizeMinInches);
		assertNull(g.displaySizeMaxInches);
	}
}

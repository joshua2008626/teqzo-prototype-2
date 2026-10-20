package com.teqzo.ai;

import com.teqzo.recommendation.Requirements;
import com.teqzo.testkit.Test;

import java.util.List;
import java.util.Map;

import static com.teqzo.testkit.Assertions.*;

class RuleBasedRequirementParserTest {

	private final RuleBasedRequirementParser parser = new RuleBasedRequirementParser(
			() -> List.of("Samsung", "Apple", "Xiaomi", "LG"), () -> 2022);

	private Requirements parse(String text) {
		return Requirements.fromMap(parser.parse(text, Map.of(), List.of()).update());
	}

	@Test
	void parsesMemoryAndStorageAndFlags() {
		Requirements r = parse("I want a phone with at least 8GB RAM and 256GB storage, 5G and wireless charging");
		assertEquals(8.0, r.ramMinGb);
		assertEquals(256.0, r.storageMinGb);
		assertEquals(Boolean.TRUE, r.requires5g);
		assertEquals(Boolean.TRUE, r.requiresWirelessCharging);
	}

	@Test
	void wordsAfterTheNumberDecideBetweenRamAndStorage() {
		Requirements r = parse("need 12GB RAM 1TB storage");
		assertEquals(12.0, r.ramMinGb);
		assertEquals(1024.0, r.storageMinGb);
		Requirements r2 = parse("ram 8gb storage 128gb");
		assertEquals(8.0, r2.ramMinGb);
		assertEquals(128.0, r2.storageMinGb);
	}

	@Test
	void parsesBareMemoryNumbersByMagnitude() {
		Requirements r = parse("8gb 256gb");
		assertEquals(8.0, r.ramMinGb);
		assertEquals(256.0, r.storageMinGb);
	}

	@Test
	void parsesIndianBudgetPhrases() {
		assertEquals(30000.0, parse("best phone under 30000").budgetMaxInr);
		assertEquals(30000.0, parse("30k budget").budgetMaxInr);
		assertEquals(25000.0, parse("budget ₹25,000").budgetMaxInr);
		assertEquals(150000.0, parse("under 1.5 lakh").budgetMaxInr);
		assertEquals(20000.0, parse("within Rs. 20000").budgetMaxInr);
	}

	@Test
	void aMemorySizeIsNotMistakenForABudget() {
		Requirements r = parse("under 8gb ram");
		assertNull(r.budgetMaxInr);
		assertEquals(8.0, r.ramMinGb);
	}

	@Test
	void parsesDisplayChargingBatteryAndCamera() {
		Requirements r = parse("120hz amoled display, 5000mah battery, 65w fast charging, 50mp camera");
		assertEquals(120.0, r.refreshRateMinHz);
		assertEquals("amoled", r.displayType);
		assertEquals(5000.0, r.batteryMinMah);
		assertEquals(65.0, r.wiredChargingMinW);
		assertEquals(50.0, r.mainCameraMinMp);
	}

	@Test
	void priorityKeywordsBecomeSoftPreferencesNotHardNumbers() {
		Requirements r = parse("Find me the best gaming phone with great battery life");
		assertEquals(2, r.priorities.size());
		assertTrue(r.isEmpty() == false && !r.hasHardRequirements());
	}

	@Test
	void dontCareAboutCameraIsIgnoredNotPrioritised() {
		Requirements r = parse("I don't care about the camera but I want excellent performance");
		assertEquals(1, r.ignored.size());
		assertEquals("camera", r.ignored.get(0).key());
		assertEquals("performance", r.priorities.get(0).key());
		assertEquals(1, r.priorities.size());
	}

	@Test
	void negatedFeaturesAreNotRequired() {
		Requirements r = parse("no need for 5G, without wireless charging");
		assertNull(r.requires5g);
		assertNull(r.requiresWirelessCharging);
	}

	@Test
	void brandsIncludeAndExclude() {
		Requirements r = parse("Samsung phones, not Xiaomi");
		assertEquals(List.of("Samsung"), r.brands);
		assertEquals(List.of("Xiaomi"), r.excludeBrands);
		assertEquals(List.of("Apple"), parse("I want an iPhone").brands);
	}

	@Test
	void shortBrandNamesNeedCapitals() {
		assertEquals(List.of("LG"), parse("An LG phone").brands);
		assertTrue(parse("it is a big flag phone").brands.isEmpty());
	}

	@Test
	void latestMeansRecentYearsOfTheDataset() {
		assertEquals(Integer.valueOf(2021), parse("the latest phones").minReleaseYear);
		assertEquals(Integer.valueOf(2020), parse("phones released after 2020").minReleaseYear);
	}

	@Test
	void forgetBudgetAndResetAreReported() {
		ParseResult r = parser.parse("forget the budget", Map.of(), List.of());
		assertTrue(r.clear().contains(Requirements.BUDGET));
		assertTrue(parser.parse("start over please", Map.of(), List.of()).reset());
	}

	@Test
	void unrelatedTextProducesNothing() {
		assertTrue(parse("hello there, how are you?").isEmpty());
	}

	@Test
	void tabletRequestsSetALargeDisplayMinimum() {
		assertEquals(7.5, parse("a tablet for reading").displaySizeMinInches);
	}
}

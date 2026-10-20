package com.teqzo.filter;

import com.teqzo.testkit.Test;

import java.util.List;

import static com.teqzo.testkit.Assertions.*;

class DeviceQueryBuilderTest {

	@Test
	void noFilterMatchesEverythingNewestFirst() {
		DeviceQueryBuilder.Built b = DeviceQueryBuilder.build(new DeviceFilter(), null);
		assertEquals("1 = 1", b.where());
		assertTrue(b.orderBy().startsWith("(COALESCE(x.announced_year, x.listed_year) IS NULL)"));
		assertTrue(b.params().isEmpty());
	}

	@Test
	void userTextOnlyEverAppearsAsBoundParameters() {
		DeviceFilter f = new DeviceFilter();
		f.q = "x'; DROP TABLE device; --";
		f.chipset = "100%_\\";
		DeviceQueryBuilder.Built b = DeviceQueryBuilder.build(f, null);
		assertFalse(b.where().contains("DROP"));
		assertFalse(b.where().contains("'"));
		assertEquals("%100%", b.params().get("chipset"));
	}

	@Test
	void searchRequiresEveryTokenAndRanksByRelevance() {
		DeviceFilter f = new DeviceFilter();
		f.q = "Samsung S24";
		DeviceQueryBuilder.Built b = DeviceQueryBuilder.build(f, null);
		assertTrue(b.where().contains("x.search_compact LIKE :t0"));
		assertTrue(b.where().contains("x.search_compact LIKE :t1"));
		assertEquals("%s24%", b.params().get("t1"));
		assertEquals("samsungs24", b.params().get("qExact"));
		assertTrue(b.orderBy().startsWith("CASE WHEN x.search_compact = :qExact"));
	}

	@Test
	void unsearchableTextMatchesNothing() {
		DeviceFilter f = new DeviceFilter();
		f.q = "%%%";
		assertEquals("1 = 0", DeviceQueryBuilder.build(f, null).where());
	}

	@Test
	void filtersCombineWithAnd() {
		DeviceFilter f = new DeviceFilter();
		f.brandIds = List.of("samsung-phones-9.php");
		f.ramMinGb = 12.0;
		f.storageMinGb = 256.0;
		f.refreshRateMinHz = 120.0;
		f.has5g = true;
		DeviceQueryBuilder.Built b = DeviceQueryBuilder.build(f, null);
		for (String part : List.of("c.brand_id IN (:brand0)", "c.ram_gb >= :ramMin", "c.storage_gb >= :storageMin",
				"c.refresh_rate_hz >= :refreshMin", "c.has_5g = 1")) {
			assertTrue(b.where().contains(part), part);
		}
		assertEquals(4, b.where().split(" AND ").length - 1);
	}

	@Test
	void budgetUsesListedInrOnlyUnlessARateIsConfigured() {
		DeviceFilter f = new DeviceFilter();
		f.priceMaxInr = 30000.0;
		DeviceQueryBuilder.Built strict = DeviceQueryBuilder.build(f, null);
		assertEquals("(x.price_inr <= :priceMax)", strict.where());
		DeviceQueryBuilder.Built converted = DeviceQueryBuilder.build(f, 90.0);
		assertTrue(converted.where().contains("COALESCE(x.price_inr, x.price_eur * :fx)"));
		assertEquals(90.0, converted.params().get("fx"));
		f.includeUnknownPrice = true;
		assertTrue(DeviceQueryBuilder.build(f, null).where().contains("x.price_inr IS NULL OR"));
	}

	@Test
	void booleanFiltersNeverTreatMissingAsNegative() {
		DeviceFilter f = new DeviceFilter();
		f.wirelessCharging = true;
		f.nfc = true;
		DeviceQueryBuilder.Built b = DeviceQueryBuilder.build(f, null);
		assertTrue(b.where().contains("x.wireless_charging_status = 'YES'"));
		assertTrue(b.where().contains("x.nfc_supported = 1"));
	}

	@Test
	void newestFirstAlsoRanksDevicesThatOnlyHaveAListingYear() {
		DeviceFilter f = new DeviceFilter();
		f.sort = SortOption.NEWEST;
		assertEquals("(COALESCE(x.announced_year, x.listed_year) IS NULL), COALESCE(x.announced_year, x.listed_year) DESC,"
				+ " c.name ASC, c.id ASC", DeviceQueryBuilder.build(f, null).orderBy());
	}

	@Test
	void noFiveGOnlyMeansSomethingForFullGsmarenaSpecifications() {
		DeviceFilter f = new DeviceFilter();
		f.has5g = false;
		assertEquals("(c.has_5g = 0 AND x.has_spec_data = 1 AND x.data_source IS NULL)",
				DeviceQueryBuilder.build(f, null).where());
	}

	@Test
	void releaseYearIsStrictUnlessTheRecommenderAsksForListingYears() {
		DeviceFilter f = new DeviceFilter();
		f.releasedFromYear = 2023;
		assertEquals("x.announced_year >= :yearFrom", DeviceQueryBuilder.build(f, null).where());
		f.includeListedYear = true;
		assertEquals("COALESCE(x.announced_year, x.listed_year) >= :yearFrom", DeviceQueryBuilder.build(f, null).where());
		assertTrue(f.copy().includeListedYear);
	}

	@Test
	void sortIsAlwaysStableByIdAndNullsLast() {
		DeviceFilter f = new DeviceFilter();
		f.sort = SortOption.BATTERY_DESC;
		assertEquals("(c.battery_mah IS NULL), c.battery_mah DESC, c.name ASC, c.id ASC",
				DeviceQueryBuilder.build(f, null).orderBy());
	}
}

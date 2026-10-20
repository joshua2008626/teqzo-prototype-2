package com.teqzo.service;

import com.teqzo.TestData;
import com.teqzo.domain.DeviceRow;
import com.teqzo.domain.NotFoundException;
import com.teqzo.dto.DeviceDetail;
import com.teqzo.dto.DeviceSummary;
import com.teqzo.dto.PageResponse;
import com.teqzo.filter.DeviceFilter;
import com.teqzo.testkit.Test;

import static com.teqzo.testkit.Assertions.*;

class DeviceServiceTest {

	private final TestData.FakeDeviceRepository repo = new TestData.FakeDeviceRepository(TestData.flagship(), TestData.budget());
	private final DeviceService service = new DeviceService(repo, new TestData.FakeBrandRepository(), new PricePolicy((Double) null));

	@Test
	void detailReturnsSectionsBenchmarksAndSourceNote() {
		DeviceDetail d = service.get("samsung_flag-1.php");
		assertEquals("Flagship One", d.device().name());
		assertEquals(Integer.valueOf(5000), d.batteryMah());
		assertEquals(45.0, d.charging().wiredW());
		assertEquals("YES", d.charging().wirelessStatus());
		assertEquals("Display", d.sections().get(0).title());
		assertEquals(1, d.benchmarks().size());
		assertTrue(d.dataSource().contains("GSMArena"));
	}

	@Test
	void unknownDeviceIsNotFound() {
		NotFoundException e = assertThrows(NotFoundException.class, () -> service.get("does-not-exist.php"));
		assertEquals("DEVICE_NOT_FOUND", e.getCode());
	}

	@Test
	void missingValuesStayNullInTheApiModel() {
		DeviceSummary s = DeviceMapper.toSummary(TestData.budget(), null);
		assertNull(s.keySpecs().wirelessChargingW());
		assertNull(s.keySpecs().refreshRateHz());
		assertNull(s.keySpecs().selfieCameraMp());
		assertNull(s.price().eur());
		assertEquals(12999.0, s.price().inr());
	}

	@Test
	void entriesWithoutSpecificationsDoNotClaimFiveGAbsence() {
		DeviceRow bare = TestData.device("x-1.php", "Nobody", "Bare");
		bare.hasSpecData = false;
		DeviceSummary s = DeviceMapper.toSummary(bare, null);
		assertNull(s.keySpecs().has5g());
		assertFalse(s.specificationsAvailable());
		repo.rows.add(bare);
		assertTrue(service.get("x-1.php").notes().get(0).contains("no specification data"));
	}

	@Test
	void convertedPricesAreLabelledApproximate() {
		DeviceRow d = TestData.device("eu-1.php", "Nobody", "Euro");
		d.priceEur = 300.0;
		assertNull(DeviceMapper.toPrice(d, null).approxInr());
		var converted = DeviceMapper.toPrice(d, 90.0);
		assertEquals(27000.0, converted.approxInr());
		assertTrue(converted.approxBasis().startsWith("Approximate"));
		assertNull(converted.inr());
		assertEquals(27000.0, DeviceMapper.effectiveInr(d, 90.0));
		assertNull(DeviceMapper.effectiveInr(d, null));
	}

	@Test
	void paginationReportsTotalsAndNextPage() {
		for (int i = 0; i < 45; i++) {
			repo.rows.add(TestData.device("extra-" + i + ".php", "Samsung", "Extra " + i));
		}
		DeviceFilter f = new DeviceFilter();
		f.size = 20;
		PageResponse<DeviceSummary> first = service.search(f);
		assertEquals(47, first.totalItems());
		assertEquals(3, first.totalPages());
		assertEquals(20, first.items().size());
		assertTrue(first.hasNext());
		f.page = 2;
		PageResponse<DeviceSummary> last = service.search(f);
		assertEquals(7, last.items().size());
		assertFalse(last.hasNext());
		f.page = 10;
		assertTrue(service.search(f).items().isEmpty());
	}

	@Test
	void searchWithNoMatchesIsAnEmptyPageNotAnError() {
		DeviceFilter f = new DeviceFilter();
		f.q = "zzzz";
		PageResponse<DeviceSummary> page = service.search(f);
		assertEquals(0, page.totalItems());
		assertEquals(0, page.totalPages());
		assertTrue(page.items().isEmpty());
	}

	@Test
	void brandListingValidatesTheBrand() {
		DeviceFilter f = new DeviceFilter();
		assertEquals(1, service.byBrand("samsung-phones-1.php", f).totalItems());
		assertEquals("BRAND_NOT_FOUND", assertThrows(NotFoundException.class, () -> service.byBrand("nope.php", f)).getCode());
	}

	@Test
	void databaseFailuresPropagateForTheGlobalHandler() {
		repo.failWith = new IllegalStateException("db down");
		assertThrows(IllegalStateException.class, () -> service.search(new DeviceFilter()));
	}

	@Test
	void pricePolicyParsesRatesDefensively() {
		assertNull(PricePolicy.parse(""));
		assertNull(PricePolicy.parse("abc"));
		assertNull(PricePolicy.parse("-5"));
		assertEquals(90.5, PricePolicy.parse(" 90.5 "));
	}
}

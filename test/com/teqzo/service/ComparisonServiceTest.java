package com.teqzo.service;

import com.teqzo.TestData;
import com.teqzo.domain.DeviceRow;
import com.teqzo.domain.InvalidRequestException;
import com.teqzo.domain.NotFoundException;
import com.teqzo.dto.ComparisonResponse;
import com.teqzo.dto.ComparisonResponse.ComparisonRow;
import com.teqzo.testkit.Test;

import java.util.List;

import static com.teqzo.testkit.Assertions.*;

class ComparisonServiceTest {

	private final TestData.FakeDeviceRepository repo = new TestData.FakeDeviceRepository(TestData.flagship(), TestData.budget());
	private final PricePolicy price = new PricePolicy((Double) null);
	private final ComparisonService service = new ComparisonService(
			new DeviceService(repo, new TestData.FakeBrandRepository(), price), price);

	private static ComparisonRow row(ComparisonResponse r, String key) {
		return r.groups().stream().flatMap(g -> g.rows().stream()).filter(x -> x.key().equals(key)).findFirst().get();
	}

	@Test
	void keepsTheRequestedDeviceOrder() {
		ComparisonResponse r = service.compare(List.of("xiaomi_budget-2.php", "samsung_flag-1.php"));
		assertEquals("Budget Plus", r.devices().get(0).name());
		assertEquals("Flagship One", r.devices().get(1).name());
	}

	@Test
	void marksHighestOnlyWhereMoreIsDefensiblyBetter() {
		ComparisonResponse r = service.compare(List.of("samsung_flag-1.php", "xiaomi_budget-2.php"));
		assertEquals(List.of(0), row(r, "ram").leaders());
		assertEquals(List.of(1), row(r, "battery").leaders());
		assertEquals(List.of(0), row(r, "wired").leaders());
		assertEquals(List.of(1), row(r, "priceInr").leaders());
		assertTrue(row(r, "mainCamera").leaders().isEmpty(), "megapixels are not a verdict");
		assertTrue(row(r, "weight").leaders().isEmpty());
		assertTrue(row(r, "displaySize").leaders().isEmpty());
		assertEquals("NEUTRAL", row(r, "mainCamera").direction());
	}

	@Test
	void unlistedValuesAreShownAsNotListedAndNeverWin() {
		ComparisonResponse r = service.compare(List.of("samsung_flag-1.php", "xiaomi_budget-2.php"));
		ComparisonRow refresh = row(r, "refreshRate");
		assertEquals("Not listed", refresh.cells().get(1).display());
		assertNull(refresh.cells().get(1).value());
		assertTrue(refresh.leaders().isEmpty(), "a single listed value is not a comparison");
		assertTrue(refresh.differs());
		assertEquals("15 W", row(r, "wireless").cells().get(0).display());
		assertEquals("₹79,999", row(r, "priceInr").cells().get(0).display());
		assertEquals("Not listed", row(r, "wireless").cells().get(1).display());
	}

	@Test
	void equalValuesAreNotHighlighted() {
		DeviceRow twin = TestData.flagship();
		twin.id = "samsung_twin-3.php";
		repo.rows.add(twin);
		ComparisonResponse r = service.compare(List.of("samsung_flag-1.php", "samsung_twin-3.php"));
		assertTrue(row(r, "ram").leaders().isEmpty());
		assertFalse(row(r, "ram").differs());
	}

	@Test
	void tiesShareTheLeaderMarker() {
		DeviceRow twin = TestData.flagship();
		twin.id = "samsung_twin-3.php";
		repo.rows.add(twin);
		ComparisonResponse r = service.compare(List.of("samsung_flag-1.php", "samsung_twin-3.php", "xiaomi_budget-2.php"));
		assertEquals(List.of(0, 1), row(r, "ram").leaders());
	}

	@Test
	void rejectsTooFewTooManyDuplicateAndBlankIds() {
		assertThrows(InvalidRequestException.class, () -> service.compare(List.of("samsung_flag-1.php")));
		assertThrows(InvalidRequestException.class, () -> service.compare(List.of("a", "b", "c", "d", "e")));
		assertThrows(InvalidRequestException.class, () -> service.compare(List.of("samsung_flag-1.php", "samsung_flag-1.php")));
		assertThrows(InvalidRequestException.class, () -> service.compare(List.of("samsung_flag-1.php", " ")));
		assertThrows(InvalidRequestException.class, () -> service.compare(null));
	}

	@Test
	void unknownDeviceIdsAreReported() {
		NotFoundException e = assertThrows(NotFoundException.class,
				() -> service.compare(List.of("samsung_flag-1.php", "ghost.php")));
		assertTrue(e.getMessage().contains("ghost.php"));
	}

	@Test
	void chargingTextNeverInventsWattage() {
		assertEquals("Yes (wattage not listed)", ComparisonService.chargingText(null, "YES"));
		assertEquals("Optional or market dependent (wattage not listed)", ComparisonService.chargingText(null, "CONDITIONAL"));
		assertEquals("15 W (optional or market dependent)", ComparisonService.chargingText(15.0, "CONDITIONAL"));
		assertNull(ComparisonService.chargingText(null, null));
	}
}

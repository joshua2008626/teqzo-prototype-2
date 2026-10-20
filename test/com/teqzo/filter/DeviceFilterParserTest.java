package com.teqzo.filter;

import com.teqzo.domain.InvalidRequestException;
import com.teqzo.testkit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.teqzo.testkit.Assertions.*;

class DeviceFilterParserTest {

	private static Map<String, String> p(String... kv) {
		Map<String, String> m = new HashMap<>();
		for (int i = 0; i < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
		return m;
	}

	@Test
	void defaultsApplyWhenNothingIsGiven() {
		DeviceFilter f = DeviceFilterParser.parse(p());
		assertEquals(0, f.page);
		assertEquals(20, f.size);
		assertNull(f.q);
	}

	@Test
	void combinesFilters() {
		DeviceFilter f = DeviceFilterParser.parse(p("brand", "samsung-phones-9.php, apple-phones-48.php", "ramMin", "12",
				"storageMin", "256", "refreshRateMin", "120", "has5g", "true", "page", "2", "size", "50"));
		assertEquals(List.of("samsung-phones-9.php", "apple-phones-48.php"), f.brandIds);
		assertEquals(12.0, f.ramMinGb);
		assertEquals(256.0, f.storageMinGb);
		assertEquals(Boolean.TRUE, f.has5g);
		assertEquals(2, f.page);
		assertEquals(50, f.size);
	}

	@Test
	void rejectsUnknownParameters() {
		InvalidRequestException e = assertThrows(InvalidRequestException.class, () -> DeviceFilterParser.parse(p("ramMn", "8")));
		assertEquals("INVALID_FILTER", e.getCode());
	}

	@Test
	void rejectsBadNumbersRangesAndSizes() {
		assertThrows(InvalidRequestException.class, () -> DeviceFilterParser.parse(p("ramMin", "abc")));
		assertThrows(InvalidRequestException.class, () -> DeviceFilterParser.parse(p("ramMin", "-1")));
		assertThrows(InvalidRequestException.class, () -> DeviceFilterParser.parse(p("size", "1000")));
		assertThrows(InvalidRequestException.class, () -> DeviceFilterParser.parse(p("page", "-1")));
		assertThrows(InvalidRequestException.class, () -> DeviceFilterParser.parse(p("displaySizeMin", "7", "displaySizeMax", "6")));
		assertThrows(InvalidRequestException.class, () -> DeviceFilterParser.parse(p("has5g", "maybe")));
		assertThrows(InvalidRequestException.class, () -> DeviceFilterParser.parse(p("sort", "random")));
	}

	@Test
	void missingDataIsNeverTreatedAsNotSupported() {
		assertThrows(InvalidRequestException.class, () -> DeviceFilterParser.parse(p("wirelessCharging", "false")));
	}

	@Test
	void relevanceSortWithoutSearchFallsBack() {
		assertNull(DeviceFilterParser.parse(p("sort", "relevance")).sort);
		assertEquals(SortOption.RELEVANCE, DeviceFilterParser.parse(p("sort", "relevance", "q", "s24")).sort);
	}

	@Test
	void nameIsAnAliasForQ() {
		assertEquals("pixel", DeviceFilterParser.parse(p("name", "pixel")).q);
	}
}

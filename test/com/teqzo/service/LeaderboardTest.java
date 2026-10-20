package com.teqzo.service;

import com.teqzo.filter.DeviceFilter;
import com.teqzo.filter.DeviceFilterParser;
import com.teqzo.filter.SortOption;
import com.teqzo.testkit.Test;

import static com.teqzo.testkit.Assertions.*;

class LeaderboardTest {

	@Test
	void everyListProducesAParsableFilterWithAValidSort() {
		for (Leaderboard board : Leaderboard.values()) {
			DeviceFilter f = DeviceFilterParser.parse(board.params(null));
			assertNotNull(f.sort);
			assertTrue(f.onlyWithSpecifications);
			assertEquals(4.0, f.displaySizeMinInches, 0.001);
			assertEquals(8.5, f.displaySizeMaxInches, 0.001);
			assertNotNull(SortOption.fromKey(board.params(null).get("sort")));
		}
	}

	@Test
	void rankedValueMustBeStated() {
		assertEquals(1.0, DeviceFilterParser.parse(Leaderboard.BATTERY.params(null)).batteryMinMah, 0.001);
		assertEquals(SortOption.BATTERY_DESC, DeviceFilterParser.parse(Leaderboard.BATTERY.params(null)).sort);
		assertEquals(SortOption.WEIGHT_ASC, DeviceFilterParser.parse(Leaderboard.LIGHTEST.params(null)).sort);
		assertNotNull(DeviceFilterParser.parse(Leaderboard.LIGHTEST.params(null)).weightMaxG);
	}

	@Test
	void budgetIsOptional() {
		assertNull(DeviceFilterParser.parse(Leaderboard.RAM.params(null)).priceMaxInr);
		assertEquals(25000.0, DeviceFilterParser.parse(Leaderboard.RAM.params(25000.0)).priceMaxInr, 0.001);
	}
}

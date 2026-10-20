package com.teqzo.service;

import com.teqzo.TestData;
import com.teqzo.dto.CountryPrice;
import com.teqzo.dto.RegionalPrice;
import com.teqzo.repository.DeviceRepository;
import com.teqzo.testkit.Test;

import java.util.List;

import static com.teqzo.testkit.Assertions.*;

class RegionalPriceServiceTest {

	@Test
	void summarisesLowestAndHighestPerCountryInDisplayOrder() {
		List<CountryPrice> out = RegionalPriceService.summarise(List.of(
				new RegionalPrice("China", "CNY", "128 GB", 5199), new RegionalPrice("USA", "USD", "128 GB", 799),
				new RegionalPrice("India", "INR", "128 GB", 69999), new RegionalPrice("India", "INR", "256 GB", 74999),
				new RegionalPrice("Atlantis", "ATL", "", 1)));
		assertEquals(List.of("India", "USA", "China", "Atlantis"), out.stream().map(CountryPrice::country).toList());
		CountryPrice india = out.get(0);
		assertEquals(69999.0, india.lowest(), 0.001);
		assertEquals(74999.0, india.highest(), 0.001);
		assertEquals(2, india.variants());
		assertEquals("INR", india.currency());
	}

	@Test
	void noPricesGivesEmptyList() {
		assertTrue(RegionalPriceService.summarise(List.of()).isEmpty());
		assertTrue(new RegionalPriceService(new TestData.FakeDeviceRepository()).forDevice("x").isEmpty());
	}

	@Test
	void readsThroughTheRepository() {
		DeviceRepository repo = new TestData.FakeDeviceRepository() {
			@Override
			public List<RegionalPrice> findRegionalPrices(String id) {
				return id.equals("a") ? List.of(new RegionalPrice("USA", "USD", "", 500)) : List.of();
			}
		};
		assertEquals(1, new RegionalPriceService(repo).forDevice("a").size());
		assertEquals(0, new RegionalPriceService(repo).forDevice("b").size());
	}
}

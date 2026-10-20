package com.teqzo.service;

import com.teqzo.TestData;
import com.teqzo.dto.MarketInsights;
import com.teqzo.testkit.Test;

import java.util.List;
import java.util.Map;

import static com.teqzo.testkit.Assertions.*;

class InsightsServiceTest {

	@Test
	void cachesTheDatabaseResult() {
		int[] calls = {0};
		TestData.FakeDeviceRepository repo = new TestData.FakeDeviceRepository() {
			@Override
			public MarketInsights insights() {
				calls[0]++;
				return new MarketInsights(List.of(), List.of(), List.of(), Map.of("GSMARENA", 1L));
			}
		};
		InsightsService service = new InsightsService(repo);
		service.get();
		service.get();
		assertEquals(1, calls[0]);
		assertEquals(1L, service.get().devicesBySource().get("GSMARENA").longValue());
	}

	@Test
	void repositoriesWithoutInsightsGiveEmpty() {
		assertTrue(new InsightsService(new TestData.FakeDeviceRepository()).get().brands().isEmpty());
	}
}

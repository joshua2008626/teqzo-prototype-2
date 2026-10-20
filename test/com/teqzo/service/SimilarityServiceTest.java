package com.teqzo.service;

import com.teqzo.TestData;
import com.teqzo.domain.DeviceRow;
import com.teqzo.domain.NotFoundException;
import com.teqzo.filter.DeviceFilter;
import com.teqzo.testkit.Test;

import java.util.List;

import static com.teqzo.testkit.Assertions.*;

class SimilarityServiceTest {

	private static DeviceRow variant(String id, double price, double ram, double battery) {
		DeviceRow d = TestData.flagship().withId(id).withName(id);
		d.priceInr = price;
		d.ramGb = ram;
		d.batteryMah = battery;
		return d;
	}

	@Test
	void ranksClosestFirstAndExcludesTheDeviceItself() {
		DeviceRow base = TestData.flagship();
		TestData.FakeDeviceRepository repo = new TestData.FakeDeviceRepository(base, variant("close", 82000, 12, 5000),
				variant("middle", 60000, 8, 4500), TestData.budget());
		List<SimilarityService.SimilarDevice> out = new SimilarityService(repo, new PricePolicy("")).similarTo(base.id, 5);
		assertEquals("close", out.get(0).device().id());
		assertTrue(out.stream().noneMatch(s -> s.device().id().equals(base.id)));
		assertTrue(out.get(0).similarity() >= out.get(1).similarity());
	}

	@Test
	void limitIsRespected() {
		DeviceRow base = TestData.flagship();
		TestData.FakeDeviceRepository repo = new TestData.FakeDeviceRepository(base, variant("a", 80000, 12, 5000),
				variant("b", 81000, 12, 5000), variant("c", 82000, 12, 5000));
		assertEquals(2, new SimilarityService(repo, new PricePolicy("")).similarTo(base.id, 2).size());
	}

	@Test
	void unknownDeviceIsNotFound() {
		assertThrows(NotFoundException.class,
				() -> new SimilarityService(new TestData.FakeDeviceRepository(), new PricePolicy("")).similarTo("nope", 3));
	}

	@Test
	void candidateFilterUsesPriceAndScreenBands() {
		DeviceFilter f = SimilarityService.candidateFilter(TestData.flagship());
		assertEquals(39999.0, f.priceMinInr, 0.01);
		assertEquals(143999.0, f.priceMaxInr, 0.01);
		assertEquals(5.6, f.displaySizeMinInches, 0.01);
		assertEquals(8.0, f.displaySizeMaxInches, 0.01);
		DeviceRow unknown = TestData.device("u", "X", "U");
		DeviceFilter open = SimilarityService.candidateFilter(unknown);
		assertNull(open.priceMaxInr);
		assertNull(open.displaySizeMinInches);
	}
}

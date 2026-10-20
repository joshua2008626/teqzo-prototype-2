package com.teqzo.recommendation;

import com.teqzo.TestData;
import com.teqzo.domain.DeviceRow;
import com.teqzo.testkit.Test;

import static com.teqzo.testkit.Assertions.*;

class LimitedSpecNoteTest {

	@Test
	void namesOnlyTheMissingFields() {
		DeviceRow d = TestData.device("m25-x", "Brand", "X");
		d.dataSource = "MOBILES_2025";
		d.weightG = 190.0; // this dataset lists weight
		String note = RecommendationService.limitedSpecNote(d);
		assertTrue(note.contains("charging speed") && note.contains("NFC") && note.contains("refresh rate"));
		assertFalse(note.contains("weight"));
	}

	@Test
	void fullyListedDeviceGetsGenericNote() {
		DeviceRow d = TestData.flagship();
		d.dataSource = "FLIPKART_2026";
		d.weightG = 200.0;
		assertEquals("Added from a smaller dataset; some details may be missing.", RecommendationService.limitedSpecNote(d));
	}
}

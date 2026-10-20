package com.teqzo.service;

import com.teqzo.TestData;
import com.teqzo.dto.ComparisonResponse;
import com.teqzo.dto.ComparisonResponse.ComparisonCell;
import com.teqzo.dto.ComparisonResponse.ComparisonGroup;
import com.teqzo.dto.ComparisonResponse.ComparisonRow;
import com.teqzo.testkit.Test;

import java.util.List;

import static com.teqzo.testkit.Assertions.*;

class ComparisonVerdictTest {

	private static ComparisonRow row(String label, Integer... leaders) {
		return new ComparisonRow(label.toLowerCase(), label, leaders.length == 0 ? "NEUTRAL" : "HIGHER_IS_BETTER", true,
				List.of(leaders), List.of(new ComparisonCell("a", 1), new ComparisonCell("b", 2)));
	}

	private static ComparisonResponse response(ComparisonRow... rows) {
		return new ComparisonResponse(List.of(DeviceMapper.toSummary(TestData.flagship(), null),
				DeviceMapper.toSummary(TestData.budget(), null)), List.of(new ComparisonGroup("All", List.of(rows))), List.of());
	}

	@Test
	void countsLeadsPerDevice() {
		ComparisonVerdict v = ComparisonVerdict.of(response(row("RAM", 0), row("Battery", 1), row("Camera", 0), row("OS")));
		assertEquals(3, v.comparableRows());
		assertEquals(List.of("RAM", "Camera"), v.leadsIn().get(0));
		assertEquals(List.of("Battery"), v.leadsIn().get(1));
		assertEquals(0, v.winner());
		assertTrue(v.lines().get(0).startsWith("Samsung Flagship One leads in 2 of 3"));
	}

	@Test
	void tieHasNoWinner() {
		ComparisonVerdict v = ComparisonVerdict.of(response(row("RAM", 0), row("Battery", 1)));
		assertEquals(-1, v.winner());
		assertTrue(v.lines().get(v.lines().size() - 1).startsWith("No single device"));
	}

	@Test
	void noComparableRowsSaysSo() {
		ComparisonVerdict v = ComparisonVerdict.of(response(row("OS")));
		assertEquals(0, v.comparableRows());
		assertEquals(1, v.lines().size());
		assertEquals(-1, v.winner());
	}

	@Test
	void sharedLeadCountsForBothDevices() {
		ComparisonVerdict v = ComparisonVerdict.of(response(row("RAM", 0, 1)));
		assertEquals(1, v.leadsIn().get(0).size());
		assertEquals(1, v.leadsIn().get(1).size());
	}
}

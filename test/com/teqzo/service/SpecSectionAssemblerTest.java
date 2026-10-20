package com.teqzo.service;

import com.teqzo.dto.SpecSection;
import com.teqzo.repository.SpecRow;
import com.teqzo.testkit.Test;

import java.util.List;

import static com.teqzo.testkit.Assertions.*;

class SpecSectionAssemblerTest {

	@Test
	void groupsInDisplayOrderAndKeepsUnknownCategoriesAtTheEnd() {
		SpecSectionAssembler.Result r = SpecSectionAssembler.assemble(List.of(
				new SpecRow("Misc", "Colors", "Black"),
				new SpecRow("Battery", "Type", "Li-Po 5000 mAh"),
				new SpecRow("Display", "Size", "6.7 inches"),
				new SpecRow("Weird", "Thing", "x")));
		List<String> titles = r.sections().stream().map(SpecSection::title).toList();
		assertEquals(List.of("Display", "Battery", "Misc", "Weird"), titles);
	}

	@Test
	void continuationRowsAreAppendedToThePreviousItem() {
		SpecSectionAssembler.Result r = SpecSectionAssembler.assemble(List.of(
				new SpecRow("Network", "Technology", "GSM / HSPA"),
				new SpecRow("Network", "\u00a0", "LTE"),
				new SpecRow("Network", "2G bands", "GSM 850")));
		var items = r.sections().get(0).items();
		assertEquals(2, items.size());
		assertEquals("GSM / HSPA\nLTE", items.get(0).value());
	}

	@Test
	void testsGoToBenchmarksAndBlankValuesAreSkipped() {
		SpecSectionAssembler.Result r = SpecSectionAssembler.assemble(List.of(
				new SpecRow("Tests", "Performance", "\r\nAnTuTu: 955884 (v9)"),
				new SpecRow("Display", "Protection", null),
				new SpecRow("Display", "Type", "  "),
				new SpecRow(null, "x", "y")));
		assertEquals("AnTuTu: 955884 (v9)", r.benchmarks().get(0).value());
		assertTrue(r.sections().isEmpty());
	}

	@Test
	void sourceTextIsNotRewritten() {
		SpecSectionAssembler.Result r = SpecSectionAssembler.assemble(List.of(
				new SpecRow("Battery", "Charging", "66W wired; 15W magnetic wireless (gravity version)")));
		assertEquals("66W wired; 15W magnetic wireless (gravity version)", r.sections().get(0).items().get(0).value());
	}
}

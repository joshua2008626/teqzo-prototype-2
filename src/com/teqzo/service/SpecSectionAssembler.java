package com.teqzo.service;

import com.teqzo.dto.SpecItem;
import com.teqzo.dto.SpecSection;
import com.teqzo.repository.SpecRow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups the raw specification rows of one device into display sections, preserving the source text.
 *
 * <p>GSMArena stores continuation lines with a blank (or non-breaking-space) label; they are appended to
 * the previous item. The "Tests" category is returned separately as benchmarks.
 */
public final class SpecSectionAssembler {

	public record Result(List<SpecSection> sections, List<SpecItem> benchmarks) {
	}

	private static final String[][] SECTION_ORDER = {
			{"Display", "Display"},
			{"Platform", "Platform"},
			{"Memory", "Memory"},
			{"Main Camera", "Main camera"},
			{"Selfie camera", "Selfie camera"},
			{"Camera", "Camera"},
			{"Battery", "Battery"},
			{"Comms", "Connectivity"},
			{"Network", "Network"},
			{"Body", "Body"},
			{"Sound", "Sound"},
			{"Features", "Features"},
			{"Launch", "Launch"},
			{"Misc", "Misc"},
	};

	private SpecSectionAssembler() {
	}

	public static Result assemble(List<SpecRow> rows) {
		Map<String, List<SpecItem>> byCategory = new LinkedHashMap<>();
		List<SpecItem> benchmarks = new ArrayList<>();

		for (SpecRow row : rows) {
			String category = clean(row.category());
			String label = clean(row.specification());
			String value = clean(row.value());
			if (category == null) {
				continue;
			}
			List<SpecItem> target = "Tests".equals(category)
					? benchmarks
					: byCategory.computeIfAbsent(category, k -> new ArrayList<>());
			if (label == null) {
				if (value != null && !target.isEmpty()) {
					SpecItem previous = target.remove(target.size() - 1);
					target.add(new SpecItem(previous.label(), previous.value() + "\n" + value));
				}
				continue;
			}
			if (value == null) {
				continue;
			}
			target.add(new SpecItem(label, value));
		}

		List<SpecSection> sections = new ArrayList<>();
		for (String[] known : SECTION_ORDER) {
			List<SpecItem> items = byCategory.remove(known[0]);
			if (items != null && !items.isEmpty()) {
				sections.add(new SpecSection(known[0], known[1], items));
			}
		}
		for (Map.Entry<String, List<SpecItem>> rest : byCategory.entrySet()) {
			if (!rest.getValue().isEmpty()) {
				sections.add(new SpecSection(rest.getKey(), rest.getKey(), rest.getValue()));
			}
		}
		return new Result(sections, benchmarks);
	}

	/** Trims and normalises line breaks and non-breaking spaces. Returns null for blank text. */
	static String clean(String text) {
		if (text == null) {
			return null;
		}
		String s = text.replace('\u00a0', ' ').replace("\r\n", "\n").replace('\r', '\n').trim();
		return s.isEmpty() ? null : s;
	}
}

package com.teqzo.dto;

import java.util.List;

public record ComparisonResponse(List<DeviceSummary> devices, List<ComparisonGroup> groups, List<String> notes) {

	public record ComparisonGroup(String title, List<ComparisonRow> rows) {
	}

	/**
	 * @param direction HIGHER_IS_BETTER / LOWER_IS_BETTER when a numeric comparison is defensible, otherwise NEUTRAL
	 * @param leaders   indexes (into {@code cells}) holding the highest/lowest value; empty for NEUTRAL rows,
	 *                  rows where fewer than two devices have a value, and rows where all values are equal
	 */
	public record ComparisonRow(String key, String label, String direction, boolean differs,
			List<Integer> leaders, List<ComparisonCell> cells) {
	}

	/** {@code display} is ready to show; {@code value} is the raw number/boolean/text or null if not listed. */
	public record ComparisonCell(String display, Object value) {
	}
}

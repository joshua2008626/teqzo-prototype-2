package com.teqzo.dto;

import java.util.List;
import java.util.Map;

public record RecommendationResponse(
		Map<String, Object> requirements,
		List<String> notes,
		boolean exactMatch,
		List<Relaxation> relaxed,
		long matchedCount,
		boolean matchedCountCapped,
		List<RecommendedDevice> recommendations,
		Map<String, Double> weights) {

	/** A requirement that was dropped to find any device, in plain words. */
	public record Relaxation(String key, String description) {
	}

	public record RecommendedDevice(
			int rank,
			DeviceSummary device,
			double score,
			List<ScorePart> breakdown,
			List<Check> checks,
			List<String> highlights,
			List<String> caveats) {
	}

	/** {@code score} is null when the dataset has nothing to score this dimension with. */
	public record ScorePart(String dimension, String label, Double score, double weight, double points,
			String detail) {
	}

	/** @param status MET, NOT_MET (only after relaxation) or UNKNOWN (value not listed in the dataset) */
	public record Check(String key, String label, String required, String actual, String status) {
	}
}

package com.teqzo.dto;

/** Charging details parsed from the dataset, next to the untouched source text. */
public record ChargingInfo(
		String raw,
		Double wiredW,
		Double wirelessW,
		String wirelessStatus,
		Double reverseW,
		String reverseStatus) {
}

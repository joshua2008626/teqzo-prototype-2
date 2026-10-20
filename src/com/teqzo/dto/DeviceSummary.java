package com.teqzo.dto;

public record DeviceSummary(
		String id,
		String name,
		String brandId,
		String brandName,
		String thumbnailUrl,
		String sourceUrl,
		Integer releaseYear,
		String status,
		boolean specificationsAvailable,
		KeySpecs keySpecs,
		PriceInfo price) {
}

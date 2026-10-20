package com.teqzo.dto;

import java.util.List;

/** Values the UI can offer in filter controls, taken from the data actually present. */
public record Facets(
		List<Double> ramGb,
		List<Double> storageGb,
		List<Integer> refreshRatesHz,
		List<String> displayTypes,
		List<String> operatingSystems,
		Integer minReleaseYear,
		Integer maxReleaseYear,
		Integer maxBatteryMah,
		Double maxWiredChargingW,
		long totalDevices,
		long devicesWithSpecifications,
		long devicesWithInrPrice,
		long devicesWithAnyListedPrice,
		long devicesFromListings) {
}

package com.teqzo.dto;

import java.util.List;

public record DeviceDetail(
		DeviceSummary device,
		Integer batteryMah,
		ChargingInfo charging,
		List<SpecSection> sections,
		List<SpecItem> benchmarks,
		List<String> notes,
		String dataSource) {
}

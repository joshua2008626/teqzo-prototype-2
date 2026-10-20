package com.teqzo.repository;

import com.teqzo.domain.DeviceRow;
import com.teqzo.dto.Facets;
import com.teqzo.dto.MarketInsights;
import com.teqzo.dto.PageResponse;
import com.teqzo.dto.RegionalPrice;
import com.teqzo.filter.DeviceFilter;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository {

	/** One page of devices matching the filter, plus the total count. */
	PageResponse<DeviceRow> search(DeviceFilter filter, Double eurToInr);

	/** Up to {@code limit} devices matching the filter, for ranking. No pagination. */
	List<DeviceRow> findCandidates(DeviceFilter filter, Double eurToInr, int limit);

	Optional<DeviceRow> findById(String id);

	List<DeviceRow> findByIds(List<String> ids);

	List<SpecRow> findSpecifications(String deviceId);

	Facets facets();

	/** Launch prices per country (table device_price_v1). Empty when the device has none or the table is missing. */
	default List<RegionalPrice> findRegionalPrices(String deviceId) {
		return List.of();
	}

	/** Counts and averages computed with GROUP BY. */
	default MarketInsights insights() {
		return MarketInsights.empty();
	}
}

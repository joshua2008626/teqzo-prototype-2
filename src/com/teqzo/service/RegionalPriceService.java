package com.teqzo.service;

import com.teqzo.dto.CountryPrice;
import com.teqzo.dto.RegionalPrice;
import com.teqzo.repository.DeviceRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Launch prices of a device per country. Amounts are shown exactly as listed: no currency conversion. */
public class RegionalPriceService {

	/** Display order of the countries in the dataset; unknown countries follow alphabetically. */
	static final List<String> COUNTRY_ORDER = List.of("India", "USA", "Pakistan", "China", "Dubai");

	private final DeviceRepository devices;

	public RegionalPriceService(DeviceRepository devices) {
		this.devices = devices;
	}

	public List<CountryPrice> forDevice(String deviceId) {
		return summarise(devices.findRegionalPrices(deviceId));
	}

	/** Every variant price, for the "by variant" view. */
	public List<RegionalPrice> variants(String deviceId) {
		return devices.findRegionalPrices(deviceId);
	}

	/** One row per country with the lowest and highest variant price. */
	static List<CountryPrice> summarise(List<RegionalPrice> prices) {
		Map<String, List<RegionalPrice>> byCountry = new LinkedHashMap<>();
		for (RegionalPrice p : prices) {
			byCountry.computeIfAbsent(p.country(), k -> new ArrayList<>()).add(p);
		}
		List<CountryPrice> out = new ArrayList<>();
		for (Map.Entry<String, List<RegionalPrice>> e : byCountry.entrySet()) {
			List<RegionalPrice> list = e.getValue();
			double low = list.stream().mapToDouble(RegionalPrice::amount).min().orElse(0);
			double high = list.stream().mapToDouble(RegionalPrice::amount).max().orElse(0);
			out.add(new CountryPrice(e.getKey(), list.get(0).currency(), low, high, list.size()));
		}
		out.sort((a, b) -> {
			int ia = COUNTRY_ORDER.indexOf(a.country());
			int ib = COUNTRY_ORDER.indexOf(b.country());
			if (ia < 0 && ib < 0) {
				return a.country().compareTo(b.country());
			}
			return Integer.compare(ia < 0 ? 99 : ia, ib < 0 ? 99 : ib);
		});
		return out;
	}
}

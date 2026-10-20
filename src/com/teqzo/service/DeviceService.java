package com.teqzo.service;

import com.teqzo.domain.DeviceRow;
import com.teqzo.domain.NotFoundException;
import com.teqzo.dto.BrandDto;
import com.teqzo.dto.DeviceDetail;
import com.teqzo.dto.DeviceSummary;
import com.teqzo.dto.PageResponse;
import com.teqzo.filter.DeviceFilter;
import com.teqzo.repository.BrandRepository;
import com.teqzo.repository.DeviceRepository;

import java.util.ArrayList;
import java.util.List;

public class DeviceService {

	static final String DATA_SOURCE = "Specifications come from the imported GSMArena dataset and, for newer phones, "
			+ "from the 2026 Flipkart listing dataset and the Mobiles Dataset 2025, which have fewer fields. "
			+ "Values the dataset does not state are shown as not listed.";

	private final DeviceRepository devices;
	private final BrandRepository brands;
	private final PricePolicy pricePolicy;

	public DeviceService(DeviceRepository devices, BrandRepository brands, PricePolicy pricePolicy) {
		this.devices = devices;
		this.brands = brands;
		this.pricePolicy = pricePolicy;
	}

	public PageResponse<DeviceSummary> search(DeviceFilter filter) {
		Double fx = pricePolicy.eurToInr();
		return devices.search(filter, fx).map(row -> DeviceMapper.toSummary(row, fx));
	}

	public PageResponse<DeviceSummary> byBrand(String brandId, DeviceFilter filter) {
		BrandDto brand = brands.findById(brandId).orElseThrow(() -> NotFoundException.brand(brandId));
		DeviceFilter scoped = filter.copy();
		scoped.brandIds = List.of(brand.id());
		return search(scoped);
	}

	public DeviceDetail get(String id) {
		DeviceRow row = devices.findById(id).orElseThrow(() -> NotFoundException.device(id));
		Double fx = pricePolicy.eurToInr();
		SpecSectionAssembler.Result specs = SpecSectionAssembler.assemble(devices.findSpecifications(id));

		List<String> notes = new ArrayList<>();
		if (!row.hasSpecData) {
			notes.add("This entry has no specification data in the imported dataset.");
		}
		if ("CONDITIONAL".equals(row.wirelessChargingStatus)) {
			notes.add("Wireless charging is listed as optional or market dependent.");
		}
		if ("CANCELLED".equals(row.statusCode)) {
			notes.add("This device was cancelled and never released.");
		}
		return new DeviceDetail(DeviceMapper.toSummary(row, fx), row.batteryMah == null ? null
				: (int) Math.round(row.batteryMah), DeviceMapper.toCharging(row), specs.sections(), specs.benchmarks(),
				notes, DATA_SOURCE);
	}

	/** Loads devices for the given ids, keeping the order of {@code ids}; throws if any id is unknown. */
	public List<DeviceRow> requireAll(List<String> ids) {
		List<DeviceRow> found = devices.findByIds(ids);
		List<DeviceRow> ordered = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		for (String id : ids) {
			DeviceRow match = found.stream().filter(r -> r.id.equals(id)).findFirst().orElse(null);
			if (match == null) {
				missing.add(id);
			} else {
				ordered.add(match);
			}
		}
		if (!missing.isEmpty()) {
			throw new NotFoundException("DEVICE_NOT_FOUND", "Device(s) not found: " + String.join(", ", missing));
		}
		return ordered;
	}
}

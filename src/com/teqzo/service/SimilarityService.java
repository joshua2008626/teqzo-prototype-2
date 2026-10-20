package com.teqzo.service;

import com.teqzo.domain.DeviceRow;
import com.teqzo.domain.NotFoundException;
import com.teqzo.dto.DeviceSummary;
import com.teqzo.filter.DeviceFilter;
import com.teqzo.repository.DeviceRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** "Phones like this one": candidates are pulled from the database near the device's price and screen size. */
public class SimilarityService {

	public record SimilarDevice(DeviceSummary device, double similarity, List<String> reasons) {
	}

	static final int CANDIDATE_LIMIT = 400;

	private final DeviceRepository devices;
	private final PricePolicy pricePolicy;

	public SimilarityService(DeviceRepository devices, PricePolicy pricePolicy) {
		this.devices = devices;
		this.pricePolicy = pricePolicy;
	}

	public List<SimilarDevice> similarTo(String deviceId, int limit) {
		DeviceRow base = devices.findById(deviceId).orElseThrow(() -> NotFoundException.device(deviceId));
		Double fx = pricePolicy.eurToInr();
		DeviceFilter filter = candidateFilter(base);
		List<SimilarDevice> scored = new ArrayList<>();
		for (DeviceRow other : devices.findCandidates(filter, fx, CANDIDATE_LIMIT)) {
			if (other.id.equals(base.id)) {
				continue;
			}
			SimilarityScorer.Result r = SimilarityScorer.score(base, other);
			if (r != null) {
				scored.add(new SimilarDevice(DeviceMapper.toSummary(other, fx), r.similarity(), r.reasons()));
			}
		}
		scored.sort(Comparator.comparingDouble(SimilarDevice::similarity).reversed());
		return scored.size() > limit ? new ArrayList<>(scored.subList(0, limit)) : scored;
	}

	/** Phones with specifications, in a price band and screen-size band around the device when it states them. */
	static DeviceFilter candidateFilter(DeviceRow base) {
		DeviceFilter f = new DeviceFilter();
		f.onlyWithSpecifications = true;
		f.excludeCancelled = true;
		if (base.priceInr != null && base.priceInr > 0) {
			f.priceMinInr = Math.floor(base.priceInr * 0.5);
			f.priceMaxInr = Math.ceil(base.priceInr * 1.8);
		}
		if (base.displaySizeInches != null) {
			f.displaySizeMinInches = Math.max(0, base.displaySizeInches - 1.2);
			f.displaySizeMaxInches = base.displaySizeInches + 1.2;
		}
		return f;
	}
}

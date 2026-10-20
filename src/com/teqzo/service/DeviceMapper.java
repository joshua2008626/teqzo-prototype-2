package com.teqzo.service;

import com.teqzo.domain.DeviceRow;
import com.teqzo.dto.ChargingInfo;
import com.teqzo.dto.DeviceSummary;
import com.teqzo.dto.KeySpecs;
import com.teqzo.dto.PriceInfo;

/** Converts internal rows into API DTOs. Never turns a missing value into a number. */
public final class DeviceMapper {

	private DeviceMapper() {
	}

	public static DeviceSummary toSummary(DeviceRow d, Double eurToInr) {
		return new DeviceSummary(d.id, d.name, d.brandId, d.brandName, d.thumbnailUrl, d.sourceUrl,
				d.announcedYear, d.statusRaw, d.hasSpecData, toKeySpecs(d), toPrice(d, eurToInr));
	}

	public static KeySpecs toKeySpecs(DeviceRow d) {
		return new KeySpecs(
				d.ramGb, d.storageGb, roundInt(d.batteryMah),
				d.wiredChargingW, d.wirelessChargingW, d.wirelessChargingStatus,
				d.reverseChargingW, d.reverseChargingStatus,
				d.displaySizeInches, d.displayType, d.displayResolution, roundInt(d.refreshRateHz),
				d.os, d.chipset, d.cpu, d.gpu,
				d.mainCameraMp, d.selfieCameraMp, d.weightG, d.ipRating,
				fiveG(d),
				d.nfcRaw, d.wifi, d.bluetooth, d.usb);
	}

	public static ChargingInfo toCharging(DeviceRow d) {
		return new ChargingInfo(d.chargingRaw, d.wiredChargingW, d.wirelessChargingW, d.wirelessChargingStatus,
				d.reverseChargingW, d.reverseChargingStatus);
	}

	public static PriceInfo toPrice(DeviceRow d, Double eurToInr) {
		Double approx = null;
		String basis = null;
		if (d.priceInr == null && d.priceEur != null && eurToInr != null) {
			approx = Math.round(d.priceEur * eurToInr / 100.0) * 100.0;
			basis = "Approximate: listed EUR price converted at a fixed configured rate of " + eurToInr + " INR per EUR";
		}
		return new PriceInfo(d.priceInr, d.priceEur, d.priceUsd, d.priceRaw, approx, basis);
	}

	/** The INR value used for budget decisions: the listed INR price, else the labelled approximation. */
	public static Double effectiveInr(DeviceRow d, Double eurToInr) {
		if (d.priceInr != null) {
			return d.priceInr;
		}
		PriceInfo p = toPrice(d, eurToInr);
		return p.approxInr();
	}

	/** TRUE/FALSE as stated; null when the dataset does not say (no specifications, or a limited-field source). */
	static Boolean fiveG(DeviceRow d) {
		if (d.has5g) {
			return Boolean.TRUE;
		}
		return d.hasSpecData && !d.limitedSpecs() ? Boolean.FALSE : null;
	}

	private static Integer roundInt(Double v) {
		return v == null ? null : (int) Math.round(v);
	}
}

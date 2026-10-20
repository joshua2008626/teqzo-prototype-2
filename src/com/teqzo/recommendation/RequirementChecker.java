package com.teqzo.recommendation;

import com.teqzo.domain.DeviceRow;
import com.teqzo.dto.RecommendationResponse.Check;
import com.teqzo.service.DeviceMapper;
import com.teqzo.util.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Evaluates each hard requirement against a device in Java. The database query already filters; this is
 * a second, independent check so that the explanation shows real values and so that a device can never be
 * presented as "meeting" a requirement the data does not support.
 */
public final class RequirementChecker {

	public static final String MET = "MET";
	public static final String NOT_MET = "NOT_MET";
	public static final String UNKNOWN = "UNKNOWN";

	private RequirementChecker() {
	}

	public static List<Check> check(Requirements r, DeviceRow d, Double eurToInr) {
		List<Check> out = new ArrayList<>();
		min(out, Requirements.RAM, "RAM", r.ramMinGb, d.ramGb, " GB");
		min(out, Requirements.STORAGE, "Storage", r.storageMinGb, d.storageGb, " GB");
		min(out, Requirements.BATTERY, "Battery", r.batteryMinMah, d.batteryMah, " mAh");
		min(out, Requirements.WIRED, "Wired charging", r.wiredChargingMinW, d.wiredChargingW, " W");
		min(out, Requirements.REFRESH, "Refresh rate", r.refreshRateMinHz, d.refreshRateHz, " Hz");
		min(out, Requirements.SIZE_MIN, "Display size (min)", r.displaySizeMinInches, d.displaySizeInches, " in");
		min(out, Requirements.CAMERA_MP, "Main camera", r.mainCameraMinMp, d.mainCameraMp, " MP");
		if (r.displaySizeMaxInches != null) {
			boolean known = d.displaySizeInches != null;
			out.add(new Check(Requirements.SIZE_MAX, "Display size (max)", "at most " + n(r.displaySizeMaxInches) + " in",
					known ? n(d.displaySizeInches) + " in" : "not listed",
					!known ? UNKNOWN : (d.displaySizeInches <= r.displaySizeMaxInches ? MET : NOT_MET)));
		}
		if (r.maxWeightG != null) {
			boolean known = d.weightG != null;
			out.add(new Check(Requirements.WEIGHT, "Weight", "at most " + n(r.maxWeightG) + " g",
					known ? n(d.weightG) + " g" : "not listed",
					!known ? UNKNOWN : (d.weightG <= r.maxWeightG ? MET : NOT_MET)));
		}
		if (Boolean.TRUE.equals(r.requiresWirelessCharging)) {
			String actual = "YES".equals(d.wirelessChargingStatus)
					? (d.wirelessChargingW != null ? n(d.wirelessChargingW) + " W" : "yes")
					: ("CONDITIONAL".equals(d.wirelessChargingStatus) ? "optional / market dependent" : "not listed");
			out.add(new Check(Requirements.WIRELESS, "Wireless charging", "required", actual,
					"YES".equals(d.wirelessChargingStatus) ? MET
							: ("CONDITIONAL".equals(d.wirelessChargingStatus) ? UNKNOWN : NOT_MET)));
		}
		if (Boolean.TRUE.equals(r.requiresReverseCharging)) {
			out.add(new Check(Requirements.REVERSE, "Reverse charging", "required",
					"YES".equals(d.reverseChargingStatus) ? "yes" : "not listed",
					"YES".equals(d.reverseChargingStatus) ? MET : NOT_MET));
		}
		if (Boolean.TRUE.equals(r.requires5g)) {
			boolean unknown = !d.has5g && d.limitedSpecs(); // the listing does not say either way
			out.add(new Check(Requirements.FIVE_G, "5G", "required",
					d.has5g ? "yes" : (unknown ? "not listed" : "no 5G listed"),
					d.has5g ? MET : (unknown ? UNKNOWN : NOT_MET)));
		}
		if (Boolean.TRUE.equals(r.requiresNfc)) {
			out.add(new Check(Requirements.NFC, "NFC", "required", d.nfcRaw == null ? "not listed" : d.nfcRaw,
					Boolean.TRUE.equals(d.nfcSupported) ? MET : (d.nfcSupported == null ? UNKNOWN : NOT_MET)));
		}
		if (Boolean.TRUE.equals(r.requiresWaterResistance)) {
			out.add(new Check(Requirements.WATER, "Water resistance", "IP rating listed",
					d.ipRating == null ? "no IP rating listed" : d.ipRating, d.ipRating != null ? MET : NOT_MET));
		}
		if (r.displayType != null) {
			boolean has = d.displayType != null;
			boolean match = has && d.displayType.toLowerCase(Locale.ROOT).contains(r.displayType);
			out.add(new Check(Requirements.DISPLAY_TYPE, "Display type", r.displayType.toUpperCase(Locale.ROOT),
					has ? d.displayType : "not listed", !has ? UNKNOWN : (match ? MET : NOT_MET)));
		}
		if (r.os != null) {
			boolean has = d.os != null;
			boolean match = has && d.os.toLowerCase(Locale.ROOT).contains(r.os);
			out.add(new Check(Requirements.OS, "Operating system", r.os.equals("ios") ? "iOS" : "Android",
					has ? d.os : "not listed", !has ? UNKNOWN : (match ? MET : NOT_MET)));
		}
		if (!r.brands.isEmpty()) {
			boolean match = d.brandName != null && r.brands.stream().anyMatch(b -> b.equalsIgnoreCase(d.brandName));
			out.add(new Check(Requirements.BRANDS, "Brand", String.join(" / ", r.brands),
					d.brandName == null ? "not listed" : d.brandName, match ? MET : NOT_MET));
		}
		if (r.minReleaseYear != null) {
			boolean has = d.announcedYear != null;
			String actualYear = has ? String.valueOf(d.announcedYear)
					: (d.listedYear != null ? "on sale in " + d.listedYear + " (launch date not listed)" : "not listed");
			out.add(new Check(Requirements.YEAR, "Announced", "in or after " + r.minReleaseYear,
					actualYear,
					!has ? UNKNOWN : (d.announcedYear >= r.minReleaseYear ? MET : NOT_MET)));
		}
		if (r.budgetMaxInr != null) {
			Double price = DeviceMapper.effectiveInr(d, eurToInr);
			boolean approx = d.priceInr == null && price != null;
			String actual = price == null ? "price not listed" : Money.inr(price) + (approx ? " (approximate)" : "");
			out.add(new Check(Requirements.BUDGET, "Price", "up to " + Money.inr(r.budgetMaxInr), actual, price == null ? UNKNOWN : (price <= r.budgetMaxInr ? MET : NOT_MET)));
		}
		return out;
	}

	private static void min(List<Check> out, String key, String label, Double required, Double actual, String unit) {
		if (required == null) {
			return;
		}
		out.add(new Check(key, label, "at least " + n(required) + unit,
				actual == null ? "not listed" : n(actual) + unit,
				actual == null ? UNKNOWN : (actual >= required ? MET : NOT_MET)));
	}

	private static String n(double v) {
		return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10.0) / 10.0);
	}
}

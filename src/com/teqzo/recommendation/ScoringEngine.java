package com.teqzo.recommendation;

import com.teqzo.domain.DeviceRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, transparent scoring of one device. Every sub-score is a simple function of values the
 * dataset lists, scaled against a fixed cap. Dimensions without any listed value score nothing (and are
 * reported as "not listed") instead of being guessed. There are no benchmark scores in the dataset, so
 * "performance" is a proxy built from RAM, CPU clock and core count; it is labelled as such in the result.
 */
public final class ScoringEngine {

	// Caps: a value at or above the cap earns the full sub-score.
	static final double RAM_CAP_GB = 12;
	static final double STORAGE_CAP_GB = 512;
	static final double CPU_CAP_GHZ = 3.2;
	static final double CPU_CORES_CAP = 8;
	static final double BATTERY_FLOOR_MAH = 2000;
	static final double BATTERY_CAP_MAH = 6000;
	static final double WIRED_CAP_W = 100;
	static final double WIRELESS_CAP_W = 30;
	static final double WIRELESS_UNKNOWN_WATTS_CREDIT = 0.4;
	static final double MAIN_CAMERA_CAP_MP = 108;
	static final double SELFIE_CAMERA_CAP_MP = 32;
	static final double DISPLAY_SIZE_FLOOR_IN = 4;
	static final double DISPLAY_SIZE_CAP_IN = 7;
	static final double REFRESH_FLOOR_HZ = 60;
	static final double REFRESH_CAP_HZ = 144;
	static final double RESOLUTION_CAP_PIXELS = 1440.0 * 3200.0;
	static final int FRESHNESS_WINDOW_YEARS = 8;

	private ScoringEngine() {
	}

	public record Result(double total, List<ScoreComponent> components) {
	}

	/** @param newestYear newest release year in the dataset; recency is measured relative to it */
	public static Result score(DeviceRow d, Weights weights, int newestYear) {
		List<ScoreComponent> out = new ArrayList<>();
		double total = 0;
		for (Dimension dim : Dimension.values()) {
			Double sub = subScore(dim, d, newestYear);
			double w = weights.of(dim);
			double points = sub == null ? 0 : sub * w * 100.0;
			total += points;
			out.add(new ScoreComponent(dim, sub, w, points, describe(dim, d)));
		}
		return new Result(total, out);
	}

	static Double subScore(Dimension dim, DeviceRow d, int newestYear) {
		switch (dim) {
			case PERFORMANCE:
				return weightedAverage(
						part(d.ramGb, v -> clamp01(v / RAM_CAP_GB), 0.4),
						part(d.cpuMaxGhz, v -> clamp01(v / CPU_CAP_GHZ), 0.4),
						part(d.cpuCores, v -> clamp01(v / CPU_CORES_CAP), 0.2));
			case MEMORY:
				return weightedAverage(
						part(d.ramGb, v -> clamp01(v / RAM_CAP_GB), 0.5),
						part(d.storageGb, v -> clamp01(v / STORAGE_CAP_GB), 0.5));
			case BATTERY:
				return d.batteryMah == null ? null
						: clamp01((d.batteryMah - BATTERY_FLOOR_MAH) / (BATTERY_CAP_MAH - BATTERY_FLOOR_MAH));
			case CHARGING:
				return charging(d);
			case CAMERA:
				return weightedAverage(
						part(d.mainCameraMp, v -> clamp01(v / MAIN_CAMERA_CAP_MP), 0.7),
						part(d.selfieCameraMp, v -> clamp01(v / SELFIE_CAMERA_CAP_MP), 0.3));
			case DISPLAY:
				return weightedAverage(
						part(d.displaySizeInches, v -> clamp01((v - DISPLAY_SIZE_FLOOR_IN)
								/ (DISPLAY_SIZE_CAP_IN - DISPLAY_SIZE_FLOOR_IN)), 0.2),
						part(d.refreshRateHz, v -> clamp01((v - REFRESH_FLOOR_HZ)
								/ (REFRESH_CAP_HZ - REFRESH_FLOOR_HZ)), 0.3),
						part(displayTypeScore(d.displayType), v -> v, 0.25),
						part(pixels(d), v -> clamp01(v / RESOLUTION_CAP_PIXELS), 0.25));
			case CONNECTIVITY:
				return connectivity(d);
			case FRESHNESS:
			{
				Integer year = d.announcedYear != null ? d.announcedYear : d.listedYear;
				return year == null ? null
						: clamp01((year - (newestYear - FRESHNESS_WINDOW_YEARS)) / (double) FRESHNESS_WINDOW_YEARS);
			}
			default:
				return null;
		}
	}

	private static Double charging(DeviceRow d) {
		if (d.chargingRaw == null && d.wiredChargingW == null && d.wirelessChargingW == null) {
			return null;
		}
		double wired = d.wiredChargingW == null ? 0 : clamp01(d.wiredChargingW / WIRED_CAP_W);
		double wireless = 0;
		if (d.wirelessChargingW != null) {
			wireless = clamp01(d.wirelessChargingW / WIRELESS_CAP_W);
		} else if ("YES".equals(d.wirelessChargingStatus)) {
			wireless = WIRELESS_UNKNOWN_WATTS_CREDIT;
		}
		double reverse = "YES".equals(d.reverseChargingStatus) ? 1 : 0;
		return 0.6 * wired + 0.25 * wireless + 0.15 * reverse;
	}

	private static Double connectivity(DeviceRow d) {
		if (!d.hasSpecData) {
			return null;
		}
		return weightedAverage(
				part(d.has5g ? Double.valueOf(1.0) : (d.limitedSpecs() ? null : Double.valueOf(0.0)), v -> v, 0.35),
				part(d.nfcSupported == null ? null : (d.nfcSupported ? 1.0 : 0.0), v -> v, 0.2),
				part(d.wifiGeneration, v -> clamp01((v - 4) / 2.0), 0.2),
				part(d.bluetoothVersion, v -> clamp01((v - 4.0) / 1.2), 0.15),
				part(d.usbTypeC == null ? null : (d.usbTypeC ? 1.0 : 0.0), v -> v, 0.1));
	}

	private static Double displayTypeScore(String type) {
		if (type == null) {
			return null;
		}
		String t = type.toLowerCase(Locale.ROOT);
		if (t.contains("oled")) {
			return 1.0;
		}
		if (t.contains("ips") || t.contains("lcd")) {
			return 0.5;
		}
		return 0.3;
	}

	private static Double pixels(DeviceRow d) {
		return d.resolutionShortPx == null || d.resolutionLongPx == null ? null
				: (double) d.resolutionShortPx * d.resolutionLongPx;
	}

	// ---------------------------------------------------------------- explanation text

	/** Human-readable listing of the values behind a dimension. Only listed values appear. */
	public static String describe(Dimension dim, DeviceRow d) {
		List<String> parts = new ArrayList<>();
		switch (dim) {
			case PERFORMANCE:
				add(parts, d.ramGb, v -> num(v) + " GB RAM");
				if (d.cpuCores != null && d.cpuMaxGhz != null) {
					parts.add(d.cpuCores + " cores, up to " + num(d.cpuMaxGhz) + " GHz");
				} else if (d.cpuMaxGhz != null) {
					parts.add("up to " + num(d.cpuMaxGhz) + " GHz");
				} else if (d.cpuCores != null) {
					parts.add(d.cpuCores + " cores");
				}
				if (d.chipset != null) {
					parts.add(d.chipset);
				}
				break;
			case MEMORY:
				add(parts, d.ramGb, v -> num(v) + " GB RAM");
				add(parts, d.storageGb, v -> num(v) + " GB storage");
				break;
			case BATTERY:
				add(parts, d.batteryMah, v -> num(v) + " mAh");
				break;
			case CHARGING:
				add(parts, d.wiredChargingW, v -> num(v) + " W wired");
				if (d.wirelessChargingW != null) {
					parts.add(num(d.wirelessChargingW) + " W wireless");
				} else if ("YES".equals(d.wirelessChargingStatus)) {
					parts.add("wireless (wattage not listed)");
				} else if ("CONDITIONAL".equals(d.wirelessChargingStatus)) {
					parts.add("wireless optional / market dependent");
				}
				if (d.reverseChargingW != null) {
					parts.add(num(d.reverseChargingW) + " W reverse");
				} else if ("YES".equals(d.reverseChargingStatus)) {
					parts.add("reverse charging");
				}
				if (parts.isEmpty() && d.chargingRaw != null) {
					parts.add(d.chargingRaw);
				}
				break;
			case CAMERA:
				add(parts, d.mainCameraMp, v -> num(v) + " MP main");
				add(parts, d.selfieCameraMp, v -> num(v) + " MP selfie");
				break;
			case DISPLAY:
				add(parts, d.displaySizeInches, v -> num(v) + " in");
				if (d.displayType != null) {
					parts.add(d.displayType.split(",")[0].trim());
				}
				add(parts, d.refreshRateHz, v -> num(v) + " Hz");
				break;
			case CONNECTIVITY:
				if (d.hasSpecData && (d.has5g || !d.limitedSpecs())) {
					parts.add(d.has5g ? "5G" : "no 5G listed");
				}
				if (d.nfcSupported != null) {
					parts.add(d.nfcSupported ? "NFC" : "no NFC");
				}
				if (d.wifiGeneration != null) {
					parts.add("Wi-Fi " + d.wifiGeneration + "-class");
				}
				if (d.bluetoothVersion != null) {
					parts.add("Bluetooth " + num(d.bluetoothVersion));
				}
				break;
			case FRESHNESS:
				if (d.announcedYear != null) {
					parts.add("announced " + d.announcedYear);
				} else if (d.listedYear != null) {
					parts.add("on sale in " + d.listedYear + " (launch date not listed)");
				}
				break;
			default:
				break;
		}
		return parts.isEmpty() ? "not listed" : String.join(", ", parts);
	}

	// ---------------------------------------------------------------- helpers

	private interface Fn {
		double apply(double v);
	}

	private record Part(Double value, Fn fn, double weight) {
	}

	private static Part part(Number value, Fn fn, double weight) {
		return new Part(value == null ? null : value.doubleValue(), fn, weight);
	}

	private static Double weightedAverage(Part... parts) {
		double sum = 0;
		double weight = 0;
		for (Part p : parts) {
			if (p.value() != null) {
				sum += p.fn().apply(p.value()) * p.weight();
				weight += p.weight();
			}
		}
		return weight == 0 ? null : sum / weight;
	}

	private static void add(List<String> parts, Double value, java.util.function.Function<Double, String> f) {
		if (value != null) {
			parts.add(f.apply(value));
		}
	}

	static double clamp01(double v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	static String num(double v) {
		return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 10.0) / 10.0);
	}
}

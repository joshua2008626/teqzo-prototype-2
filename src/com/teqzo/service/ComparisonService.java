package com.teqzo.service;

import com.teqzo.domain.DeviceRow;
import com.teqzo.domain.InvalidRequestException;
import com.teqzo.dto.ComparisonResponse;
import com.teqzo.dto.ComparisonResponse.ComparisonCell;
import com.teqzo.dto.ComparisonResponse.ComparisonGroup;
import com.teqzo.dto.ComparisonResponse.ComparisonRow;
import com.teqzo.dto.DeviceSummary;
import com.teqzo.util.Money;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ComparisonService {

	public static final int MIN_DEVICES = 2;
	public static final int MAX_DEVICES = 4;
	static final String NOT_LISTED = "Not listed";

	private static final String HIGHER = "HIGHER_IS_BETTER";
	private static final String LOWER = "LOWER_IS_BETTER";
	private static final String NEUTRAL = "NEUTRAL";

	private record Field(String group, String key, String label, String direction,
			Function<DeviceRow, Object> value, Function<DeviceRow, String> display) {
	}

	private final DeviceService deviceService;
	private final PricePolicy pricePolicy;
	private final List<Field> fields;

	public ComparisonService(DeviceService deviceService, PricePolicy pricePolicy) {
		this.deviceService = deviceService;
		this.pricePolicy = pricePolicy;
		this.fields = buildFields();
	}

	public ComparisonResponse compare(List<String> ids) {
		List<String> unique = validate(ids);
		List<DeviceRow> rows = deviceService.requireAll(unique);
		Double fx = pricePolicy.eurToInr();

		Map<String, List<ComparisonRow>> grouped = new LinkedHashMap<>();
		for (Field f : fields) {
			grouped.computeIfAbsent(f.group(), k -> new ArrayList<>()).add(buildRow(f, rows));
		}
		List<ComparisonGroup> groups = new ArrayList<>();
		grouped.forEach((title, list) -> groups.add(new ComparisonGroup(title, list)));

		List<String> notes = new ArrayList<>();
		notes.add("\"Highest\" / \"Lowest\" markers appear only for numbers where more (or less) is a defensible"
				+ " advantage. Megapixels, screen size and weight are shown without a verdict.");
		notes.add("\"Not listed\" means the dataset does not state the value; it does not mean the feature is absent.");
		return new ComparisonResponse(rows.stream().map(r -> DeviceMapper.toSummary(r, fx)).toList(), groups, notes);
	}

	static List<String> validate(List<String> ids) {
		if (ids == null) {
			throw InvalidRequestException.request("ids is required.");
		}
		LinkedHashSet<String> unique = new LinkedHashSet<>();
		for (String id : ids) {
			if (id == null || id.isBlank()) {
				throw InvalidRequestException.request("Device ids must not be blank.");
			}
			unique.add(id.trim());
		}
		if (unique.size() < MIN_DEVICES || unique.size() > MAX_DEVICES) {
			throw InvalidRequestException.request(
					"Select between " + MIN_DEVICES + " and " + MAX_DEVICES + " different devices to compare.");
		}
		return new ArrayList<>(unique);
	}

	private ComparisonRow buildRow(Field f, List<DeviceRow> devices) {
		List<ComparisonCell> cells = new ArrayList<>();
		List<Object> values = new ArrayList<>();
		for (DeviceRow d : devices) {
			Object v = f.value().apply(d);
			String shown = f.display().apply(d);
			cells.add(new ComparisonCell(shown == null ? NOT_LISTED : shown, v));
			values.add(shown);
		}
		boolean differs = values.stream().map(v -> v == null ? NOT_LISTED : v).distinct().count() > 1;
		return new ComparisonRow(f.key(), f.label(), f.direction(), differs, leaders(f, cells), cells);
	}

	private static List<Integer> leaders(Field f, List<ComparisonCell> cells) {
		if (NEUTRAL.equals(f.direction())) {
			return List.of();
		}
		List<Integer> numericIndexes = new ArrayList<>();
		List<Double> numbers = new ArrayList<>();
		for (int i = 0; i < cells.size(); i++) {
			if (cells.get(i).value() instanceof Number n) {
				numericIndexes.add(i);
				numbers.add(n.doubleValue());
			}
		}
		if (numbers.size() < 2 || numbers.stream().distinct().count() < 2) {
			return List.of();
		}
		double best = HIGHER.equals(f.direction())
				? numbers.stream().mapToDouble(Double::doubleValue).max().getAsDouble()
				: numbers.stream().mapToDouble(Double::doubleValue).min().getAsDouble();
		List<Integer> out = new ArrayList<>();
		for (int i = 0; i < numbers.size(); i++) {
			if (numbers.get(i) == best) {
				out.add(numericIndexes.get(i));
			}
		}
		return out;
	}

	// ------------------------------------------------------------------ field table

	private List<Field> buildFields() {
		List<Field> list = new ArrayList<>();
		String d = "Display";
		list.add(num(d, "displaySize", "Size", NEUTRAL, r -> r.displaySizeInches, " in"));
		list.add(text(d, "resolution", "Resolution", r -> r.displayResolution));
		list.add(text(d, "displayType", "Type", r -> r.displayType));
		list.add(num(d, "refreshRate", "Refresh rate", HIGHER, r -> r.refreshRateHz, " Hz"));

		String p = "Platform & memory";
		list.add(text(p, "os", "Operating system", r -> r.os));
		list.add(text(p, "chipset", "Chipset", r -> r.chipset));
		list.add(text(p, "cpu", "CPU", r -> r.cpu));
		list.add(text(p, "gpu", "GPU", r -> r.gpu));
		list.add(num(p, "ram", "RAM", HIGHER, r -> r.ramGb, " GB"));
		list.add(num(p, "storage", "Storage", HIGHER, r -> r.storageGb, " GB"));

		String c = "Camera";
		list.add(num(c, "mainCamera", "Main camera", NEUTRAL, r -> r.mainCameraMp, " MP"));
		list.add(num(c, "selfieCamera", "Selfie camera", NEUTRAL, r -> r.selfieCameraMp, " MP"));

		String b = "Battery & charging";
		list.add(num(b, "battery", "Battery capacity", HIGHER, r -> r.batteryMah, " mAh"));
		list.add(num(b, "wired", "Wired charging", HIGHER, r -> r.wiredChargingW, " W"));
		list.add(new Field(b, "wireless", "Wireless charging", HIGHER, r -> r.wirelessChargingW,
				r -> chargingText(r.wirelessChargingW, r.wirelessChargingStatus)));
		list.add(new Field(b, "reverse", "Reverse charging", HIGHER, r -> r.reverseChargingW,
				r -> chargingText(r.reverseChargingW, r.reverseChargingStatus)));
		list.add(text(b, "chargingRaw", "Charging (source text)", r -> r.chargingRaw));

		String n = "Connectivity";
		list.add(new Field(n, "has5g", "5G", NEUTRAL, DeviceMapper::fiveG,
				r -> DeviceMapper.fiveG(r) == null ? null : (r.has5g ? "Yes" : "No 5G listed")));
		list.add(text(n, "wifi", "Wi-Fi", r -> r.wifi));
		list.add(text(n, "bluetooth", "Bluetooth", r -> r.bluetooth));
		list.add(text(n, "nfc", "NFC", r -> r.nfcRaw));
		list.add(text(n, "usb", "USB", r -> r.usb));

		String body = "Body";
		list.add(num(body, "weight", "Weight", NEUTRAL, r -> r.weightG, " g"));
		list.add(text(body, "ip", "IP rating", r -> r.ipRating));

		String l = "Launch & price";
		list.add(new Field(l, "year", "Announced", NEUTRAL, r -> r.announcedYear,
				r -> r.announcedYear == null ? null : String.valueOf(r.announcedYear)));
		list.add(text(l, "status", "Status", r -> r.statusRaw));
		list.add(new Field(l, "priceInr", "Listed price (INR)", LOWER, r -> r.priceInr,
				r -> r.priceInr == null ? null : Money.inr(r.priceInr)));
		return list;
	}

	private static Field num(String group, String key, String label, String direction,
			Function<DeviceRow, Double> getter, String unit) {
		return new Field(group, key, label, direction, getter::apply, r -> {
			Double v = getter.apply(r);
			return v == null ? null : formatNumber(v) + unit;
		});
	}

	private static Field text(String group, String key, String label, Function<DeviceRow, String> getter) {
		return new Field(group, key, label, NEUTRAL, getter::apply, r -> {
			String v = getter.apply(r);
			return v == null || v.isBlank() ? null : v;
		});
	}

	static String chargingText(Double watts, String status) {
		String qualifier = "CONDITIONAL".equals(status) ? " (optional or market dependent)" : "";
		if (watts != null) {
			return formatNumber(watts) + " W" + qualifier;
		}
		if ("YES".equals(status)) {
			return "Yes (wattage not listed)";
		}
		if ("CONDITIONAL".equals(status)) {
			return "Optional or market dependent (wattage not listed)";
		}
		return null;
	}

	static String formatNumber(double v) {
		return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(Math.round(v * 100.0) / 100.0);
	}
}

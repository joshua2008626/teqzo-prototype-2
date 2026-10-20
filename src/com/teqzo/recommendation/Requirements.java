package com.teqzo.recommendation;

import com.teqzo.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Structured user requirements. This is the contract between the language understanding step (an LLM
 * or the rule-based parser) and the deterministic recommendation engine.
 *
 * <p>Hard requirements (numbers, booleans, brand, OS, budget) decide which devices are eligible.
 * {@link #priorities} and {@link #ignored} only influence ranking.
 *
 * <p>The JSON form uses snake_case keys, for example {@code ram_min_gb}.
 */
public class Requirements {

	// ---- hard requirements -------------------------------------------------------------------
	public Double ramMinGb;
	public Double storageMinGb;
	public Double batteryMinMah;
	public Double wiredChargingMinW;
	public Boolean requiresWirelessCharging;
	public Boolean requiresReverseCharging;
	public Boolean requires5g;
	public Boolean requiresNfc;
	public Boolean requiresWaterResistance;
	public Double refreshRateMinHz;
	public Double displaySizeMinInches;
	public Double displaySizeMaxInches;
	public String displayType;
	public Double mainCameraMinMp;
	public Double maxWeightG;
	public String os;
	public List<String> brands = new ArrayList<>();
	public List<String> excludeBrands = new ArrayList<>();
	public Double budgetMaxInr;
	public Integer minReleaseYear;

	// ---- soft preferences ----------------------------------------------------------------------
	public List<UseCase> priorities = new ArrayList<>();
	public List<Dimension> ignored = new ArrayList<>();

	public static final String RAM = "ram_min_gb";
	public static final String STORAGE = "storage_min_gb";
	public static final String BATTERY = "battery_min_mah";
	public static final String WIRED = "wired_charging_min_w";
	public static final String WIRELESS = "requires_wireless_charging";
	public static final String REVERSE = "requires_reverse_charging";
	public static final String FIVE_G = "requires_5g";
	public static final String NFC = "requires_nfc";
	public static final String WATER = "requires_water_resistance";
	public static final String REFRESH = "refresh_rate_min_hz";
	public static final String SIZE_MIN = "display_size_min_inches";
	public static final String SIZE_MAX = "display_size_max_inches";
	public static final String DISPLAY_TYPE = "display_type";
	public static final String CAMERA_MP = "main_camera_min_mp";
	public static final String WEIGHT = "max_weight_g";
	public static final String OS = "os";
	public static final String BRANDS = "brands";
	public static final String EXCLUDE_BRANDS = "exclude_brands";
	public static final String BUDGET = "budget_max_inr";
	public static final String YEAR = "min_release_year";
	public static final String USE_CASE = "use_case";
	public static final String PRIORITIES = "priorities";
	public static final String IGNORED = "ignored";

	/** Every key a client or model may send. Anything else is ignored. */
	public static final Set<String> KNOWN_KEYS = Set.of(RAM, STORAGE, BATTERY, WIRED, WIRELESS, REVERSE, FIVE_G,
			NFC, WATER, REFRESH, SIZE_MIN, SIZE_MAX, DISPLAY_TYPE, CAMERA_MP, WEIGHT, OS, BRANDS, EXCLUDE_BRANDS,
			BUDGET, YEAR, USE_CASE, PRIORITIES, IGNORED);

	private static final Set<String> LIST_KEYS = Set.of(BRANDS, EXCLUDE_BRANDS, PRIORITIES, IGNORED);

	/** True when nothing has been asked for yet (no constraints and no priorities). */
	public boolean isEmpty() {
		return toMap().isEmpty();
	}

	/** True when at least one hard requirement is set. */
	public boolean hasHardRequirements() {
		Map<String, Object> m = toMap();
		m.remove(USE_CASE);
		m.remove(PRIORITIES);
		m.remove(IGNORED);
		return !m.isEmpty();
	}

	public UseCase primaryUseCase() {
		return priorities.isEmpty() ? UseCase.BALANCED : priorities.get(0);
	}

	public Requirements copy() {
		return fromMap(toMap());
	}

	// ------------------------------------------------------------------ JSON map form

	/** Only fields that are set are included; empty lists are omitted. */
	public Map<String, Object> toMap() {
		Map<String, Object> m = new LinkedHashMap<>();
		put(m, RAM, ramMinGb);
		put(m, STORAGE, storageMinGb);
		put(m, BATTERY, batteryMinMah);
		put(m, WIRED, wiredChargingMinW);
		put(m, WIRELESS, requiresWirelessCharging);
		put(m, REVERSE, requiresReverseCharging);
		put(m, FIVE_G, requires5g);
		put(m, NFC, requiresNfc);
		put(m, WATER, requiresWaterResistance);
		put(m, REFRESH, refreshRateMinHz);
		put(m, SIZE_MIN, displaySizeMinInches);
		put(m, SIZE_MAX, displaySizeMaxInches);
		put(m, DISPLAY_TYPE, displayType);
		put(m, CAMERA_MP, mainCameraMinMp);
		put(m, WEIGHT, maxWeightG);
		put(m, OS, os);
		if (!brands.isEmpty()) {
			m.put(BRANDS, new ArrayList<>(brands));
		}
		if (!excludeBrands.isEmpty()) {
			m.put(EXCLUDE_BRANDS, new ArrayList<>(excludeBrands));
		}
		put(m, BUDGET, budgetMaxInr);
		put(m, YEAR, minReleaseYear);
		if (!priorities.isEmpty()) {
			m.put(USE_CASE, priorities.get(0).key());
			List<String> keys = new ArrayList<>();
			priorities.forEach(p -> keys.add(p.key()));
			m.put(PRIORITIES, keys);
		}
		if (!ignored.isEmpty()) {
			List<String> keys = new ArrayList<>();
			ignored.forEach(i -> keys.add(i.key()));
			m.put(IGNORED, keys);
		}
		return m;
	}

	private static void put(Map<String, Object> m, String key, Object value) {
		if (value != null) {
			m.put(key, value);
		}
	}

	/**
	 * Lenient reader: unknown keys are ignored, numbers may arrive as numbers or numeric strings, wrongly
	 * typed values are skipped. Range checks are done by {@link RequirementsValidator}, not here.
	 */
	public static Requirements fromMap(Map<String, Object> map) {
		Requirements r = new Requirements();
		if (map == null) {
			return r;
		}
		r.ramMinGb = number(map.get(RAM));
		r.storageMinGb = number(map.get(STORAGE));
		r.batteryMinMah = number(map.get(BATTERY));
		r.wiredChargingMinW = number(map.get(WIRED));
		r.requiresWirelessCharging = trueOnly(map.get(WIRELESS));
		r.requiresReverseCharging = trueOnly(map.get(REVERSE));
		r.requires5g = trueOnly(map.get(FIVE_G));
		r.requiresNfc = trueOnly(map.get(NFC));
		r.requiresWaterResistance = trueOnly(map.get(WATER));
		r.refreshRateMinHz = number(map.get(REFRESH));
		r.displaySizeMinInches = number(map.get(SIZE_MIN));
		r.displaySizeMaxInches = number(map.get(SIZE_MAX));
		r.displayType = text(map.get(DISPLAY_TYPE));
		r.mainCameraMinMp = number(map.get(CAMERA_MP));
		r.maxWeightG = number(map.get(WEIGHT));
		r.os = text(map.get(OS));
		r.brands = strings(map.get(BRANDS));
		r.excludeBrands = strings(map.get(EXCLUDE_BRANDS));
		r.budgetMaxInr = number(map.get(BUDGET));
		Double year = number(map.get(YEAR));
		r.minReleaseYear = year == null ? null : (int) Math.round(year);

		LinkedHashSet<UseCase> useCases = new LinkedHashSet<>();
		UseCase primary = UseCase.fromKey(text(map.get(USE_CASE)));
		if (primary != null && primary != UseCase.BALANCED) {
			useCases.add(primary);
		}
		for (String s : strings(map.get(PRIORITIES))) {
			UseCase u = UseCase.fromKey(s);
			if (u != null && u != UseCase.BALANCED) {
				useCases.add(u);
			}
		}
		r.priorities = new ArrayList<>(useCases);

		LinkedHashSet<Dimension> ignoredDims = new LinkedHashSet<>();
		for (String s : strings(map.get(IGNORED))) {
			Dimension d = Dimension.fromKey(s);
			if (d != null) {
				ignoredDims.add(d);
			}
		}
		r.ignored = new ArrayList<>(ignoredDims);
		return r;
	}

	// ------------------------------------------------------------------ merging

	/**
	 * Combines what was known before with what the latest message changed.
	 *
	 * @param reset  forget everything known before
	 * @param clear  keys to remove from the previous state
	 * @param update keys the latest message set; scalars replace, lists are added to
	 */
	public static Map<String, Object> merge(Map<String, Object> previous, Map<String, Object> update,
			Set<String> clear, boolean reset) {
		Map<String, Object> result = new LinkedHashMap<>();
		if (!reset && previous != null) {
			result.putAll(fromMap(previous).toMap());
		}
		if (clear != null) {
			for (String key : clear) {
				result.remove(key);
				if (USE_CASE.equals(key)) {
					result.remove(PRIORITIES);
				}
			}
		}
		if (update == null) {
			return result;
		}
		Map<String, Object> cleanUpdate = fromMap(update).toMap();
		for (Map.Entry<String, Object> e : cleanUpdate.entrySet()) {
			String key = e.getKey();
			if (USE_CASE.equals(key)) {
				continue; // derived from priorities
			}
			if (LIST_KEYS.contains(key)) {
				result.put(key, union(result.get(key), e.getValue()));
			} else {
				result.put(key, e.getValue());
			}
		}
		// A dimension cannot be both a priority and ignored: the most recent statement wins.
		Requirements merged = fromMap(result);
		Requirements delta = fromMap(cleanUpdate);
		merged.ignored.removeIf(d -> delta.priorities.stream().anyMatch(p -> mapsTo(p, d)));
		merged.priorities.removeIf(p -> delta.ignored.stream().anyMatch(d -> mapsTo(p, d)));
		return merged.toMap();
	}

	/** True when the use case is "about" the dimension (camera use case and camera dimension). */
	static boolean mapsTo(UseCase useCase, Dimension dimension) {
		switch (useCase) {
			case CAMERA:
				return dimension == Dimension.CAMERA;
			case BATTERY:
				return dimension == Dimension.BATTERY || dimension == Dimension.CHARGING;
			case DISPLAY:
				return dimension == Dimension.DISPLAY;
			case PERFORMANCE:
			case GAMING:
				return dimension == Dimension.PERFORMANCE;
			default:
				return false;
		}
	}

	private static Object union(Object existing, Object added) {
		LinkedHashSet<String> out = new LinkedHashSet<>(strings(existing));
		out.addAll(strings(added));
		return new ArrayList<>(out);
	}

	// ------------------------------------------------------------------ coercion helpers

	static Double number(Object o) {
		if (o instanceof Number) {
			double d = ((Number) o).doubleValue();
			return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
		}
		if (o instanceof String) {
			try {
				double d = Double.parseDouble(((String) o).trim().replace(",", ""));
				return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}

	/** The assistant only records "must have" statements; "false"/null mean no requirement. */
	static Boolean trueOnly(Object o) {
		if (o instanceof Boolean) {
			return ((Boolean) o) ? Boolean.TRUE : null;
		}
		if (o instanceof String) {
			return "true".equalsIgnoreCase(((String) o).trim()) ? Boolean.TRUE : null;
		}
		return null;
	}

	static String text(Object o) {
		if (!(o instanceof String)) {
			return null;
		}
		String s = ((String) o).trim();
		return s.isEmpty() ? null : s;
	}

	static List<String> strings(Object o) {
		List<String> out = new ArrayList<>();
		if (o instanceof List) {
			for (Object item : (List<?>) o) {
				String s = text(item);
				if (s != null && !out.contains(s)) {
					out.add(s);
				}
			}
		} else if (o instanceof String) {
			for (String part : ((String) o).split(",")) {
				String s = text(part);
				if (s != null && !out.contains(s)) {
					out.add(s);
				}
			}
		}
		return out;
	}

	@Override
	public String toString() {
		return Json.stringify(toMap());
	}

	/** Lower-case key for case-insensitive comparisons. */
	static String norm(String s) {
		return s == null ? null : s.trim().toLowerCase(Locale.ROOT);
	}
}

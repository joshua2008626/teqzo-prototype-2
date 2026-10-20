package com.teqzo.filter;

import com.teqzo.search.SearchQuery;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the WHERE / ORDER BY parts of every device query as portable SQL with named parameters.
 *
 * <p>User input only ever reaches the database as bound parameters. Column names and sort expressions
 * come from fixed strings in this class. Missing data never matches a requirement: a device with a
 * NULL value fails any condition on that value.
 */
public final class DeviceQueryBuilder {

	public static final String SELECT_COLUMNS = String.join(", ",
			"c.id AS id", "c.brand_id AS brand_id", "b.name AS brand_name", "c.name AS name",
			"c.url AS source_url", "c.thumbnail AS thumbnail",
			"c.ram_gb AS ram_gb", "c.storage_gb AS storage_gb", "c.battery_mah AS battery_mah",
			"c.wired_charging_w AS wired_charging_w", "x.wireless_charging_w AS wireless_charging_w",
			"c.reverse_charging_w AS reverse_charging_w",
			"x.wireless_charging_status AS wireless_charging_status",
			"x.reverse_charging_status AS reverse_charging_status", "x.charging_raw AS charging_raw",
			"c.display_size_inches AS display_size_inches", "c.display_type AS display_type",
			"c.display_resolution AS display_resolution", "c.refresh_rate_hz AS refresh_rate_hz",
			"x.resolution_short_px AS resolution_short_px", "x.resolution_long_px AS resolution_long_px",
			"c.os AS os", "c.chipset AS chipset", "c.cpu AS cpu", "c.gpu AS gpu",
			"x.cpu_cores AS cpu_cores", "x.cpu_max_ghz AS cpu_max_ghz",
			"c.main_camera_mp AS main_camera_mp", "c.selfie_camera_mp AS selfie_camera_mp",
			"c.weight_g AS weight_g", "c.ip_rating AS ip_rating", "c.has_5g AS has_5g",
			"c.wifi AS wifi", "x.wifi_generation AS wifi_generation", "c.bluetooth AS bluetooth",
			"x.bluetooth_version AS bluetooth_version", "c.nfc AS nfc_raw", "x.nfc_supported AS nfc_supported",
			"c.usb AS usb", "x.usb_type_c AS usb_type_c",
			"x.announced_year AS announced_year", "x.listed_year AS listed_year", "x.data_source AS data_source",
			"x.status_code AS status_code", "x.status_raw AS status_raw",
			"x.price_raw AS price_raw", "x.price_inr AS price_inr", "x.price_eur AS price_eur",
			"x.price_usd AS price_usd", "x.has_spec_data AS has_spec_data");

	public static final String FROM_CLAUSE = " FROM device_core_v4 c"
			+ " JOIN device_extras_v1 x ON x.device_id = c.id"
			+ " LEFT JOIN brand b ON b.id = c.brand_id";

	/**
	 * Year used to put the newest devices first: the announced year, or the year a retail listing was seen for
	 * devices added from the Flipkart dataset (which carries no launch date).
	 */
	static final String SORT_YEAR = "COALESCE(x.announced_year, x.listed_year)";

	/** The parts of a query. {@code params} is ordered and safe to hand to NamedParameterJdbcTemplate. */
	public record Built(String where, String orderBy, Map<String, Object> params) {
	}

	private DeviceQueryBuilder() {
	}

	/** Price expression used for budget filters and price sorting. */
	public static String priceExpression(Double eurToInr) {
		return eurToInr == null ? "x.price_inr" : "COALESCE(x.price_inr, x.price_eur * :fx)";
	}

	public static Built build(DeviceFilter f, Double eurToInr) {
		List<String> where = new ArrayList<>();
		Map<String, Object> params = new LinkedHashMap<>();
		SearchQuery query = SearchQuery.parse(f.q);

		if (f.q != null && !f.q.isBlank() && query == null) {
			where.add("1 = 0"); // search text with nothing searchable in it matches nothing
		}
		if (query != null) {
			for (int i = 0; i < query.tokens().size(); i++) {
				String token = query.tokens().get(i);
				String param = "t" + i;
				if (SearchQuery.matchesSpecText(token)) {
					where.add("(x.search_compact LIKE :" + param + " OR LOWER(c.chipset) LIKE :" + param + ")");
				} else {
					where.add("x.search_compact LIKE :" + param);
				}
				params.put(param, "%" + token + "%");
			}
			params.put("qExact", query.compact());
			params.put("qPrefix", query.compact() + "%");
			params.put("qSuffix", "%" + query.compact());
			params.put("qContains", "%" + query.compact() + "%");
		}

		addIn(where, params, "c.brand_id", "brand", f.brandIds, false);
		addIn(where, params, "c.brand_id", "xbrand", f.excludeBrandIds, true);

		addMin(where, params, "c.ram_gb", "ramMin", f.ramMinGb);
		addMin(where, params, "c.storage_gb", "storageMin", f.storageMinGb);
		addMin(where, params, "c.battery_mah", "batteryMin", f.batteryMinMah);
		addMin(where, params, "c.wired_charging_w", "wiredMin", f.wiredChargingMinW);
		addMin(where, params, "x.wireless_charging_w", "wirelessMin", f.wirelessChargingMinW);
		addMin(where, params, "c.refresh_rate_hz", "refreshMin", f.refreshRateMinHz);
		addMin(where, params, "c.display_size_inches", "sizeMin", f.displaySizeMinInches);
		addMin(where, params, "c.main_camera_mp", "mainCamMin", f.mainCameraMinMp);
		addMin(where, params, "c.selfie_camera_mp", "selfieCamMin", f.selfieCameraMinMp);
		if (f.displaySizeMaxInches != null) {
			where.add("c.display_size_inches <= :sizeMax");
			params.put("sizeMax", f.displaySizeMaxInches);
		}
		if (f.weightMaxG != null) {
			where.add("c.weight_g <= :weightMax");
			params.put("weightMax", f.weightMaxG);
		}

		if (Boolean.TRUE.equals(f.wirelessCharging)) {
			where.add("x.wireless_charging_status = 'YES'");
		}
		if (Boolean.TRUE.equals(f.reverseCharging)) {
			where.add("x.reverse_charging_status = 'YES'");
		}
		if (Boolean.TRUE.equals(f.ipRated)) {
			where.add("c.ip_rating IS NOT NULL");
		}
		if (f.has5g != null) {
			// "No 5G" only means something for devices with full GSMArena specifications.
			where.add(f.has5g ? "c.has_5g = 1"
					: "(c.has_5g = 0 AND x.has_spec_data = 1 AND x.data_source IS NULL)");
		}
		if (f.nfc != null) {
			where.add(f.nfc ? "x.nfc_supported = 1" : "x.nfc_supported = 0");
		}

		addContains(where, params, "LOWER(c.display_type)", "displayType", f.displayType);
		addContains(where, params, "LOWER(c.chipset)", "chipset", f.chipset);
		addContains(where, params, "LOWER(c.os)", "os", f.os);

		if (f.priceMaxInr != null || f.priceMinInr != null) {
			String price = priceExpression(eurToInr);
			if (eurToInr != null) {
				params.put("fx", eurToInr);
			}
			List<String> bounds = new ArrayList<>();
			if (f.priceMaxInr != null) {
				bounds.add(price + " <= :priceMax");
				params.put("priceMax", f.priceMaxInr);
			}
			if (f.priceMinInr != null) {
				bounds.add(price + " >= :priceMin");
				params.put("priceMin", f.priceMinInr);
			}
			String within = String.join(" AND ", bounds);
			where.add(f.includeUnknownPrice ? "(" + price + " IS NULL OR (" + within + "))" : "(" + within + ")");
		}

		String yearExpr = f.includeListedYear ? SORT_YEAR : "x.announced_year";
		if (f.releasedFromYear != null) {
			where.add(yearExpr + " >= :yearFrom");
			params.put("yearFrom", f.releasedFromYear);
		}
		if (f.releasedToYear != null) {
			where.add(yearExpr + " <= :yearTo");
			params.put("yearTo", f.releasedToYear);
		}
		if (f.excludeCancelled) {
			where.add("(x.status_code IS NULL OR x.status_code <> 'CANCELLED')");
		}
		if (f.onlyWithSpecifications) {
			where.add("x.has_spec_data = 1");
		}
		if (f.requireOsListed) {
			where.add("c.os IS NOT NULL");
		}

		String whereSql = where.isEmpty() ? "1 = 1" : String.join(" AND ", where);
		return new Built(whereSql, orderBy(f, query != null, eurToInr, params), params);
	}

	private static String orderBy(DeviceFilter f, boolean hasQuery, Double eurToInr, Map<String, Object> params) {
		SortOption sort = f.sort != null ? f.sort : (hasQuery ? SortOption.RELEVANCE : SortOption.NEWEST);
		if (sort == SortOption.RELEVANCE && !hasQuery) {
			sort = SortOption.NEWEST;
		}
		String primary;
		switch (sort) {
			case RELEVANCE:
				primary = "CASE WHEN x.search_compact = :qExact THEN 0"
						+ " WHEN x.search_compact LIKE :qPrefix OR x.search_compact LIKE :qSuffix THEN 1"
						+ " WHEN x.search_compact LIKE :qContains THEN 2 ELSE 3 END,"
						+ " (" + SORT_YEAR + " IS NULL), " + SORT_YEAR + " DESC, LENGTH(x.search_compact)";
				break;
			case NAME:
				primary = "b.name ASC, c.name ASC";
				break;
			case NEWEST:
				primary = "(" + SORT_YEAR + " IS NULL), " + SORT_YEAR + " DESC, c.name ASC";
				break;
			case BATTERY_DESC:
				primary = descNullsLast("c.battery_mah");
				break;
			case RAM_DESC:
				primary = descNullsLast("c.ram_gb");
				break;
			case STORAGE_DESC:
				primary = descNullsLast("c.storage_gb");
				break;
			case REFRESH_DESC:
				primary = descNullsLast("c.refresh_rate_hz");
				break;
			case WIRED_CHARGING_DESC:
				primary = descNullsLast("c.wired_charging_w");
				break;
			case CAMERA_DESC:
				primary = descNullsLast("c.main_camera_mp");
				break;
			case DISPLAY_SIZE_DESC:
				primary = descNullsLast("c.display_size_inches");
				break;
			case WEIGHT_ASC:
				primary = "(c.weight_g IS NULL), c.weight_g ASC";
				break;
			case PRICE_ASC:
			case PRICE_DESC:
				String price = priceExpression(eurToInr);
				if (eurToInr != null) {
					params.put("fx", eurToInr);
				}
				primary = "(" + price + " IS NULL), " + price + (sort == SortOption.PRICE_ASC ? " ASC" : " DESC");
				break;
			default:
				primary = "c.name ASC";
		}
		return primary + ", c.id ASC";
	}

	private static String descNullsLast(String column) {
		return "(" + column + " IS NULL), " + column + " DESC, c.name ASC";
	}

	private static void addMin(List<String> where, Map<String, Object> params, String column, String param, Double v) {
		if (v != null) {
			where.add(column + " >= :" + param);
			params.put(param, v);
		}
	}

	private static void addContains(List<String> where, Map<String, Object> params, String expr, String param,
			String text) {
		String clean = sanitizeLikeText(text);
		if (clean != null) {
			where.add(expr + " LIKE :" + param);
			params.put(param, "%" + clean + "%");
		}
	}

	private static void addIn(List<String> where, Map<String, Object> params, String column, String prefix,
			List<String> values, boolean negate) {
		if (values == null || values.isEmpty()) {
			return;
		}
		List<String> names = new ArrayList<>();
		for (int i = 0; i < values.size(); i++) {
			String name = prefix + i;
			names.add(":" + name);
			params.put(name, values.get(i));
		}
		where.add(column + (negate ? " NOT IN (" : " IN (") + String.join(", ", names) + ")");
	}

	/** Lower-cases and removes characters that act as LIKE wildcards. Returns null when nothing is left. */
	static String sanitizeLikeText(String text) {
		if (text == null) {
			return null;
		}
		String clean = text.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "").replace("\\", "").trim();
		return clean.isEmpty() ? null : clean;
	}
}

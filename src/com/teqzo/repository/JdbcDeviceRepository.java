package com.teqzo.repository;

import com.teqzo.domain.DeviceRow;
import com.teqzo.dto.Facets;
import com.teqzo.dto.MarketInsights;
import com.teqzo.dto.PageResponse;
import com.teqzo.dto.RegionalPrice;
import com.teqzo.util.Log;
import com.teqzo.filter.DeviceFilter;
import com.teqzo.filter.DeviceQueryBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcDeviceRepository implements DeviceRepository {

	private static final DeviceRowMapper ROW_MAPPER = new DeviceRowMapper();
	private static final String SELECT = "SELECT " + DeviceQueryBuilder.SELECT_COLUMNS + DeviceQueryBuilder.FROM_CLAUSE;

	/** Display technologies offered by the UI; matched as case-insensitive substrings of the display type text. */
	private static final List<String> DISPLAY_TYPES = List.of("AMOLED", "OLED", "LTPO", "IPS", "LCD", "TFT");
	private static final List<String> OPERATING_SYSTEMS = List.of("Android", "iOS", "Windows", "HarmonyOS", "KaiOS");

	private static final Log LOG = Log.of(JdbcDeviceRepository.class);

	private final Jdbc jdbc;

	public JdbcDeviceRepository(Jdbc jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public PageResponse<DeviceRow> search(DeviceFilter filter, Double eurToInr) {
		DeviceQueryBuilder.Built built = DeviceQueryBuilder.build(filter, eurToInr);
		Long total = jdbc.queryForObject(
				"SELECT COUNT(*)" + DeviceQueryBuilder.FROM_CLAUSE + " WHERE " + built.where(),
				built.params(), rs -> rs.getLong(1));
		long totalItems = total == null ? 0 : total;

		Map<String, Object> params = new LinkedHashMap<>(built.params());
		params.put("limit", filter.size);
		params.put("offset", (long) filter.page * filter.size);
		List<DeviceRow> rows = totalItems == 0 ? List.of() : jdbc.query(
				SELECT + " WHERE " + built.where() + " ORDER BY " + built.orderBy() + " LIMIT :limit OFFSET :offset",
				params, ROW_MAPPER);
		return PageResponse.of(rows, filter.page, filter.size, totalItems);
	}

	@Override
	public List<DeviceRow> findCandidates(DeviceFilter filter, Double eurToInr, int limit) {
		DeviceQueryBuilder.Built built = DeviceQueryBuilder.build(filter, eurToInr);
		Map<String, Object> params = new LinkedHashMap<>(built.params());
		params.put("limit", limit);
		return jdbc.query(
				SELECT + " WHERE " + built.where() + " ORDER BY " + built.orderBy() + " LIMIT :limit",
				params, ROW_MAPPER);
	}

	@Override
	public Optional<DeviceRow> findById(String id) {
		List<DeviceRow> rows = jdbc.query(SELECT + " WHERE c.id = :id", Map.of("id", id), ROW_MAPPER);
		return rows.stream().findFirst();
	}

	@Override
	public List<DeviceRow> findByIds(List<String> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		return jdbc.query(SELECT + " WHERE c.id IN (:ids)", Map.of("ids", ids), ROW_MAPPER);
	}

	@Override
	public List<SpecRow> findSpecifications(String deviceId) {
		return jdbc.query(
				"SELECT spec_category, specification, spec_value FROM device_specification"
						+ " WHERE device_id = :id ORDER BY spec_id",
				Map.of("id", deviceId),
				rs -> new SpecRow(rs.getString("spec_category"), rs.getString("specification"),
						rs.getString("spec_value")));
	}

	@Override
	public Facets facets() {
		Map<String, Object> none = new HashMap<>();
		List<Double> ram = jdbc.query(
				"SELECT DISTINCT ram_gb FROM device_core_v4 WHERE ram_gb IS NOT NULL ORDER BY ram_gb", none,
				rs -> rs.getDouble(1));
		List<Double> storage = jdbc.query(
				"SELECT DISTINCT storage_gb FROM device_core_v4 WHERE storage_gb IS NOT NULL ORDER BY storage_gb",
				none, rs -> rs.getDouble(1));
		List<Integer> refresh = new ArrayList<>();
		for (Double hz : jdbc.query(
				"SELECT DISTINCT refresh_rate_hz FROM device_core_v4 WHERE refresh_rate_hz IS NOT NULL"
						+ " ORDER BY refresh_rate_hz", none, rs -> rs.getDouble(1))) {
			refresh.add((int) Math.round(hz));
		}
		long[] counts = jdbc.queryForObject(
				"SELECT COUNT(*), SUM(has_spec_data),"
						+ " SUM(CASE WHEN price_inr IS NOT NULL THEN 1 ELSE 0 END),"
						+ " SUM(CASE WHEN price_inr IS NOT NULL OR price_eur IS NOT NULL OR price_usd IS NOT NULL"
						+ " THEN 1 ELSE 0 END), MIN(announced_year), MAX(announced_year),"
						+ " SUM(CASE WHEN data_source IS NOT NULL THEN 1 ELSE 0 END) FROM device_extras_v1",
				none, rs -> new long[] { rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4),
						rs.getLong(5), rs.getLong(6), rs.getLong(7) });
		double[] maxima = jdbc.queryForObject(
				"SELECT MAX(battery_mah), MAX(wired_charging_w) FROM device_core_v4", none,
				rs -> new double[] { rs.getDouble(1), rs.getDouble(2) });
		long[] c = counts == null ? new long[7] : counts;
		double[] m = maxima == null ? new double[2] : maxima;
		return new Facets(ram, storage, refresh, DISPLAY_TYPES, OPERATING_SYSTEMS,
				c[4] == 0 ? null : (int) c[4], c[5] == 0 ? null : (int) c[5], (int) m[0], m[1],
				c[0], c[1], c[2], c[3], c[6]);
	}

	@Override
	public List<RegionalPrice> findRegionalPrices(String deviceId) {
		try {
			return jdbc.query(
					"SELECT country, currency, variant, amount FROM device_price_v1 WHERE device_id = :id"
							+ " ORDER BY country, amount",
					Map.of("id", deviceId),
					rs -> new RegionalPrice(rs.getString("country"), rs.getString("currency"),
							rs.getString("variant"), rs.getDouble("amount")));
		} catch (DataAccessException e) {
			LOG.warn("Regional prices are not available (run database/import_mobiles2025.py): {}", e.getMessage());
			return List.of();
		}
	}

	@Override
	public MarketInsights insights() {
		Map<String, Object> none = new HashMap<>();
		List<MarketInsights.BrandStat> brands = jdbc.query(
				"SELECT x.brand_name AS brand, COUNT(*) AS n, AVG(x.price_inr) AS avg_price,"
						+ " AVG(c.battery_mah) AS avg_battery, AVG(c.ram_gb) AS avg_ram"
						+ " FROM device_extras_v1 x JOIN device_core_v4 c ON c.id = x.device_id"
						+ " WHERE x.has_spec_data = 1 AND x.brand_name IS NOT NULL"
						+ " GROUP BY x.brand_name HAVING COUNT(*) >= 3 ORDER BY n DESC, brand LIMIT 12",
				none, rs -> new MarketInsights.BrandStat(rs.getString("brand"), rs.getLong("n"),
						nullableDouble(rs, "avg_price"), nullableDouble(rs, "avg_battery"), nullableDouble(rs, "avg_ram")));

		List<MarketInsights.YearCount> years = jdbc.query(
				"SELECT COALESCE(announced_year, listed_year) AS yr, COUNT(*) AS n FROM device_extras_v1"
						+ " WHERE has_spec_data = 1 AND COALESCE(announced_year, listed_year) IS NOT NULL"
						+ " GROUP BY yr ORDER BY yr DESC LIMIT 12",
				none, rs -> new MarketInsights.YearCount(rs.getInt("yr"), rs.getLong("n")));

		String[] labels = {"under 10,000", "10,000 - 19,999", "20,000 - 29,999", "30,000 - 49,999", "50,000 - 79,999",
				"80,000 and above"};
		Map<Integer, Long> bandCounts = new HashMap<>();
		jdbc.query("SELECT CASE WHEN price_inr < 10000 THEN 0 WHEN price_inr < 20000 THEN 1"
						+ " WHEN price_inr < 30000 THEN 2 WHEN price_inr < 50000 THEN 3 WHEN price_inr < 80000 THEN 4"
						+ " ELSE 5 END AS band, COUNT(*) AS n FROM device_extras_v1"
						+ " WHERE price_inr IS NOT NULL AND has_spec_data = 1 GROUP BY band",
				none, rs -> bandCounts.put(rs.getInt("band"), rs.getLong("n")));
		List<MarketInsights.PriceBand> bands = new ArrayList<>();
		for (int i = 0; i < labels.length; i++) {
			bands.add(new MarketInsights.PriceBand(labels[i], bandCounts.getOrDefault(i, 0L)));
		}

		Map<String, Long> sources = new LinkedHashMap<>();
		jdbc.query("SELECT COALESCE(data_source, 'GSMARENA') AS src, COUNT(*) AS n FROM device_extras_v1"
						+ " GROUP BY src ORDER BY n DESC", none, rs -> sources.put(rs.getString("src"), rs.getLong("n")));
		return new MarketInsights(brands, years, bands, sources);
	}

	/** getDouble returns 0 for SQL NULL; this keeps "not stated" as null. */
	private static Double nullableDouble(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
		double value = rs.getDouble(column);
		return rs.wasNull() ? null : value;
	}
}

package com.teqzo.config;

import com.teqzo.repository.Jdbc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Checks that the tables and columns the application needs exist. Returns readable instructions, never throws. */
public final class SchemaVerifier {

	private static final List<String> REQUIRED_TABLES = List.of("brand", "device", "device_specification",
			"device_core_v4", "device_extras_v1");
	private static final List<String> REQUIRED_EXTRAS_COLUMNS = List.of("listed_year", "data_source");

	private final Jdbc jdbc;

	public SchemaVerifier(Jdbc jdbc) {
		this.jdbc = jdbc;
	}

	/** Problems found; an empty list means the schema looks right. */
	public List<String> problems() {
		List<String> problems = new ArrayList<>();
		try {
			List<String> tables = jdbc.query(
					"SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()", Map.of(),
					rs -> rs.getString(1).toLowerCase());
			for (String table : REQUIRED_TABLES) {
				if (!tables.contains(table)) {
					problems.add("Required table '" + table + "' is missing. See README (Database setup)."
							+ " For device_extras_v1 run: python database/build_device_extras.py");
				}
			}
			if (!tables.contains("device_price_v1")) {
				problems.add("Optional table 'device_price_v1' is missing, so launch prices by country are not shown."
						+ " To add them run: python database/import_mobiles2025.py <Mobiles_Dataset_2025.csv>");
			}
			if (tables.contains("device_extras_v1")) {
				List<String> columns = jdbc.query(
						"SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE()"
								+ " AND table_name = 'device_extras_v1'", Map.of(), rs -> rs.getString(1).toLowerCase());
				for (String column : REQUIRED_EXTRAS_COLUMNS) {
					if (!columns.contains(column)) {
						problems.add("device_extras_v1 has no column '" + column + "': it was built by an older version"
								+ " of build_device_extras.py. Re-run: python database/build_device_extras.py");
					}
				}
			}
		} catch (RuntimeException e) {
			problems.add("Could not verify the database schema (" + e.getMessage() + ").");
		}
		return problems;
	}
}

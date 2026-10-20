package com.teqzo.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Runs SELECT statements with named parameters using plain JDBC (PreparedStatement + ResultSet). */
public final class Jdbc {

	private final Database database;

	public Jdbc(Database database) {
		this.database = database;
	}

	/** All rows, mapped one by one. */
	public <T> List<T> query(String sql, Map<String, ?> params, RowMapper<T> mapper) {
		NamedSql.Prepared prepared = NamedSql.prepare(sql, params);
		Connection connection = database.connection();
		try (PreparedStatement statement = connection.prepareStatement(prepared.sql())) {
			for (int i = 0; i < prepared.values().size(); i++) {
				statement.setObject(i + 1, prepared.values().get(i));
			}
			try (ResultSet rs = statement.executeQuery()) {
				List<T> rows = new ArrayList<>();
				while (rs.next()) {
					rows.add(mapper.map(rs));
				}
				return rows;
			}
		} catch (SQLException e) {
			throw new DataAccessException("Database query failed: " + e.getMessage(), e);
		}
	}

	/** The first row, or null when there is none. */
	public <T> T queryForObject(String sql, Map<String, ?> params, RowMapper<T> mapper) {
		List<T> rows = query(sql, params, mapper);
		return rows.isEmpty() ? null : rows.get(0);
	}
}

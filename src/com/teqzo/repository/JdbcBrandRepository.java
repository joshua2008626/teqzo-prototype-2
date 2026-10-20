package com.teqzo.repository;

import com.teqzo.dto.BrandDto;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcBrandRepository implements BrandRepository {

	private static final String SELECT = "SELECT b.id AS id, b.name AS name, COUNT(c.id) AS device_count"
			+ " FROM brand b LEFT JOIN device_core_v4 c ON c.brand_id = b.id";
	private static final String GROUP = " GROUP BY b.id, b.name";
	private static final RowMapper<BrandDto> MAPPER = rs ->
			new BrandDto(rs.getString("id"), rs.getString("name"), rs.getLong("device_count"));

	private final Jdbc jdbc;

	public JdbcBrandRepository(Jdbc jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<BrandDto> findAll() {
		return jdbc.query(SELECT + GROUP + " ORDER BY COUNT(c.id) DESC, b.name ASC", Map.of(), MAPPER);
	}

	@Override
	public Optional<BrandDto> findById(String id) {
		return jdbc.query(SELECT + " WHERE b.id = :id" + GROUP, Map.of("id", id), MAPPER).stream().findFirst();
	}
}

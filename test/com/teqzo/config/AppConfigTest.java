package com.teqzo.config;

import com.teqzo.testkit.Test;

import java.util.Map;

import static com.teqzo.testkit.Assertions.*;

class AppConfigTest {

	@Test
	void buildsUrlFromParts() {
		AppConfig c = AppConfig.of(Map.of("DB_HOST", "db", "DB_PORT", "3307", "DB_NAME", "teqzo"));
		assertTrue(c.jdbcUrl().startsWith("jdbc:mysql://db:3307/teqzo?"));
		assertEquals("root", c.dbUser());
		assertEquals("", c.dbPassword());
	}

	@Test
	void explicitUrlWins() {
		AppConfig c = AppConfig.of(Map.of("DB_URL", "jdbc:mysql://x/y", "DB_HOST", "ignored"));
		assertEquals("jdbc:mysql://x/y", c.jdbcUrl());
	}

	@Test
	void defaultsAndInts() {
		AppConfig c = AppConfig.of(Map.of("N", "7", "BAD", "x"));
		assertTrue(c.jdbcUrl().contains("localhost:3306/tech_finder"));
		assertEquals(7, c.getInt("N", 1));
		assertEquals(1, c.getInt("BAD", 1));
		assertEquals(2, c.getInt("MISSING", 2));
	}
}

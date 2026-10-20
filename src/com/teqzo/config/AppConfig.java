package com.teqzo.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Settings from environment variables, optionally read from a {@code .env} file (KEY=VALUE per line) in the
 * working directory or its parent. Real environment variables win over the file. Nothing is hard-coded: no
 * password or API key is stored in the source code.
 */
public final class AppConfig {

	private final Map<String, String> values = new HashMap<>();

	private AppConfig() {
	}

	/** Loads {@code .env} (if present) and the process environment. */
	public static AppConfig load() {
		AppConfig config = new AppConfig();
		for (Path candidate : new Path[] { Path.of(".env"), Path.of("..", ".env") }) {
			if (Files.isRegularFile(candidate)) {
				config.readFile(candidate);
				break;
			}
		}
		System.getenv().forEach((k, v) -> {
			if (v != null && !v.isBlank()) {
				config.values.put(k, v);
			}
		});
		return config;
	}

	/** For tests: settings from a map only. */
	public static AppConfig of(Map<String, String> map) {
		AppConfig config = new AppConfig();
		config.values.putAll(map);
		return config;
	}

	private void readFile(Path file) {
		try {
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				parseLine(line);
			}
		} catch (IOException e) {
			System.err.println("[WARN] Could not read " + file + ": " + e.getMessage());
		}
	}

	void parseLine(String line) {
		String s = line.strip();
		if (s.isEmpty() || s.startsWith("#") || !s.contains("=")) {
			return;
		}
		int eq = s.indexOf('=');
		String key = s.substring(0, eq).strip();
		String value = s.substring(eq + 1).strip();
		if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
				|| (value.startsWith("'") && value.endsWith("'")))) {
			value = value.substring(1, value.length() - 1);
		}
		if (!key.isEmpty() && !value.isEmpty()) {
			values.put(key, value);
		}
	}

	public String get(String key, String fallback) {
		String v = values.get(key);
		return v == null || v.isBlank() ? fallback : v;
	}

	public String get(String key) {
		return get(key, "");
	}

	public int getInt(String key, int fallback) {
		try {
			return Integer.parseInt(get(key, String.valueOf(fallback)).trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** JDBC URL: DB_URL if given, otherwise built from DB_HOST / DB_PORT / DB_NAME / DB_USE_SSL. */
	public String jdbcUrl() {
		String explicit = get("DB_URL");
		if (!explicit.isEmpty()) {
			return explicit;
		}
		return "jdbc:mysql://" + get("DB_HOST", "localhost") + ":" + get("DB_PORT", "3306") + "/"
				+ get("DB_NAME", "tech_finder") + "?useSSL=" + get("DB_USE_SSL", "false")
				+ "&serverTimezone=Asia/Kolkata&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
	}

	public String dbUser() {
		return get("DB_USERNAME", "root");
	}

	public String dbPassword() {
		return get("DB_PASSWORD", "");
	}
}

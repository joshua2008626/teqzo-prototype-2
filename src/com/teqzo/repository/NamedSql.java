package com.teqzo.repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Converts SQL with named parameters ({@code :name}) into SQL with JDBC {@code ?} markers plus the values in
 * the right order. A collection value expands to one marker per element ({@code IN (:ids)}). Text inside
 * single quotes is left alone. Values are never pasted into the SQL: they are always bound with a
 * PreparedStatement, which is what keeps user input from changing the query.
 */
public final class NamedSql {

	/** SQL with ? markers and the values to bind, in order. */
	public record Prepared(String sql, List<Object> values) {
	}

	private NamedSql() {
	}

	public static Prepared prepare(String sql, Map<String, ?> params) {
		StringBuilder out = new StringBuilder();
		List<Object> values = new ArrayList<>();
		boolean inQuote = false;
		int i = 0;
		while (i < sql.length()) {
			char c = sql.charAt(i);
			if (c == '\'') {
				inQuote = !inQuote;
				out.append(c);
				i++;
			} else if (!inQuote && c == ':' && i + 1 < sql.length() && isNameStart(sql.charAt(i + 1))
					&& (i == 0 || sql.charAt(i - 1) != ':')) {
				int end = i + 1;
				while (end < sql.length() && isNamePart(sql.charAt(end))) {
					end++;
				}
				String name = sql.substring(i + 1, end);
				if (!params.containsKey(name)) {
					throw new IllegalArgumentException("No value for SQL parameter :" + name);
				}
				Object value = params.get(name);
				if (value instanceof Collection<?> collection) {
					if (collection.isEmpty()) {
						throw new IllegalArgumentException("Empty list for SQL parameter :" + name);
					}
					out.append(String.join(", ", collection.stream().map(x -> "?").toList()));
					values.addAll(collection);
				} else {
					out.append('?');
					values.add(value);
				}
				i = end;
			} else {
				out.append(c);
				i++;
			}
		}
		return new Prepared(out.toString(), values);
	}

	private static boolean isNameStart(char c) {
		return Character.isLetter(c) || c == '_';
	}

	private static boolean isNamePart(char c) {
		return Character.isLetterOrDigit(c) || c == '_';
	}
}

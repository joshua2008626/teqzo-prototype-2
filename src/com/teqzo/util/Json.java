package com.teqzo.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer used for talking to AI providers and for parsing model output.
 *
 * <p>It is intentionally tiny and dependency free so the AI layer does not depend on a specific
 * Jackson major version. Objects become {@code LinkedHashMap<String,Object>}, arrays
 * {@code ArrayList<Object>}, numbers {@code Long} or {@code Double}.
 */
public final class Json {

	private Json() {
	}

	public static final class JsonException extends RuntimeException {
		public JsonException(String message) {
			super(message);
		}
	}

	// ------------------------------------------------------------------ parse

	public static Object parse(String text) {
		if (text == null) {
			throw new JsonException("JSON text is null");
		}
		Parser parser = new Parser(text);
		parser.skipWhitespace();
		Object value = parser.readValue();
		parser.skipWhitespace();
		if (!parser.atEnd()) {
			throw new JsonException("Unexpected trailing characters at position " + parser.pos);
		}
		return value;
	}

	/** Parses an object. Tolerates Markdown code fences and text around the outermost braces. */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> parseObjectLenient(String text) {
		if (text == null) {
			throw new JsonException("JSON text is null");
		}
		int start = text.indexOf('{');
		int end = text.lastIndexOf('}');
		if (start < 0 || end <= start) {
			throw new JsonException("No JSON object found");
		}
		Object value = parse(text.substring(start, end + 1));
		if (!(value instanceof Map)) {
			throw new JsonException("Top-level JSON value is not an object");
		}
		return (Map<String, Object>) value;
	}

	private static final class Parser {
		private final String s;
		private int pos;

		Parser(String s) {
			this.s = s;
		}

		boolean atEnd() {
			return pos >= s.length();
		}

		void skipWhitespace() {
			while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
				pos++;
			}
		}

		Object readValue() {
			if (atEnd()) {
				throw new JsonException("Unexpected end of JSON");
			}
			char c = s.charAt(pos);
			switch (c) {
				case '{':
					return readObject();
				case '[':
					return readArray();
				case '"':
					return readString();
				case 't':
					return readLiteral("true", Boolean.TRUE);
				case 'f':
					return readLiteral("false", Boolean.FALSE);
				case 'n':
					return readLiteral("null", null);
				default:
					if (c == '-' || (c >= '0' && c <= '9')) {
						return readNumber();
					}
					throw new JsonException("Unexpected character '" + c + "' at position " + pos);
			}
		}

		private Object readLiteral(String literal, Object value) {
			if (!s.startsWith(literal, pos)) {
				throw new JsonException("Invalid literal at position " + pos);
			}
			pos += literal.length();
			return value;
		}

		private Map<String, Object> readObject() {
			Map<String, Object> map = new LinkedHashMap<>();
			pos++; // {
			skipWhitespace();
			if (peek() == '}') {
				pos++;
				return map;
			}
			while (true) {
				skipWhitespace();
				if (peek() != '"') {
					throw new JsonException("Expected object key at position " + pos);
				}
				String key = readString();
				skipWhitespace();
				expect(':');
				skipWhitespace();
				map.put(key, readValue());
				skipWhitespace();
				char c = next();
				if (c == '}') {
					return map;
				}
				if (c != ',') {
					throw new JsonException("Expected ',' or '}' at position " + (pos - 1));
				}
			}
		}

		private List<Object> readArray() {
			List<Object> list = new ArrayList<>();
			pos++; // [
			skipWhitespace();
			if (peek() == ']') {
				pos++;
				return list;
			}
			while (true) {
				skipWhitespace();
				list.add(readValue());
				skipWhitespace();
				char c = next();
				if (c == ']') {
					return list;
				}
				if (c != ',') {
					throw new JsonException("Expected ',' or ']' at position " + (pos - 1));
				}
			}
		}

		private String readString() {
			expect('"');
			StringBuilder sb = new StringBuilder();
			while (true) {
				if (atEnd()) {
					throw new JsonException("Unterminated string");
				}
				char c = s.charAt(pos++);
				if (c == '"') {
					return sb.toString();
				}
				if (c != '\\') {
					sb.append(c);
					continue;
				}
				if (atEnd()) {
					throw new JsonException("Unterminated escape sequence");
				}
				char e = s.charAt(pos++);
				switch (e) {
					case '"':
					case '\\':
					case '/':
						sb.append(e);
						break;
					case 'b':
						sb.append('\b');
						break;
					case 'f':
						sb.append('\f');
						break;
					case 'n':
						sb.append('\n');
						break;
					case 'r':
						sb.append('\r');
						break;
					case 't':
						sb.append('\t');
						break;
					case 'u':
						if (pos + 4 > s.length()) {
							throw new JsonException("Bad unicode escape");
						}
						try {
							sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
						} catch (NumberFormatException ex) {
							throw new JsonException("Bad unicode escape at position " + pos);
						}
						pos += 4;
						break;
					default:
						throw new JsonException("Bad escape '\\" + e + "' at position " + (pos - 1));
				}
			}
		}

		private Number readNumber() {
			int start = pos;
			if (peek() == '-') {
				pos++;
			}
			boolean isDouble = false;
			while (!atEnd()) {
				char c = s.charAt(pos);
				if (c >= '0' && c <= '9') {
					pos++;
				} else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
					isDouble = true;
					pos++;
				} else {
					break;
				}
			}
			String token = s.substring(start, pos);
			try {
				if (!isDouble) {
					return Long.parseLong(token);
				}
				return Double.parseDouble(token);
			} catch (NumberFormatException ex) {
				throw new JsonException("Bad number '" + token + "'");
			}
		}

		private char peek() {
			return atEnd() ? '\0' : s.charAt(pos);
		}

		private char next() {
			if (atEnd()) {
				throw new JsonException("Unexpected end of JSON");
			}
			return s.charAt(pos++);
		}

		private void expect(char c) {
			if (atEnd() || s.charAt(pos) != c) {
				throw new JsonException("Expected '" + c + "' at position " + pos);
			}
			pos++;
		}
	}

	// -------------------------------------------------------------- stringify

	public static String stringify(Object value) {
		StringBuilder sb = new StringBuilder();
		write(sb, value);
		return sb.toString();
	}

	private static void write(StringBuilder sb, Object value) {
		if (value == null) {
			sb.append("null");
		} else if (value instanceof String || value instanceof Character || value instanceof Enum) {
			writeString(sb, value.toString());
		} else if (value instanceof Boolean) {
			sb.append(value);
		} else if (value instanceof Number) {
			writeNumber(sb, (Number) value);
		} else if (value instanceof Map) {
			sb.append('{');
			boolean first = true;
			for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
				if (!first) {
					sb.append(',');
				}
				first = false;
				writeString(sb, String.valueOf(e.getKey()));
				sb.append(':');
				write(sb, e.getValue());
			}
			sb.append('}');
		} else if (value instanceof Iterable) {
			sb.append('[');
			boolean first = true;
			for (Object o : (Iterable<?>) value) {
				if (!first) {
					sb.append(',');
				}
				first = false;
				write(sb, o);
			}
			sb.append(']');
		} else {
			throw new JsonException("Cannot serialise " + value.getClass().getName());
		}
	}

	private static void writeNumber(StringBuilder sb, Number n) {
		if (n instanceof Double || n instanceof Float) {
			double d = n.doubleValue();
			if (Double.isNaN(d) || Double.isInfinite(d)) {
				sb.append("null");
			} else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
				sb.append((long) d);
			} else {
				sb.append(d);
			}
		} else {
			sb.append(n);
		}
	}

	private static void writeString(StringBuilder sb, String s) {
		sb.append('"');
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
				case '"':
					sb.append("\\\"");
					break;
				case '\\':
					sb.append("\\\\");
					break;
				case '\n':
					sb.append("\\n");
					break;
				case '\r':
					sb.append("\\r");
					break;
				case '\t':
					sb.append("\\t");
					break;
				default:
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
			}
		}
		sb.append('"');
	}

	// ---------------------------------------------------------- typed access

	@SuppressWarnings("unchecked")
	public static Map<String, Object> asObject(Object value) {
		return value instanceof Map ? (Map<String, Object>) value : null;
	}

	@SuppressWarnings("unchecked")
	public static List<Object> asArray(Object value) {
		return value instanceof List ? (List<Object>) value : null;
	}

	/** Walks a path of map keys / list indexes ("choices", 0, "message", "content"). */
	public static Object path(Object root, Object... steps) {
		Object cur = root;
		for (Object step : steps) {
			if (cur == null) {
				return null;
			}
			if (step instanceof Integer && cur instanceof List) {
				List<?> list = (List<?>) cur;
				int i = (Integer) step;
				cur = i >= 0 && i < list.size() ? list.get(i) : null;
			} else if (step instanceof String && cur instanceof Map) {
				cur = ((Map<?, ?>) cur).get(step);
			} else {
				return null;
			}
		}
		return cur;
	}
}

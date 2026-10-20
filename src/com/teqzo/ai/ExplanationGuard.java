package com.teqzo.ai;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Last line of defence against invented specifications in model-written text. A summary is accepted only
 * if every number it contains also appears in the database-backed facts it was given (small counts such
 * as "3 devices" are allowed). Otherwise the caller falls back to deterministic text.
 */
public final class ExplanationGuard {

	static final int MAX_LENGTH = 700;
	static final int MAX_FREE_INTEGER = 10;
	private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

	private ExplanationGuard() {
	}

	public static boolean isGrounded(String reply, String facts) {
		if (reply == null || reply.isBlank() || reply.length() > MAX_LENGTH) {
			return false;
		}
		String lower = reply.toLowerCase();
		if (lower.contains("http") || lower.contains("```")) {
			return false;
		}
		Set<String> allowed = numbers(facts);
		for (String n : numbers(reply)) {
			if (allowed.contains(n)) {
				continue;
			}
			if (n.indexOf('.') < 0 && n.length() <= 2 && Integer.parseInt(n) <= MAX_FREE_INTEGER) {
				continue;
			}
			return false;
		}
		return true;
	}

	static Set<String> numbers(String text) {
		Set<String> out = new HashSet<>();
		Matcher m = NUMBER.matcher(text.replace(",", ""));
		while (m.find()) {
			out.add(normalise(m.group()));
		}
		return out;
	}

	private static String normalise(String n) {
		if (n.indexOf('.') >= 0) {
			String t = n.replaceAll("0+$", "");
			return t.endsWith(".") ? t.substring(0, t.length() - 1) : t;
		}
		return n;
	}
}

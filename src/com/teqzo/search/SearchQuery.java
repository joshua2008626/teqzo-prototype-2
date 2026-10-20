package com.teqzo.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Normalised free-text search input.
 *
 * <p>Devices are matched against a "compact" search text (brand + name, lower case, letters and digits
 * only; stored in {@code device_extras_v1.search_compact}). Normalising the query the same way makes
 * "iPhone 15", "iphone15", "Samsung S24" and "galaxy-s24" behave consistently.
 */
public record SearchQuery(List<String> tokens, String compact) {

	public static final int MAX_TOKENS = 8;
	public static final int MAX_TOKEN_LENGTH = 40;

	/** Returns {@code null} when the input has nothing searchable in it. */
	public static SearchQuery parse(String raw) {
		if (raw == null) {
			return null;
		}
		String lower = raw.toLowerCase(Locale.ROOT);
		List<String> tokens = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (int i = 0; i <= lower.length(); i++) {
			char c = i < lower.length() ? lower.charAt(i) : ' ';
			boolean alnum = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
			if (alnum) {
				current.append(c);
			} else if (current.length() > 0) {
				String token = current.toString();
				if (token.length() > MAX_TOKEN_LENGTH) {
					token = token.substring(0, MAX_TOKEN_LENGTH);
				}
				if (tokens.size() < MAX_TOKENS) {
					tokens.add(token);
				}
				current.setLength(0);
			}
		}
		if (tokens.isEmpty()) {
			return null;
		}
		return new SearchQuery(List.copyOf(tokens), String.join("", tokens));
	}

	/**
	 * A token may also be matched against the chipset text when it looks like a word (4+ characters
	 * containing a letter) or a model number of 3+ digits ("888"). Short numeric tokens such as "7" are never
	 * matched against chipsets, so "Pixel 7" does not drag in unrelated devices.
	 */
	public static boolean matchesSpecText(String token) {
		boolean hasLetter = false;
		for (int i = 0; i < token.length(); i++) {
			char c = token.charAt(i);
			if (c >= 'a' && c <= 'z') {
				hasLetter = true;
				break;
			}
		}
		return hasLetter ? token.length() >= 4 : token.length() >= 3;
	}
}

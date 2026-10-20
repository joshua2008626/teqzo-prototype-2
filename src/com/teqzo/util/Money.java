package com.teqzo.util;

/** Formatting of rupee amounts with Indian digit grouping, e.g. 150000 becomes "₹1,50,000". */
public final class Money {

	private Money() {
	}

	public static String inr(double amount) {
		long whole = Math.round(Math.abs(amount));
		String digits = Long.toString(whole);
		StringBuilder out = new StringBuilder();
		int n = digits.length();
		if (n <= 3) {
			out.append(digits);
		} else {
			String head = digits.substring(0, n - 3);
			String tail = digits.substring(n - 3);
			// groups of two digits to the left of the last three
			for (int i = 0; i < head.length(); i++) {
				if (i > 0 && (head.length() - i) % 2 == 0) {
					out.append(',');
				}
				out.append(head.charAt(i));
			}
			out.append(',').append(tail);
		}
		return (amount < 0 ? "-₹" : "₹") + out;
	}
}

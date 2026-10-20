package com.teqzo.service;
import com.teqzo.util.Log;

/**
 * Decides how prices are interpreted. By default only prices that the dataset lists in INR are used for
 * budget filtering. If {@code PRICE_EUR_TO_INR} is set, EUR prices are converted with that fixed rate and
 * the result is always labelled approximate.
 */
public class PricePolicy {

	private static final Log log = Log.of(PricePolicy.class);

	private final Double eurToInr;

	public PricePolicy(String configuredRate) {
		this.eurToInr = parse(configuredRate);
		if (eurToInr != null) {
			log.info("EUR prices will be converted to approximate INR at a fixed rate of {}", eurToInr);
		}
	}

	/** For tests and manual wiring. */
	public PricePolicy(Double eurToInr) {
		this.eurToInr = eurToInr;
	}

	static Double parse(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		try {
			double rate = Double.parseDouble(text.trim());
			return rate > 0 && rate < 10_000 ? rate : null;
		} catch (NumberFormatException e) {
			log.warn("Ignoring invalid PRICE_EUR_TO_INR value");
			return null;
		}
	}

	/** Fixed EUR to INR rate, or {@code null} when conversion is disabled. */
	public Double eurToInr() {
		return eurToInr;
	}
}

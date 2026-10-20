package com.teqzo.dto;

/**
 * Prices exactly as the dataset lists them. {@code approxInr} is only present when an administrator
 * configured an EUR to INR rate, and is then labelled approximate by {@code approxBasis}.
 */
public record PriceInfo(Double inr, Double eur, Double usd, String raw, Double approxInr, String approxBasis) {
	public boolean isEmpty() {
		return inr == null && eur == null && usd == null && raw == null;
	}
}

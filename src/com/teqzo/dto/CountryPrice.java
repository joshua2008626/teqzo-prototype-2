package com.teqzo.dto;

/** The launch prices of one device in one country: the cheapest and dearest variant. */
public record CountryPrice(String country, String currency, double lowest, double highest, int variants) {
}

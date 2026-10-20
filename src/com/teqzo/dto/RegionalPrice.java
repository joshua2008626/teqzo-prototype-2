package com.teqzo.dto;

/** One launch price of one RAM/storage variant in one country, exactly as the dataset lists it (never converted). */
public record RegionalPrice(String country, String currency, String variant, double amount) {
}

package com.teqzo.repository;

/** One row of the original {@code device_specification} table. */
public record SpecRow(String category, String specification, String value) {
}

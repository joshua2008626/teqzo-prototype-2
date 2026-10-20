package com.teqzo.dto;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages, boolean hasNext) {

	public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalItems) {
		int totalPages = size <= 0 ? 0 : (int) Math.ceil(totalItems / (double) size);
		return new PageResponse<>(items, page, size, totalItems, totalPages, page + 1 < totalPages);
	}

	public <R> PageResponse<R> map(Function<T, R> mapper) {
		return new PageResponse<>(items.stream().map(mapper).toList(), page, size, totalItems, totalPages, hasNext);
	}
}

package com.teqzo.service;

import com.teqzo.dto.Facets;
import com.teqzo.repository.DeviceRepository;

public class FacetsService {

	private static final long CACHE_MILLIS = 10 * 60 * 1000L;

	private final DeviceRepository repository;
	private volatile Facets cached;
	private volatile long cachedAt;

	public FacetsService(DeviceRepository repository) {
		this.repository = repository;
	}

	public Facets get() {
		long now = System.currentTimeMillis();
		Facets snapshot = cached;
		if (snapshot == null || now - cachedAt > CACHE_MILLIS) {
			snapshot = repository.facets();
			cached = snapshot;
			cachedAt = now;
		}
		return snapshot;
	}
}

package com.teqzo.service;

import com.teqzo.dto.BrandDto;
import com.teqzo.repository.BrandRepository;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class BrandService {

	private static final long CACHE_MILLIS = 10 * 60 * 1000L;

	private final BrandRepository repository;
	private volatile List<BrandDto> cached;
	private volatile long cachedAt;

	public BrandService(BrandRepository repository) {
		this.repository = repository;
	}

	/** Brands that have at least one device, most devices first. Cached briefly: the data is static. */
	public List<BrandDto> all() {
		long now = System.currentTimeMillis();
		List<BrandDto> snapshot = cached;
		if (snapshot == null || now - cachedAt > CACHE_MILLIS) {
			snapshot = repository.findAll().stream().filter(b -> b.deviceCount() > 0).toList();
			cached = snapshot;
			cachedAt = now;
		}
		return snapshot;
	}

	public Optional<BrandDto> findByName(String name) {
		if (name == null) {
			return Optional.empty();
		}
		String wanted = name.trim().toLowerCase(Locale.ROOT);
		return all().stream().filter(b -> b.name() != null && b.name().toLowerCase(Locale.ROOT).equals(wanted))
				.findFirst();
	}
}

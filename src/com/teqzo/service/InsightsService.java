package com.teqzo.service;

import com.teqzo.dto.MarketInsights;
import com.teqzo.repository.DeviceRepository;

/** Market figures computed by the database (GROUP BY), cached for a few minutes. */
public class InsightsService {

	private static final long CACHE_MILLIS = 5 * 60 * 1000L;

	private final DeviceRepository devices;
	private volatile MarketInsights cached;
	private volatile long cachedAt;

	public InsightsService(DeviceRepository devices) {
		this.devices = devices;
	}

	public MarketInsights get() {
		long now = System.currentTimeMillis();
		MarketInsights snapshot = cached;
		if (snapshot == null || now - cachedAt > CACHE_MILLIS) {
			snapshot = devices.insights();
			cached = snapshot;
			cachedAt = now;
		}
		return snapshot;
	}
}

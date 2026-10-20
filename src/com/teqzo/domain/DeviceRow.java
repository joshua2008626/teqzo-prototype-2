package com.teqzo.domain;

/**
 * One device as read from {@code device_core_v4} joined with {@code device_extras_v1} and the brand.
 *
 * <p>Every numeric/text field is nullable on purpose: {@code null} means "the dataset does not state
 * this value" and must never be treated as zero or as "not supported".
 */
public class DeviceRow {
	public String id;
	public String brandId;
	public String brandName;
	public String name;
	public String sourceUrl;
	public String thumbnailUrl;

	public Double ramGb;
	public Double storageGb;
	public Double batteryMah;
	public Double wiredChargingW;
	public Double wirelessChargingW;
	public Double reverseChargingW;
	/** YES, CONDITIONAL (optional / market dependent) or null (not stated). */
	public String wirelessChargingStatus;
	public String reverseChargingStatus;
	public String chargingRaw;

	public Double displaySizeInches;
	public String displayType;
	public String displayResolution;
	public Double refreshRateHz;
	public Integer resolutionShortPx;
	public Integer resolutionLongPx;

	public String os;
	public String chipset;
	public String cpu;
	public String gpu;
	public Integer cpuCores;
	public Double cpuMaxGhz;

	public Double mainCameraMp;
	public Double selfieCameraMp;
	public Double weightG;
	public String ipRating;

	/** True when the dataset lists 5G bands. False means "no 5G listed". */
	public boolean has5g;
	public String wifi;
	public Integer wifiGeneration;
	public String bluetooth;
	public Double bluetoothVersion;
	/** Raw NFC text from the dataset ("Yes", "No", "Yes (market dependent)", ...). */
	public String nfcRaw;
	/** TRUE/FALSE only when the dataset says plain Yes/No; null otherwise. */
	public Boolean nfcSupported;
	public String usb;
	public Boolean usbTypeC;

	public Integer announcedYear;
	/** Year a retail listing was seen (Flipkart import). Not a launch year; used only for "newest first" and freshness. */
	public Integer listedYear;
	/** null = GSMArena data, otherwise the name of the smaller dataset the device was added from (FLIPKART_2026). */
	public String dataSource;
	public String statusCode;
	public String statusRaw;

	public String priceRaw;
	public Double priceInr;
	public Double priceEur;
	public Double priceUsd;

	public boolean hasSpecData;

	/**
	 * True for devices from a dataset that only carries a handful of fields. For those, "no 5G flag" means the
	 * listing does not say, not that the phone lacks 5G.
	 */
	public boolean limitedSpecs() {
		return dataSource != null;
	}

	public DeviceRow withId(String id) {
		this.id = id;
		return this;
	}

	public DeviceRow withName(String name) {
		this.name = name;
		return this;
	}
}

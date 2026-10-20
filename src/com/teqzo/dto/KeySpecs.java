package com.teqzo.dto;

/** Normalised headline specifications. {@code null} always means "not stated in the dataset". */
public record KeySpecs(
		Double ramGb,
		Double storageGb,
		Integer batteryMah,
		Double wiredChargingW,
		Double wirelessChargingW,
		String wirelessChargingStatus,
		Double reverseChargingW,
		String reverseChargingStatus,
		Double displaySizeInches,
		String displayType,
		String displayResolution,
		Integer refreshRateHz,
		String os,
		String chipset,
		String cpu,
		String gpu,
		Double mainCameraMp,
		Double selfieCameraMp,
		Double weightG,
		String ipRating,
		Boolean has5g,
		String nfc,
		String wifi,
		String bluetooth,
		String usb) {
}

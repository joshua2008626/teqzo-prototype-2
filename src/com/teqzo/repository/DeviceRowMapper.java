package com.teqzo.repository;

import com.teqzo.domain.DeviceRow;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

/** Maps the aliased columns of {@code DeviceQueryBuilder.SELECT_COLUMNS}. NULL stays null. */
public class DeviceRowMapper implements RowMapper<DeviceRow> {

	@Override
	public DeviceRow map(ResultSet rs) throws SQLException {
		DeviceRow d = new DeviceRow();
		d.id = rs.getString("id");
		d.brandId = rs.getString("brand_id");
		d.brandName = rs.getString("brand_name");
		d.name = rs.getString("name");
		d.sourceUrl = rs.getString("source_url");
		d.thumbnailUrl = rs.getString("thumbnail");

		d.ramGb = dbl(rs, "ram_gb");
		d.storageGb = dbl(rs, "storage_gb");
		d.batteryMah = dbl(rs, "battery_mah");
		d.wiredChargingW = dbl(rs, "wired_charging_w");
		d.wirelessChargingW = dbl(rs, "wireless_charging_w");
		d.reverseChargingW = dbl(rs, "reverse_charging_w");
		d.wirelessChargingStatus = rs.getString("wireless_charging_status");
		d.reverseChargingStatus = rs.getString("reverse_charging_status");
		d.chargingRaw = rs.getString("charging_raw");

		d.displaySizeInches = dbl(rs, "display_size_inches");
		d.displayType = rs.getString("display_type");
		d.displayResolution = rs.getString("display_resolution");
		d.refreshRateHz = dbl(rs, "refresh_rate_hz");
		d.resolutionShortPx = integer(rs, "resolution_short_px");
		d.resolutionLongPx = integer(rs, "resolution_long_px");

		d.os = rs.getString("os");
		d.chipset = rs.getString("chipset");
		d.cpu = rs.getString("cpu");
		d.gpu = rs.getString("gpu");
		d.cpuCores = integer(rs, "cpu_cores");
		d.cpuMaxGhz = dbl(rs, "cpu_max_ghz");

		d.mainCameraMp = dbl(rs, "main_camera_mp");
		d.selfieCameraMp = dbl(rs, "selfie_camera_mp");
		d.weightG = dbl(rs, "weight_g");
		d.ipRating = rs.getString("ip_rating");

		d.has5g = rs.getInt("has_5g") == 1;
		d.wifi = rs.getString("wifi");
		d.wifiGeneration = integer(rs, "wifi_generation");
		d.bluetooth = rs.getString("bluetooth");
		d.bluetoothVersion = dbl(rs, "bluetooth_version");
		d.nfcRaw = rs.getString("nfc_raw");
		Integer nfc = integer(rs, "nfc_supported");
		d.nfcSupported = nfc == null ? null : nfc == 1;
		d.usb = rs.getString("usb");
		Integer typeC = integer(rs, "usb_type_c");
		d.usbTypeC = typeC == null ? null : typeC == 1;

		d.announcedYear = integer(rs, "announced_year");
		d.listedYear = integer(rs, "listed_year");
		d.dataSource = rs.getString("data_source");
		d.statusCode = rs.getString("status_code");
		d.statusRaw = rs.getString("status_raw");
		d.priceRaw = rs.getString("price_raw");
		d.priceInr = dbl(rs, "price_inr");
		d.priceEur = dbl(rs, "price_eur");
		d.priceUsd = dbl(rs, "price_usd");
		d.hasSpecData = rs.getInt("has_spec_data") == 1;
		return d;
	}

	private static Double dbl(ResultSet rs, String column) throws SQLException {
		BigDecimal value = rs.getBigDecimal(column);
		return value == null ? null : value.doubleValue();
	}

	private static Integer integer(ResultSet rs, String column) throws SQLException {
		int value = rs.getInt(column);
		return rs.wasNull() ? null : value;
	}
}

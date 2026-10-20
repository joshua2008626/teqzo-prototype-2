package com.teqzo.service;

import com.teqzo.dto.DeviceSummary;
import com.teqzo.dto.KeySpecs;
import com.teqzo.repository.DataAccessException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes a device list as CSV (RFC 4180 quoting) that opens in Excel. */
public final class CsvExporter {

	static final String HEADER = "id,brand,name,year,price_inr,price_usd,ram_gb,storage_gb,battery_mah,display_inches,"
			+ "chipset,os,main_camera_mp,selfie_camera_mp,weight_g,has_5g";

	private CsvExporter() {
	}

	public static String toCsv(List<DeviceSummary> devices) {
		StringBuilder out = new StringBuilder(HEADER).append("\r\n");
		for (DeviceSummary d : devices) {
			KeySpecs k = d.keySpecs();
			Object[] cells = {d.id(), d.brandName(), d.name(), d.releaseYear(),
					d.price() == null ? null : d.price().inr(), d.price() == null ? null : d.price().usd(),
					k == null ? null : k.ramGb(), k == null ? null : k.storageGb(), k == null ? null : k.batteryMah(),
					k == null ? null : k.displaySizeInches(), k == null ? null : k.chipset(), k == null ? null : k.os(),
					k == null ? null : k.mainCameraMp(), k == null ? null : k.selfieCameraMp(),
					k == null ? null : k.weightG(), k == null || k.has5g() == null ? null : (k.has5g() ? "yes" : "no")};
			for (int i = 0; i < cells.length; i++) {
				if (i > 0) {
					out.append(',');
				}
				out.append(cell(cells[i]));
			}
			out.append("\r\n");
		}
		return out.toString();
	}

	/** Empty for null (not listed); numbers without a trailing .0; text quoted when needed. */
	static String cell(Object value) {
		if (value == null) {
			return "";
		}
		String text;
		if (value instanceof Double d) {
			text = d == Math.rint(d) ? String.valueOf(d.longValue()) : String.valueOf(d);
		} else {
			text = String.valueOf(value);
			// spreadsheet formula injection: text starting with = + - @ would be run as a formula
			if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
				text = "'" + text;
			}
		}
		if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
			return "\"" + text.replace("\"", "\"\"") + "\"";
		}
		return text;
	}

	public static void write(Path file, List<DeviceSummary> devices) {
		try {
			Files.write(file, toCsv(devices).getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new DataAccessException("Could not write " + file + ": " + e.getMessage(), e);
		}
	}
}

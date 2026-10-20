package com.teqzo.ui;

import com.teqzo.ai.AiStatus;
import com.teqzo.ai.ChatResponse;
import com.teqzo.dto.ComparisonResponse;
import com.teqzo.dto.CountryPrice;
import com.teqzo.dto.MarketInsights;
import com.teqzo.service.ComparisonVerdict;
import com.teqzo.service.SimilarityService.SimilarDevice;
import com.teqzo.dto.ComparisonResponse.ComparisonCell;
import com.teqzo.dto.ComparisonResponse.ComparisonGroup;
import com.teqzo.dto.ComparisonResponse.ComparisonRow;
import com.teqzo.dto.DeviceDetail;
import com.teqzo.dto.DeviceSummary;
import com.teqzo.dto.Facets;
import com.teqzo.dto.KeySpecs;
import com.teqzo.dto.PageResponse;
import com.teqzo.dto.PriceInfo;
import com.teqzo.dto.RecommendationResponse.Check;
import com.teqzo.dto.RecommendationResponse.RecommendedDevice;
import com.teqzo.dto.SpecItem;
import com.teqzo.dto.SpecSection;
import com.teqzo.util.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Turns the data objects into text for the console. Missing values are always shown as "Not listed". */
public final class Views {

	public static final String NOT_LISTED = "Not listed";
	private static final int LINE = 78;

	private Views() {
	}

	// ------------------------------------------------------------------------------- small helpers

	public static String num(Double value) {
		if (value == null) {
			return null;
		}
		if (value == Math.rint(value)) {
			return String.format(Locale.ROOT, "%.0f", value);
		}
		return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
	}

	public static String orNotListed(String value) {
		return value == null || value.isBlank() ? NOT_LISTED : value;
	}

	public static String fit(String text, int width) {
		String s = text == null ? "" : text.replace('\n', ' ');
		if (s.length() <= width) {
			return s + " ".repeat(width - s.length());
		}
		return s.substring(0, Math.max(0, width - 1)) + "~";
	}

	/** Always plain ASCII so column widths stay right on any terminal. */
	public static String rs(String text) {
		return text.replace("₹ ", "Rs ").replace("₹", "Rs ").replace("€ ", "EUR ").replace("€", "EUR ");
	}

	public static String price(PriceInfo p) {
		if (p == null) {
			return NOT_LISTED;
		}
		if (p.inr() != null) {
			return rs(Money.inr(p.inr()));
		}
		if (p.approxInr() != null) {
			return "about " + rs(Money.inr(p.approxInr())) + " (approx.)";
		}
		if (p.eur() != null) {
			return "€" + num(p.eur());
		}
		if (p.usd() != null) {
			return "$" + num(p.usd());
		}
		return NOT_LISTED;
	}

	public static String ramStorage(KeySpecs k) {
		String ram = k.ramGb() == null ? "?" : num(k.ramGb()) + " GB";
		String storage = k.storageGb() == null ? "?" : num(k.storageGb()) + " GB";
		return k.ramGb() == null && k.storageGb() == null ? NOT_LISTED : ram + " / " + storage;
	}

	public static String fullName(DeviceSummary d) {
		String brand = d.brandName() == null ? "" : d.brandName();
		return (brand + " " + d.name()).trim();
	}

	// ------------------------------------------------------------------------------- device lists

	/** A numbered table of one page of devices. {@code firstNumber} is the number of the first row. */
	public static List<String> deviceTable(PageResponse<DeviceSummary> page, int firstNumber) {
		List<String> lines = new ArrayList<>();
		lines.add(fit("#", 4) + fit("Device", 36) + fit("RAM / Storage", 15) + fit("Battery", 10) + "Price");
		lines.add("-".repeat(LINE));
		int n = firstNumber;
		for (DeviceSummary d : page.items()) {
			KeySpecs k = d.keySpecs();
			String battery = k.batteryMah() == null ? "-" : k.batteryMah() + " mAh";
			lines.add(fit(String.valueOf(n++), 4) + fit(fullName(d), 36) + fit(ramStorage(k).replace(NOT_LISTED, "-"), 15)
					+ fit(battery, 10) + price(d.price()).replace(NOT_LISTED, "-"));
		}
		lines.add("-".repeat(LINE));
		lines.add("Page " + (page.page() + 1) + " of " + Math.max(1, page.totalPages()) + "  |  "
				+ page.totalItems() + " device" + (page.totalItems() == 1 ? "" : "s") + " found");
		return lines;
	}

	// ------------------------------------------------------------------------------- one device

	public static List<String> detail(DeviceDetail detail) {
		DeviceSummary d = detail.device();
		KeySpecs k = d.keySpecs();
		List<String> out = new ArrayList<>();
		out.add("=".repeat(LINE));
		out.add(fullName(d));
		out.add("=".repeat(LINE));
		List<String> meta = new ArrayList<>();
		if (d.releaseYear() != null) {
			meta.add("Announced " + d.releaseYear());
		}
		if (d.status() != null) {
			meta.add(d.status());
		}
		if (!meta.isEmpty()) {
			out.add(String.join(" · ", meta));
		}
		out.add("Id: " + d.id());
		out.add("");
		out.add("KEY SPECIFICATIONS");
		out.add(row("Price", price(d.price())));
		if (d.price() != null && d.price().raw() != null && d.price().inr() == null) {
			out.add(row("Price as listed", d.price().raw()));
		}
		out.add(row("RAM", k.ramGb() == null ? null : num(k.ramGb()) + " GB"));
		out.add(row("Storage", k.storageGb() == null ? null : num(k.storageGb()) + " GB"));
		out.add(row("Battery", detail.batteryMah() == null ? null : detail.batteryMah() + " mAh"));
		out.add(row("Charging", detail.charging() == null ? null : detail.charging().raw()));
		out.add(row("Display", join(k.displaySizeInches() == null ? null : num(k.displaySizeInches()) + " in",
				k.displayType(), k.refreshRateHz() == null ? null : k.refreshRateHz() + " Hz")));
		out.add(row("Operating system", k.os()));
		out.add(row("Chipset", k.chipset()));
		out.add(row("Main camera", k.mainCameraMp() == null ? null : num(k.mainCameraMp()) + " MP"));
		out.add(row("Selfie camera", k.selfieCameraMp() == null ? null : num(k.selfieCameraMp()) + " MP"));
		out.add(row("5G", k.has5g() == null ? null : (k.has5g() ? "Yes" : "No 5G listed")));
		out.add(row("NFC", k.nfc()));
		out.add(row("Weight", k.weightG() == null ? null : num(k.weightG()) + " g"));
		out.add(row("Water/dust resistance", k.ipRating()));
		for (String note : detail.notes()) {
			out.add("");
			out.add("Note: " + note);
		}
		if (!detail.sections().isEmpty()) {
			out.add("");
			out.add("FULL SPECIFICATIONS (as listed in the data)");
			for (SpecSection section : detail.sections()) {
				out.add("");
				out.add("[" + section.title() + "]");
				for (SpecItem item : section.items()) {
					String[] valueLines = item.value().split("\n");
					out.add("  " + fit(item.label(), 20) + valueLines[0]);
					for (int i = 1; i < valueLines.length; i++) {
						out.add("  " + " ".repeat(20) + valueLines[i]);
					}
				}
			}
		}
		if (!detail.benchmarks().isEmpty()) {
			out.add("");
			out.add("[Benchmarks]");
			for (SpecItem item : detail.benchmarks()) {
				out.add("  " + item.label() + ": " + item.value().replace("\n", "; ").trim());
			}
		}
		out.add("");
		out.add(detail.dataSource());
		return out;
	}

	private static String row(String label, String value) {
		return "  " + fit(label, 22) + orNotListed(value);
	}

	private static String join(String... parts) {
		List<String> present = new ArrayList<>();
		for (String p : parts) {
			if (p != null && !p.isBlank()) {
				present.add(p.split(",")[0].trim());
			}
		}
		return present.isEmpty() ? null : String.join(", ", present);
	}

	// ------------------------------------------------------------------------------- comparison

	public static List<String> comparison(ComparisonResponse r) {
		int count = r.devices().size();
		int labelWidth = 22;
		int cellWidth = Math.max(16, Math.min(26, (LINE + 20 - labelWidth) / count));
		List<String> out = new ArrayList<>();
		StringBuilder header = new StringBuilder(" ".repeat(labelWidth));
		for (DeviceSummary d : r.devices()) {
			header.append(fit(fullName(d), cellWidth - 1)).append(' ');
		}
		out.add(header.toString());
		for (ComparisonGroup group : r.groups()) {
			out.add("");
			out.add("[" + group.title() + "]");
			for (ComparisonRow row : group.rows()) {
				StringBuilder line = new StringBuilder(fit(row.label(), labelWidth));
				for (int i = 0; i < row.cells().size(); i++) {
					ComparisonCell cell = row.cells().get(i);
					String shown = rs(cell.display()) + (row.leaders().contains(i) ? " *" : "");
					line.append(fit(shown, cellWidth - 1)).append(' ');
				}
				out.add(line.toString());
			}
		}
		out.add("");
		out.add("* = best listed value where more (or less) is a clear advantage");
		for (String note : r.notes()) {
			out.add(note);
		}
		return out;
	}

	// ------------------------------------------------------------------------------- assistant

	public static List<String> chat(ChatResponse r) {
		List<String> out = new ArrayList<>();
		if (r.notice() != null) {
			out.add("(" + r.notice() + ")");
			out.add("");
		}
		out.add(r.reply());
		if (r.understood() != null && !r.understood().isBlank()) {
			out.add("");
			out.add("Understood requirements: " + r.understood());
		}
		if (r.recommendations() != null && !r.recommendations().recommendations().isEmpty()) {
			out.add("");
			for (RecommendedDevice rec : r.recommendations().recommendations()) {
				out.addAll(recommendation(rec));
			}
		}
		if (r.followUp() != null) {
			out.add("");
			out.add("Assistant asks: " + r.followUp());
		}
		if (r.suggestions() != null && !r.suggestions().isEmpty()) {
			out.add("");
			out.add("You could say: " + String.join("  |  ", r.suggestions().stream().map(s -> "\"" + s + "\"").toList()));
		}
		return out;
	}

	public static List<String> recommendation(RecommendedDevice rec) {
		List<String> out = new ArrayList<>();
		DeviceSummary d = rec.device();
		out.add("#" + rec.rank() + "  " + fullName(d) + "   score " + num(Math.round(rec.score() * 10) / 10.0) + "/100   "
				+ price(d.price()));
		out.add("    " + ramStorage(d.keySpecs()).replace(NOT_LISTED, "RAM/storage not listed")
				+ (d.keySpecs().batteryMah() == null ? "" : ", " + d.keySpecs().batteryMah() + " mAh"));
		for (String h : rec.highlights()) {
			out.add("    + " + h);
		}
		for (Check c : rec.checks()) {
			String mark = "MET".equals(c.status()) ? "[ok]" : ("UNKNOWN".equals(c.status()) ? "[?]" : "[x]");
			out.add("    " + mark + " " + c.label() + ": needs " + c.required() + ", listed " + c.actual());
		}
		for (String caveat : rec.caveats()) {
			out.add("    ! " + caveat);
		}
		out.add("    id: " + d.id());
		out.add("");
		return out;
	}

	// ------------------------------------------------------------------------------- about

	private static String sourceLabel(String key) {
		return switch (key) {
			case "GSMARENA" -> "GSMArena snapshot (full specifications, announced up to 2022)";
			case "FLIPKART_2026" -> "Flipkart 2026 listings (newer phones, fewer fields)";
			case "MOBILES_2025" -> "Mobiles Dataset 2025 (2014-2025 models, launch prices in 5 countries)";
			default -> key;
		};
	}

	public static List<String> about(Facets f, int brandCount, AiStatus ai, MarketInsights insights) {
		List<String> out = new ArrayList<>();
		out.add("DATA");
		out.add("  Devices: " + f.totalDevices() + " (" + f.devicesWithSpecifications() + " with specifications)");
		out.add("  Brands with devices: " + brandCount);
		if (insights != null && !insights.devicesBySource().isEmpty()) {
			insights.devicesBySource().forEach((key, count) -> out.add("  " + count + " from " + sourceLabel(key)));
		} else if (f.devicesFromListings() > 0) {
			out.add("  " + (f.totalDevices() - f.devicesFromListings()) + " from the GSMArena snapshot, "
					+ f.devicesFromListings() + " from newer datasets with fewer fields");
		}
		out.add("  Devices with a price: " + f.devicesWithAnyListedPrice() + " (" + f.devicesWithInrPrice() + " in INR)");
		out.add("");
		out.add("ASSISTANT");
		out.add("  Mode: " + ai.mode() + (ai.llmEnabled() ? " (" + ai.provider() + ", " + ai.model() + ")" : ""));
		out.add("  " + ai.message());
		return out;
	}

	// ------------------------------------------------------------------------------- extra screens

	static String money(String currency, double amount) {
		return currency + " " + String.format(Locale.ROOT, "%,.0f", amount);
	}

	/** Launch prices per country, exactly as listed (no conversion between currencies). */
	public static List<String> regionalPrices(List<CountryPrice> prices) {
		List<String> out = new ArrayList<>();
		if (prices.isEmpty()) {
			return out;
		}
		out.add("LAUNCH PRICES BY COUNTRY (as listed in the Mobiles 2025 dataset; not converted)");
		for (CountryPrice p : prices) {
			String range = p.variants() > 1 && p.lowest() != p.highest()
					? money(p.currency(), p.lowest()) + " - " + String.format(Locale.ROOT, "%,.0f", p.highest())
							+ "  (" + p.variants() + " variants)"
					: money(p.currency(), p.lowest());
			out.add("  " + fit(p.country(), 12) + range);
		}
		return out;
	}

	public static List<String> similar(List<SimilarDevice> list) {
		List<String> out = new ArrayList<>();
		if (list.isEmpty()) {
			out.add("No similar phones found (too few listed values to compare).");
			return out;
		}
		out.add("#   Phone                               Match   Price         Why");
		out.add("-".repeat(LINE));
		int n = 1;
		for (SimilarDevice s : list) {
			DeviceSummary d = s.device();
			out.add(fit(String.valueOf(n++), 4) + fit(fullName(d), 36) + fit(num(s.similarity()) + "%", 8)
					+ fit(price(d.price()).replace(NOT_LISTED, "-"), 14) + String.join(", ", s.reasons()));
		}
		out.add("-".repeat(LINE));
		out.add("Match uses only values both phones list (price, RAM, storage, battery, screen, camera, year, OS).");
		return out;
	}

	public static List<String> verdict(ComparisonVerdict verdict) {
		List<String> out = new ArrayList<>();
		out.add("");
		out.add("VERDICT (rows where more or less is clearly better)");
		verdict.lines().forEach(l -> out.add("  " + l));
		return out;
	}

	public static List<String> shortlist(List<DeviceSummary> devices) {
		List<String> out = new ArrayList<>();
		out.add(fit("#", 4) + fit("Device", 36) + fit("RAM / Storage", 15) + fit("Battery", 10) + "Price");
		out.add("-".repeat(LINE));
		int n = 1;
		for (DeviceSummary d : devices) {
			KeySpecs k = d.keySpecs();
			String battery = k.batteryMah() == null ? "-" : k.batteryMah() + " mAh";
			out.add(fit(String.valueOf(n++), 4) + fit(fullName(d), 36) + fit(ramStorage(k).replace(NOT_LISTED, "-"), 15)
					+ fit(battery, 10) + price(d.price()).replace(NOT_LISTED, "-"));
		}
		return out;
	}

	static String bar(long value, long max, int width) {
		if (max <= 0 || value <= 0) {
			return "";
		}
		return "#".repeat((int) Math.max(1, Math.round((double) value * width / max)));
	}

	public static List<String> insights(MarketInsights m) {
		List<String> out = new ArrayList<>();
		out.add("MARKET INSIGHTS (computed by the database with GROUP BY; phones with specifications only)");
		out.add("");
		out.add("Brands with the most phones");
		out.add("  " + fit("Brand", 14) + fit("Phones", 8) + fit("Avg price", 12) + fit("Avg battery", 13) + "Avg RAM");
		for (MarketInsights.BrandStat b : m.brands()) {
			out.add("  " + fit(b.brand(), 14) + fit(String.valueOf(b.devices()), 8)
					+ fit(b.avgPriceInr() == null ? "-" : "Rs " + String.format(Locale.ROOT, "%,.0f", b.avgPriceInr()), 12)
					+ fit(b.avgBatteryMah() == null ? "-" : String.format(Locale.ROOT, "%.0f mAh", b.avgBatteryMah()), 13)
					+ (b.avgRamGb() == null ? "-" : String.format(Locale.ROOT, "%.1f GB", b.avgRamGb())));
		}
		long maxYear = m.years().stream().mapToLong(MarketInsights.YearCount::devices).max().orElse(0);
		out.add("");
		out.add("Phones per year (announced, or listed for retail listings)");
		for (MarketInsights.YearCount y : m.years()) {
			out.add("  " + y.year() + "  " + fit(bar(y.devices(), maxYear, 30), 31) + y.devices());
		}
		long maxBand = m.priceBands().stream().mapToLong(MarketInsights.PriceBand::devices).max().orElse(0);
		out.add("");
		out.add("Phones by listed INR price (only phones with an INR price)");
		for (MarketInsights.PriceBand b : m.priceBands()) {
			out.add("  " + fit(b.label(), 18) + fit(bar(b.devices(), maxBand, 30), 31) + b.devices());
		}
		out.add("");
		out.add("Averages use only phones that state the value; prices come from different years, so compare with care.");
		return out;
	}
}

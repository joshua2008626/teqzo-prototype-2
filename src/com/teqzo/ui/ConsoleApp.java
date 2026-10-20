package com.teqzo.ui;

import com.teqzo.ai.ChatRequest;
import com.teqzo.ai.ChatResponse;
import com.teqzo.ai.ChatTurn;
import com.teqzo.config.AppContext;
import com.teqzo.domain.InvalidRequestException;
import com.teqzo.domain.NotFoundException;
import com.teqzo.dto.BrandDto;
import com.teqzo.dto.ComparisonResponse;
import com.teqzo.dto.DeviceDetail;
import com.teqzo.dto.DeviceSummary;
import com.teqzo.dto.PageResponse;
import com.teqzo.domain.DeviceRow;
import com.teqzo.service.Leaderboard;
import com.teqzo.service.ComparisonVerdict;
import com.teqzo.service.CsvExporter;
import com.teqzo.service.DeviceMapper;
import com.teqzo.filter.DeviceFilterParser;
import com.teqzo.filter.SortOption;
import com.teqzo.repository.DataAccessException;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** The menu-driven console application. All data access goes through the service classes. */
public final class ConsoleApp {

	static final int PAGE_SIZE = 10;
	static final int MAX_TRAY = 4;

	private final AppContext ctx;
	private final Console io;
	private final List<DeviceSummary> tray = new ArrayList<>();

	public ConsoleApp(AppContext ctx, Console io) {
		this.ctx = ctx;
		this.io = io;
	}

	// ------------------------------------------------------------------------------------ main menu

	public void run() {
		io.println();
		io.println("=".repeat(60));
		io.println("  TEQZO - find, filter, compare and get recommendations");
		io.println("=".repeat(60));
		while (true) {
			io.println();
			io.println("MAIN MENU");
			io.println("  1) Search devices by name");
			io.println("  2) Filter devices by specifications");
			io.println("  3) Browse by brand");
			io.println("  4) Show a device's details");
			io.println("  5) Compare devices (selected: " + tray.size() + ")");
			io.println("  6) Ask the recommendation assistant");
			io.println("  7) Top lists (battery, RAM, camera, lightest, cheapest...)");
			io.println("  8) My shortlist (" + ctx.shortlist.ids().size() + " saved)");
			io.println("  9) Market insights");
			io.println("  10) About the data");
			io.println("  0) Exit");
			String choice = io.ask("Choose: ");
			if (choice == null || choice.equals("0") || choice.equalsIgnoreCase("q")) {
				io.println("Goodbye.");
				return;
			}
			guarded(() -> {
				switch (choice) {
					case "1" -> searchByName();
					case "2" -> filterDevices();
					case "3" -> browseByBrand();
					case "4" -> detailsByName();
					case "5" -> compareMenu();
					case "6" -> assistant();
					case "7" -> topLists();
					case "8" -> shortlistMenu();
					case "9" -> insights();
					case "10" -> about();
					default -> io.println("Please enter a number from the menu.");
				}
			});
		}
	}

	/** Runs one action and reports problems in plain words instead of stopping the program. */
	void guarded(Runnable action) {
		try {
			action.run();
		} catch (InvalidRequestException | NotFoundException e) {
			io.println("Problem: " + e.getMessage());
		} catch (DataAccessException e) {
			io.println("Database problem: " + e.getMessage());
			io.println("Check that MySQL is running and that DB_* settings are correct (see README).");
		} catch (RuntimeException e) {
			io.println("Unexpected error: " + e);
			if (System.getenv("TEQZO_LOG") != null) {
				e.printStackTrace();
			}
		}
	}

	// ------------------------------------------------------------------------------------ search / filter

	void searchByName() {
		String text = io.ask("Search text (brand, model or chipset, e.g. \"iphone 17\"): ");
		if (text == null || text.isBlank()) {
			return;
		}
		Map<String, String> params = new LinkedHashMap<>();
		params.put("q", text);
		showResults(params, "Results for \"" + text + "\"");
	}

	void filterDevices() {
		io.println("Enter the filters you want; press Enter to skip any of them.");
		Map<String, String> p = new LinkedHashMap<>();
		String text = io.ask("Name contains: ");
		if (text == null) {
			return;
		}
		put(p, "q", text);
		String brandText = io.ask("Brand(s), comma separated (e.g. Samsung, OnePlus): ");
		if (brandText == null) {
			return;
		}
		List<String> brandIds = new ArrayList<>();
		for (String name : brandText.split(",")) {
			if (name.isBlank()) {
				continue;
			}
			Optional<BrandDto> brand = ctx.brands.findByName(name);
			if (brand.isPresent()) {
				brandIds.add(brand.get().id());
			} else {
				io.println("  No brand called \"" + name.trim() + "\" - ignored.");
			}
		}
		if (!brandIds.isEmpty()) {
			p.put("brand", String.join(",", brandIds));
		}
		String[][] numeric = {
				{"priceMaxInr", "Maximum price in rupees: "}, {"priceMinInr", "Minimum price in rupees: "},
				{"ramMin", "Minimum RAM (GB): "}, {"storageMin", "Minimum storage (GB): "},
				{"batteryMin", "Minimum battery (mAh): "}, {"wiredChargingMin", "Minimum wired charging (W): "},
				{"displaySizeMin", "Minimum screen size (inches): "}, {"displaySizeMax", "Maximum screen size (inches): "},
				{"refreshRateMin", "Minimum refresh rate (Hz): "}, {"mainCameraMin", "Minimum main camera (MP): "},
				{"releasedFrom", "Announced in or after year: "}};
		for (String[] field : numeric) {
			String answer = io.ask(field[1]);
			if (answer == null) {
				return;
			}
			put(p, field[0], answer);
		}
		for (String[] field : new String[][] {{"has5g", "5G (yes/no/Enter): "}, {"nfc", "NFC (yes/no/Enter): "}}) {
			String answer = io.ask(field[1]);
			if (answer == null) {
				return;
			}
			put(p, field[0], yesNo(answer));
		}
		String wireless = io.ask("Must have wireless charging? (yes/Enter): ");
		if (wireless == null) {
			return;
		}
		if (yesNo(wireless) != null && yesNo(wireless).equals("true")) {
			p.put("wirelessCharging", "true");
		}
		String os = io.ask("Operating system contains (Android/iOS): ");
		if (os == null) {
			return;
		}
		put(p, "os", os);
		String chipset = io.ask("Chipset contains (e.g. Snapdragon): ");
		if (chipset == null) {
			return;
		}
		put(p, "chipset", chipset);
		String sort = io.ask("Sort by [" + SortOption.allowedKeys() + "] (Enter = newest): ");
		if (sort == null) {
			return;
		}
		put(p, "sort", sort);
		showResults(p, "Devices matching your filters");
	}

	private static void put(Map<String, String> params, String key, String value) {
		if (value != null && !value.isBlank()) {
			params.put(key, value.trim());
		}
	}

	/** "yes"/"y"/"true" -> "true", "no"/"n"/"false" -> "false", anything else -> null. */
	static String yesNo(String answer) {
		String a = answer == null ? "" : answer.trim().toLowerCase(Locale.ROOT);
		if (a.equals("y") || a.equals("yes") || a.equals("true")) {
			return "true";
		}
		if (a.equals("n") || a.equals("no") || a.equals("false")) {
			return "false";
		}
		return null;
	}

	// ------------------------------------------------------------------------------------ brands

	void browseByBrand() {
		List<BrandDto> brands = ctx.brands.all();
		io.println("BRANDS (devices in the data)");
		int shown = 0;
		StringBuilder line = new StringBuilder();
		for (BrandDto b : brands) {
			line.append(Views.fit(b.name() + " (" + b.deviceCount() + ")", 26));
			if (++shown % 3 == 0) {
				io.println("  " + line);
				line.setLength(0);
			}
		}
		if (line.length() > 0) {
			io.println("  " + line);
		}
		String name = io.ask("Brand name: ");
		if (name == null || name.isBlank()) {
			return;
		}
		Optional<BrandDto> brand = ctx.brands.findByName(name);
		if (brand.isEmpty()) {
			io.println("No brand called \"" + name + "\". Spell it as shown above.");
			return;
		}
		Map<String, String> params = new LinkedHashMap<>();
		params.put("brand", brand.get().id());
		showResults(params, brand.get().name() + " devices");
	}

	// ------------------------------------------------------------------------------------ result lists

	/**
	 * Shows a page of results and handles the paging / open / compare commands until the user goes back.
	 * {@code baseParams} are the filter parameters; page and size are added here.
	 */
	void showResults(Map<String, String> baseParams, String title) {
		int page = 0;
		while (true) {
			Map<String, String> params = new LinkedHashMap<>(baseParams);
			params.put("page", String.valueOf(page));
			params.put("size", String.valueOf(PAGE_SIZE));
			PageResponse<DeviceSummary> result = ctx.deviceService.search(DeviceFilterParser.parse(params));
			io.println();
			io.println(title);
			if (result.items().isEmpty()) {
				io.println("No devices found. Try fewer or looser filters.");
				return;
			}
			Views.deviceTable(result, page * PAGE_SIZE + 1).forEach(io::println);
			String command = io.ask("[number] details | c <n> compare | s <n> shortlist | x export CSV | n next | p previous | b back: ");
			if (command == null || command.isBlank() || command.equalsIgnoreCase("b")) {
				return;
			}
			String lower = command.toLowerCase(Locale.ROOT);
			if (lower.equals("n")) {
				if (result.hasNext()) {
					page++;
				} else {
					io.println("This is the last page.");
				}
			} else if (lower.equals("p")) {
				page = Math.max(0, page - 1);
			} else if (lower.startsWith("c ")) {
				DeviceSummary chosen = pick(result, page, lower.substring(2).trim());
				if (chosen != null) {
					addToTray(chosen);
				}
			} else if (lower.startsWith("s ")) {
				DeviceSummary chosen = pick(result, page, lower.substring(2).trim());
				if (chosen != null) {
					addToShortlist(chosen);
				}
			} else if (lower.equals("x")) {
				exportResults(baseParams);
			} else {
				DeviceSummary chosen = pick(result, page, lower);
				if (chosen != null) {
					showDetail(chosen.id());
				}
			}
		}
	}

	/** The device with the given list number on the current page, or null (with a message). */
	private DeviceSummary pick(PageResponse<DeviceSummary> result, int page, String text) {
		try {
			int number = Integer.parseInt(text.trim());
			int index = number - 1 - page * PAGE_SIZE;
			if (index >= 0 && index < result.items().size()) {
				return result.items().get(index);
			}
		} catch (NumberFormatException ignored) {
			// fall through to the message
		}
		io.println("Type a number shown in the list, or n, p, b.");
		return null;
	}

	// ------------------------------------------------------------------------------------ one device

	void detailsByName() {
		String text = io.ask("Device name (e.g. \"galaxy s25 ultra\") or an id: ");
		if (text == null || text.isBlank()) {
			return;
		}
		DeviceSummary chosen = findOne(text);
		if (chosen != null) {
			showDetail(chosen.id());
		}
	}

	/** Searches by text and lets the user pick one result. Returns null when nothing was chosen. */
	DeviceSummary findOne(String text) {
		Map<String, String> params = new LinkedHashMap<>();
		params.put("q", text);
		params.put("size", String.valueOf(PAGE_SIZE));
		PageResponse<DeviceSummary> result = ctx.deviceService.search(DeviceFilterParser.parse(params));
		if (result.items().isEmpty()) {
			io.println("No device matches \"" + text + "\".");
			return null;
		}
		if (result.items().size() == 1) {
			return result.items().get(0);
		}
		Views.deviceTable(result, 1).forEach(io::println);
		String answer = io.ask("Number of the device (Enter to cancel): ");
		return answer == null || answer.isBlank() ? null : pick(result, 0, answer);
	}

	void showDetail(String id) {
		DeviceDetail detail = ctx.deviceService.get(id);
		io.println();
		Views.detail(detail).forEach(io::println);
		List<String> prices = Views.regionalPrices(ctx.regionalPrices.forDevice(id));
		if (!prices.isEmpty()) {
			io.println();
			prices.forEach(io::println);
		}
		while (true) {
			String command = io.ask("c = compare | s = save to shortlist | l = similar phones | b = back: ");
			if (command == null || command.isBlank() || command.equalsIgnoreCase("b")) {
				return;
			}
			if (command.equalsIgnoreCase("c")) {
				addToTray(detail.device());
			} else if (command.equalsIgnoreCase("s")) {
				addToShortlist(detail.device());
			} else if (command.equalsIgnoreCase("l")) {
				guarded(() -> showSimilar(id));
			}
		}
	}

	// ------------------------------------------------------------------------------------ compare

	void addToTray(DeviceSummary device) {
		if (tray.stream().anyMatch(d -> d.id().equals(device.id()))) {
			io.println(Views.fullName(device) + " is already selected.");
		} else if (tray.size() >= MAX_TRAY) {
			io.println("You can compare up to " + MAX_TRAY + " devices. Remove one first (menu 5).");
		} else {
			tray.add(device);
			io.println("Added " + Views.fullName(device) + " (" + tray.size() + " selected).");
		}
	}

	void compareMenu() {
		while (true) {
			io.println();
			io.println("COMPARE - selected devices:");
			if (tray.isEmpty()) {
				io.println("  (none yet)");
			}
			for (int i = 0; i < tray.size(); i++) {
				io.println("  " + (i + 1) + ". " + Views.fullName(tray.get(i)));
			}
			io.println("  a = add a device by name | r <number> = remove | c = compare now | x = clear | b = back");
			String command = io.ask("Choose: ");
			if (command == null || command.isBlank() || command.equalsIgnoreCase("b")) {
				return;
			}
			String lower = command.toLowerCase(Locale.ROOT);
			if (lower.equals("a")) {
				String text = io.ask("Device name: ");
				if (text != null && !text.isBlank()) {
					DeviceSummary chosen = findOne(text);
					if (chosen != null) {
						addToTray(chosen);
					}
				}
			} else if (lower.startsWith("r ")) {
				try {
					int index = Integer.parseInt(lower.substring(2).trim()) - 1;
					if (index >= 0 && index < tray.size()) {
						io.println("Removed " + Views.fullName(tray.remove(index)) + ".");
					} else {
						io.println("There is no device with that number.");
					}
				} catch (NumberFormatException e) {
					io.println("Type r followed by a number, for example: r 2");
				}
			} else if (lower.equals("x")) {
				tray.clear();
			} else if (lower.equals("c")) {
				guarded(() -> {
					ComparisonResponse result = ctx.comparison.compare(tray.stream().map(DeviceSummary::id).toList());
					io.println();
					Views.comparison(result).forEach(io::println);
					Views.verdict(ComparisonVerdict.of(result)).forEach(io::println);
				});
			} else {
				io.println("Unknown command.");
			}
		}
	}

	// ------------------------------------------------------------------------------------ assistant

	void assistant() {
		io.println();
		io.println("ASSISTANT - describe the phone you want (budget, brand, RAM, battery, camera, gaming...).");
		io.println("Type 'new' to start over, or 'b' to go back to the menu.");
		Map<String, Object> requirements = Map.of();
		List<String> asked = List.of();
		List<ChatTurn> history = new ArrayList<>();
		while (true) {
			String message = io.ask("\nYou: ");
			if (message == null || message.isBlank() || message.equalsIgnoreCase("b")) {
				return;
			}
			if (message.equalsIgnoreCase("new")) {
				requirements = Map.of();
				asked = List.of();
				history.clear();
				io.println("Started a new conversation.");
				continue;
			}
			try {
				List<ChatTurn> recent = history.size() > 20 ? history.subList(history.size() - 20, history.size())
						: history;
				ChatResponse response = ctx.assistant.chat(new ChatRequest(message, new ArrayList<>(recent),
						requirements, asked));
				io.println();
				Views.chat(response).forEach(io::println);
				requirements = response.requirements();
				asked = response.asked();
				history.add(new ChatTurn("user", message));
				history.add(new ChatTurn("assistant", response.reply()));
			} catch (InvalidRequestException e) {
				io.println("Problem: " + e.getMessage());
			}
		}
	}

	// ------------------------------------------------------------------------------------ similar

	void showSimilar(String id) {
		io.println();
		io.println("PHONES SIMILAR TO THIS ONE");
		var list = ctx.similarity.similarTo(id, 5);
		Views.similar(list).forEach(io::println);
		if (list.isEmpty()) {
			return;
		}
		String answer = io.ask("Number to open, c <number> to compare, Enter to go back: ");
		if (answer == null || answer.isBlank()) {
			return;
		}
		String lower = answer.trim().toLowerCase(Locale.ROOT);
		boolean compare = lower.startsWith("c ");
		try {
			int index = Integer.parseInt(compare ? lower.substring(2).trim() : lower) - 1;
			if (index >= 0 && index < list.size()) {
				if (compare) {
					addToTray(list.get(index).device());
				} else {
					showDetail(list.get(index).device().id());
				}
				return;
			}
		} catch (NumberFormatException ignored) {
			// fall through
		}
		io.println("Type a number from the list.");
	}

	// ------------------------------------------------------------------------------------ top lists

	void topLists() {
		Leaderboard[] boards = Leaderboard.values();
		io.println();
		io.println("TOP LISTS (phones only; a phone must state the value to be ranked)");
		for (int i = 0; i < boards.length; i++) {
			io.println("  " + (i + 1) + ") " + boards[i].title());
		}
		String choice = io.ask("Choose (Enter to go back): ");
		if (choice == null || choice.isBlank()) {
			return;
		}
		int index;
		try {
			index = Integer.parseInt(choice.trim()) - 1;
		} catch (NumberFormatException e) {
			io.println("Type a number from the list.");
			return;
		}
		if (index < 0 || index >= boards.length) {
			io.println("Type a number from the list.");
			return;
		}
		String budgetText = io.ask("Maximum price in rupees (Enter for no limit): ");
		Double budget = null;
		if (budgetText != null && !budgetText.isBlank()) {
			try {
				budget = Double.parseDouble(budgetText.trim().replace(",", ""));
			} catch (NumberFormatException e) {
				io.println("That is not a number; showing the list without a price limit.");
			}
		}
		showResults(boards[index].params(budget), boards[index].title());
	}

	// ------------------------------------------------------------------------------------ shortlist

	void addToShortlist(DeviceSummary device) {
		if (ctx.shortlist.contains(device.id())) {
			io.println(Views.fullName(device) + " is already on your shortlist.");
		} else if (ctx.shortlist.add(device.id())) {
			io.println("Saved " + Views.fullName(device) + " to your shortlist (" + ctx.shortlist.ids().size() + " saved).");
		} else {
			io.println("The shortlist is full (" + com.teqzo.service.ShortlistStore.MAX_ENTRIES + "). Remove one first (menu 8).");
		}
	}

	/** The shortlisted devices in saved order; ids that no longer exist in the database are dropped from the file. */
	private List<DeviceSummary> shortlistDevices() {
		List<String> ids = ctx.shortlist.ids();
		if (ids.isEmpty()) {
			return List.of();
		}
		Double fx = ctx.pricePolicy.eurToInr();
		List<DeviceRow> rows = ctx.devices.findByIds(ids);
		List<DeviceSummary> out = new ArrayList<>();
		for (String id : ids) {
			DeviceRow row = rows.stream().filter(r -> r.id.equals(id)).findFirst().orElse(null);
			if (row == null) {
				ctx.shortlist.remove(id);
				io.println("(" + id + " is no longer in the database and was removed from your shortlist.)");
			} else {
				out.add(DeviceMapper.toSummary(row, fx));
			}
		}
		return out;
	}

	void shortlistMenu() {
		while (true) {
			List<DeviceSummary> list = shortlistDevices();
			io.println();
			io.println("MY SHORTLIST (saved in " + ctx.shortlist.file() + ")");
			if (list.isEmpty()) {
				io.println("  Empty. Save phones with 's' from a result list or a details page.");
				return;
			}
			Views.shortlist(list).forEach(io::println);
			String command = io.ask("[number] details | r <n> remove | c compare all (2-4) | x export CSV | clear | b back: ");
			if (command == null || command.isBlank() || command.equalsIgnoreCase("b")) {
				return;
			}
			String lower = command.trim().toLowerCase(Locale.ROOT);
			if (lower.equals("clear")) {
				ctx.shortlist.clear();
				io.println("Shortlist cleared.");
			} else if (lower.equals("x")) {
				guarded(() -> exportDevices(list));
			} else if (lower.equals("c")) {
				if (list.size() < 2 || list.size() > MAX_TRAY) {
					io.println("Comparison needs 2 to " + MAX_TRAY + " devices; remove or add some first.");
				} else {
					tray.clear();
					tray.addAll(list);
					guarded(() -> {
						ComparisonResponse result = ctx.comparison.compare(list.stream().map(DeviceSummary::id).toList());
						io.println();
						Views.comparison(result).forEach(io::println);
						Views.verdict(ComparisonVerdict.of(result)).forEach(io::println);
					});
				}
			} else if (lower.startsWith("r ")) {
				int index = parseIndex(lower.substring(2), list.size());
				if (index < 0) {
					io.println("There is no device with that number.");
				} else {
					ctx.shortlist.remove(list.get(index).id());
					io.println("Removed " + Views.fullName(list.get(index)) + ".");
				}
			} else {
				int index = parseIndex(lower, list.size());
				if (index < 0) {
					io.println("Type a number, r <number>, c, x, clear or b.");
				} else {
					showDetail(list.get(index).id());
				}
			}
		}
	}

	/** Zero-based index for a 1-based number typed by the user, or -1 when it is not valid. */
	static int parseIndex(String text, int size) {
		try {
			int n = Integer.parseInt(text.trim());
			return n >= 1 && n <= size ? n - 1 : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	// ------------------------------------------------------------------------------------ export

	private static final int EXPORT_LIMIT = 500;

	/** Exports every match of the current search (up to 500) to a CSV file in the working directory. */
	void exportResults(Map<String, String> baseParams) {
		List<DeviceSummary> all = new ArrayList<>();
		for (int page = 0; all.size() < EXPORT_LIMIT; page++) {
			Map<String, String> params = new LinkedHashMap<>(baseParams);
			params.put("page", String.valueOf(page));
			params.put("size", "100");
			PageResponse<DeviceSummary> result = ctx.deviceService.search(DeviceFilterParser.parse(params));
			all.addAll(result.items());
			if (!result.hasNext()) {
				break;
			}
		}
		boolean cut = all.size() > EXPORT_LIMIT;
		exportDevices(cut ? all.subList(0, EXPORT_LIMIT) : all);
		if (cut) {
			io.println("(Only the first " + EXPORT_LIMIT + " matches are exported; add filters to narrow the list.)");
		}
	}

	void exportDevices(List<DeviceSummary> devices) {
		if (devices.isEmpty()) {
			io.println("Nothing to export.");
			return;
		}
		Path file = Path.of("teqzo-export-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
				+ ".csv");
		CsvExporter.write(file, devices);
		io.println("Exported " + devices.size() + " devices to " + file.toAbsolutePath() + " (opens in Excel).");
	}

	// ------------------------------------------------------------------------------------ insights

	private com.teqzo.dto.MarketInsights insightsOrEmpty() {
		try {
			return ctx.insights.get();
		} catch (DataAccessException e) {
			return com.teqzo.dto.MarketInsights.empty();
		}
	}

	void insights() {
		io.println();
		Views.insights(ctx.insights.get()).forEach(io::println);
	}

	// ------------------------------------------------------------------------------------ about

	void about() {
		io.println();
		Views.about(ctx.facets.get(), ctx.brands.all().size(), ctx.assistant.status(), insightsOrEmpty()).forEach(io::println);
	}
}

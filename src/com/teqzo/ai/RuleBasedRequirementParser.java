package com.teqzo.ai;

import com.teqzo.recommendation.Dimension;
import com.teqzo.recommendation.Requirements;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic English / Indian-English understanding of shopping requests. Used when no AI key is
 * configured and as a fallback when the AI provider fails. It only extracts what the text says.
 */
public class RuleBasedRequirementParser implements RequirementParser {

	static final double FAST_CHARGING_W = 30;
	static final double HIGH_REFRESH_HZ = 90;
	static final double COMPACT_MAX_INCHES = 6.1;
	static final double LARGE_MIN_INCHES = 6.6;
	static final double TABLET_MIN_INCHES = 7.5;
	static final double RAM_BARE_NUMBER_MAX_GB = 24;
	static final double STORAGE_BARE_NUMBER_MIN_GB = 32;

	private static final String NEG = "(?:no|not|without|don'?t|dont|do not|doesn'?t|never|except|other than|avoid)";
	private static final Pattern RESET = Pattern.compile("\\b(start over|start again|reset|new search|forget everything|forget all)\\b");
	private static final Pattern NUMBER_UNIT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(gb|tb)\\b");
	private static final Pattern MAH = Pattern.compile("(\\d{4,5})\\s*mah");
	private static final Pattern WATTS = Pattern.compile("(\\d{2,3})\\s*(?:w|watt|watts)\\b");
	private static final Pattern HZ = Pattern.compile("(\\d{2,3})\\s*hz");
	private static final Pattern MP = Pattern.compile("(\\d{2,3})\\s*mp\\b");
	private static final Pattern INCHES = Pattern.compile("(\\d(?:\\.\\d)?)\\s*(?:inch|inches|\")");
	private static final Pattern BUDGET_A = Pattern.compile(
			"(?:under|below|within|upto|up to|less than|maximum|max|budget(?: of| is| around)?|around|about|upper limit)\\s*(?:rs\\.?|inr|₹)?\\s*(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k|thousand|lakhs?|lacs?|l)?\\b");
	private static final Pattern BUDGET_B = Pattern.compile("(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k|thousand|lakhs?|lacs?)?\\s*(?:rs\\.?|inr|rupees)?\\s*budget");
	private static final Pattern BUDGET_C = Pattern.compile("(?:₹|rs\\.?|inr)\\s*(\\d[\\d,]*(?:\\.\\d+)?)\\s*(k|thousand|lakhs?|lacs?)?");
	private static final Pattern YEAR_AFTER = Pattern.compile("(?:after|since|from|newer than|released in or after)\\s*(20\\d\\d)");
	private static final Pattern IGNORE_A = Pattern.compile(
			"(?:don'?t|dont|do not|doesn'?t|not|no)\\s+(?:really\\s+|much\\s+)?(?:care|need|want|matter|bother|worry)[^.,;]{0,25}?\\b(camera|battery|display|screen|gaming|performance)");
	private static final Pattern IGNORE_B = Pattern.compile(
			"\\b(camera|battery|display|screen|gaming|performance)\\s+(?:doesn'?t|does not|isn'?t|is not|is not that|isn'?t that)\\s+(?:matter|important)");

	private static final Map<String, List<String>> USE_CASE_WORDS = new LinkedHashMap<>();

	static {
		USE_CASE_WORDS.put("gaming", List.of("gaming", "game", "games", "pubg", "bgmi", "fortnite", "genshin", "free fire"));
		USE_CASE_WORDS.put("camera", List.of("camera", "photo", "photos", "photography", "selfie", "selfies", "vlog", "portrait"));
		USE_CASE_WORDS.put("battery", List.of("battery", "backup", "long lasting", "all day", "endurance", "fast charging", "fast charge"));
		USE_CASE_WORDS.put("display", List.of("display", "screen", "brightness", "amoled", "oled"));
		USE_CASE_WORDS.put("performance", List.of("performance", "fast phone", "speed", "powerful", "flagship", "processor", "multitasking", "smooth"));
	}

	private final Supplier<List<String>> brandNames;
	private final IntSupplier newestYear;

	public RuleBasedRequirementParser(Supplier<List<String>> brandNames, IntSupplier newestYear) {
		this.brandNames = brandNames;
		this.newestYear = newestYear;
	}

	@Override
	public ParseResult parse(String message, Map<String, Object> current, List<ChatTurn> history) {
		String original = message == null ? "" : message;
		String text = " " + original.toLowerCase(Locale.ROOT).replace('’', '\'') + " ";
		Map<String, Object> update = new LinkedHashMap<>();
		Set<String> clear = new LinkedHashSet<>();
		boolean reset = RESET.matcher(text).find();

		parseClears(text, clear);
		Set<String> ignoredDims = new LinkedHashSet<>();
		text = extractIgnored(text, ignoredDims);

		parseBudget(text, update);
		parseMemory(text, update);
		parseNumbers(text, update);
		parseFlags(text, update);
		parseBrandsAndOs(text, original, update);
		parseYear(text, update);
		parseCompactLarge(text, update);

		List<String> priorities = new ArrayList<>();
		for (Map.Entry<String, List<String>> e : USE_CASE_WORDS.entrySet()) {
			for (String word : e.getValue()) {
				if (containsWord(text, word)) {
					priorities.add(e.getKey());
					break;
				}
			}
		}
		if (!priorities.isEmpty()) {
			update.put(Requirements.PRIORITIES, priorities);
		}
		if (!ignoredDims.isEmpty()) {
			update.put(Requirements.IGNORED, new ArrayList<>(ignoredDims));
		}
		return new ParseResult(update, clear, reset);
	}

	// ------------------------------------------------------------------ pieces

	private void parseClears(String text, Set<String> clear) {
		if (Pattern.compile("\\b(no budget|any budget|forget (?:the )?budget|ignore (?:the )?budget|remove (?:the )?budget|without budget)\\b")
				.matcher(text).find()) {
			clear.add(Requirements.BUDGET);
		}
		if (Pattern.compile("\\b(any brand|all brands|forget (?:the )?brand|ignore (?:the )?brand|remove (?:the )?brand)\\b")
				.matcher(text).find()) {
			clear.add(Requirements.BRANDS);
			clear.add(Requirements.EXCLUDE_BRANDS);
		}
	}

	/** Records "don't care about X" and removes that phrase so X is not also read as a priority. */
	private String extractIgnored(String text, Set<String> ignored) {
		String result = text;
		for (Pattern p : new Pattern[] { IGNORE_A, IGNORE_B }) {
			Matcher m = p.matcher(result);
			StringBuffer sb = new StringBuffer();
			while (m.find()) {
				switch (m.group(1)) {
					case "camera":
						ignored.add(Dimension.CAMERA.key());
						break;
					case "battery":
						ignored.add(Dimension.BATTERY.key());
						ignored.add(Dimension.CHARGING.key());
						break;
					case "display":
					case "screen":
						ignored.add(Dimension.DISPLAY.key());
						break;
					default:
						ignored.add(Dimension.PERFORMANCE.key());
				}
				m.appendReplacement(sb, " ");
			}
			m.appendTail(sb);
			result = sb.toString();
		}
		return result;
	}

	private void parseBudget(String text, Map<String, Object> update) {
		for (Pattern p : new Pattern[] { BUDGET_A, BUDGET_B, BUDGET_C }) {
			Matcher m = p.matcher(text);
			while (m.find()) {
				String after = text.substring(m.end()).stripLeading();
				if (after.matches("^(gb|tb|mah|hz|mp|w\\b|watt|inch|\").*")) {
					continue; // "under 8 gb" is not a budget
				}
				double value = amount(m.group(1), m.groupCount() >= 2 ? m.group(2) : null);
				if (value >= 1000) {
					update.put(Requirements.BUDGET, value);
					return;
				}
			}
		}
	}

	static double amount(String number, String suffix) {
		double v;
		try {
			v = Double.parseDouble(number.replace(",", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
		if (suffix == null) {
			return v;
		}
		if (suffix.startsWith("k") || suffix.startsWith("thousand")) {
			return v * 1000;
		}
		return v * 100000; // lakh / lac / l
	}

	private static final Pattern MEM_TOKEN = Pattern.compile(
			"(\\d+(?:\\.\\d+)?)\\s*(gb|tb)\\b|\\b(ram|storage|rom|memory)\\b");

	/**
	 * Reads memory numbers and the words RAM/storage in order. If the first token is a number the phrasing is
	 * "12GB RAM 1TB storage" (a word belongs to the number before it); if it is a word the phrasing is
	 * "RAM 12GB storage 1TB" (a word belongs to the number after it). Numbers left without a word are
	 * classified by size: up to 24 GB is RAM, 32 GB and above is storage.
	 */
	private void parseMemory(String text, Map<String, Object> update) {
		Matcher m = MEM_TOKEN.matcher(text);
		Boolean numberFirst = null;
		Double pendingNumber = null;
		String pendingWord = null;
		while (m.find()) {
			boolean isNumber = m.group(1) != null;
			if (numberFirst == null) {
				numberFirst = isNumber;
			}
			if (isNumber) {
				double gb = toGb(m.group(1), m.group(2));
				if (!numberFirst && pendingWord != null) {
					assignMemory(update, pendingWord, gb);
					pendingWord = null;
				} else {
					if (pendingNumber != null) {
						assignBare(update, pendingNumber);
					}
					pendingNumber = numberFirst ? gb : null;
					if (!numberFirst) {
						assignBare(update, gb);
					}
				}
			} else if (numberFirst) {
				if (pendingNumber != null) {
					assignMemory(update, m.group(3), pendingNumber);
					pendingNumber = null;
				}
			} else {
				pendingWord = m.group(3);
			}
		}
		if (pendingNumber != null) {
			assignBare(update, pendingNumber);
		}
	}

	private static void assignBare(Map<String, Object> update, double gb) {
		if (gb <= RAM_BARE_NUMBER_MAX_GB && !update.containsKey(Requirements.RAM)) {
			update.put(Requirements.RAM, gb);
		} else if (gb >= STORAGE_BARE_NUMBER_MIN_GB && !update.containsKey(Requirements.STORAGE)) {
			update.put(Requirements.STORAGE, gb);
		}
	}

	private static double toGb(String number, String unit) {
		return Double.parseDouble(number) * ("tb".equals(unit) ? 1024 : 1);
	}

	private static void assignMemory(Map<String, Object> update, String word, double gb) {
		update.put("ram".equals(word) ? Requirements.RAM : Requirements.STORAGE, gb);
	}

	private void parseNumbers(String text, Map<String, Object> update) {
		Matcher m = MAH.matcher(text);
		if (m.find()) {
			update.put(Requirements.BATTERY, Double.parseDouble(m.group(1)));
		}
		if (text.contains("charg")) {
			Matcher w = WATTS.matcher(text);
			while (w.find()) {
				boolean wirelessContext = text.substring(w.end(), Math.min(text.length(), w.end() + 12)).contains("wireless");
				if (!wirelessContext) {
					update.put(Requirements.WIRED, Double.parseDouble(w.group(1)));
					break;
				}
			}
			if (!update.containsKey(Requirements.WIRED) && containsAny(text, "fast charging", "fast charge", "quick charging", "super fast")) {
				update.put(Requirements.WIRED, FAST_CHARGING_W);
			}
		}
		Matcher hz = HZ.matcher(text);
		if (hz.find()) {
			update.put(Requirements.REFRESH, Double.parseDouble(hz.group(1)));
		} else if (containsAny(text, "high refresh", "smooth display", "smooth screen")) {
			update.put(Requirements.REFRESH, HIGH_REFRESH_HZ);
		}
		Matcher mp = MP.matcher(text);
		if (mp.find()) {
			update.put(Requirements.CAMERA_MP, Double.parseDouble(mp.group(1)));
		}
		Matcher in = INCHES.matcher(text);
		if (in.find()) {
			String before = text.substring(Math.max(0, in.start() - 14), in.start());
			boolean max = before.matches(".*(under|below|max|maximum|at most|less than|smaller than)\\s*$");
			update.put(max ? Requirements.SIZE_MAX : Requirements.SIZE_MIN, Double.parseDouble(in.group(1)));
		}
	}

	private void parseFlags(String text, Map<String, Object> update) {
		flag(text, update, Requirements.FIVE_G, "\\b5g\\b");
		flag(text, update, Requirements.NFC, "\\bnfc\\b");
		flag(text, update, Requirements.WIRELESS, "wireless (?:charging|charger)|qi charging|\\bwireless\\b(?! earbuds)");
		flag(text, update, Requirements.REVERSE, "reverse (?:wireless )?charging");
		flag(text, update, Requirements.WATER, "waterproof|water[- ]resist\\w*|\\bip[5-6]\\d\\b|dust and water|splash[- ]proof");
		if (flagAt(text, "\\bamoled\\b")) {
			update.put(Requirements.DISPLAY_TYPE, "amoled");
		} else if (flagAt(text, "\\boled\\b")) {
			update.put(Requirements.DISPLAY_TYPE, "oled");
		}
	}

	private void flag(String text, Map<String, Object> update, String key, String regex) {
		if (flagAt(text, regex)) {
			update.put(key, Boolean.TRUE);
		}
	}

	/** True if the pattern occurs and is not preceded by a negation like "no" or "without". */
	private boolean flagAt(String text, String regex) {
		Matcher m = Pattern.compile(regex).matcher(text);
		while (m.find()) {
			String before = text.substring(Math.max(0, m.start() - 22), m.start());
			if (!Pattern.compile("\\b" + NEG + "\\b[^.,;]*$").matcher(before).find()) {
				return true;
			}
		}
		return false;
	}

	private void parseBrandsAndOs(String text, String original, Map<String, Object> update) {
		List<String> include = new ArrayList<>();
		List<String> exclude = new ArrayList<>();
		for (String brand : brandNames.get()) {
			if (brand == null || brand.length() < 2) {
				continue;
			}
			Matcher m = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(brand.toLowerCase(Locale.ROOT)) + "(?![a-z0-9])").matcher(text);
			if (!m.find()) {
				continue;
			}
			if (brand.length() == 2 && !Pattern.compile("\\b" + Pattern.quote(brand) + "\\b").matcher(original).find()) {
				continue; // short names such as LG must be written in capitals to count
			}
			String before = text.substring(Math.max(0, m.start() - 18), m.start());
			if (Pattern.compile("\\b" + NEG + "\\b[^.,;]*$").matcher(before).find()) {
				exclude.add(brand);
			} else {
				include.add(brand);
			}
		}
		if (containsWord(text, "iphone") || containsWord(text, "iphones")) {
			for (String b : brandNames.get()) {
				if ("apple".equalsIgnoreCase(b) && !include.contains(b)) {
					include.add(b);
				}
			}
		}
		if (!include.isEmpty()) {
			update.put(Requirements.BRANDS, include);
		}
		if (!exclude.isEmpty()) {
			update.put(Requirements.EXCLUDE_BRANDS, exclude);
		}
		if (flagAt(text, "\\bios\\b")) {
			update.put(Requirements.OS, "ios");
		} else if (flagAt(text, "\\bandroid\\b")) {
			update.put(Requirements.OS, "android");
		}
	}

	private void parseYear(String text, Map<String, Object> update) {
		Matcher m = YEAR_AFTER.matcher(text);
		if (m.find()) {
			update.put(Requirements.YEAR, Double.parseDouble(m.group(1)));
		} else if (containsAny(text, "latest", "newest", "most recent", "recently launched", "new launch")) {
			update.put(Requirements.YEAR, (double) (newestYear.getAsInt() - 1));
		}
	}

	private void parseCompactLarge(String text, Map<String, Object> update) {
		if (containsAny(text, "compact", "small phone", "one hand", "one-hand", "pocketable")) {
			update.putIfAbsent(Requirements.SIZE_MAX, COMPACT_MAX_INCHES);
		} else if (containsAny(text, "tablet", "ipad")) {
			update.putIfAbsent(Requirements.SIZE_MIN, TABLET_MIN_INCHES);
		} else if (containsAny(text, "big screen", "large screen", "large display", "big display", "big phone")) {
			update.putIfAbsent(Requirements.SIZE_MIN, LARGE_MIN_INCHES);
		}
	}

	private static boolean containsAny(String text, String... needles) {
		for (String n : needles) {
			if (text.contains(n)) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsWord(String text, String word) {
		return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(word) + "(?![a-z0-9])").matcher(text).find();
	}
}

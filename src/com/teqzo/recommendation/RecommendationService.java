package com.teqzo.recommendation;

import com.teqzo.domain.DeviceRow;
import com.teqzo.dto.BrandDto;
import com.teqzo.dto.RecommendationResponse;
import com.teqzo.dto.RecommendationResponse.Check;
import com.teqzo.dto.RecommendationResponse.Relaxation;
import com.teqzo.dto.RecommendationResponse.RecommendedDevice;
import com.teqzo.dto.RecommendationResponse.ScorePart;
import com.teqzo.filter.DeviceFilter;
import com.teqzo.repository.DeviceRepository;
import com.teqzo.service.BrandService;
import com.teqzo.service.DeviceMapper;
import com.teqzo.service.FacetsService;
import com.teqzo.service.PricePolicy;
import com.teqzo.util.Money;
import com.teqzo.util.Log;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Eligibility and ranking. The database decides which devices qualify; the scoring engine orders them.
 * Language models are never involved here.
 */
public class RecommendationService {

	private static final Log log = Log.of(RecommendationService.class);

	public static final int CANDIDATE_LIMIT = 3000;
	public static final int DEFAULT_LIMIT = 5;
	public static final int MAX_LIMIT = 10;
	static final int FALLBACK_NEWEST_YEAR = 2022;
	static final int HIGHLIGHTS = 3;
	/**
	 * The dataset mixes phones, tablets and smartwatches and has no device-type column. Unless the user sets
	 * a display size (or asks for a tablet), recommendations are limited to phone-sized displays.
	 */
	static final double PHONE_MIN_INCHES = 3.0;
	static final double PHONE_MAX_INCHES = 7.2;

	private final DeviceRepository devices;
	private final BrandService brands;
	private final FacetsService facets;
	private final PricePolicy pricePolicy;

	public RecommendationService(DeviceRepository devices, BrandService brands, FacetsService facets,
			PricePolicy pricePolicy) {
		this.devices = devices;
		this.brands = brands;
		this.facets = facets;
		this.pricePolicy = pricePolicy;
	}

	public RecommendationResponse recommend(Map<String, Object> requirementsMap, Integer limit) {
		return recommend(Requirements.fromMap(requirementsMap), limit == null ? DEFAULT_LIMIT : limit);
	}

	public RecommendationResponse recommend(Requirements input, int requestedLimit) {
		int limit = Math.max(1, Math.min(MAX_LIMIT, requestedLimit));
		RequirementsValidator.Validated validated = RequirementsValidator.validate(input);
		List<String> notes = new ArrayList<>(validated.notes());
		Requirements original = validated.requirements();
		resolveBrands(original, notes);

		Double fx = pricePolicy.eurToInr();
		Requirements working = original.copy();
		boolean includeUnknownPrice = false;
		List<Relaxation> relaxed = new ArrayList<>();

		List<DeviceRow> candidates = fetch(working, includeUnknownPrice, fx);
		if (candidates.isEmpty() && original.budgetMaxInr != null) {
			includeUnknownPrice = true;
			relaxed.add(new Relaxation(Requirements.BUDGET, "Included devices whose price is not listed in the dataset,"
					+ " because too few devices have a price that can be checked against your budget."));
			candidates = fetch(working, true, fx);
		}
		if (candidates.isEmpty()) {
			for (Step step : steps()) {
				if (!step.applies.test(working)) {
					continue;
				}
				step.apply.accept(working);
				relaxed.add(new Relaxation(step.key, step.description));
				candidates = fetch(working, includeUnknownPrice, fx);
				if (!candidates.isEmpty()) {
					break;
				}
			}
		}

		boolean exact = relaxed.isEmpty();
		Weights weights = Weights.forRequirements(original.priorities, original.ignored);
		int newestYear = newestYear(candidates);

		List<Scored> scored = new ArrayList<>();
		for (DeviceRow row : candidates) {
			List<Check> checks = RequirementChecker.check(original, row, fx);
			if (exact && checks.stream().anyMatch(c -> RequirementChecker.NOT_MET.equals(c.status()))) {
				log.warn("Device {} passed the SQL filter but failed an in-memory check; excluded", row.id);
				continue;
			}
			scored.add(new Scored(row, ScoringEngine.score(row, weights, newestYear), checks));
		}
		scored.sort(Comparator
				.comparingDouble((Scored s) -> s.result.total()).reversed()
				.thenComparing(s -> s.row.announcedYear == null ? 0 : -s.row.announcedYear)
				.thenComparing(s -> s.row.name == null ? "" : s.row.name));

		List<RecommendedDevice> top = new ArrayList<>();
		for (int i = 0; i < Math.min(limit, scored.size()); i++) {
			top.add(toRecommended(i + 1, scored.get(i), original, weights, newestYear, fx));
		}
		Map<String, Double> weightMap = new LinkedHashMap<>();
		for (Dimension d : Dimension.values()) {
			weightMap.put(d.key(), Math.round(weights.of(d) * 1000.0) / 1000.0);
		}
		return new RecommendationResponse(original.toMap(), notes, exact, relaxed, scored.size(),
				candidates.size() >= CANDIDATE_LIMIT, top, weightMap);
	}

	// ------------------------------------------------------------------ candidates

	private List<DeviceRow> fetch(Requirements r, boolean includeUnknownPrice, Double fx) {
		return devices.findCandidates(toFilter(r, includeUnknownPrice), fx, CANDIDATE_LIMIT);
	}

	/** Maps hard requirements to a database filter. Brand names must already be resolved to ids. */
	DeviceFilter toFilter(Requirements r, boolean includeUnknownPrice) {
		DeviceFilter f = new DeviceFilter();
		f.ramMinGb = r.ramMinGb;
		f.storageMinGb = r.storageMinGb;
		f.batteryMinMah = r.batteryMinMah;
		f.wiredChargingMinW = r.wiredChargingMinW;
		f.wirelessCharging = r.requiresWirelessCharging;
		f.reverseCharging = r.requiresReverseCharging;
		f.has5g = r.requires5g;
		f.nfc = r.requiresNfc;
		f.ipRated = r.requiresWaterResistance;
		f.refreshRateMinHz = r.refreshRateMinHz;
		boolean sizeChosen = r.displaySizeMinInches != null || r.displaySizeMaxInches != null;
		f.displaySizeMinInches = sizeChosen ? r.displaySizeMinInches : Double.valueOf(PHONE_MIN_INCHES);
		f.displaySizeMaxInches = sizeChosen ? r.displaySizeMaxInches : Double.valueOf(PHONE_MAX_INCHES);
		f.displayType = r.displayType;
		f.mainCameraMinMp = r.mainCameraMinMp;
		f.weightMaxG = r.maxWeightG;
		f.os = r.os;
		f.brandIds = resolvedBrandIds(r.brands);
		f.excludeBrandIds = resolvedBrandIds(r.excludeBrands);
		f.priceMaxInr = r.budgetMaxInr;
		f.includeUnknownPrice = includeUnknownPrice;
		f.releasedFromYear = r.minReleaseYear;
		f.includeListedYear = true; // "recent" also covers phones listed for sale in 2026 whose launch date is unknown
		f.excludeCancelled = true;
		f.onlyWithSpecifications = true;
		f.requireOsListed = true;
		f.sort = com.teqzo.filter.SortOption.NEWEST;
		return f;
	}

	private List<String> resolvedBrandIds(List<String> names) {
		if (names == null || names.isEmpty()) {
			return null;
		}
		List<String> ids = new ArrayList<>();
		for (String n : names) {
			brands.findByName(n).map(BrandDto::id).ifPresent(ids::add);
		}
		return ids.isEmpty() ? List.of("__no_such_brand__") : ids;
	}

	/** Keeps only brands that exist in the dataset (canonical spelling); unknown ones are reported. */
	private void resolveBrands(Requirements r, List<String> notes) {
		r.brands = canonical(r.brands, notes);
		r.excludeBrands = canonical(r.excludeBrands, notes);
	}

	private List<String> canonical(List<String> names, List<String> notes) {
		List<String> out = new ArrayList<>();
		for (String n : names) {
			Optional<BrandDto> b = brands.findByName(n);
			if (b.isPresent()) {
				out.add(b.get().name());
			} else {
				notes.add("The dataset has no brand called \"" + n + "\", so it was ignored.");
			}
		}
		return out;
	}

	private int newestYear(List<DeviceRow> candidates) {
		Integer fromFacets = facets.get().maxReleaseYear();
		if (fromFacets != null) {
			return fromFacets;
		}
		return candidates.stream().map(c -> c.announcedYear).filter(y -> y != null).max(Integer::compare)
				.orElse(FALLBACK_NEWEST_YEAR);
	}

	// ------------------------------------------------------------------ relaxation

	private record Step(String key, String description, java.util.function.Predicate<Requirements> applies,
			Consumer<Requirements> apply) {
	}

	/** Order in which requirements are dropped when nothing matches: least essential first. */
	static List<Step> steps() {
		List<Step> s = new ArrayList<>();
		s.add(new Step(Requirements.NFC, "Dropped the NFC requirement.", r -> r.requiresNfc != null,
				r -> r.requiresNfc = null));
		s.add(new Step(Requirements.WATER, "Dropped the water-resistance requirement.",
				r -> r.requiresWaterResistance != null, r -> r.requiresWaterResistance = null));
		s.add(new Step(Requirements.WIRELESS, "Dropped the wireless-charging requirement.",
				r -> r.requiresWirelessCharging != null, r -> r.requiresWirelessCharging = null));
		s.add(new Step(Requirements.REVERSE, "Dropped the reverse-charging requirement.",
				r -> r.requiresReverseCharging != null, r -> r.requiresReverseCharging = null));
		s.add(new Step(Requirements.DISPLAY_TYPE, "Dropped the display-type requirement.",
				r -> r.displayType != null, r -> r.displayType = null));
		s.add(new Step(Requirements.REFRESH, "Dropped the minimum refresh rate.",
				r -> r.refreshRateMinHz != null, r -> r.refreshRateMinHz = null));
		s.add(new Step(Requirements.CAMERA_MP, "Dropped the minimum main-camera resolution.",
				r -> r.mainCameraMinMp != null, r -> r.mainCameraMinMp = null));
		s.add(new Step(Requirements.STORAGE, "Dropped the minimum storage.",
				r -> r.storageMinGb != null, r -> r.storageMinGb = null));
		s.add(new Step(Requirements.WIRED, "Dropped the minimum wired-charging speed.",
				r -> r.wiredChargingMinW != null, r -> r.wiredChargingMinW = null));
		s.add(new Step(Requirements.FIVE_G, "Dropped the 5G requirement.",
				r -> r.requires5g != null, r -> r.requires5g = null));
		s.add(new Step(Requirements.YEAR, "Dropped the minimum release year.",
				r -> r.minReleaseYear != null, r -> r.minReleaseYear = null));
		s.add(new Step(Requirements.WEIGHT, "Dropped the maximum weight.",
				r -> r.maxWeightG != null, r -> r.maxWeightG = null));
		s.add(new Step(Requirements.SIZE_MAX, "Dropped the maximum display size.",
				r -> r.displaySizeMaxInches != null, r -> r.displaySizeMaxInches = null));
		s.add(new Step(Requirements.SIZE_MIN, "Dropped the minimum display size.",
				r -> r.displaySizeMinInches != null, r -> r.displaySizeMinInches = null));
		s.add(new Step(Requirements.RAM, "Dropped the minimum RAM.",
				r -> r.ramMinGb != null, r -> r.ramMinGb = null));
		s.add(new Step(Requirements.BATTERY, "Dropped the minimum battery capacity.",
				r -> r.batteryMinMah != null, r -> r.batteryMinMah = null));
		return s;
	}

	// ------------------------------------------------------------------ presentation

	private record Scored(DeviceRow row, ScoringEngine.Result result, List<Check> checks) {
	}

	private RecommendedDevice toRecommended(int rank, Scored s, Requirements req, Weights weights, int newestYear,
			Double fx) {
		List<ScorePart> parts = new ArrayList<>();
		for (ScoreComponent c : s.result.components()) {
			parts.add(new ScorePart(c.dimension().key(), c.dimension().label(),
					c.score() == null ? null : round(c.score(), 3), round(c.weight(), 3), round(c.points(), 1),
					c.detail()));
		}
		List<String> highlights = s.result.components().stream()
				.filter(c -> c.score() != null && c.weight() > 0)
				.sorted(Comparator.comparingDouble(ScoreComponent::points).reversed())
				.limit(HIGHLIGHTS)
				.map(c -> c.dimension().label() + ": " + c.detail())
				.toList();
		return new RecommendedDevice(rank, DeviceMapper.toSummary(s.row, fx), round(s.result.total(), 1), parts,
				s.checks, highlights, caveats(s, req, weights, newestYear));
	}

	private List<String> caveats(Scored s, Requirements req, Weights weights, int newestYear) {
		List<String> out = new ArrayList<>();
		DeviceRow d = s.row;
		for (Check c : s.checks) {
			if (RequirementChecker.NOT_MET.equals(c.status())) {
				out.add("Does not meet: " + c.label() + " (needs " + c.required() + ", listed: " + c.actual() + ").");
			} else if (RequirementChecker.UNKNOWN.equals(c.status())) {
				out.add(c.label() + " could not be verified (" + c.actual() + ").");
			}
		}
		if ("CONDITIONAL".equals(d.wirelessChargingStatus)) {
			out.add("Wireless charging is listed as optional or market dependent.");
		}
		if (d.announcedYear != null && d.announcedYear <= newestYear - 3) {
			out.add("Announced in " + d.announcedYear + "; newer models may exist outside this dataset.");
		}
		if (d.limitedSpecs()) {
			out.add(limitedSpecNote(d));
		}
		if (d.statusCode != null && d.statusCode.equals("DISCONTINUED")) {
			out.add("Listed as discontinued.");
		}
		if (weights.of(Dimension.CAMERA) >= 0.15) {
			out.add("Camera score uses listed megapixels only; megapixels do not measure image quality.");
		}
		if (weights.of(Dimension.PERFORMANCE) >= 0.2) {
			out.add("Performance is estimated from listed RAM, CPU clock and core count, not from a benchmark.");
		}
		return out;
	}

	private static double round(double v, int places) {
		double f = Math.pow(10, places);
		return Math.round(v * f) / f;
	}

	/** Short text such as "8 GB RAM, 5G" used in prompts and templated replies. */
	public static String describeRequirements(Requirements r) {
		List<String> p = new ArrayList<>();
		if (r.ramMinGb != null) p.add("at least " + ScoringEngine.num(r.ramMinGb) + " GB RAM");
		if (r.storageMinGb != null) p.add("at least " + ScoringEngine.num(r.storageMinGb) + " GB storage");
		if (r.batteryMinMah != null) p.add("battery of " + ScoringEngine.num(r.batteryMinMah) + " mAh or more");
		if (r.wiredChargingMinW != null) p.add("wired charging of " + ScoringEngine.num(r.wiredChargingMinW) + " W or more");
		if (Boolean.TRUE.equals(r.requiresWirelessCharging)) p.add("wireless charging");
		if (Boolean.TRUE.equals(r.requiresReverseCharging)) p.add("reverse charging");
		if (Boolean.TRUE.equals(r.requires5g)) p.add("5G");
		if (Boolean.TRUE.equals(r.requiresNfc)) p.add("NFC");
		if (Boolean.TRUE.equals(r.requiresWaterResistance)) p.add("an IP water-resistance rating");
		if (r.refreshRateMinHz != null) p.add("a " + ScoringEngine.num(r.refreshRateMinHz) + " Hz or faster display");
		if (r.displayType != null) p.add(r.displayType.toUpperCase(Locale.ROOT) + " display");
		if (r.mainCameraMinMp != null) p.add("a main camera of " + ScoringEngine.num(r.mainCameraMinMp) + " MP or more");
		if (r.os != null) p.add(r.os.equals("ios") ? "iOS" : "Android");
		if (!r.brands.isEmpty()) p.add("brand " + String.join(" or ", r.brands));
		if (!r.excludeBrands.isEmpty()) p.add("not " + String.join(", ", r.excludeBrands));
		if (r.budgetMaxInr != null) p.add("a budget up to " + Money.inr(r.budgetMaxInr));
		for (UseCase u : r.priorities) p.add("a focus on " + u.key());
		return String.join(", ", p);
	}

	/** Names only the fields this device really lacks, so the note is never wrong for richer datasets. */
	static String limitedSpecNote(DeviceRow d) {
		java.util.List<String> missing = new java.util.ArrayList<>();
		if (d.wiredChargingW == null && d.chargingRaw == null) {
			missing.add("charging speed");
		}
		if (d.weightG == null) {
			missing.add("weight");
		}
		if (d.nfcSupported == null && d.nfcRaw == null) {
			missing.add("NFC");
		}
		if (d.refreshRateHz == null) {
			missing.add("refresh rate");
		}
		return missing.isEmpty() ? "Added from a smaller dataset; some details may be missing."
				: "Added from a smaller dataset with fewer fields: " + String.join(", ", missing) + " not listed.";
	}
}

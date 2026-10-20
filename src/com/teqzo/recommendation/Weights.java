package com.teqzo.recommendation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Per-dimension weights (summing to 1) derived from the user's priorities.
 *
 * <p>Each use case has a fixed profile. With several priorities the profiles are averaged, then blended
 * 75/25 with the balanced profile. Ignored dimensions get weight 0 and the rest is renormalised.
 */
public final class Weights {

	public static final double PRIORITY_SHARE = 0.75;

	private static final Map<UseCase, double[]> PROFILES = new EnumMap<>(UseCase.class);

	// Order: PERFORMANCE, MEMORY, BATTERY, CHARGING, CAMERA, DISPLAY, CONNECTIVITY, FRESHNESS (percent)
	static {
		PROFILES.put(UseCase.BALANCED, new double[] { 20, 10, 15, 10, 15, 15, 10, 5 });
		PROFILES.put(UseCase.GAMING, new double[] { 33, 12, 10, 5, 2, 25, 3, 10 });
		PROFILES.put(UseCase.CAMERA, new double[] { 10, 5, 10, 3, 47, 10, 5, 10 });
		PROFILES.put(UseCase.BATTERY, new double[] { 5, 3, 47, 25, 3, 4, 3, 10 });
		PROFILES.put(UseCase.DISPLAY, new double[] { 10, 3, 8, 2, 7, 55, 5, 10 });
		PROFILES.put(UseCase.PERFORMANCE, new double[] { 45, 18, 7, 3, 3, 8, 6, 10 });
	}

	private final double[] values;

	private Weights(double[] values) {
		this.values = values;
	}

	public double of(Dimension d) {
		return values[d.ordinal()];
	}

	public static Weights forRequirements(List<UseCase> priorities, List<Dimension> ignored) {
		double[] mix = new double[Dimension.values().length];
		double[] balanced = PROFILES.get(UseCase.BALANCED);
		if (priorities == null || priorities.isEmpty()) {
			System.arraycopy(balanced, 0, mix, 0, mix.length);
		} else {
			for (int i = 0; i < mix.length; i++) {
				double avg = 0;
				for (UseCase u : priorities) {
					avg += PROFILES.get(u)[i];
				}
				avg /= priorities.size();
				mix[i] = PRIORITY_SHARE * avg + (1 - PRIORITY_SHARE) * balanced[i];
			}
		}
		if (ignored != null) {
			for (Dimension d : ignored) {
				mix[d.ordinal()] = 0;
			}
		}
		double sum = 0;
		for (double v : mix) {
			sum += v;
		}
		double[] normalised = new double[mix.length];
		for (int i = 0; i < mix.length; i++) {
			normalised[i] = sum == 0 ? 0 : mix[i] / sum;
		}
		return new Weights(normalised);
	}
}

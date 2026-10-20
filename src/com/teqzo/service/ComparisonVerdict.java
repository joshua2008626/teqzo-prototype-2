package com.teqzo.service;

import com.teqzo.dto.ComparisonResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * A plain-words summary of a comparison: in how many of the rows with a clear "higher/lower is better" direction
 * each device leads. It uses the same leaders as the table, so it never claims more than the table shows.
 */
public record ComparisonVerdict(List<String> names, List<List<String>> leadsIn, int comparableRows) {

	public static ComparisonVerdict of(ComparisonResponse response) {
		List<String> names = response.devices().stream()
				.map(d -> (d.brandName() == null ? "" : d.brandName() + " ") + d.name()).toList();
		List<List<String>> leads = new ArrayList<>();
		for (int i = 0; i < names.size(); i++) {
			leads.add(new ArrayList<>());
		}
		int comparable = 0;
		for (ComparisonResponse.ComparisonGroup group : response.groups()) {
			for (ComparisonResponse.ComparisonRow row : group.rows()) {
				if (row.leaders().isEmpty()) {
					continue;
				}
				comparable++;
				for (int index : row.leaders()) {
					if (index >= 0 && index < leads.size()) {
						leads.get(index).add(row.label());
					}
				}
			}
		}
		return new ComparisonVerdict(names, leads, comparable);
	}

	/** Index of the device that leads in the most rows, or -1 when there are no comparable rows or a tie. */
	public int winner() {
		int best = -1;
		int bestCount = 0;
		boolean tie = false;
		for (int i = 0; i < leadsIn.size(); i++) {
			int n = leadsIn.get(i).size();
			if (n > bestCount) {
				best = i;
				bestCount = n;
				tie = false;
			} else if (n == bestCount && n > 0) {
				tie = true;
			}
		}
		return tie ? -1 : best;
	}

	public List<String> lines() {
		List<String> out = new ArrayList<>();
		if (comparableRows == 0) {
			out.add("Not enough listed values to say which device is ahead.");
			return out;
		}
		for (int i = 0; i < names.size(); i++) {
			List<String> rows = leadsIn.get(i);
			out.add(names.get(i) + " leads in " + rows.size() + " of " + comparableRows + " comparable rows"
					+ (rows.isEmpty() ? "." : ": " + String.join(", ", rows) + "."));
		}
		int w = winner();
		out.add(w >= 0 ? "Most leads: " + names.get(w) + ". This counts rows; it does not say which phone suits you."
				: "No single device is clearly ahead; it depends on what matters to you.");
		return out;
	}
}

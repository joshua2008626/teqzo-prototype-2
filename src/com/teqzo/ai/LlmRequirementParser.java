package com.teqzo.ai;

import com.teqzo.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Asks a language model to translate the user's message into structured requirements. The model only
 * returns JSON about what the user wants; it never sees or produces device data. Whatever it returns is
 * validated again by the backend before use.
 */
public class LlmRequirementParser implements RequirementParser {

	static final int HISTORY_TURNS = 6;

	static final String SYSTEM_PROMPT = String.join("\n",
			"You convert a shopper's message about phones/tablets into structured requirements.",
			"Reply with ONE JSON object and nothing else (no prose, no code fences).",
			"",
			"JSON shape: {\"updates\": {...}, \"clear\": [...], \"reset\": false}",
			"- updates: only the requirements the user's LATEST message states or changes. Omit everything else.",
			"- clear: keys the user asks to forget (e.g. \"budget_max_inr\").",
			"- reset: true only if the user wants to start over.",
			"",
			"Allowed keys inside updates (use exactly these names; omit unknown values, never guess):",
			"  ram_min_gb (number), storage_min_gb (number), battery_min_mah (number),",
			"  wired_charging_min_w (number), requires_wireless_charging (true), requires_reverse_charging (true),",
			"  requires_5g (true), requires_nfc (true), requires_water_resistance (true),",
			"  refresh_rate_min_hz (number), display_size_min_inches (number), display_size_max_inches (number),",
			"  display_type (one of: amoled, oled, ips, lcd, ltpo, tft), main_camera_min_mp (number),",
			"  max_weight_g (number), os (android | ios), brands (array of brand names),",
			"  exclude_brands (array of brand names), budget_max_inr (number, Indian rupees),",
			"  min_release_year (number),",
			"  priorities (array from: gaming, camera, battery, display, performance),",
			"  ignored (array from: performance, memory, battery, charging, camera, display, connectivity, recency).",
			"",
			"Rules:",
			"- Extract only what the user said. Do not invent numbers, brands or devices.",
			"- Indian phrasing: \"30k\" = 30000, \"1 lakh\" = 100000, \"under 30000\" = budget_max_inr 30000.",
			"- \"I don't care about the camera\" means ignored: [\"camera\"], not a camera priority.",
			"- \"good camera\", \"best battery\", \"for gaming\" are priorities, not numeric requirements.",
			"- Do NOT recommend or name any phone model. Another system looks devices up.",
			"- The CURRENT requirements are provided so you can tell what is new. Do not repeat them.");

	private final AiProvider provider;

	public LlmRequirementParser(AiProvider provider) {
		this.provider = provider;
	}

	@Override
	public ParseResult parse(String message, Map<String, Object> current, List<ChatTurn> history)
			throws AiProviderException {
		List<ChatTurn> turns = new ArrayList<>();
		if (history != null) {
			int from = Math.max(0, history.size() - HISTORY_TURNS);
			for (ChatTurn t : history.subList(from, history.size())) {
				if (t != null && t.content() != null && ("user".equals(t.role()) || "assistant".equals(t.role()))) {
					turns.add(new ChatTurn(t.role(), t.content()));
				}
			}
		}
		String latest = "CURRENT requirements: " + Json.stringify(current == null ? Map.of() : current)
				+ "\nLATEST user message: " + message;
		turns.add(new ChatTurn("user", latest));
		turns = normaliseTurns(turns);

		String raw = provider.complete(SYSTEM_PROMPT, turns);
		Map<String, Object> parsed;
		try {
			parsed = Json.parseObjectLenient(raw);
		} catch (Json.JsonException e) {
			throw new AiProviderException("The AI answer was not valid JSON.", e);
		}
		Map<String, Object> updates = new LinkedHashMap<>();
		Map<String, Object> fromModel = Json.asObject(parsed.get("updates"));
		if (fromModel != null) {
			updates.putAll(fromModel);
		}
		Set<String> clear = new LinkedHashSet<>();
		List<Object> clearList = Json.asArray(parsed.get("clear"));
		if (clearList != null) {
			for (Object o : clearList) {
				if (o instanceof String) {
					clear.add((String) o);
				}
			}
		}
		boolean reset = Boolean.TRUE.equals(parsed.get("reset"));
		return new ParseResult(updates, clear, reset);
	}

	/** Providers require the conversation to start with a user turn and alternate roles. */
	static List<ChatTurn> normaliseTurns(List<ChatTurn> turns) {
		List<ChatTurn> out = new ArrayList<>();
		for (ChatTurn t : turns) {
			if (!out.isEmpty() && out.get(out.size() - 1).role().equals(t.role())) {
				ChatTurn last = out.remove(out.size() - 1);
				out.add(new ChatTurn(t.role(), last.content() + "\n" + t.content()));
			} else if (out.isEmpty() && !"user".equals(t.role())) {
				continue;
			} else {
				out.add(t);
			}
		}
		return out;
	}
}

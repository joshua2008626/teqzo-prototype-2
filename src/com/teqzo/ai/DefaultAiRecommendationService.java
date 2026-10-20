package com.teqzo.ai;

import com.teqzo.dto.RecommendationResponse;
import com.teqzo.dto.RecommendationResponse.RecommendedDevice;
import com.teqzo.recommendation.RecommendationService;
import com.teqzo.recommendation.Requirements;
import com.teqzo.util.Json;
import com.teqzo.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pipeline: user text, requirement parser (LLM or rules), merge with earlier requirements, backend
 * validation, database filtering and ranking, then a reply. The reply's device list and every number
 * in it come from the database; an LLM may only word the summary and is checked by {@link ExplanationGuard}.
 */
public class DefaultAiRecommendationService implements AiRecommendationService {

	private static final Log log = Log.of(DefaultAiRecommendationService.class);

	static final int RESULT_COUNT = 5;
	static final String ASKED_BUDGET = "budget";
	static final String ASKED_PRIORITY = "priority";

	static final String SUMMARY_PROMPT = String.join("\n",
			"You write a short, friendly summary (at most 60 words) of device recommendations.",
			"Use ONLY the facts in the JSON the user sends. Do not mention any device that is not listed there.",
			"Do not state any number that does not appear in the JSON. Do not claim any feature that is not listed.",
			"Plain sentences only: no markdown, no links, no lists.");

	private final AiSettings settings;
	private final Optional<AiProvider> provider;
	private final RequirementParser llmParser;
	private final RequirementParser ruleParser;
	private final RecommendationService recommendations;

	public DefaultAiRecommendationService(AiSettings settings, Optional<AiProvider> provider,
			RequirementParser ruleParser, RecommendationService recommendations) {
		this.settings = settings;
		this.provider = provider;
		this.llmParser = provider.<RequirementParser>map(LlmRequirementParser::new).orElse(null);
		this.ruleParser = ruleParser;
		this.recommendations = recommendations;
	}

	@Override
	public AiStatus status() {
		if (provider.isPresent()) {
			return new AiStatus(true, settings.provider(), settings.model(), "llm",
					"Language model understanding is on. Device data always comes from the database.");
		}
		return new AiStatus(false, settings.provider(), settings.model(), "rules", configurationMessage());
	}

	private String configurationMessage() {
		if (!settings.hasKey()) {
			return "The AI language model is not configured. Set the AI_API_KEY environment variable (and optionally"
					+ " AI_PROVIDER / AI_MODEL) and restart TEQZO. Until then the assistant uses its built-in"
					+ " rule-based understanding; recommendations still come from the database.";
		}
		return "An AI key is set but the provider settings are incomplete (AI_MODEL or AI_BASE_URL is missing for"
				+ " provider \"" + settings.provider() + "\"). Using the built-in rule-based understanding.";
	}

	@Override
	public ChatResponse chat(ChatRequest request) {
		String message = request.message().trim();
		Map<String, Object> previous = request.requirements() == null ? Map.of() : request.requirements();
		List<String> asked = new ArrayList<>(request.asked() == null ? List.of() : request.asked());

		String mode = "rules";
		String notice = null;
		ParseResult parsed = null;
		if (llmParser != null) {
			try {
				parsed = llmParser.parse(message, previous, request.history());
				mode = "llm";
			} catch (AiProviderException e) {
				log.warn("AI requirement parsing failed, using rules: {}", e.getMessage());
				notice = "The AI service could not be reached just now, so the built-in understanding was used.";
			}
		} else {
			notice = configurationMessage();
		}
		if (parsed == null) {
			try {
				parsed = ruleParser.parse(message, previous, request.history());
			} catch (AiProviderException e) {
				throw new IllegalStateException(e); // the rule parser never throws this
			}
		}
		if (parsed.reset()) {
			asked.clear();
		}

		Map<String, Object> merged = Requirements.merge(previous, parsed.update(), parsed.clear(), parsed.reset());
		Requirements requirements = Requirements.fromMap(merged);
		if (requirements.isEmpty()) {
			return new ChatResponse(
					"I didn't pick up a requirement yet. Tell me what matters to you, for example a budget, a brand, "
							+ "RAM or storage, battery life, camera, gaming or display.",
					"", Map.of(), asked, null, starterSuggestions(), null, mode, notice);
		}

		RecommendationResponse result = recommendations.recommend(requirements, RESULT_COUNT);
		Requirements state = Requirements.fromMap(result.requirements());
		String understood = RecommendationService.describeRequirements(state);
		String followUp = nextQuestion(state, asked);

		String reply = buildReply(result, understood, mode.equals("llm"));
		if (!result.notes().isEmpty()) {
			reply = reply + " " + String.join(" ", result.notes());
		}
		return new ChatResponse(reply, understood, result.requirements(), asked, followUp,
				suggestions(state), result, mode, notice);
	}

	// ------------------------------------------------------------------ follow-up

	static String nextQuestion(Requirements state, List<String> asked) {
		if (state.budgetMaxInr == null && !asked.contains(ASKED_BUDGET)) {
			asked.add(ASKED_BUDGET);
			return "What is your approximate budget in rupees? (Prices are listed in the dataset for only some devices.)";
		}
		if (state.priorities.isEmpty() && !asked.contains(ASKED_PRIORITY)) {
			asked.add(ASKED_PRIORITY);
			return "What matters most to you: gaming, camera, battery life or display?";
		}
		return null;
	}

	static List<String> starterSuggestions() {
		return List.of("Find me the best battery phone", "Best gaming phones", "Best camera phones",
				"Phones with wireless charging", "8GB RAM, 256GB storage and 5G", "Best display");
	}

	static List<String> suggestions(Requirements state) {
		List<String> out = new ArrayList<>();
		if (state.budgetMaxInr == null) {
			out.add("Under 30000");
		}
		if (!Boolean.TRUE.equals(state.requiresWirelessCharging)) {
			out.add("Add wireless charging");
		}
		if (!Boolean.TRUE.equals(state.requires5g)) {
			out.add("It must have 5G");
		}
		if (state.refreshRateMinHz == null) {
			out.add("120Hz display");
		}
		out.add("Start over");
		return out.size() > 5 ? out.subList(0, 5) : out;
	}

	// ------------------------------------------------------------------ reply text

	private String buildReply(RecommendationResponse r, String understood, boolean llm) {
		List<RecommendedDevice> top = r.recommendations();
		String wanted = understood.isBlank() ? "your request" : understood;
		if (top.isEmpty()) {
			return "I could not find any device in the database that matches " + wanted
					+ ", even after relaxing the optional requirements. Try a different budget, brand or specification.";
		}
		StringBuilder sb = new StringBuilder();
		if (!r.relaxed().isEmpty()) {
			sb.append("No device matches everything you asked for. ");
			r.relaxed().forEach(x -> sb.append(x.description()).append(' '));
			sb.append("Here are the best matches for the rest. ");
		}
		String summary = llm ? llmSummary(r, wanted) : null;
		if (summary != null) {
			sb.append(summary);
		} else {
			sb.append(templateSummary(r, wanted));
		}
		boolean priceUnknown = top.stream().anyMatch(d -> d.checks().stream()
				.anyMatch(c -> "price".equals(c.label().toLowerCase()) && "UNKNOWN".equals(c.status())));
		boolean budgetRelaxed = r.relaxed().stream().anyMatch(x -> Requirements.BUDGET.equals(x.key()));
		if (Requirements.fromMap(r.requirements()).budgetMaxInr != null && !budgetRelaxed) {
			sb.append(" Prices are listed for only some devices, so only devices with a listed price were checked"
					+ " against your budget.");
		}
		if (priceUnknown) {
			sb.append(" The dataset lists no price for some of these devices, so their fit to your budget is unconfirmed.");
		}
		return sb.toString().trim();
	}

	static String templateSummary(RecommendationResponse r, String wanted) {
		RecommendedDevice first = r.recommendations().get(0);
		String lead = first.device().brandName() + " " + first.device().name();
		if (!r.exactMatch()) {
			return "The top " + (r.recommendations().size() == 1 ? "match is " : r.recommendations().size()
					+ " matches are shown below, led by ") + lead + ".";
		}
		String count = r.matchedCountCapped() ? "over " + r.matchedCount() : String.valueOf(r.matchedCount());
		return "I found " + count + " device" + (r.matchedCount() == 1 ? "" : "s") + " in the database for " + wanted
				+ ". The top " + (r.recommendations().size() == 1 ? "match is " : r.recommendations().size()
						+ " matches are shown below, led by ") + lead + ".";
	}

	private String llmSummary(RecommendationResponse r, String wanted) {
		String facts = Json.stringify(facts(r, wanted));
		try {
			String text = provider.get().complete(SUMMARY_PROMPT, List.of(new ChatTurn("user", facts))).trim();
			if (ExplanationGuard.isGrounded(text, facts)) {
				return text;
			}
			log.info("AI summary rejected by the grounding check; using the template summary");
		} catch (AiProviderException e) {
			log.warn("AI summary failed: {}", e.getMessage());
		}
		return null;
	}

	static Map<String, Object> facts(RecommendationResponse r, String wanted) {
		Map<String, Object> facts = new LinkedHashMap<>();
		facts.put("user_requirements", wanted);
		facts.put("devices_matching_in_database", r.matchedCount());
		List<Object> list = new ArrayList<>();
		for (RecommendedDevice d : r.recommendations()) {
			Map<String, Object> m = new LinkedHashMap<>();
			m.put("rank", d.rank());
			m.put("name", d.device().brandName() + " " + d.device().name());
			m.put("score_out_of_100", d.score());
			m.put("highlights", d.highlights());
			list.add(m);
		}
		facts.put("top_devices", list);
		return facts;
	}
}

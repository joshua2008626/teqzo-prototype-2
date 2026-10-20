package com.teqzo.ai;

import com.teqzo.TestData;
import com.teqzo.domain.DeviceRow;
import com.teqzo.recommendation.RecommendationService;
import com.teqzo.recommendation.Requirements;
import com.teqzo.service.BrandService;
import com.teqzo.service.FacetsService;
import com.teqzo.service.PricePolicy;
import com.teqzo.testkit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.teqzo.testkit.Assertions.*;

class DefaultAiRecommendationServiceTest {

	/** Scripted provider: answers are returned in order; "FAIL" throws. */
	static class ScriptedProvider implements AiProvider {
		final List<String> answers;
		int calls;

		ScriptedProvider(String... answers) {
			this.answers = new ArrayList<>(List.of(answers));
		}

		@Override
		public String name() {
			return "test";
		}

		@Override
		public String model() {
			return "test-model";
		}

		@Override
		public String complete(String system, List<ChatTurn> messages) throws AiProviderException {
			calls++;
			String next = answers.isEmpty() ? "FAIL" : answers.remove(0);
			if (next.equals("FAIL")) {
				throw new AiProviderException("boom");
			}
			return next;
		}
	}

	private final TestData.FakeDeviceRepository repo = new TestData.FakeDeviceRepository(TestData.flagship(), TestData.budget());

	private DefaultAiRecommendationService service(AiProvider provider, String key) {
		RecommendationService recs = new RecommendationService(repo, new BrandService(new TestData.FakeBrandRepository()),
				new FacetsService(repo), new PricePolicy((Double) null));
		RuleBasedRequirementParser rules = new RuleBasedRequirementParser(() -> List.of("Samsung", "Xiaomi", "Apple"), () -> 2022);
		AiSettings settings = AiSettings.resolve("openai", key, "", "", 30);
		return new DefaultAiRecommendationService(settings, Optional.ofNullable(provider), rules, recs);
	}

	private static ChatRequest request(String message, ChatResponse previous) {
		return new ChatRequest(message, List.of(), previous == null ? null : previous.requirements(),
				previous == null ? null : previous.asked());
	}

	@Test
	void withoutAKeyTheAssistantStillWorksAndExplainsHowToConfigureIt() {
		DefaultAiRecommendationService s = service(null, "");
		AiStatus status = s.status();
		assertFalse(status.llmEnabled());
		assertTrue(status.message().contains("AI_API_KEY"));
		ChatResponse r = s.chat(request("I need 8GB RAM and 5G", null));
		assertEquals("rules", r.mode());
		assertTrue(r.notice().contains("AI_API_KEY"));
		assertEquals(1, r.recommendations().recommendations().size());
		assertEquals("Flagship One", r.recommendations().recommendations().get(0).device().name());
	}

	@Test
	void conversationContextAccumulatesAcrossTurns() {
		DefaultAiRecommendationService s = service(null, "");
		ChatResponse t1 = s.chat(request("I want a phone with excellent battery", null));
		assertNotNull(t1.followUp());
		assertTrue(t1.followUp().toLowerCase().contains("budget"));
		ChatResponse t2 = s.chat(request("Under 30000", t1));
		assertEquals(30000.0, Requirements.fromMap(t2.requirements()).budgetMaxInr);
		assertEquals("battery", Requirements.fromMap(t2.requirements()).priorities.get(0).key());
		assertNull(t2.followUp(), "budget was asked and a priority is known, so nothing more to ask");
	}

	@Test
	void budgetIsAskedOnlyOnce() {
		DefaultAiRecommendationService s = service(null, "");
		ChatResponse t1 = s.chat(request("gaming phone", null));
		assertTrue(t1.followUp().toLowerCase().contains("budget"));
		ChatResponse t2 = s.chat(request("with 5G", t1));
		assertTrue(t2.followUp() == null || !t2.followUp().toLowerCase().contains("budget"));
	}

	@Test
	void emptyUnderstandingAsksForDetailsWithoutRecommendations() {
		ChatResponse r = service(null, "").chat(request("hello", null));
		assertNull(r.recommendations());
		assertFalse(r.suggestions().isEmpty());
		assertTrue(r.reply().contains("Tell me"));
	}

	@Test
	void llmOutputIsValidatedAndDrivesTheDatabaseQuery() {
		ScriptedProvider p = new ScriptedProvider(
				"```json\n{\"updates\":{\"ram_min_gb\":8,\"requires_5g\":true,\"ram_min_gb_typo\":3,\"battery_min_mah\":99999999},\"clear\":[],\"reset\":false}\n```",
				"The Samsung Flagship One is a strong match for what you described.");
		ChatResponse r = service(p, "key").chat(request("something fast with 5g", null));
		assertEquals("llm", r.mode());
		assertNull(r.notice());
		assertEquals(8.0, Requirements.fromMap(r.requirements()).ramMinGb);
		assertNull(Requirements.fromMap(r.requirements()).batteryMinMah, "implausible value dropped by backend validation");
		assertEquals(1, r.recommendations().recommendations().size());
		assertTrue(r.reply().contains("strong match"));
	}

	@Test
	void hallucinatedSummaryIsReplacedByTemplateText() {
		ScriptedProvider p = new ScriptedProvider(
				"{\"updates\":{\"ram_min_gb\":8}}",
				"The Flagship One has a 7777 mAh battery and 200W charging, the best ever!");
		ChatResponse r = service(p, "key").chat(request("8gb ram", null));
		assertFalse(r.reply().contains("7777"));
		assertTrue(r.reply().contains("I found"));
	}

	@Test
	void providerFailureFallsBackToRulesWithANotice() {
		ScriptedProvider p = new ScriptedProvider("FAIL");
		ChatResponse r = service(p, "key").chat(request("8gb ram and 5g", null));
		assertEquals("rules", r.mode());
		assertTrue(r.notice().contains("could not be reached"));
		assertEquals(1, r.recommendations().recommendations().size());
	}

	@Test
	void summaryFailureStillReturnsDatabaseResults() {
		ScriptedProvider p = new ScriptedProvider("{\"updates\":{\"requires_5g\":true}}", "FAIL");
		ChatResponse r = service(p, "key").chat(request("5g", null));
		assertEquals("llm", r.mode());
		assertFalse(r.recommendations().recommendations().isEmpty());
		assertTrue(r.reply().contains("I found"));
	}

	@Test
	void noExactMatchIsExplainedAndRelaxationIsListed() {
		ChatResponse r = service(null, "").chat(request("8gb ram with 165hz and nfc", null));
		assertFalse(r.recommendations().exactMatch());
		assertTrue(r.reply().startsWith("No device matches everything"));
		assertFalse(r.recommendations().relaxed().isEmpty());
	}

	@Test
	void nothingAtAllMatchingIsSaidPlainly() {
		ChatResponse r = service(null, "").chat(request("only Apple phones", null));
		assertTrue(r.recommendations().recommendations().isEmpty());
		assertTrue(r.reply().contains("could not find any device"));
	}

	@Test
	void restartingClearsEarlierRequirements() {
		DefaultAiRecommendationService s = service(null, "");
		ChatResponse t1 = s.chat(request("5g with 8gb ram", null));
		ChatResponse t2 = s.chat(request("start over, gaming", t1));
		Requirements r = Requirements.fromMap(t2.requirements());
		assertNull(r.requires5g);
		assertNull(r.ramMinGb);
	}

	@Test
	void llmSeesPreviousRequirementsSoItCanReturnOnlyTheChange() {
		ScriptedProvider p = new ScriptedProvider("{\"updates\":{\"budget_max_inr\":20000}}", "FAIL");
		DefaultAiRecommendationService s = service(p, "key");
		ChatResponse r = s.chat(new ChatRequest("make it 20k", List.of(), Map.of("requires_5g", true, "ram_min_gb", 4), List.of()));
		Requirements out = Requirements.fromMap(r.requirements());
		assertEquals(Boolean.TRUE, out.requires5g);
		assertEquals(4.0, out.ramMinGb);
		assertEquals(20000.0, out.budgetMaxInr);
	}

	@Test
	void deviceNamesInRepliesComeFromTheDatabase() {
		DeviceRow only = TestData.device("sony_x-9.php", "Samsung", "Only One");
		only.ramGb = 16.0;
		repo.rows.clear();
		repo.rows.add(only);
		ChatResponse r = service(null, "").chat(request("16gb ram", null));
		assertTrue(r.reply().contains("Only One"));
	}
}

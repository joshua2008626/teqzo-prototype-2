package com.teqzo.ai;

import com.teqzo.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Chat Completions protocol: OpenAI, Gemini (OpenAI endpoint), Groq, OpenRouter, Ollama, vLLM, ... */
public class OpenAiCompatibleProvider implements AiProvider {

	private final AiSettings settings;
	private final HttpJsonClient http;

	public OpenAiCompatibleProvider(AiSettings settings) {
		this.settings = settings;
		this.http = new HttpJsonClient(settings.timeoutSeconds());
	}

	@Override
	public String name() {
		return settings.provider();
	}

	@Override
	public String model() {
		return settings.model();
	}

	@Override
	public String complete(String systemPrompt, List<ChatTurn> messages) throws AiProviderException {
		List<Map<String, String>> wire = new ArrayList<>();
		wire.add(Map.of("role", "system", "content", systemPrompt));
		for (ChatTurn t : messages) {
			wire.add(Map.of("role", t.role(), "content", t.content()));
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", settings.model());
		body.put("messages", wire);
		Map<String, String> headers = Map.of("Authorization", "Bearer " + settings.apiKey());
		Map<String, Object> response = http.postJson(settings.baseUrl() + "/chat/completions", headers, body);
		Object content = Json.path(response, "choices", 0, "message", "content");
		if (!(content instanceof String) || ((String) content).isBlank()) {
			throw new AiProviderException("The AI provider returned an empty answer.");
		}
		return (String) content;
	}
}

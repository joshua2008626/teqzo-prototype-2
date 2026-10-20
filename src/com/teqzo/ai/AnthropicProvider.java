package com.teqzo.ai;

import com.teqzo.util.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Anthropic Messages API. */
public class AnthropicProvider implements AiProvider {

	static final String API_VERSION = "2023-06-01";
	static final int MAX_TOKENS = 1024;

	private final AiSettings settings;
	private final HttpJsonClient http;

	public AnthropicProvider(AiSettings settings) {
		this.settings = settings;
		this.http = new HttpJsonClient(settings.timeoutSeconds());
	}

	@Override
	public String name() {
		return "anthropic";
	}

	@Override
	public String model() {
		return settings.model();
	}

	@Override
	public String complete(String systemPrompt, List<ChatTurn> messages) throws AiProviderException {
		List<Map<String, String>> wire = new ArrayList<>();
		for (ChatTurn t : messages) {
			wire.add(Map.of("role", t.role(), "content", t.content()));
		}
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", settings.model());
		body.put("max_tokens", MAX_TOKENS);
		body.put("system", systemPrompt);
		body.put("messages", wire);
		Map<String, String> headers = Map.of("x-api-key", settings.apiKey(), "anthropic-version", API_VERSION);
		Map<String, Object> response = http.postJson(settings.baseUrl() + "/v1/messages", headers, body);

		StringBuilder text = new StringBuilder();
		List<Object> blocks = Json.asArray(response.get("content"));
		if (blocks != null) {
			for (Object block : blocks) {
				Object type = Json.path(block, "type");
				Object t = Json.path(block, "text");
				if ("text".equals(type) && t instanceof String) {
					text.append((String) t);
				}
			}
		}
		if (text.toString().isBlank()) {
			throw new AiProviderException("The AI provider returned an empty answer.");
		}
		return text.toString();
	}
}

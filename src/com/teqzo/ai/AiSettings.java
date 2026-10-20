package com.teqzo.ai;

import java.util.Locale;

/** Resolved AI configuration. The API key lives only in memory and is never logged or returned by the API. */
public record AiSettings(String provider, String apiKey, String model, String baseUrl, int timeoutSeconds) {

	public boolean hasKey() {
		return apiKey != null && !apiKey.isBlank();
	}

	/** Builds settings applying per-provider defaults for anything not configured. */
	public static AiSettings resolve(String provider, String apiKey, String model, String baseUrl, int timeoutSeconds) {
		String p = provider == null || provider.isBlank() ? "openai" : provider.trim().toLowerCase(Locale.ROOT);
		String defaultBase;
		String defaultModel;
		switch (p) {
			case "anthropic":
				defaultBase = "https://api.anthropic.com";
				defaultModel = "claude-haiku-5-5";
				break;
			case "gemini":
				defaultBase = "https://generativelanguage.googleapis.com/v1beta/openai";
				defaultModel = "gemini-2.0-flash";
				break;
			case "groq":
				defaultBase = "https://api.groq.com/openai/v1";
				defaultModel = "llama-3.3-70b-versatile";
				break;
			case "openrouter":
				defaultBase = "https://openrouter.ai/api/v1";
				defaultModel = "openai/gpt-4o-mini";
				break;
			case "openai-compatible":
				defaultBase = "";
				defaultModel = "";
				break;
			default:
				p = "openai";
				defaultBase = "https://api.openai.com/v1";
				defaultModel = "gpt-4o-mini";
		}
		String base = baseUrl == null || baseUrl.isBlank() ? defaultBase : baseUrl.trim();
		while (base.endsWith("/")) {
			base = base.substring(0, base.length() - 1);
		}
		String m = model == null || model.isBlank() ? defaultModel : model.trim();
		int timeout = timeoutSeconds <= 0 ? 30 : Math.min(timeoutSeconds, 120);
		return new AiSettings(p, apiKey == null ? "" : apiKey.trim(), m, base, timeout);
	}

	/** True when a key is present and the provider has the base URL and model it needs. */
	public boolean usable() {
		return hasKey() && !baseUrl.isBlank() && !model.isBlank();
	}
}

package com.teqzo.ai;

import java.util.Optional;

public final class AiProviderFactory {

	private AiProviderFactory() {
	}

	/** Empty when the assistant is not fully configured; the rest of the application keeps working. */
	public static Optional<AiProvider> create(AiSettings settings) {
		if (!settings.usable()) {
			return Optional.empty();
		}
		if ("anthropic".equals(settings.provider())) {
			return Optional.of(new AnthropicProvider(settings));
		}
		return Optional.of(new OpenAiCompatibleProvider(settings));
	}
}

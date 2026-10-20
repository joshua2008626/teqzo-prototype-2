package com.teqzo.ai;

/**
 * The assistant. The provider behind it (OpenAI, Anthropic, Gemini, a local model, or none) can be
 * swapped through configuration without changing callers.
 */
public interface AiRecommendationService {

	ChatResponse chat(ChatRequest request);

	AiStatus status();
}

package com.teqzo.ai;

import com.teqzo.dto.RecommendationResponse;

import java.util.List;
import java.util.Map;

/**
 * @param mode   "llm" when a language model understood the message, "rules" for the built-in parser
 * @param notice set when the assistant is running in a reduced mode (for example no API key)
 */
public record ChatResponse(
		String reply,
		String understood,
		Map<String, Object> requirements,
		List<String> asked,
		String followUp,
		List<String> suggestions,
		RecommendationResponse recommendations,
		String mode,
		String notice) {
}

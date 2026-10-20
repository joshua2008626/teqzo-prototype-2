package com.teqzo.ai;

import java.util.List;
import java.util.Map;

/** Turns a natural-language message into structured requirements. Implementations must not invent values. */
public interface RequirementParser {

	ParseResult parse(String message, Map<String, Object> current, List<ChatTurn> history)
			throws AiProviderException;
}

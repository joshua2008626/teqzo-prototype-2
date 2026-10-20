package com.teqzo.ai;

import com.teqzo.domain.InvalidRequestException;

import java.util.List;
import java.util.Map;

/**
 * One turn of the assistant conversation. The service keeps no session: the caller sends back the
 * {@code requirements} and {@code asked} values it received in the previous response, which is how
 * earlier answers are remembered.
 */
public record ChatRequest(String message, List<ChatTurn> history, Map<String, Object> requirements,
		List<String> asked) {

	public static final int MAX_MESSAGE_LENGTH = 1000;
	public static final int MAX_HISTORY = 40;
	public static final int MAX_ASKED = 10;

	public ChatRequest {
		if (message == null || message.isBlank()) {
			throw InvalidRequestException.request("message must not be blank");
		}
		if (message.length() > MAX_MESSAGE_LENGTH) {
			throw InvalidRequestException.request("message is too long");
		}
		if (history != null && history.size() > MAX_HISTORY) {
			throw InvalidRequestException.request("history is too long");
		}
		if (asked != null && asked.size() > MAX_ASKED) {
			throw InvalidRequestException.request("asked is too long");
		}
	}
}

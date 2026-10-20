package com.teqzo.ai;

import com.teqzo.domain.InvalidRequestException;
import com.teqzo.testkit.Test;

import java.util.List;
import java.util.Map;

import static com.teqzo.testkit.Assertions.*;

class ChatRequestTest {

	@Test
	void acceptsNormalMessage() {
		ChatRequest r = new ChatRequest("best phone under 30000", List.of(), Map.of(), List.of());
		assertEquals("best phone under 30000", r.message());
	}

	@Test
	void rejectsBlankAndOversizedInput() {
		assertThrows(InvalidRequestException.class, () -> new ChatRequest("  ", List.of(), Map.of(), List.of()));
		assertThrows(InvalidRequestException.class, () -> new ChatRequest("x".repeat(1001), List.of(), Map.of(), List.of()));
		List<ChatTurn> many = java.util.Collections.nCopies(41, new ChatTurn("user", "hi"));
		assertThrows(InvalidRequestException.class, () -> new ChatRequest("hi", many, Map.of(), List.of()));
	}
}

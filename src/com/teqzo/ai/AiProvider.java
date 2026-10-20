package com.teqzo.ai;

import java.util.List;

/** A language model backend. Implementations only transport text; they know nothing about devices. */
public interface AiProvider {

	String name();

	String model();

	/** Sends a system prompt and conversation; returns the assistant's text. */
	String complete(String systemPrompt, List<ChatTurn> messages) throws AiProviderException;
}

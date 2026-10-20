package com.teqzo.ai;

/** Any failure talking to an AI provider. The message never contains credentials. */
public class AiProviderException extends Exception {
	public AiProviderException(String message) {
		super(message);
	}

	public AiProviderException(String message, Throwable cause) {
		super(message, cause);
	}
}

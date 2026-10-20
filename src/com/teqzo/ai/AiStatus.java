package com.teqzo.ai;

/** Public view of the AI configuration. Never contains the key. */
public record AiStatus(boolean llmEnabled, String provider, String model, String mode, String message) {
}

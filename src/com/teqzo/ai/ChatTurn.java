package com.teqzo.ai;

/** One message in a conversation; role is "user" or "assistant". */
public record ChatTurn(String role, String content) {
}

package com.teqzo.ai;

import java.util.Map;
import java.util.Set;

/**
 * What the latest user message changed.
 *
 * @param update keys the message set (see {@code Requirements} keys)
 * @param clear  keys the user asked to forget
 * @param reset  the user wants to start over
 */
public record ParseResult(Map<String, Object> update, Set<String> clear, boolean reset) {
}

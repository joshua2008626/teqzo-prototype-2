package com.teqzo.ai;

import com.sun.net.httpserver.HttpServer;
import com.teqzo.util.Json;
import com.teqzo.testkit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.teqzo.testkit.Assertions.*;

/** Talks to a real local HTTP server so the wire format of each provider is exercised. */
class AiProvidersTest {

	private static HttpServer server(AtomicReference<String> body, AtomicReference<String> auth, AtomicReference<String> path,
			int status, String response) throws Exception {
		HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		s.createContext("/", ex -> {
			body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			auth.set(ex.getRequestHeaders().getFirst("Authorization") + "|" + ex.getRequestHeaders().getFirst("x-api-key")
					+ "|" + ex.getRequestHeaders().getFirst("anthropic-version"));
			path.set(ex.getRequestURI().getPath());
			byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
			ex.getResponseHeaders().add("Content-Type", "application/json");
			ex.sendResponseHeaders(status, bytes.length);
			ex.getResponseBody().write(bytes);
			ex.close();
		});
		s.start();
		return s;
	}

	private static String base(HttpServer s) {
		return "http://127.0.0.1:" + s.getAddress().getPort();
	}

	@Test
	void openAiCompatibleSendsMessagesAndReadsTheAnswer() throws Exception {
		AtomicReference<String> body = new AtomicReference<>(), auth = new AtomicReference<>(), path = new AtomicReference<>();
		HttpServer s = server(body, auth, path, 200, "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hello é\"}}]}");
		try {
			AiSettings settings = AiSettings.resolve("openai-compatible", "secret-key", "m1", base(s) + "/v1/", 5);
			String answer = new OpenAiCompatibleProvider(settings).complete("be brief", List.of(new ChatTurn("user", "hi")));
			assertEquals("hello é", answer);
			assertEquals("/v1/chat/completions", path.get());
			assertTrue(auth.get().startsWith("Bearer secret-key"));
			Map<String, Object> sent = Json.asObject(Json.parse(body.get()));
			assertEquals("m1", sent.get("model"));
			assertEquals("system", Json.path(sent, "messages", 0, "role"));
			assertEquals("hi", Json.path(sent, "messages", 1, "content"));
		} finally {
			s.stop(0);
		}
	}

	@Test
	void anthropicUsesItsOwnHeadersAndResponseShape() throws Exception {
		AtomicReference<String> body = new AtomicReference<>(), auth = new AtomicReference<>(), path = new AtomicReference<>();
		HttpServer s = server(body, auth, path, 200, "{\"content\":[{\"type\":\"text\",\"text\":\"part1 \"},{\"type\":\"text\",\"text\":\"part2\"}]}");
		try {
			AiSettings settings = AiSettings.resolve("anthropic", "ant-key", "claude-test", base(s), 5);
			String answer = new AnthropicProvider(settings).complete("sys", List.of(new ChatTurn("user", "hi")));
			assertEquals("part1 part2", answer);
			assertEquals("/v1/messages", path.get());
			assertEquals("null|ant-key|2023-06-01", auth.get());
			Map<String, Object> sent = Json.asObject(Json.parse(body.get()));
			assertEquals("sys", sent.get("system"));
			assertEquals("claude-test", sent.get("model"));
		} finally {
			s.stop(0);
		}
	}

	@Test
	void httpErrorsAndGarbageBecomeProviderExceptionsWithoutLeakingTheKey() throws Exception {
		AtomicReference<String> b = new AtomicReference<>(), a = new AtomicReference<>(), p = new AtomicReference<>();
		HttpServer failing = server(b, a, p, 401, "{\"error\":{\"message\":\"bad key secret-key\"}}");
		try {
			AiSettings settings = AiSettings.resolve("openai-compatible", "secret-key", "m", base(failing), 5);
			AiProviderException e = assertThrows(AiProviderException.class,
					() -> new OpenAiCompatibleProvider(settings).complete("s", List.of(new ChatTurn("user", "x"))));
			assertTrue(e.getMessage().contains("401"));
			assertFalse(e.getMessage().contains("secret-key"));
		} finally {
			failing.stop(0);
		}
		HttpServer garbage = server(b, a, p, 200, "not json");
		try {
			AiSettings settings = AiSettings.resolve("openai-compatible", "k", "m", base(garbage), 5);
			assertThrows(AiProviderException.class,
					() -> new OpenAiCompatibleProvider(settings).complete("s", List.of(new ChatTurn("user", "x"))));
		} finally {
			garbage.stop(0);
		}
	}

	@Test
	void unreachableServerIsAProviderException() {
		AiSettings settings = AiSettings.resolve("openai-compatible", "k", "m", "http://127.0.0.1:1", 2);
		assertThrows(AiProviderException.class,
				() -> new OpenAiCompatibleProvider(settings).complete("s", List.of(new ChatTurn("user", "x"))));
	}

	@Test
	void factoryRequiresAKeyAndCompleteSettings() {
		assertTrue(AiProviderFactory.create(AiSettings.resolve("openai", "", "", "", 30)).isEmpty());
		assertTrue(AiProviderFactory.create(AiSettings.resolve("openai-compatible", "k", "", "", 30)).isEmpty());
		assertTrue(AiProviderFactory.create(AiSettings.resolve("openai", "k", "", "", 30)).isPresent());
		assertEquals("anthropic", AiProviderFactory.create(AiSettings.resolve("anthropic", "k", "", "", 30)).get().name());
	}

	@Test
	void defaultsPerProviderCanBeOverridden() {
		AiSettings gemini = AiSettings.resolve("gemini", "k", "", "", 30);
		assertTrue(gemini.baseUrl().contains("generativelanguage"));
		AiSettings custom = AiSettings.resolve("openai", "k", "my-model", "http://localhost:11434/v1/", 999);
		assertEquals("my-model", custom.model());
		assertEquals("http://localhost:11434/v1", custom.baseUrl());
		assertEquals(120, custom.timeoutSeconds());
	}
}

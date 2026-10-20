package com.teqzo.ai;

import com.teqzo.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Small JSON-over-HTTPS helper shared by providers. */
final class HttpJsonClient {

	private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
	private final int timeoutSeconds;

	HttpJsonClient(int timeoutSeconds) {
		this.timeoutSeconds = timeoutSeconds;
	}

	Map<String, Object> postJson(String url, Map<String, String> headers, Object body) throws AiProviderException {
		HttpRequest.Builder builder;
		try {
			builder = HttpRequest.newBuilder(URI.create(url));
		} catch (IllegalArgumentException e) {
			throw new AiProviderException("The configured AI base URL is not valid.");
		}
		builder.timeout(Duration.ofSeconds(timeoutSeconds)).header("Content-Type", "application/json");
		headers.forEach(builder::header);
		HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body),
				StandardCharsets.UTF_8)).build();
		try {
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			int status = response.statusCode();
			if (status < 200 || status >= 300) {
				throw new AiProviderException("The AI provider answered with HTTP " + status + ".");
			}
			Map<String, Object> parsed = Json.asObject(Json.parse(response.body()));
			if (parsed == null) {
				throw new AiProviderException("The AI provider returned an unexpected response.");
			}
			return parsed;
		} catch (IOException e) {
			throw new AiProviderException("Could not reach the AI provider (" + e.getClass().getSimpleName() + ").", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AiProviderException("The request to the AI provider was interrupted.", e);
		} catch (Json.JsonException e) {
			throw new AiProviderException("The AI provider returned invalid JSON.", e);
		}
	}
}

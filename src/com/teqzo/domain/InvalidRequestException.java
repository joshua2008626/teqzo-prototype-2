package com.teqzo.domain;

/** Thrown when a request is syntactically fine but semantically invalid. Mapped to HTTP 400. */
public class InvalidRequestException extends RuntimeException {
	private final String code;

	public InvalidRequestException(String code, String message) {
		super(message);
		this.code = code;
	}

	public static InvalidRequestException filter(String message) {
		return new InvalidRequestException("INVALID_FILTER", message);
	}

	public static InvalidRequestException request(String message) {
		return new InvalidRequestException("INVALID_REQUEST", message);
	}

	public String getCode() {
		return code;
	}
}

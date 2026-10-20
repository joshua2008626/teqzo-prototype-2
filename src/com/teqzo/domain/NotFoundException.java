package com.teqzo.domain;

/** Thrown when a requested entity does not exist. Mapped to HTTP 404. */
public class NotFoundException extends RuntimeException {
	private final String code;

	public NotFoundException(String code, String message) {
		super(message);
		this.code = code;
	}

	public String getCode() {
		return code;
	}

	public static NotFoundException device(String id) {
		return new NotFoundException("DEVICE_NOT_FOUND", "The requested device was not found: " + id);
	}

	public static NotFoundException brand(String id) {
		return new NotFoundException("BRAND_NOT_FOUND", "The requested brand was not found: " + id);
	}
}

package com.teqzo.repository;

/** A database problem (cannot connect, bad SQL, ...). Wraps the checked SQLException. */
public class DataAccessException extends RuntimeException {

	public DataAccessException(String message, Throwable cause) {
		super(message, cause);
	}
}

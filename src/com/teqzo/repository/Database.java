package com.teqzo.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Opens and keeps one JDBC connection (the console application is single threaded). If the connection
 * was lost it is opened again on the next use.
 */
public final class Database {

	private final String url;
	private final String user;
	private final String password;
	private Connection connection;

	public Database(String url, String user, String password) {
		this.url = url;
		this.user = user;
		this.password = password;
	}

	public synchronized Connection connection() {
		try {
			if (connection == null || connection.isClosed() || !connection.isValid(2)) {
				connection = DriverManager.getConnection(url, user, password);
			}
			return connection;
		} catch (SQLException e) {
			throw new DataAccessException("Cannot connect to the database: " + e.getMessage(), e);
		}
	}

	public synchronized void close() {
		try {
			if (connection != null) {
				connection.close();
			}
		} catch (SQLException ignored) {
			// closing at exit; nothing useful to do
		}
		connection = null;
	}
}

package com.teqzo.util;

import java.io.PrintStream;

/**
 * A very small logger (core Java only). Messages use {} placeholders like "Device {} failed".
 * Only warnings and errors are printed unless the environment variable TEQZO_LOG is set to "info".
 */
public final class Log {

	private static final boolean INFO = "info".equalsIgnoreCase(System.getenv("TEQZO_LOG"));
	private static final PrintStream OUT = System.err;

	private final String name;

	private Log(String name) {
		this.name = name;
	}

	public static Log of(Class<?> type) {
		return new Log(type.getSimpleName());
	}

	public void info(String message, Object... args) {
		if (INFO) {
			print("INFO", message, args);
		}
	}

	public void warn(String message, Object... args) {
		print("WARN", message, args);
	}

	public void error(String message, Object... args) {
		print("ERROR", message, args);
	}

	private void print(String level, String message, Object[] args) {
		OUT.println("[" + level + "] " + name + ": " + format(message, args));
	}

	/** Replaces each {} with the next argument. Package-visible for tests. */
	static String format(String message, Object[] args) {
		StringBuilder out = new StringBuilder();
		int next = 0;
		int i = 0;
		while (i < message.length()) {
			if (message.startsWith("{}", i) && next < args.length) {
				out.append(args[next++]);
				i += 2;
			} else {
				out.append(message.charAt(i++));
			}
		}
		return out.toString();
	}
}

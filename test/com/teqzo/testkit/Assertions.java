package com.teqzo.testkit;

import java.util.Objects;

/** The few assertion methods the tests use. */
public final class Assertions {

	private Assertions() {
	}

	@FunctionalInterface
	public interface Executable {
		void execute() throws Throwable;
	}

	private static void fail(String message) {
		throw new AssertionError(message);
	}

	public static void assertEquals(Object expected, Object actual) {
		if (!Objects.equals(expected, actual)) {
			fail("expected <" + expected + "> but was <" + actual + ">");
		}
	}

	public static void assertEquals(Object expected, Object actual, String message) {
		if (!Objects.equals(expected, actual)) {
			fail(message + ": expected <" + expected + "> but was <" + actual + ">");
		}
	}

	public static void assertEquals(double expected, double actual, double delta) {
		if (Math.abs(expected - actual) > delta) {
			fail("expected <" + expected + "> but was <" + actual + ">");
		}
	}

	public static void assertEquals(double expected, double actual, double delta, String message) {
		if (Math.abs(expected - actual) > delta) {
			fail(message + ": expected <" + expected + "> but was <" + actual + ">");
		}
	}

	public static void assertEquals(long expected, long actual) {
		if (expected != actual) {
			fail("expected <" + expected + "> but was <" + actual + ">");
		}
	}

	public static void assertEquals(int expected, int actual) {
		if (expected != actual) {
			fail("expected <" + expected + "> but was <" + actual + ">");
		}
	}

	public static void assertTrue(boolean condition) {
		if (!condition) {
			fail("expected true");
		}
	}

	public static void assertTrue(boolean condition, String message) {
		if (!condition) {
			fail(message);
		}
	}

	public static void assertFalse(boolean condition) {
		if (condition) {
			fail("expected false");
		}
	}

	public static void assertFalse(boolean condition, String message) {
		if (condition) {
			fail(message);
		}
	}

	public static void assertNull(Object value) {
		if (value != null) {
			fail("expected null but was <" + value + ">");
		}
	}

	public static void assertNull(Object value, String message) {
		if (value != null) {
			fail(message + ": expected null but was <" + value + ">");
		}
	}

	public static void assertNotNull(Object value) {
		if (value == null) {
			fail("expected a value but was null");
		}
	}

	public static <T extends Throwable> T assertThrows(Class<T> type, Executable executable) {
		try {
			executable.execute();
		} catch (Throwable t) {
			if (type.isInstance(t)) {
				return type.cast(t);
			}
			throw new AssertionError("expected " + type.getSimpleName() + " but got " + t, t);
		}
		throw new AssertionError("expected " + type.getSimpleName() + " but nothing was thrown");
	}
}

package com.teqzo.ui;

import com.teqzo.testkit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static com.teqzo.testkit.Assertions.*;

class ConsoleTest {

	@Test
	void asciiFallbackReplacesRupeeSign() {
		assertEquals("Rs 29,999", Console.toAscii("₹29,999").replace("Rs  ", "Rs "));
	}

	@Test
	void askReturnsNullAtEndOfInput() {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		Console c = new Console(new ByteArrayInputStream("one\n".getBytes(StandardCharsets.UTF_8)),
				new PrintStream(bytes, true, StandardCharsets.UTF_8), true);
		assertEquals("one", c.ask("> "));
		assertNull(c.ask("> "));
		assertTrue(c.isEndOfInput());
	}

	@Test
	void yesNoAnswers() {
		assertEquals("true", ConsoleApp.yesNo("Yes"));
		assertEquals("false", ConsoleApp.yesNo("n"));
		assertNull(ConsoleApp.yesNo(""));
	}
}

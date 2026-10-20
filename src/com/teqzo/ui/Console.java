package com.teqzo.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;

/**
 * Keyboard input and screen output. If the terminal cannot show non-ASCII characters (typical for the
 * Windows console), symbols such as the rupee sign are replaced by plain ASCII text.
 */
public final class Console {

	private final BufferedReader in;
	private final PrintStream out;
	private final boolean unicode;
	private boolean endOfInput;

	public Console(InputStream input, PrintStream output) {
		this(input, output, terminalSupportsUnicode());
	}

	public Console(InputStream input, PrintStream output, boolean unicode) {
		this.in = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
		this.out = output;
		this.unicode = unicode;
	}

	private static boolean terminalSupportsUnicode() {
		String forced = System.getenv("TEQZO_ASCII");
		if (forced != null && !forced.isBlank() && !forced.equals("0")) {
			return false;
		}
		String encoding = System.getProperty("stdout.encoding", Charset.defaultCharset().name());
		return encoding.toUpperCase().contains("UTF");
	}

	public void println(String text) {
		out.println(unicode ? text : toAscii(text));
	}

	public void println() {
		out.println();
	}

	/** Shows the prompt and returns the typed line (trimmed), or null when the input has ended. */
	public String ask(String prompt) {
		out.print(unicode ? prompt : toAscii(prompt));
		out.flush();
		try {
			String line = in.readLine();
			if (line == null) {
				endOfInput = true;
				out.println();
				return null;
			}
			return line.trim();
		} catch (IOException e) {
			endOfInput = true;
			return null;
		}
	}

	public boolean isEndOfInput() {
		return endOfInput;
	}

	/** Replaces characters outside ASCII with readable ASCII equivalents. */
	static String toAscii(String text) {
		String s = text.replace("₹ ", "Rs ").replace("₹", "Rs ").replace("€ ", "EUR ").replace("€", "EUR ").replace("≥", ">=").replace("≤", "<=")
				.replace("–", "-").replace("—", "-").replace("·", "|").replace("…", "...")
				.replace("“", "\"").replace("”", "\"").replace("‘", "'").replace("’", "'").replace("→", "->")
				.replace("✓", "[ok]").replace("✗", "[x]").replace("×", "x").replace("°", " deg");
		String plain = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		StringBuilder out = new StringBuilder();
		for (char c : plain.toCharArray()) {
			out.append(c < 128 ? c : '?');
		}
		return out.toString();
	}
}

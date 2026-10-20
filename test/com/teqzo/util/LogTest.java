package com.teqzo.util;

import com.teqzo.testkit.Test;

import static com.teqzo.testkit.Assertions.*;

class LogTest {

	@Test
	void fillsPlaceholdersInOrder() {
		assertEquals("a 1 b x", Log.format("a {} b {}", new Object[] {1, "x"}));
	}

	@Test
	void keepsExtraPlaceholders() {
		assertEquals("a 1 {}", Log.format("a {} {}", new Object[] {1}));
	}
}

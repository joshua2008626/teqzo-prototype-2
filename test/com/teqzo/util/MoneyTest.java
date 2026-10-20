package com.teqzo.util;

import com.teqzo.testkit.Test;

import static com.teqzo.testkit.Assertions.assertEquals;

class MoneyTest {

	@Test
	void usesIndianDigitGrouping() {
		assertEquals("₹999", Money.inr(999));
		assertEquals("₹30,000", Money.inr(30000));
		assertEquals("₹1,50,000", Money.inr(150000));
		assertEquals("₹12,34,567", Money.inr(1234567.4));
	}
}

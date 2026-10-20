package com.teqzo.service;

import com.teqzo.testkit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.teqzo.testkit.Assertions.*;

class ShortlistStoreTest {

	private static Path temp() throws IOException {
		return Files.createTempDirectory("teqzo-shortlist").resolve("list.txt");
	}

	@Test
	void addsPersistsAndReloads() throws IOException {
		Path file = temp();
		ShortlistStore store = new ShortlistStore(file);
		assertTrue(store.add("a"));
		assertTrue(store.add("b"));
		assertFalse(store.add("a"), "no duplicates");
		assertEquals(List.of("a", "b"), new ShortlistStore(file).ids());
	}

	@Test
	void removeAndClear() throws IOException {
		Path file = temp();
		ShortlistStore store = new ShortlistStore(file);
		store.add("a");
		store.add("b");
		assertTrue(store.remove("a"));
		assertFalse(store.remove("zzz"));
		assertEquals(List.of("b"), new ShortlistStore(file).ids());
		store.clear();
		assertTrue(new ShortlistStore(file).ids().isEmpty());
	}

	@Test
	void missingOrBrokenFileGivesEmptyList() throws IOException {
		assertTrue(new ShortlistStore(temp()).ids().isEmpty());
		Path file = temp();
		Files.write(file, new byte[] {(byte) 0xff, (byte) 0xfe, 0});
		assertNotNull(new ShortlistStore(file).ids());
	}

	@Test
	void ignoresBlankLinesCommentsAndDuplicatesWhenLoading() throws IOException {
		Path file = temp();
		Files.write(file, "# my list\n\na\na\n  b  \n".getBytes(StandardCharsets.UTF_8));
		assertEquals(List.of("a", "b"), new ShortlistStore(file).ids());
	}

	@Test
	void sizeIsLimitedAndBadIdsRejected() throws IOException {
		ShortlistStore store = new ShortlistStore(temp());
		for (int i = 0; i < ShortlistStore.MAX_ENTRIES; i++) {
			assertTrue(store.add("id" + i));
		}
		assertFalse(store.add("one-too-many"));
		assertFalse(store.add(""));
		assertFalse(store.add(null));
		assertFalse(store.add("a\nb"));
	}
}

package com.teqzo.service;

import com.teqzo.repository.DataAccessException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * The user's shortlist: device ids kept in a small text file (one id per line) so it survives restarts.
 * The file is rewritten on every change; a damaged or missing file simply gives an empty shortlist.
 */
public class ShortlistStore {

	public static final int MAX_ENTRIES = 50;

	private final Path file;
	private final List<String> ids = new ArrayList<>();

	public ShortlistStore(Path file) {
		this.file = file;
		load();
	}

	public synchronized List<String> ids() {
		return List.copyOf(ids);
	}

	public synchronized boolean contains(String id) {
		return ids.contains(id);
	}

	/** @return false when the id was already there or the list is full */
	public synchronized boolean add(String id) {
		if (id == null || id.isBlank() || ids.contains(id) || ids.size() >= MAX_ENTRIES || id.indexOf('\n') >= 0) {
			return false;
		}
		ids.add(id);
		save();
		return true;
	}

	public synchronized boolean remove(String id) {
		boolean removed = ids.remove(id);
		if (removed) {
			save();
		}
		return removed;
	}

	public synchronized void clear() {
		ids.clear();
		save();
	}

	public Path file() {
		return file;
	}

	private void load() {
		ids.clear();
		if (!Files.isRegularFile(file)) {
			return;
		}
		try {
			for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
				String id = line.trim();
				if (!id.isEmpty() && !id.startsWith("#") && !ids.contains(id) && ids.size() < MAX_ENTRIES) {
					ids.add(id);
				}
			}
		} catch (IOException | RuntimeException e) {
			ids.clear(); // unreadable file: start empty rather than fail
		}
	}

	private void save() {
		try {
			Path parent = file.toAbsolutePath().getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.write(temp, String.join("\n", ids).concat(ids.isEmpty() ? "" : "\n").getBytes(StandardCharsets.UTF_8));
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			throw new DataAccessException("Could not save the shortlist to " + file + ": " + e.getMessage(), e);
		}
	}
}

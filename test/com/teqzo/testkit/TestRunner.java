package com.teqzo.testkit;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Runs every method annotated with {@link Test} in every compiled class whose name ends with "Test".
 * Usage: {@code java -cp out TestRunner out}
 */
public final class TestRunner {

	private TestRunner() {
	}

	public static void main(String[] args) throws Exception {
		Path root = Path.of(args.length > 0 ? args[0] : "out");
		int passed = 0;
		List<String> failures = new ArrayList<>();
		for (String className : testClasses(root)) {
			Class<?> type = Class.forName(className);
			List<Method> methods = new ArrayList<>();
			for (Method m : type.getDeclaredMethods()) {
				if (m.isAnnotationPresent(Test.class)) {
					methods.add(m);
				}
			}
			methods.sort(Comparator.comparing(Method::getName));
			for (Method method : methods) {
				try {
					Constructor<?> constructor = type.getDeclaredConstructor();
					constructor.setAccessible(true);
					method.setAccessible(true);
					method.invoke(constructor.newInstance());
					passed++;
				} catch (InvocationTargetException e) {
					failures.add(type.getSimpleName() + "." + method.getName() + ": " + e.getCause());
				}
			}
		}
		failures.forEach(f -> System.out.println("FAILED " + f));
		System.out.println("Tests: " + (passed + failures.size()) + ", passed: " + passed + ", failed: " + failures.size());
		System.exit(failures.isEmpty() ? 0 : 1);
	}

	private static List<String> testClasses(Path root) throws IOException {
		try (Stream<Path> files = Files.walk(root)) {
			return files.map(root::relativize).map(Path::toString)
					.filter(p -> p.endsWith("Test.class") && !p.contains("$"))
					.map(p -> p.substring(0, p.length() - ".class".length()).replace('/', '.').replace('\\', '.'))
					.sorted().toList();
		}
	}
}

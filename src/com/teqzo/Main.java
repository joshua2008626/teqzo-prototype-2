package com.teqzo;

import com.teqzo.config.AppConfig;
import com.teqzo.config.AppContext;
import com.teqzo.config.SchemaVerifier;
import com.teqzo.repository.DataAccessException;
import com.teqzo.repository.Database;
import com.teqzo.ui.Console;
import com.teqzo.ui.ConsoleApp;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * TEQZO: a console application over a MySQL database of phone specifications.
 * Start it with {@code java -cp "out:lib/*" com.teqzo.Main} (see README). Use {@code --check} to only test the
 * database connection.
 */
public final class Main {

	private Main() {
	}

	public static void main(String[] args) {
		AppConfig config = AppConfig.load();
		Database database = new Database(config.jdbcUrl(), config.dbUser(), config.dbPassword());
		try {
			database.connection();
		} catch (DataAccessException e) {
			System.err.println("Could not connect to the database.");
			System.err.println("  " + e.getMessage());
			System.err.println("Check: MySQL is running; DB_HOST / DB_PORT / DB_NAME / DB_USERNAME / DB_PASSWORD are set"
					+ " (see .env.example); the MySQL driver jar is in the lib folder.");
			System.exit(1);
			return;
		}
		AppContext context = new AppContext(config, database);
		PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
		Console console = new Console(System.in, out);
		for (String problem : new SchemaVerifier(context.jdbc).problems()) {
			console.println("WARNING: " + problem);
		}
		if (args.length > 0 && args[0].equals("--check")) {
			console.println("Database OK, " + context.facets.get().totalDevices() + " devices.");
			database.close();
			return;
		}
		try {
			new ConsoleApp(context, console).run();
		} finally {
			database.close();
		}
	}
}

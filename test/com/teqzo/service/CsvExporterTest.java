package com.teqzo.service;

import com.teqzo.TestData;
import com.teqzo.domain.DeviceRow;
import com.teqzo.testkit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.teqzo.testkit.Assertions.*;

class CsvExporterTest {

	@Test
	void writesHeaderAndOneLinePerDevice() {
		String csv = CsvExporter.toCsv(List.of(DeviceMapper.toSummary(TestData.flagship(), null)));
		String[] lines = csv.split("\r\n");
		assertEquals(2, lines.length);
		assertEquals(CsvExporter.HEADER, lines[0]);
		assertTrue(lines[1].startsWith("samsung_flag-1.php,Samsung,Flagship One,2022,79999,"));
		assertTrue(lines[1].endsWith(",yes"));
	}

	@Test
	void missingValuesAreEmptyNotZero() {
		DeviceRow d = TestData.device("x", "Brand", "Name");
		String line = CsvExporter.toCsv(List.of(DeviceMapper.toSummary(d, null))).split("\r\n")[1];
		assertEquals("x,Brand,Name,2022,,,,,,,,Android 12,,,,no", line);
	}

	@Test
	void quotesCommasAndQuotes() {
		assertEquals("\"a,b\"", CsvExporter.cell("a,b"));
		assertEquals("\"say \"\"hi\"\"\"", CsvExporter.cell("say \"hi\""));
		assertEquals("plain", CsvExporter.cell("plain"));
		assertEquals("", CsvExporter.cell(null));
		assertEquals("5000", CsvExporter.cell(5000.0));
		assertEquals("6.7", CsvExporter.cell(6.7));
	}

	@Test
	void formulaLookingTextIsDefused() {
		assertEquals("'=SUM(A1)", CsvExporter.cell("=SUM(A1)"));
		assertEquals("'@cmd", CsvExporter.cell("@cmd"));
		assertEquals("'-1", CsvExporter.cell("-1"));
	}

	@Test
	void writesAFile() throws IOException {
		Path file = Files.createTempDirectory("teqzo-csv").resolve("out.csv");
		CsvExporter.write(file, List.of(DeviceMapper.toSummary(TestData.budget(), null)));
		assertTrue(Files.readString(file, StandardCharsets.UTF_8).contains("Budget Plus"));
	}
}

package com.example.springbatch.batch;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationCsvWriter {
	private final Path outputPath;

	public ReconciliationCsvWriter(@Value("${app.output.result-csv-path}") String outputPath) {
		this.outputPath = Path.of(outputPath);
	}

	public synchronized void initializeOutput() throws Exception {
		Path parent = outputPath.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.writeString(outputPath, "line_number,transaction_id,status,action,error_message" + System.lineSeparator(),
				StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	public synchronized void writeResults(List<ReconciliationProcessingResult> results) throws Exception {
		List<ReconciliationProcessingResult> rows = results.stream().filter(
				result -> "INVALID_RECORD".equals(result.status()) || "DUPLICATE_IN_CSV1".equals(result.status()))
				.toList();
		if (rows.isEmpty()) {
			return;
		}
		try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
			for (ReconciliationProcessingResult result : rows) {
				String transactionId = result.input() == null || result.input().getTransactionId() == null ? ""
						: result.input().getTransactionId();
				String errorMessage = result.errorMessage() == null ? "" : result.errorMessage();
				printer.printRecord(result.input() == null ? "" : result.input().getLineNumber(), transactionId,
						result.status(), result.action(), errorMessage);
			}
		}
	}
}
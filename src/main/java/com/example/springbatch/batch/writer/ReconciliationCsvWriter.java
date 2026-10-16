package com.example.springbatch.batch.writer;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
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

		Files.writeString(outputPath,
				"job_instance_id,job_execution_id,line_number,transaction_id,"
						+ "status,action,error_message,previous_amount,new_amount" + System.lineSeparator(),
				StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	public synchronized void writeResults(List<ReconciliationProcessingResult> results) throws Exception {

		List<ReconciliationProcessingResult> rows = results.stream()
				.filter(result -> "INVALID_RECORD".equals(result.status())
						|| "DUPLICATE_IN_CSV1".equals(result.status()) || "MISMATCH".equals(result.status())
						|| "MISSING_IN_DATABASE".equals(result.status()))
				.toList();

		if (rows.isEmpty()) {
			return;
		}

		StepContext context = StepSynchronizationManager.getContext();

		if (context == null) {
			throw new IllegalStateException("No active Spring Batch step context available " + "to retrieve job IDs.");
		}

		long jobInstanceId = context.getStepExecution().getJobExecution().getJobInstance().getInstanceId();

		Long jobExecutionId = context.getStepExecution().getJobExecution().getId();

		Path parent = outputPath.toAbsolutePath().getParent();

		if (parent != null) {
			Files.createDirectories(parent);
		}

		try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {

			for (ReconciliationProcessingResult result : rows) {

				String transactionId = result.input() == null || result.input().getTransactionId() == null ? ""
						: result.input().getTransactionId();

				String errorMessage = result.errorMessage() == null ? "" : result.errorMessage();

				String previousAmount = result.previousAmount() == null ? "" : result.previousAmount().toPlainString();

				String newAmount = "MISMATCH".equals(result.status()) && result.input() != null
						&& result.input().getAmount() != null ? result.input().getAmount() : "";

				printer.printRecord(jobInstanceId, jobExecutionId == null ? "" : jobExecutionId,
						result.input() == null ? "" : result.input().getLineNumber(), transactionId, result.status(),
						result.action(), errorMessage, previousAmount, newAmount);
			}
		}
	}
}

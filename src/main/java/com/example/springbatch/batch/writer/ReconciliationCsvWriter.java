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

import com.example.springbatch.constants.Messages;

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
		Files.writeString(outputPath, Messages.CSV_OUTPUTFILE_HEADER + System.lineSeparator(), StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
	}

	public synchronized void writeResults(List<ReconciliationProcessingResult> results) throws Exception {
		List<ReconciliationProcessingResult> rows = results.stream()
				.filter(result -> Messages.INVALID_RECORD.equals(result.status())
						|| Messages.DUPLICATE_IN_CSV_1.equals(result.status()) || Messages.MISMATCH.equals(result.status())
						|| Messages.MISSING_IN_DATABASE.equals(result.status()))
				.toList();
		if (rows.isEmpty()) {
			return;
		}
		StepContext context = StepSynchronizationManager.getContext();
		if (context == null) {
			throw new IllegalStateException(Messages.STEP_CONTEXT_NOT_FOUND);
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
				String newAmount = Messages.MISMATCH.equals(result.status()) && result.input() != null
						&& result.input().getAmount() != null ? result.input().getAmount() : "";
				printer.printRecord(jobInstanceId, jobExecutionId == null ? "" : jobExecutionId,
						result.input() == null ? "" : result.input().getLineNumber(), transactionId, result.status(),
						result.action(), errorMessage, previousAmount, newAmount);
			}
		}
	}
}

package com.example.springbatch.batch.writer;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
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
import com.example.springbatch.enums.ReconciliationStatus;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ReconciliationCsvWriter {
	private final Path outputPath;

	public ReconciliationCsvWriter(@Value("${app.output.result-csv-path}") String outputPath) {
		this.outputPath = Path.of(outputPath);
	}

	private Path getOutputPath(long jobInstanceId) {
		String filename = "reconciliation_" + jobInstanceId + ".csv";
		return outputPath.resolve(filename);
	}

	private StepContext getStepContext() {
		StepContext context = StepSynchronizationManager.getContext();
		if (context == null) {
			throw new IllegalStateException(Messages.STEP_CONTEXT_NOT_FOUND);
		}
		return context;
	}

	private long getCurrentJobInstanceId() {
		return getStepContext().getStepExecution().getJobExecution().getJobInstance().getInstanceId();
	}

	public synchronized void initializeOutput() throws Exception {
		long jobInstanceId = getCurrentJobInstanceId();
		Path instanceOutputPath = getOutputPath(jobInstanceId);
		Path parent = instanceOutputPath.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		if (Files.notExists(instanceOutputPath)) {
			try {
				Files.writeString(instanceOutputPath, Messages.CSV_OUTPUTFILE_HEADER + System.lineSeparator(),
						StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
				log.info("Created reconciliation CSV: {}", instanceOutputPath.toAbsolutePath());
			} catch (FileAlreadyExistsException exception) {
				log.debug("Reconciliation CSV already exists: {}", instanceOutputPath);
			}
		}
	}

	public synchronized void writeResults(List<ReconciliationProcessingResult> results) throws Exception {
		List<ReconciliationProcessingResult> rows = results.stream()
				.filter(result -> result.status() == ReconciliationStatus.INVALID_RECORD
						|| result.status() == ReconciliationStatus.DUPLICATE_IN_CSV1
						|| result.status() == ReconciliationStatus.MISMATCH
						|| result.status() == ReconciliationStatus.MISSING_IN_DATABASE)
				.toList();
		if (rows.isEmpty()) {
			return;
		}
		StepContext context = getStepContext();
		long jobInstanceId = context.getStepExecution().getJobExecution().getJobInstance().getInstanceId();
		Long jobExecutionId = context.getStepExecution().getJobExecution().getId();
		initializeOutput();
		Path instanceOutputPath = getOutputPath(jobInstanceId);

		try (BufferedWriter writer = Files.newBufferedWriter(instanceOutputPath, StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
				CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
			for (ReconciliationProcessingResult result : rows) {
				String transactionId = result.input() == null || result.input().getTransactionId() == null ? ""
						: result.input().getTransactionId();
				String errorMessage = result.errorMessage() == null ? "" : result.errorMessage();
				String previousAmount = result.previousAmount() == null ? "" : result.previousAmount().toPlainString();
				String newAmount = result.status() == ReconciliationStatus.MISMATCH && result.input() != null
						&& result.input().getAmount() != null ? result.input().getAmount() : "";
				printer.printRecord(jobInstanceId, jobExecutionId == null ? "" : jobExecutionId,
						result.input() == null ? "" : result.input().getLineNumber(), transactionId,
						result.status().name(), result.action().name(), errorMessage, previousAmount, newAmount);
			}
			printer.flush();
		}
	}
}

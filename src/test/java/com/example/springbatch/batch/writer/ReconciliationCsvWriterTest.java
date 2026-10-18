package com.example.springbatch.batch.writer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.core.step.StepExecution;

import com.example.springbatch.constants.Messages;
import com.example.springbatch.dto.TransactionRequestDto;
import com.example.springbatch.enums.ReconciliationAction;
import com.example.springbatch.enums.ReconciliationStatus;

class ReconciliationCsvWriterTest {

	@TempDir
	Path tempDir;

	private Path output;
	private ReconciliationCsvWriter writer;

	@BeforeEach
	void setUp() {
		output = tempDir.resolve("nested/reconciliation_100.csv");
		writer = new ReconciliationCsvWriter(tempDir.resolve("nested/result.csv").toString());
	}

	@AfterEach
	void cleanUp() {
		StepSynchronizationManager.close();
	}

	private ReconciliationProcessingResult result(ReconciliationStatus status, ReconciliationAction action,
			TransactionRequestDto input, String error, BigDecimal previousAmount) {

		return new ReconciliationProcessingResult(input, null, status, action, error, previousAmount);
	}

	private TransactionRequestDto input(String transactionId, String amount, Long lineNumber) {

		TransactionRequestDto dto = new TransactionRequestDto();
		dto.setTransactionId(transactionId);
		dto.setAmount(amount);
		dto.setLineNumber(lineNumber);
		return dto;
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private void withStepContext(ThrowingRunnable action) throws Exception {
		withStepContext(100L, 200L, action);
	}

	private void withStepContext(Long jobExecutionId, ThrowingRunnable action) throws Exception {
		withStepContext(100L, jobExecutionId, action);
	}

	private void withStepContext(Long jobInstanceId, Long jobExecutionId, ThrowingRunnable action) throws Exception {

		StepSynchronizationManager.close();

		JobInstance instance = mock(JobInstance.class);
		JobExecution jobExecution = mock(JobExecution.class);
		StepExecution stepExecution = mock(StepExecution.class);

		when(instance.getInstanceId()).thenReturn(jobInstanceId);
		when(jobExecution.getJobInstance()).thenReturn(instance);

		if (jobExecutionId != null) {
			when(jobExecution.getId()).thenReturn(jobExecutionId);
		}

		when(stepExecution.getJobExecution()).thenReturn(jobExecution);
		StepSynchronizationManager.register(stepExecution);

		try {
			action.run();
		} finally {
			StepSynchronizationManager.close();
		}
	}

	private void initializeOutputForTest() throws Exception {
		withStepContext(() -> writer.initializeOutput());
	}

	@Test
	void shouldInitializeOutputAndCreateParentDirectories() throws Exception {
		initializeOutputForTest();

		assertTrue(Files.exists(output));
		assertEquals(Messages.CSV_OUTPUTFILE_HEADER + System.lineSeparator(), Files.readString(output));
	}

	@Test
	void shouldPreserveExistingOutputWhenSameJobInstanceRestarts() throws Exception {
		Files.createDirectories(output.getParent());
		Files.writeString(output, "existing output" + System.lineSeparator());

		initializeOutputForTest();

		assertEquals("existing output" + System.lineSeparator(), Files.readString(output));
	}

	@Test
	void shouldCreateSeparateOutputForDifferentJobInstance() throws Exception {
		initializeOutputForTest();

		Path secondOutput = output.getParent().resolve("reconciliation_101.csv");

		withStepContext(101L, 201L, () -> writer.initializeOutput());

		assertTrue(Files.exists(output));
		assertTrue(Files.exists(secondOutput));
		assertEquals(Messages.CSV_OUTPUTFILE_HEADER + System.lineSeparator(), Files.readString(secondOutput));
	}

	@Test
	void shouldWriteResultsToSeparateFilesForDifferentJobInstances() throws Exception {
		ReconciliationProcessingResult first = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				input("TX100", null, 1L), "Invalid amount", null);

		ReconciliationProcessingResult second = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				input("TX101", null, 1L), "Invalid amount", null);

		withStepContext(100L, 200L, () -> writer.writeResults(List.of(first)));

		withStepContext(101L, 201L, () -> writer.writeResults(List.of(second)));

		Path firstOutput = output.getParent().resolve("reconciliation_100.csv");
		Path secondOutput = output.getParent().resolve("reconciliation_101.csv");

		assertTrue(Files.exists(firstOutput));
		assertTrue(Files.exists(secondOutput));
		assertTrue(Files.readString(firstOutput).contains("TX100"));
		assertFalse(Files.readString(firstOutput).contains("TX101"));
		assertTrue(Files.readString(secondOutput).contains("TX101"));
		assertFalse(Files.readString(secondOutput).contains("TX100"));
	}

	@Test
	void shouldRejectInitializationWithoutStepContext() {
		StepSynchronizationManager.close();

		IllegalStateException exception = assertThrows(IllegalStateException.class, () -> writer.initializeOutput());

		assertEquals(Messages.STEP_CONTEXT_NOT_FOUND, exception.getMessage());
	}

	@Test
	void shouldIgnoreEmptyResults() throws Exception {
		StepSynchronizationManager.close();

		writer.writeResults(List.of());

		assertFalse(Files.exists(output));
	}

	@Test
	void shouldIgnoreNonReportableStatuses() throws Exception {
		initializeOutputForTest();

		List<ReconciliationProcessingResult> results = List.of(
				result(ReconciliationStatus.VALID, ReconciliationAction.INSERT, input("TX1", "100", 2L), null, null),
				result(ReconciliationStatus.MATCH, ReconciliationAction.NONE, input("TX2", "200", 3L), null, null));

		StepSynchronizationManager.close();

		assertDoesNotThrow(() -> writer.writeResults(results));

		assertEquals(Messages.CSV_OUTPUTFILE_HEADER + System.lineSeparator(), Files.readString(output));
	}

	@Test
	void shouldIgnoreNonReportableResultsWithoutStepContext() {
		StepSynchronizationManager.close();

		ReconciliationProcessingResult match = result(ReconciliationStatus.MATCH, ReconciliationAction.NONE,
				input("TX10", "10.00", 11L), null, null);

		assertDoesNotThrow(() -> writer.writeResults(List.of(match)));
	}

	@Test
	void shouldRejectReportableResultsWithoutStepContext() throws Exception {
		ReconciliationProcessingResult invalid = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				input("TX1", null, 2L), "Invalid amount", null);

		StepSynchronizationManager.close();

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> writer.writeResults(List.of(invalid)));

		assertEquals(Messages.STEP_CONTEXT_NOT_FOUND, exception.getMessage());
	}

	@Test
	void shouldWriteInvalidRecord() throws Exception {
		ReconciliationProcessingResult invalid = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				input("TX1", null, 2L), "Invalid amount", null);

		withStepContext(() -> writer.writeResults(List.of(invalid)));

		String content = Files.readString(output);
		assertTrue(content.contains(Messages.CSV_OUTPUTFILE_HEADER));
		assertTrue(content.contains("TX1"));
		assertTrue(content.contains("INVALID_RECORD"));
		assertTrue(content.contains("SKIP"));
		assertTrue(content.contains("Invalid amount"));
	}

	@Test
	void shouldWriteDuplicateRecord() throws Exception {
		ReconciliationProcessingResult duplicate = result(ReconciliationStatus.DUPLICATE_IN_CSV1,
				ReconciliationAction.SKIP, input("TX2", "50.00", 3L), "Duplicate transaction", null);

		withStepContext(() -> writer.writeResults(List.of(duplicate)));

		String content = Files.readString(output);
		assertTrue(content.contains("TX2"));
		assertTrue(content.contains("DUPLICATE_IN_CSV1"));
		assertTrue(content.contains("Duplicate transaction"));
	}

	@Test
	void shouldWriteMismatchWithBothAmounts() throws Exception {
		ReconciliationProcessingResult mismatch = result(ReconciliationStatus.MISMATCH,
				ReconciliationAction.UPDATE_AMOUNT, input("TX3", "150.50", 4L), "Amount changed",
				new BigDecimal("100.00"));

		withStepContext(() -> writer.writeResults(List.of(mismatch)));

		String content = Files.readString(output);
		assertTrue(content.contains("TX3"));
		assertTrue(content.contains("MISMATCH"));
		assertTrue(content.contains("UPDATE_AMOUNT"));
		assertTrue(content.contains("Amount changed"));
		assertTrue(content.contains("100.00"));
		assertTrue(content.contains("150.50"));
	}

	@Test
	void shouldWriteMissingTransaction() throws Exception {
		ReconciliationProcessingResult missing = result(ReconciliationStatus.MISSING_IN_DATABASE,
				ReconciliationAction.REPORT, input("TX4", "25.00", 5L), "Transaction not found", null);

		withStepContext(() -> writer.writeResults(List.of(missing)));

		String content = Files.readString(output);
		assertTrue(content.contains("TX4"));
		assertTrue(content.contains("MISSING_IN_DATABASE"));
		assertTrue(content.contains("REPORT"));
		assertTrue(content.contains("Transaction not found"));
	}

	@Test
	void shouldWriteEmptyFieldsWhenInputIsNull() throws Exception {
		ReconciliationProcessingResult invalid = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				null, null, null);

		withStepContext(() -> writer.writeResults(List.of(invalid)));

		String content = Files.readString(output);
		assertTrue(content.contains("INVALID_RECORD"));
		assertTrue(content.contains("SKIP"));
		assertFalse(content.contains("null"));
	}

	@Test
	void shouldWriteEmptyFieldsWhenInputValuesAreNull() throws Exception {
		ReconciliationProcessingResult invalid = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				input(null, null, null), null, null);

		withStepContext(() -> writer.writeResults(List.of(invalid)));

		String content = Files.readString(output);
		assertTrue(content.contains("INVALID_RECORD"));
		assertTrue(content.contains("SKIP"));
		assertFalse(content.contains("null"));
	}

	@Test
	void shouldWriteEmptyNewAmountWhenMismatchAmountIsNull() throws Exception {
		ReconciliationProcessingResult mismatch = result(ReconciliationStatus.MISMATCH,
				ReconciliationAction.UPDATE_AMOUNT, input("TX5", null, 6L), null, null);

		withStepContext(() -> writer.writeResults(List.of(mismatch)));

		String content = Files.readString(output);
		assertTrue(content.contains("TX5"));
		assertTrue(content.contains("MISMATCH"));
		assertTrue(content.contains("UPDATE_AMOUNT"));
		assertFalse(content.contains("null"));
	}

	@Test
	void shouldWriteEmptyPreviousAmountWhenPreviousAmountIsNull() throws Exception {
		ReconciliationProcessingResult mismatch = result(ReconciliationStatus.MISMATCH,
				ReconciliationAction.UPDATE_AMOUNT, input("TX8", "300.00", 9L), "Amount changed", null);

		withStepContext(() -> writer.writeResults(List.of(mismatch)));

		String content = Files.readString(output);
		assertTrue(content.contains("TX8"));
		assertTrue(content.contains("MISMATCH"));
		assertTrue(content.contains("300.00"));
	}

	@Test
	void shouldWriteMismatchWithNullInput() throws Exception {
		ReconciliationProcessingResult mismatch = result(ReconciliationStatus.MISMATCH,
				ReconciliationAction.UPDATE_AMOUNT, null, "Input unavailable", null);

		withStepContext(() -> writer.writeResults(List.of(mismatch)));

		String content = Files.readString(output);
		assertTrue(content.contains("MISMATCH"));
		assertTrue(content.contains("UPDATE_AMOUNT"));
		assertTrue(content.contains("Input unavailable"));
		assertFalse(content.contains("null"));
	}

	@Test
	void shouldWriteNonMismatchWithNullInput() throws Exception {
		ReconciliationProcessingResult invalid = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				null, "Invalid input", new BigDecimal("10.00"));

		withStepContext(() -> writer.writeResults(List.of(invalid)));

		String content = Files.readString(output);
		assertTrue(content.contains("INVALID_RECORD"));
		assertTrue(content.contains("Invalid input"));
		assertTrue(content.contains("10.00"));
	}

	@Test
	void shouldWriteReportableResultWhenJobExecutionIdIsNotStubbed() throws Exception {
		ReconciliationProcessingResult invalid = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				input("TX9", null, 10L), "Invalid record", null);

		withStepContext((Long) null, () -> writer.writeResults(List.of(invalid)));

		String content = Files.readString(output);
		assertTrue(content.contains("TX9"));
		assertTrue(content.contains("INVALID_RECORD"));
	}

	@Test
	void shouldAppendMultipleResultsWithoutRepeatingHeader() throws Exception {
		ReconciliationProcessingResult first = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				input("TX6", null, 7L), "Invalid record", null);

		ReconciliationProcessingResult second = result(ReconciliationStatus.MISSING_IN_DATABASE,
				ReconciliationAction.REPORT, input("TX7", "20.00", 8L), "Missing transaction", null);

		withStepContext(() -> writer.writeResults(List.of(first, second)));

		String content = Files.readString(output);

		assertTrue(content.contains("TX6"));
		assertTrue(content.contains("TX7"));
		assertEquals(1, content.lines().filter(line -> line.equals(Messages.CSV_OUTPUTFILE_HEADER)).count());
		assertEquals(3, content.lines().count());
	}

	@Test
	void shouldAppendToExistingOutputWithoutRemovingPreviousRows() throws Exception {
		Files.createDirectories(output.getParent());

		Files.writeString(output,
				Messages.CSV_OUTPUTFILE_HEADER + System.lineSeparator() + "previous row" + System.lineSeparator(),
				StandardCharsets.UTF_8);

		ReconciliationProcessingResult invalid = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				input("TX13", null, 14L), "Invalid record", null);

		withStepContext(() -> writer.writeResults(List.of(invalid)));

		List<String> lines = Files.readAllLines(output);

		assertEquals(3, lines.size());
		assertEquals("previous row", lines.get(1));
		assertTrue(lines.get(2).contains("TX13"));
	}
}

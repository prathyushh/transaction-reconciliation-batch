
package com.example.springbatch.batch.writer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
		output = tempDir.resolve("nested/result.csv");
		writer = new ReconciliationCsvWriter(output.toString());
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

	private void withStepContext(Runnable action) {
		withStepContext(200L, action);
	}

	private void withStepContext(Long jobExecutionId, Runnable action) {
		JobInstance instance = mock(JobInstance.class);
		JobExecution jobExecution = mock(JobExecution.class);
		StepExecution stepExecution = mock(StepExecution.class);

		when(instance.getInstanceId()).thenReturn(100L);
		when(jobExecution.getJobInstance()).thenReturn(instance);

		// JobExecution.getId() returns primitive long.
		// It cannot be stubbed with null.
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

	@Test
	void shouldInitializeOutputAndCreateParentDirectories() throws Exception {
		writer.initializeOutput();

		assertTrue(Files.exists(output));
		assertEquals(Messages.CSV_OUTPUTFILE_HEADER + System.lineSeparator(), Files.readString(output));
	}

	@Test
	void shouldTruncateExistingOutputOnInitialization() throws Exception {
		Files.createDirectories(output.getParent());
		Files.writeString(output, "old content");

		writer.initializeOutput();

		assertEquals(Messages.CSV_OUTPUTFILE_HEADER + System.lineSeparator(), Files.readString(output));
	}

	@Test
	void shouldIgnoreEmptyResults() throws Exception {
		writer.initializeOutput();

		writer.writeResults(List.of());

		assertEquals(Messages.CSV_OUTPUTFILE_HEADER + System.lineSeparator(), Files.readString(output));
	}

	@Test
	void shouldIgnoreNonReportableStatuses() throws Exception {
		writer.initializeOutput();

		List<ReconciliationProcessingResult> results = List.of(
				result(ReconciliationStatus.VALID, ReconciliationAction.INSERT, input("TX1", "100", 2L), null, null),
				result(ReconciliationStatus.MATCH, ReconciliationAction.NONE, input("TX2", "200", 3L), null, null));

		writer.writeResults(results);

		assertEquals(Messages.CSV_OUTPUTFILE_HEADER + System.lineSeparator(), Files.readString(output));
	}

	@Test
	void shouldRejectReportableResultsWithoutStepContext() throws Exception {
		writer.initializeOutput();

		ReconciliationProcessingResult invalid = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				input("TX1", null, 2L), "Invalid amount", null);

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> writer.writeResults(List.of(invalid)));

		assertEquals(Messages.STEP_CONTEXT_NOT_FOUND, exception.getMessage());
	}

	@Test
	void shouldWriteInvalidRecord() throws Exception {
		writer.initializeOutput();

		ReconciliationProcessingResult invalid = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				input("TX1", null, 2L), "Invalid amount", null);

		withStepContext(() -> assertDoesNotThrow(() -> writer.writeResults(List.of(invalid))));

		String content = Files.readString(output);

		assertTrue(content.contains("TX1"));
		assertTrue(content.contains("INVALID_RECORD"));
		assertTrue(content.contains("SKIP"));
		assertTrue(content.contains("Invalid amount"));
	}

	@Test
	void shouldWriteDuplicateRecord() throws Exception {
		writer.initializeOutput();

		ReconciliationProcessingResult duplicate = result(ReconciliationStatus.DUPLICATE_IN_CSV1,
				ReconciliationAction.SKIP, input("TX2", "50.00", 3L), "Duplicate transaction", null);

		withStepContext(() -> assertDoesNotThrow(() -> writer.writeResults(List.of(duplicate))));

		String content = Files.readString(output);

		assertTrue(content.contains("TX2"));
		assertTrue(content.contains("DUPLICATE_IN_CSV1"));
		assertTrue(content.contains("Duplicate transaction"));
	}

	@Test
	void shouldWriteMismatchWithBothAmounts() throws Exception {
		writer.initializeOutput();

		ReconciliationProcessingResult mismatch = result(ReconciliationStatus.MISMATCH,
				ReconciliationAction.UPDATE_AMOUNT, input("TX3", "150.50", 4L), "Amount changed",
				new BigDecimal("100.00"));

		withStepContext(() -> assertDoesNotThrow(() -> writer.writeResults(List.of(mismatch))));

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
		writer.initializeOutput();

		ReconciliationProcessingResult missing = result(ReconciliationStatus.MISSING_IN_DATABASE,
				ReconciliationAction.REPORT, input("TX4", "25.00", 5L), "Transaction not found", null);

		withStepContext(() -> assertDoesNotThrow(() -> writer.writeResults(List.of(missing))));

		String content = Files.readString(output);

		assertTrue(content.contains("TX4"));
		assertTrue(content.contains("MISSING_IN_DATABASE"));
		assertTrue(content.contains("REPORT"));
		assertTrue(content.contains("Transaction not found"));
	}

	@Test
	void shouldWriteEmptyFieldsWhenInputIsNull() throws Exception {
		writer.initializeOutput();

		ReconciliationProcessingResult invalid = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				null, null, null);

		withStepContext(() -> assertDoesNotThrow(() -> writer.writeResults(List.of(invalid))));

		String content = Files.readString(output);

		assertTrue(content.contains("INVALID_RECORD"));
		assertTrue(content.contains("SKIP"));
		assertFalse(content.contains("null"));
	}

	@Test
	void shouldWriteEmptyFieldsWhenInputValuesAreNull() throws Exception {
		writer.initializeOutput();

		ReconciliationProcessingResult invalid = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				input(null, null, null), null, null);

		withStepContext(() -> assertDoesNotThrow(() -> writer.writeResults(List.of(invalid))));

		String content = Files.readString(output);

		assertTrue(content.contains("INVALID_RECORD"));
		assertTrue(content.contains("SKIP"));
		assertFalse(content.contains("null"));
	}

	@Test
	void shouldWriteEmptyNewAmountWhenMismatchAmountIsNull() throws Exception {
		writer.initializeOutput();

		ReconciliationProcessingResult mismatch = result(ReconciliationStatus.MISMATCH,
				ReconciliationAction.UPDATE_AMOUNT, input("TX5", null, 6L), null, null);

		withStepContext(() -> assertDoesNotThrow(() -> writer.writeResults(List.of(mismatch))));

		String content = Files.readString(output);

		assertTrue(content.contains("TX5"));
		assertTrue(content.contains("MISMATCH"));
		assertTrue(content.contains("UPDATE_AMOUNT"));
		assertFalse(content.contains("null"));
	}

	@Test
	void shouldWriteEmptyPreviousAmountWhenPreviousAmountIsNull() throws Exception {
		writer.initializeOutput();

		ReconciliationProcessingResult mismatch = result(ReconciliationStatus.MISMATCH,
				ReconciliationAction.UPDATE_AMOUNT, input("TX8", "300.00", 9L), "Amount changed", null);

		withStepContext(() -> assertDoesNotThrow(() -> writer.writeResults(List.of(mismatch))));

		String content = Files.readString(output);

		assertTrue(content.contains("TX8"));
		assertTrue(content.contains("MISMATCH"));
		assertTrue(content.contains("300.00"));
	}

	@Test
	void shouldWriteMismatchWithNullInput() throws Exception {
		writer.initializeOutput();

		ReconciliationProcessingResult mismatch = result(ReconciliationStatus.MISMATCH,
				ReconciliationAction.UPDATE_AMOUNT, null, "Input unavailable", null);

		withStepContext(() -> assertDoesNotThrow(() -> writer.writeResults(List.of(mismatch))));

		String content = Files.readString(output);

		assertTrue(content.contains("MISMATCH"));
		assertTrue(content.contains("UPDATE_AMOUNT"));
		assertTrue(content.contains("Input unavailable"));
		assertFalse(content.contains("null"));
	}

	@Test
	void shouldWriteMismatchWithNullInputAmountAndPreviousAmount() throws Exception {
		writer.initializeOutput();

		ReconciliationProcessingResult mismatch = result(ReconciliationStatus.MISMATCH,
				ReconciliationAction.UPDATE_AMOUNT, input("TX11", null, 12L), null, null);

		withStepContext(() -> assertDoesNotThrow(() -> writer.writeResults(List.of(mismatch))));

		String content = Files.readString(output);

		assertTrue(content.contains("TX11"));
		assertTrue(content.contains("MISMATCH"));
		assertTrue(content.contains("UPDATE_AMOUNT"));
		assertFalse(content.contains("null"));
	}

	@Test
	void shouldWriteNonMismatchWithNullInput() throws Exception {
		writer.initializeOutput();

		ReconciliationProcessingResult invalid = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				null, "Invalid input", new BigDecimal("10.00"));

		withStepContext(() -> assertDoesNotThrow(() -> writer.writeResults(List.of(invalid))));

		String content = Files.readString(output);

		assertTrue(content.contains("INVALID_RECORD"));
		assertTrue(content.contains("Invalid input"));
		assertTrue(content.contains("10.00"));
	}

	@Test
	void shouldWriteMismatchWithTransactionIdAndAmount() throws Exception {
		writer.initializeOutput();

		ReconciliationProcessingResult mismatch = result(ReconciliationStatus.MISMATCH,
				ReconciliationAction.UPDATE_AMOUNT, input("TX12", "250.00", 13L), "Amount mismatch",
				new BigDecimal("200.00"));

		withStepContext(() -> assertDoesNotThrow(() -> writer.writeResults(List.of(mismatch))));

		String content = Files.readString(output);

		assertTrue(content.contains("TX12"));
		assertTrue(content.contains("250.00"));
		assertTrue(content.contains("200.00"));
		assertTrue(content.contains("Amount mismatch"));
	}

	@Test
	void shouldWriteInvalidRecordWithNullErrorMessageAndPreviousAmount() throws Exception {
		writer.initializeOutput();

		ReconciliationProcessingResult invalid = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				input("TX13", null, 14L), null, null);

		withStepContext(() -> assertDoesNotThrow(() -> writer.writeResults(List.of(invalid))));

		String content = Files.readString(output);

		assertTrue(content.contains("TX13"));
		assertTrue(content.contains("INVALID_RECORD"));
		assertTrue(content.contains("SKIP"));
		assertFalse(content.contains("null"));
	}

	@Test
	void shouldWriteReportableResultWhenJobExecutionIdIsNotExplicitlyStubbed() throws Exception {
		writer.initializeOutput();

		ReconciliationProcessingResult invalid = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				input("TX9", null, 10L), "Invalid record", null);

		// Primitive long IDs cannot be null; Mockito returns 0L by default.
		withStepContext(null, () -> assertDoesNotThrow(() -> writer.writeResults(List.of(invalid))));

		String content = Files.readString(output);

		assertTrue(content.contains("TX9"));
		assertTrue(content.contains("INVALID_RECORD"));
	}

	@Test
	void shouldAppendMultipleResultsWithoutRepeatingHeader() throws Exception {
		writer.initializeOutput();

		ReconciliationProcessingResult first = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				input("TX6", null, 7L), "Invalid record", null);

		ReconciliationProcessingResult second = result(ReconciliationStatus.MISSING_IN_DATABASE,
				ReconciliationAction.REPORT, input("TX7", "20.00", 8L), "Missing transaction", null);

		withStepContext(() -> assertDoesNotThrow(() -> writer.writeResults(List.of(first, second))));

		String content = Files.readString(output);

		assertTrue(content.contains("TX6"));
		assertTrue(content.contains("TX7"));

		assertEquals(1, content.lines().filter(line -> line.equals(Messages.CSV_OUTPUTFILE_HEADER)).count());

		assertEquals(3, content.lines().count());
	}

	@Test
	void shouldWriteReportableResultWithNullInputAndNullOptionalFields() throws Exception {
		writer.initializeOutput();

		ReconciliationProcessingResult invalid = result(ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP,
				null, null, null);

		withStepContext(() -> assertDoesNotThrow(() -> writer.writeResults(List.of(invalid))));

		String content = Files.readString(output);

		assertTrue(content.contains("INVALID_RECORD"));
		assertTrue(content.contains("SKIP"));
		assertFalse(content.contains("null"));
	}

	@Test
	void shouldIgnoreNonReportableResultWithoutStepContext() throws Exception {
		writer.initializeOutput();

		StepSynchronizationManager.close();

		ReconciliationProcessingResult match = result(ReconciliationStatus.MATCH, ReconciliationAction.NONE,
				input("TX10", "10.00", 11L), null, null);

		assertDoesNotThrow(() -> writer.writeResults(List.of(match)));

		assertEquals(Messages.CSV_OUTPUTFILE_HEADER + System.lineSeparator(), Files.readString(output));
	}
}

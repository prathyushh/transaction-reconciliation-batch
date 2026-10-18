package com.example.springbatch.batch.writer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.Chunk;

import com.example.springbatch.constants.Messages;
import com.example.springbatch.dto.TransactionRequestDto;
import com.example.springbatch.entity.TransactionRecord;
import com.example.springbatch.enums.ReconciliationAction;
import com.example.springbatch.enums.ReconciliationStatus;
import com.example.springbatch.repository.AuditLogRepository;
import com.example.springbatch.repository.TransactionRecordRepository;

class ReconciliationItemWriterTest {

	private TransactionRecordRepository transactionRepository;
	private ReconciliationCsvWriter csvWriter;
	private AuditLogRepository auditRepository;
	private ReconciliationItemWriter writer;

	@BeforeEach
	void setUp() {
		transactionRepository = mock(TransactionRecordRepository.class);
		csvWriter = mock(ReconciliationCsvWriter.class);
		auditRepository = mock(AuditLogRepository.class);

		JobInstance instance = mock(JobInstance.class);
		JobExecution execution = mock(JobExecution.class);
		StepExecution step = mock(StepExecution.class);

		when(instance.getInstanceId()).thenReturn(100L);
		when(execution.getJobInstance()).thenReturn(instance);
		when(step.getJobExecution()).thenReturn(execution);

		StepSynchronizationManager.register(step);

		when(auditRepository.incrementCounts(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
				anyLong(), anyLong())).thenReturn(1);

		writer = new ReconciliationItemWriter(transactionRepository, csvWriter, auditRepository);
	}

	@AfterEach
	void clearStepContext() {
		StepSynchronizationManager.close();
	}

	private ReconciliationProcessingResult result(ReconciliationStatus status, TransactionRecord transaction,
			TransactionRequestDto input) {

		return new ReconciliationProcessingResult(input, transaction, status,
				status == ReconciliationStatus.MISMATCH ? ReconciliationAction.UPDATE_AMOUNT
						: ReconciliationAction.NONE,
				null, null);
	}

	private TransactionRecord transaction(String id, String amount) {
		return TransactionRecord.builder().jobInstanceId(100L).transactionId(id).amount(new BigDecimal(amount)).build();
	}

	private TransactionRequestDto input(String id, String amount) {
		TransactionRequestDto dto = new TransactionRequestDto();
		dto.setTransactionId(id);
		dto.setAmount(amount);
		return dto;
	}

	@Test
	void shouldIgnoreEmptyChunk() throws Exception {
		writer.write(new Chunk<>());

		verifyNoInteractions(transactionRepository, csvWriter, auditRepository);
	}

	@Test
	void shouldCountMatchedRecords() throws Exception {
		var match = result(ReconciliationStatus.MATCH, transaction("TX1", "100"), input("TX1", "100"));

		writer.write(new Chunk<>(List.of(match)));

		verifyNoInteractions(transactionRepository);
		verify(csvWriter).writeResults(List.of(match));

		verify(auditRepository).incrementCounts(100L, 1L, 0L, 1L, 0L, 0L, 0L, 0L);
	}

	@Test
	void shouldUpdateAmountForMismatch() throws Exception {
		TransactionRecord record = transaction("TX1", "100");

		var mismatch = result(ReconciliationStatus.MISMATCH, record, input("TX1", "125.50"));

		writer.write(new Chunk<>(List.of(mismatch)));

		assertEquals(new BigDecimal("125.50"), record.getAmount());
		assertNotNull(record.getUpdatedAt());

		verify(transactionRepository).save(record);
		verify(csvWriter).writeResults(List.of(mismatch));

		verify(auditRepository).incrementCounts(100L, 1L, 0L, 0L, 1L, 0L, 0L, 0L);
	}

	@Test
	void shouldNotUpdateMismatchWhenTransactionIsNull() throws Exception {
		var mismatch = result(ReconciliationStatus.MISMATCH, null, input("TX1", "125.50"));

		writer.write(new Chunk<>(List.of(mismatch)));

		verifyNoInteractions(transactionRepository);
		verify(csvWriter).writeResults(List.of(mismatch));

		verify(auditRepository).incrementCounts(100L, 1L, 0L, 0L, 0L, 0L, 0L, 0L);
	}

	@Test
	void shouldNotUpdateMismatchWhenInputIsNull() throws Exception {
		var mismatch = result(ReconciliationStatus.MISMATCH, transaction("TX1", "100"), null);

		writer.write(new Chunk<>(List.of(mismatch)));

		verifyNoInteractions(transactionRepository);

		verify(auditRepository).incrementCounts(100L, 1L, 0L, 0L, 0L, 0L, 0L, 0L);
	}

	@Test
	void shouldNotUpdateMismatchWhenCsv2AmountIsNull() throws Exception {
		var mismatch = result(ReconciliationStatus.MISMATCH, transaction("TX1", "100"), input("TX1", null));

		writer.write(new Chunk<>(List.of(mismatch)));

		verifyNoInteractions(transactionRepository);

		verify(auditRepository).incrementCounts(100L, 1L, 0L, 0L, 0L, 0L, 0L, 0L);
	}

	@Test
	void shouldCountMissingTransactions() throws Exception {
		var missing = result(ReconciliationStatus.MISSING_IN_DATABASE, null, input("TX1", "100"));

		writer.write(new Chunk<>(List.of(missing)));

		verifyNoInteractions(transactionRepository);

		verify(csvWriter).writeResults(List.of(missing));

		verify(auditRepository).incrementCounts(100L, 1L, 0L, 0L, 0L, 0L, 0L, 1L);
	}

	@Test
	void shouldIgnoreStatusesNotUsedForReconciliationCounters() throws Exception {
		var valid = result(ReconciliationStatus.VALID, transaction("TX1", "100"), input("TX1", "100"));

		var invalid = result(ReconciliationStatus.INVALID_RECORD, null, input("TX2", "invalid"));

		writer.write(new Chunk<>(List.of(valid, invalid)));

		verifyNoInteractions(transactionRepository);

		verify(csvWriter).writeResults(List.of(valid, invalid));

		verify(auditRepository).incrementCounts(100L, 2L, 0L, 0L, 0L, 0L, 0L, 0L);
	}

	@Test
	void shouldThrowWhenStepContextIsMissing() throws Exception {
		StepSynchronizationManager.close();

		var match = result(ReconciliationStatus.MATCH, transaction("TX1", "100"), input("TX1", "100"));

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> writer.write(new Chunk<>(List.of(match))));

		assertEquals(Messages.STEP_CONTEXT_NOT_FOUND, exception.getMessage());

		verify(csvWriter).writeResults(List.of(match));
		verifyNoInteractions(auditRepository);
	}

	@Test
	void shouldThrowWhenAuditSummaryIsNotUpdated() throws Exception {
		when(auditRepository.incrementCounts(anyLong(), anyLong(), anyLong(), anyLong(), anyLong(), anyLong(),
				anyLong(), anyLong())).thenReturn(0);

		var match = result(ReconciliationStatus.MATCH, transaction("TX1", "100"), input("TX1", "100"));

		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> writer.write(new Chunk<>(List.of(match))));

		assertTrue(exception.getMessage().contains("Audit summary not found for job instance 100"));

		verify(auditRepository).incrementCounts(100L, 1L, 0L, 1L, 0L, 0L, 0L, 0L);
	}
}

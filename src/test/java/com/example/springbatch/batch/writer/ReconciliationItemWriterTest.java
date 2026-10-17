package com.example.springbatch.batch.writer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;

import com.example.springbatch.dto.TransactionRequestDto;
import com.example.springbatch.entity.TransactionRecord;
import com.example.springbatch.enums.ReconciliationAction;
import com.example.springbatch.enums.ReconciliationStatus;
import com.example.springbatch.repository.TransactionRecordRepository;
import com.example.springbatch.service.AuditLogService;

class ReconciliationItemWriterTest {

	private TransactionRecordRepository repository;
	private ReconciliationCsvWriter csvWriter;
	private AuditLogService auditService;
	private ReconciliationItemWriter writer;

	@BeforeEach
	void setUp() {
		repository = mock(TransactionRecordRepository.class);
		csvWriter = mock(ReconciliationCsvWriter.class);
		auditService = mock(AuditLogService.class);

		writer = new ReconciliationItemWriter(repository, csvWriter, auditService);
	}

	private ReconciliationProcessingResult result(ReconciliationStatus status, TransactionRecord record,
			TransactionRequestDto input) {

		return new ReconciliationProcessingResult(input, record, status,
				status == ReconciliationStatus.MISMATCH ? ReconciliationAction.UPDATE_AMOUNT
						: ReconciliationAction.NONE,
				null, record == null ? null : record.getAmount());
	}

	private TransactionRequestDto input(String amount) {
		TransactionRequestDto dto = new TransactionRequestDto();
		dto.setTransactionId("TX1");
		dto.setAmount(amount);
		return dto;
	}

	private TransactionRecord record() {
		return TransactionRecord.builder().transactionId("TX1").amount(new BigDecimal("100.00")).build();
	}

	@Test
	void shouldIgnoreEmptyChunk() throws Exception {
		writer.write(new Chunk<>());

		verifyNoInteractions(repository, csvWriter, auditService);
	}

	@Test
	void shouldCountMatchedRecords() throws Exception {
		var match = result(ReconciliationStatus.MATCH, null, input("100"));

		writer.write(new Chunk<>(List.of(match)));

		verifyNoInteractions(repository);
		verify(auditService).recordCsv2Chunk(1, 1, 0, 0);
		verify(csvWriter).writeResults(List.of(match));
	}

	@Test
	void shouldUpdateAmountForMismatch() throws Exception {
		TransactionRecord transaction = record();

		writer.write(new Chunk<>(List.of(result(ReconciliationStatus.MISMATCH, transaction, input("150.50")))));

		assertEquals(new BigDecimal("150.50"), transaction.getAmount());
		assertNotNull(transaction.getUpdatedAt());

		verify(repository).save(transaction);
		verify(auditService).recordCsv2Chunk(1, 0, 1, 0);
	}

	@Test
	void shouldNotUpdateWhenTransactionIsNull() throws Exception {
		writer.write(new Chunk<>(List.of(result(ReconciliationStatus.MISMATCH, null, input("150")))));

		verifyNoInteractions(repository);
		verify(auditService).recordCsv2Chunk(1, 0, 0, 0);
	}

	@Test
	void shouldNotUpdateWhenInputIsNull() throws Exception {
		writer.write(new Chunk<>(List.of(result(ReconciliationStatus.MISMATCH, record(), null))));

		verifyNoInteractions(repository);
		verify(auditService).recordCsv2Chunk(1, 0, 0, 0);
	}

	@Test
	void shouldNotUpdateWhenAmountIsNull() throws Exception {
		writer.write(new Chunk<>(List.of(result(ReconciliationStatus.MISMATCH, record(), input(null)))));

		verifyNoInteractions(repository);
		verify(auditService).recordCsv2Chunk(1, 0, 0, 0);
	}

	@Test
	void shouldCountMissingTransactions() throws Exception {
		var missing = result(ReconciliationStatus.MISSING_IN_DATABASE, null, input("100"));

		writer.write(new Chunk<>(List.of(missing)));

		verifyNoInteractions(repository);
		verify(auditService).recordCsv2Chunk(1, 0, 0, 1);
		verify(csvWriter).writeResults(List.of(missing));
	}

	@Test
	void shouldIgnoreOtherStatusesForCounters() throws Exception {
		var invalid = result(ReconciliationStatus.INVALID_RECORD, null, input("100"));

		writer.write(new Chunk<>(List.of(invalid)));

		verifyNoInteractions(repository);
		verify(auditService).recordCsv2Chunk(1, 0, 0, 0);
	}

	@Test
	void shouldHandleMultipleStatusesInOneChunk() throws Exception {
		TransactionRecord transaction = record();

		var match = result(ReconciliationStatus.MATCH, null, input("100"));

		var mismatch = result(ReconciliationStatus.MISMATCH, transaction, input("150"));

		var missing = result(ReconciliationStatus.MISSING_IN_DATABASE, null, input("200"));

		var invalid = result(ReconciliationStatus.INVALID_RECORD, null, input(null));

		writer.write(new Chunk<>(List.of(match, mismatch, missing, invalid)));

		assertEquals(new BigDecimal("150"), transaction.getAmount());

		verify(repository).save(transaction);
		verify(csvWriter).writeResults(List.of(match, mismatch, missing, invalid));
		verify(auditService).recordCsv2Chunk(4, 1, 1, 1);
	}
}
package com.example.springbatch.batch.processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import com.example.springbatch.batch.writer.ReconciliationProcessingResult;
import com.example.springbatch.constants.Messages;
import com.example.springbatch.dto.TransactionRequestDto;
import com.example.springbatch.entity.TransactionRecord;
import com.example.springbatch.enums.ReconciliationAction;
import com.example.springbatch.enums.ReconciliationStatus;
import com.example.springbatch.repository.TransactionRecordRepository;

class ReconciliationProcessorTest {

	private TransactionRecordRepository repository;
	private ReconciliationProcessor processor;

	@BeforeEach
	void setUp() {
		repository = mock(TransactionRecordRepository.class);
		processor = new ReconciliationProcessor(repository, 100L);
	}

	private TransactionRequestDto input(String amount) {
		TransactionRequestDto dto = new TransactionRequestDto();
		dto.setTransactionId("TXN001");
		dto.setAmount(amount);
		return dto;
	}

	private TransactionRecord record(String amount) {
		return TransactionRecord.builder().jobInstanceId(100L).transactionId("TXN001").amount(new BigDecimal(amount))
				.build();
	}

	@Test
	void shouldReturnMatchWhenAmountsAreEqual() {
		when(repository.findByJobInstanceIdAndTransactionId(100L, "TXN001")).thenReturn(Optional.of(record("100.00")));

		ReconciliationProcessingResult result = processor.process(input("100.0"));

		assertEquals(ReconciliationStatus.MATCH, result.status());
		assertEquals(ReconciliationAction.NONE, result.action());
		assertEquals(new BigDecimal("100.00"), result.previousAmount());
		assertNull(result.errorMessage());
	}

	@Test
	void shouldReturnMismatchWhenAmountsDiffer() {
		when(repository.findByJobInstanceIdAndTransactionId(100L, "TXN001")).thenReturn(Optional.of(record("100.00")));

		ReconciliationProcessingResult result = processor.process(input("125.50"));

		assertEquals(ReconciliationStatus.MISMATCH, result.status());
		assertEquals(ReconciliationAction.UPDATE_AMOUNT, result.action());
		assertEquals(Messages.DATABASE_AMOUNT_UPDATED_TO_CSV2, result.errorMessage());
		assertEquals(new BigDecimal("100.00"), result.previousAmount());
	}

	@Test
	void shouldReturnMissingWhenTransactionDoesNotExist() {
		when(repository.findByJobInstanceIdAndTransactionId(100L, "TXN001")).thenReturn(Optional.empty());

		ReconciliationProcessingResult result = processor.process(input("100"));

		assertEquals(ReconciliationStatus.MISSING_IN_DATABASE, result.status());
		assertEquals(ReconciliationAction.REPORT, result.action());
		assertEquals(Messages.TRANSACTION_NOT_FOUND_IN_DATABASE, result.errorMessage());
		assertNull(result.transaction());
		assertNull(result.previousAmount());
	}

	static Stream<Arguments> equalAmounts() {
		return Stream.of(Arguments.of("100", "100.00"), Arguments.of("0", "0.00"), Arguments.of("-10.5", "-10.50"));
	}

	@ParameterizedTest
	@MethodSource("equalAmounts")
	void shouldCompareAmountsNumerically(String databaseAmount, String csvAmount) {
		when(repository.findByJobInstanceIdAndTransactionId(100L, "TXN001"))
				.thenReturn(Optional.of(record(databaseAmount)));

		assertEquals(ReconciliationStatus.MATCH, processor.process(input(csvAmount)).status());
	}

	@Test
	void shouldLookUpUsingCurrentJobInstanceAndTransactionId() {
		when(repository.findByJobInstanceIdAndTransactionId(100L, "TXN001")).thenReturn(Optional.of(record("5")));

		processor.process(input("5"));

		verify(repository).findByJobInstanceIdAndTransactionId(100L, "TXN001");
	}
}
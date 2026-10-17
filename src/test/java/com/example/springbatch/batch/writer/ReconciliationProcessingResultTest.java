package com.example.springbatch.batch.writer;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.example.springbatch.dto.TransactionRequestDto;
import com.example.springbatch.entity.TransactionRecord;
import com.example.springbatch.enums.ReconciliationAction;
import com.example.springbatch.enums.ReconciliationStatus;

class ReconciliationProcessingResultTest {

	@Test
	void shouldBeValidWhenTransactionExists() {
		TransactionRecord record = new TransactionRecord();

		ReconciliationProcessingResult result = new ReconciliationProcessingResult(new TransactionRequestDto(), record,
				ReconciliationStatus.VALID, ReconciliationAction.INSERT, null, null);

		assertTrue(result.isValid());
		assertSame(record, result.transaction());
	}

	@Test
	void shouldBeInvalidWhenTransactionIsNull() {
		ReconciliationProcessingResult result = new ReconciliationProcessingResult(new TransactionRequestDto(), null,
				ReconciliationStatus.INVALID_RECORD, ReconciliationAction.SKIP, "Invalid", null);

		assertFalse(result.isValid());
	}

	@Test
	void shouldPreserveAllRecordFields() {
		TransactionRequestDto dto = new TransactionRequestDto();
		TransactionRecord record = new TransactionRecord();
		BigDecimal previousAmount = new BigDecimal("10.50");

		ReconciliationProcessingResult result = new ReconciliationProcessingResult(dto, record,
				ReconciliationStatus.MISMATCH, ReconciliationAction.UPDATE_AMOUNT, "Amount changed", previousAmount);

		assertSame(dto, result.input());
		assertSame(record, result.transaction());
		assertEquals(ReconciliationStatus.MISMATCH, result.status());
		assertEquals(ReconciliationAction.UPDATE_AMOUNT, result.action());
		assertEquals("Amount changed", result.errorMessage());
		assertEquals(previousAmount, result.previousAmount());
	}
}
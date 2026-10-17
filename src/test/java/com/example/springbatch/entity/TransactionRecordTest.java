package com.example.springbatch.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class TransactionRecordTest {

	@Test
	void shouldBuildRecordWithAllFields() {
		LocalDateTime now = LocalDateTime.now();

		TransactionRecord record = TransactionRecord.builder().id(1L).jobInstanceId(10L).transactionId("TX1")
				.accountNumber("12345").transactionDate(LocalDate.of(2026, 1, 15)).amount(new BigDecimal("100.50"))
				.currency("INR").transactionType("CREDIT").description("Test").createdAt(now).updatedAt(now).build();

		assertEquals(1L, record.getId());
		assertEquals(10L, record.getJobInstanceId());
		assertEquals("TX1", record.getTransactionId());
		assertEquals("12345", record.getAccountNumber());
		assertEquals(LocalDate.of(2026, 1, 15), record.getTransactionDate());
		assertEquals(new BigDecimal("100.50"), record.getAmount());
		assertEquals("INR", record.getCurrency());
		assertEquals("CREDIT", record.getTransactionType());
		assertEquals("Test", record.getDescription());
		assertEquals(now, record.getCreatedAt());
		assertEquals(now, record.getUpdatedAt());
	}

	@Test
	void shouldSupportNoArgsAndAllArgsConstructors() {
		assertNotNull(new TransactionRecord());

		TransactionRecord record = new TransactionRecord(1L, 10L, "TX1", "12345", LocalDate.now(), BigDecimal.ONE,
				"INR", "CREDIT", null, LocalDateTime.now(), LocalDateTime.now());

		assertEquals("TX1", record.getTransactionId());
	}

	@Test
	void shouldSupportSettersEqualsHashCodeAndToString() {
		TransactionRecord first = TransactionRecord.builder().id(1L).transactionId("TX1").build();

		TransactionRecord second = TransactionRecord.builder().id(1L).transactionId("TX1").build();

		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
		assertTrue(first.toString().contains("TX1"));

		second.setTransactionId("TX2");
		assertNotEquals(first, second);
	}
}
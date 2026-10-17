package com.example.springbatch.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TransactionRequestDtoTest {

	@Test
	void shouldStoreAndReturnAllFields() {
		TransactionRequestDto dto = new TransactionRequestDto();

		dto.setTransactionId("TX1");
		dto.setAccountNumber("12345");
		dto.setTransactionDate("2026-01-15");
		dto.setAmount("100.50");
		dto.setCurrency("INR");
		dto.setTransactionType("CREDIT");
		dto.setDescription("Test");
		dto.setLineNumber(2L);

		assertEquals("TX1", dto.getTransactionId());
		assertEquals("12345", dto.getAccountNumber());
		assertEquals("2026-01-15", dto.getTransactionDate());
		assertEquals("100.50", dto.getAmount());
		assertEquals("INR", dto.getCurrency());
		assertEquals("CREDIT", dto.getTransactionType());
		assertEquals("Test", dto.getDescription());
		assertEquals(2L, dto.getLineNumber());
	}

	@Test
	void shouldSupportNoArgsConstructor() {
		assertNotNull(new TransactionRequestDto());
	}

	@Test
	void shouldSupportEqualsHashCodeAndToString() {
		TransactionRequestDto first = new TransactionRequestDto();
		first.setTransactionId("TX1");

		TransactionRequestDto second = new TransactionRequestDto();
		second.setTransactionId("TX1");

		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
		assertTrue(first.toString().contains("TX1"));

		second.setTransactionId("TX2");
		assertNotEquals(first, second);
	}
}
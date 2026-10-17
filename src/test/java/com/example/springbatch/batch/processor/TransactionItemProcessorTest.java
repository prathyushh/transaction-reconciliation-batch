package com.example.springbatch.batch.processor;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.example.springbatch.batch.writer.ReconciliationProcessingResult;
import com.example.springbatch.constants.Messages;
import com.example.springbatch.dto.TransactionRequestDto;
import com.example.springbatch.enums.ReconciliationAction;
import com.example.springbatch.enums.ReconciliationStatus;

class TransactionItemProcessorTest {

	private TransactionItemProcessor processor;

	@BeforeEach
	void setUp() {
		processor = new TransactionItemProcessor(100L);
	}

	private TransactionRequestDto validInput() {
		TransactionRequestDto dto = new TransactionRequestDto();
		dto.setTransactionId("TXN001");
		dto.setAccountNumber("123456789");
		dto.setTransactionDate("2026-01-15");
		dto.setAmount("100.50");
		dto.setCurrency("INR");
		dto.setTransactionType("CREDIT");
		dto.setDescription("Test transaction");
		dto.setLineNumber(2L);
		return dto;
	}

	@Test
	void shouldProcessValidRecord() {
		ReconciliationProcessingResult result = processor.process(validInput());

		assertEquals(ReconciliationStatus.VALID, result.status());
		assertEquals(ReconciliationAction.INSERT, result.action());
		assertNotNull(result.transaction());
		assertEquals(100L, result.transaction().getJobInstanceId());
		assertEquals("TXN001", result.transaction().getTransactionId());
		assertEquals(new BigDecimal("100.50"), result.transaction().getAmount());
		assertEquals(LocalDate.parse("2026-01-15"), result.transaction().getTransactionDate());
		assertNotNull(result.transaction().getCreatedAt());
		assertNotNull(result.transaction().getUpdatedAt());
		assertNull(result.errorMessage());
	}

	@Test
	void shouldTrimRequiredFields() {
		TransactionRequestDto dto = validInput();
		dto.setTransactionId(" TXN001 ");
		dto.setAccountNumber(" 12345 ");
		dto.setCurrency(" INR ");

		ReconciliationProcessingResult result = processor.process(dto);

		assertEquals("TXN001", result.transaction().getTransactionId());
		assertEquals("12345", result.transaction().getAccountNumber());
		assertEquals("INR", result.transaction().getCurrency());
	}

	@Test
	void shouldAcceptBoundaryLengths() {
		TransactionRequestDto dto = validInput();
		dto.setTransactionId("T".repeat(50));
		dto.setAccountNumber("1".repeat(17));
		dto.setCurrency("USD");
		dto.setDescription("D".repeat(500));

		assertEquals(ReconciliationStatus.VALID, processor.process(dto).status());
	}

	@Test
	void shouldAllowNullDescription() {
		TransactionRequestDto dto = validInput();
		dto.setDescription(null);

		ReconciliationProcessingResult result = processor.process(dto);

		assertEquals(ReconciliationStatus.VALID, result.status());
		assertNull(result.transaction().getDescription());
	}

	@Test
	void shouldRejectNullInput() {
		ReconciliationProcessingResult result = processor.process(null);

		assertInvalid(result, Messages.EMPTY_RECORD);
		assertNull(result.input());
	}

	static Stream<Arguments> missingRequiredFields() {
		return Stream.of(Arguments.of("transaction_id", Messages.MISSING_TRANSACTION_ID),
				Arguments.of("account_number", Messages.MISSING_ACCOUNT_NUMBER),
				Arguments.of("transaction_date", Messages.MISSING_TRANSACTION_DATE),
				Arguments.of("amount", Messages.MISSING_AMOUNT), Arguments.of("currency", Messages.MISSING_CURRENCY),
				Arguments.of("transaction_type", Messages.MISSING_TRANSACTION_TYPE));
	}

	@ParameterizedTest
	@MethodSource("missingRequiredFields")
	void shouldRejectMissingRequiredFields(String field, String message) {
		TransactionRequestDto dto = validInput();

		switch (field) {
		case "transaction_id" -> dto.setTransactionId(" ");
		case "account_number" -> dto.setAccountNumber(null);
		case "transaction_date" -> dto.setTransactionDate("");
		case "amount" -> dto.setAmount(null);
		case "currency" -> dto.setCurrency(" ");
		case "transaction_type" -> dto.setTransactionType(null);
		}

		assertInvalid(processor.process(dto), message);
	}

	static Stream<Arguments> invalidValues() {
		return Stream.of(Arguments.of("longId", Messages.TRANSACTION_ID_EXCEEDS_LIMIT),
				Arguments.of("longAccount", Messages.ACCOUNT_NUMBER_EXCEEDS_LIMIT),
				Arguments.of("shortCurrency", Messages.INVALID_CURRENCY_LENGTH),
				Arguments.of("longDescription", Messages.DESCRIPTION_EXCEEDS_LIMIT),
				Arguments.of("badDate", Messages.INVALID_TRANSACTION_DATE),
				Arguments.of("badAmount", Messages.INVALID_AMOUNT_FORMAT),
				Arguments.of("negativeAmount", Messages.NEGATIVE_AMOUNT));
	}

	@ParameterizedTest
	@MethodSource("invalidValues")
	void shouldRejectInvalidValues(String type, String message) {
		TransactionRequestDto dto = validInput();

		switch (type) {
		case "longId" -> dto.setTransactionId("T".repeat(51));
		case "longAccount" -> dto.setAccountNumber("1".repeat(18));
		case "shortCurrency" -> dto.setCurrency("US");
		case "longDescription" -> dto.setDescription("D".repeat(501));
		case "badDate" -> dto.setTransactionDate("15-01-2026");
		case "badAmount" -> dto.setAmount("abc");
		case "negativeAmount" -> dto.setAmount("-0.01");
		}

		assertInvalid(processor.process(dto), message);
	}

	@Test
	void shouldAcceptZeroAmount() {
		TransactionRequestDto dto = validInput();
		dto.setAmount("0");

		assertEquals(ReconciliationStatus.VALID, processor.process(dto).status());
	}

	@Test
	void shouldRejectImpossibleCalendarDate() {
		TransactionRequestDto dto = validInput();
		dto.setTransactionDate("2026-02-30");

		assertInvalid(processor.process(dto), Messages.INVALID_TRANSACTION_DATE);
	}

	private void assertInvalid(ReconciliationProcessingResult result, String message) {
		assertEquals(ReconciliationStatus.INVALID_RECORD, result.status());
		assertEquals(ReconciliationAction.SKIP, result.action());
		assertNull(result.transaction());
		assertEquals(message, result.errorMessage());
	}
}
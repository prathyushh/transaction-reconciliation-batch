package com.example.springbatch.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InvalidCsvRecordExceptionTest {

	@Test
	void shouldPreserveMessage() {
		InvalidCsvRecordException exception = new InvalidCsvRecordException("Invalid record");

		assertEquals("Invalid record", exception.getMessage());
		assertInstanceOf(RuntimeException.class, exception);
	}

	@Test
	void shouldAllowNullMessage() {
		InvalidCsvRecordException exception = new InvalidCsvRecordException(null);

		assertNull(exception.getMessage());
	}
}
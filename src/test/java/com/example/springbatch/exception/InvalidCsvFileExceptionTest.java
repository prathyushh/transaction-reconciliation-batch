package com.example.springbatch.exception;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class InvalidCsvFileExceptionTest {

	@Test
	void shouldPreserveMessage() {
		InvalidCsvFileException exception = new InvalidCsvFileException("Invalid file");

		assertEquals("Invalid file", exception.getMessage());
		assertInstanceOf(RuntimeException.class, exception);
	}

	@Test
	void shouldAllowNullMessage() {
		InvalidCsvFileException exception = new InvalidCsvFileException(null);

		assertNull(exception.getMessage());
	}
}
package com.example.springbatch.exception;

public class InvalidCsvFileException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public InvalidCsvFileException(String message) {
		super(message);
	}
}

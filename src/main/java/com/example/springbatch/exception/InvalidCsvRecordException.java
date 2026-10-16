package com.example.springbatch.exception;

public class InvalidCsvRecordException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public InvalidCsvRecordException(String message) {
		super(message);
	}
}

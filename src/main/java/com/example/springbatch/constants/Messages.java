package com.example.springbatch.constants;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class Messages {
	public static final String INPUT_CSV_NOT_READABLE = "Input CSV does not exist or is not readable: ";
	public static final String INPUT_CSV_EXTENSION_NOT_CORRECT = "Input file must have a .csv extension: ";
	public static final String INPUT_CSV_FILE_SIZE_EXCEEDS = "CSV file exceeds the maximum allowed size of ";
	public static final String INVALID_CSV_FILE = "Invalid CSV file";
	public static final String CSV_EMPTY_NO_HEADER = "Input CSV is empty or has no header: ";
	public static final String CSV_VALIDATION_FAILED = "Failed to validate CSV file: ";
	public static final String STEP_CONTEXT_NOT_FOUND = "No active Spring Batch step context found.";
	public static final String NO_PROCESSING_SUMMARY_FOUND = "Processing summary not found for job execution ";
	public static final String EXPECTED_ONE_PROCESSING_SUMMARY = "Expected one PROCESSING_SUMMARY row for job execution ";
	public static final String SUMMARY_EVENT = "PROCESSING_SUMMARY";
	public static final String AUDIT_DETAIL = "CSV1 loading and CSV2 reconciliation summary";
	public static final String TRANSACTION_ALREADY_EXISTS = "Transaction already exists for this job instance";
	public static final String DUPLICATE_TRANSACTION_ID = "Duplicate transaction_id in CSV1";

	public static final String CSV_OUTPUTFILE_HEADER = "job_instance_id,job_execution_id,line_number,transaction_id,status,action,error_message,previous_amount,new_amount";

	public static final String EMPTY_RECORD = "Empty record";
	public static final String TRANSACTION_ID_EXCEEDS_LIMIT = "Transaction ID exceeds 50 characters";
	public static final String ACCOUNT_NUMBER_EXCEEDS_LIMIT = "Account number exceeds 17 characters";
	public static final String INVALID_CURRENCY_LENGTH = "Invalid currency length";
	public static final String DESCRIPTION_EXCEEDS_LIMIT = "Description exceeds 500 characters";
	public static final String INVALID_TRANSACTION_DATE = "Invalid transaction date";
	public static final String INVALID_AMOUNT_FORMAT = "Invalid amount format";
	public static final String NEGATIVE_AMOUNT = "Amount cannot be negative";
	public static final String MISSING_TRANSACTION_ID = "Missing transaction ID";
	public static final String MISSING_ACCOUNT_NUMBER = "Missing account number";
	public static final String MISSING_TRANSACTION_DATE = "Missing transaction date";
	public static final String MISSING_AMOUNT = "Missing amount";
	public static final String MISSING_CURRENCY = "Missing currency";
	public static final String MISSING_TRANSACTION_TYPE = "Missing transaction type";
	public static final String MISSING_REQUIRED_FIELD = "Missing required field";

	public static final String TRANSACTION_NOT_FOUND_IN_DATABASE = "Transaction not found in database";
	public static final String DATABASE_AMOUNT_UPDATED_TO_CSV2 = "Database amount updated to CSV2 amount";
}
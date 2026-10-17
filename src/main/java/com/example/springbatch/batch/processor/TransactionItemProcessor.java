package com.example.springbatch.batch.processor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.springbatch.batch.writer.ReconciliationProcessingResult;
import com.example.springbatch.constants.Messages;
import com.example.springbatch.dto.TransactionRequestDto;
import com.example.springbatch.entity.TransactionRecord;
import com.example.springbatch.exception.InvalidCsvRecordException;
import com.example.springbatch.enums.ReconciliationAction;
import com.example.springbatch.enums.ReconciliationStatus;

import lombok.RequiredArgsConstructor;

import org.springframework.batch.infrastructure.item.ItemProcessor;

@RequiredArgsConstructor
public class TransactionItemProcessor implements ItemProcessor<TransactionRequestDto, ReconciliationProcessingResult> {
	private final long jobInstanceId;

	@Override
	public ReconciliationProcessingResult process(TransactionRequestDto input) {
		try {
			if (input == null) {
				throw new InvalidCsvRecordException(Messages.EMPTY_RECORD);
			}
//            if(input.getLineNumber()==500) {
//            	throw new RuntimeException("pause");
//            }
			String transactionId = required(input.getTransactionId(), "transaction_id");
			String accountNumber = required(input.getAccountNumber(), "account_number");
			String transactionDate = required(input.getTransactionDate(), "transaction_date");
			String amountValue = required(input.getAmount(), "amount");
			String currency = required(input.getCurrency(), "currency");
			String transactionType = required(input.getTransactionType(), "transaction_type");
			if (transactionId.length() > 50) {
				throw new InvalidCsvRecordException(Messages.TRANSACTION_ID_EXCEEDS_LIMIT);
			}
			if (accountNumber.length() > 17) {
				throw new InvalidCsvRecordException(Messages.ACCOUNT_NUMBER_EXCEEDS_LIMIT);
			}
			if (currency.length() != 3) {
				throw new InvalidCsvRecordException(Messages.INVALID_CURRENCY_LENGTH);
			}
			if (input.getDescription() != null && input.getDescription().length() > 500) {
				throw new InvalidCsvRecordException(Messages.DESCRIPTION_EXCEEDS_LIMIT);
			}
			LocalDate date;
			try {
				date = LocalDate.parse(transactionDate);
			} catch (RuntimeException ex) {
				throw new InvalidCsvRecordException(Messages.INVALID_TRANSACTION_DATE);
			}
			BigDecimal amount;
			try {
				amount = new BigDecimal(amountValue);
			} catch (NumberFormatException ex) {
				throw new InvalidCsvRecordException(Messages.INVALID_AMOUNT_FORMAT);
			}
			if (amount.signum() < 0) {
				throw new InvalidCsvRecordException(Messages.NEGATIVE_AMOUNT);
			}
			LocalDateTime now = LocalDateTime.now();
			TransactionRecord record = TransactionRecord.builder().jobInstanceId(jobInstanceId)
					.transactionId(transactionId).accountNumber(accountNumber).transactionDate(date).amount(amount)
					.currency(currency).transactionType(transactionType).description(input.getDescription())
					.createdAt(now).updatedAt(now).build();
			return new ReconciliationProcessingResult(input, record, ReconciliationStatus.VALID,
					ReconciliationAction.INSERT, null, null);
		} catch (InvalidCsvRecordException ex) {
			return new ReconciliationProcessingResult(input, null, ReconciliationStatus.INVALID_RECORD,
					ReconciliationAction.SKIP, ex.getMessage(), null);
		}
	}

	private String required(String value, String field) {
		if (value == null || value.isBlank()) {
			String message = switch (field) {
			case "transaction_id" -> Messages.MISSING_TRANSACTION_ID;
			case "account_number" -> Messages.MISSING_ACCOUNT_NUMBER;
			case "transaction_date" -> Messages.MISSING_TRANSACTION_DATE;
			case "amount" -> Messages.MISSING_AMOUNT;
			case "currency" -> Messages.MISSING_CURRENCY;
			case "transaction_type" -> Messages.MISSING_TRANSACTION_TYPE;
			default -> Messages.MISSING_REQUIRED_FIELD;
			};

			throw new InvalidCsvRecordException(message);
		}

		return value.trim();
	}
}
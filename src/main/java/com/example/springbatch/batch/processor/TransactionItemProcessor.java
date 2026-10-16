package com.example.springbatch.batch.processor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.springbatch.batch.writer.ReconciliationProcessingResult;
import com.example.springbatch.dto.TransactionRequestDto;
import com.example.springbatch.entity.TransactionRecord;
import com.example.springbatch.exception.InvalidCsvRecordException;

import lombok.RequiredArgsConstructor;

import org.springframework.batch.infrastructure.item.ItemProcessor;

@RequiredArgsConstructor
public class TransactionItemProcessor
        implements ItemProcessor<TransactionRequestDto, ReconciliationProcessingResult> {

    private final long jobInstanceId;

    @Override
    public ReconciliationProcessingResult process(TransactionRequestDto input) {
        try {
            if (input == null) {
                throw new InvalidCsvRecordException("Empty record");
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
                throw new InvalidCsvRecordException("Transaction ID exceeds 50 characters");
            }

            if (accountNumber.length() > 17) {
                throw new InvalidCsvRecordException("Account number exceeds 17 characters");
            }

            if (currency.length() != 3) {
                throw new InvalidCsvRecordException("Invalid currency length");
            }

            if (input.getDescription() != null && input.getDescription().length() > 500) {
                throw new InvalidCsvRecordException("Description exceeds 500 characters");
            }

            LocalDate date;
            try {
                date = LocalDate.parse(transactionDate);
            } catch (RuntimeException ex) {
                throw new InvalidCsvRecordException("Invalid transaction date");
            }

            BigDecimal amount;
            try {
                amount = new BigDecimal(amountValue);
            } catch (NumberFormatException ex) {
                throw new InvalidCsvRecordException("Invalid amount format");
            }

            if (amount.signum() < 0) {
                throw new InvalidCsvRecordException("Amount cannot be negative");
            }

            LocalDateTime now = LocalDateTime.now();

            TransactionRecord record = TransactionRecord.builder()
                    .jobInstanceId(jobInstanceId)
                    .transactionId(transactionId)
                    .accountNumber(accountNumber)
                    .transactionDate(date)
                    .amount(amount)
                    .currency(currency)
                    .transactionType(transactionType)
                    .description(input.getDescription())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            return new ReconciliationProcessingResult(
                    input, record, "VALID", "INSERT", null, null);

        } catch (InvalidCsvRecordException ex) {
            return new ReconciliationProcessingResult(
                    input, null, "INVALID_RECORD", "SKIP", ex.getMessage(), null);
        }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            String message = switch (field) {
                case "transaction_id" -> "Missing transaction ID";
                case "account_number" -> "Missing account number";
                case "transaction_date" -> "Missing transaction date";
                case "amount" -> "Missing amount";
                case "currency" -> "Missing currency";
                case "transaction_type" -> "Missing transaction type";
                default -> "Missing required field";
            };

            throw new InvalidCsvRecordException(message);
        }

        return value.trim();
    }
}
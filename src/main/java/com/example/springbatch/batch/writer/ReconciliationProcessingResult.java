package com.example.springbatch.batch.writer;

import java.math.BigDecimal;

import com.example.springbatch.dto.TransactionRequestDto;
import com.example.springbatch.entity.TransactionRecord;
import com.example.springbatch.enums.ReconciliationAction;
import com.example.springbatch.enums.ReconciliationStatus;

public record ReconciliationProcessingResult(TransactionRequestDto input, TransactionRecord transaction,
		ReconciliationStatus status, ReconciliationAction action, String errorMessage, BigDecimal previousAmount) {
	public boolean isValid() {
		return transaction != null;
	}
}
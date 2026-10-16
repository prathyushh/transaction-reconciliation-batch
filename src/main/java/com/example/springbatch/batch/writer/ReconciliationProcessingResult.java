package com.example.springbatch.batch.writer;

import java.math.BigDecimal;
import com.example.springbatch.dto.TransactionRequestDto;
import com.example.springbatch.entity.TransactionRecord;

public record ReconciliationProcessingResult(TransactionRequestDto input, TransactionRecord transaction, String status,
		String action, String errorMessage, BigDecimal previousAmount) {
	public boolean isValid() {
		return transaction != null;
	}
}
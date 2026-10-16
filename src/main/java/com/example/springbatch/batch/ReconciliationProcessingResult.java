package com.example.springbatch.batch;

import com.example.springbatch.dto.TransactionRequestDto;
import com.example.springbatch.entity.TransactionRecord;

public record ReconciliationProcessingResult(TransactionRequestDto input, TransactionRecord transaction, String status,
		String action, String errorMessage) {
	public boolean isValid() {
		return transaction != null;
	}
}
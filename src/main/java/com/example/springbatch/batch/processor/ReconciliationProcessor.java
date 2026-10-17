package com.example.springbatch.batch.processor;

import java.math.BigDecimal;

import org.springframework.batch.infrastructure.item.ItemProcessor;

import com.example.springbatch.batch.writer.ReconciliationProcessingResult;
import com.example.springbatch.constants.Messages;
import com.example.springbatch.dto.TransactionRequestDto;
import com.example.springbatch.entity.TransactionRecord;
import com.example.springbatch.enums.ReconciliationAction;
import com.example.springbatch.enums.ReconciliationStatus;
import com.example.springbatch.repository.TransactionRecordRepository;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ReconciliationProcessor implements ItemProcessor<TransactionRequestDto, ReconciliationProcessingResult> {

	private final TransactionRecordRepository transactionRecordRepository;
	private final long jobInstanceId;

	@Override
	public ReconciliationProcessingResult process(TransactionRequestDto input) {
		String transactionId = input.getTransactionId();
		BigDecimal csv2Amount = new BigDecimal(input.getAmount());
		TransactionRecord transaction = transactionRecordRepository
				.findByJobInstanceIdAndTransactionId(jobInstanceId, transactionId).orElse(null);
		if (transaction == null) {
			return new ReconciliationProcessingResult(input, null, ReconciliationStatus.MISSING_IN_DATABASE,
					ReconciliationAction.REPORT, Messages.TRANSACTION_NOT_FOUND_IN_DATABASE, null);
		}
		BigDecimal databaseAmount = transaction.getAmount();
		if (databaseAmount.compareTo(csv2Amount) == 0) {
			return new ReconciliationProcessingResult(input, transaction, ReconciliationStatus.MATCH,
					ReconciliationAction.NONE, null, databaseAmount);
		}
		return new ReconciliationProcessingResult(input, transaction, ReconciliationStatus.MISMATCH,
				ReconciliationAction.UPDATE_AMOUNT, Messages.DATABASE_AMOUNT_UPDATED_TO_CSV2, databaseAmount);
	}
}
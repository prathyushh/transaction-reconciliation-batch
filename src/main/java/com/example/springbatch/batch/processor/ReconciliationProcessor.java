package com.example.springbatch.batch.processor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.example.springbatch.batch.writer.ReconciliationProcessingResult;
import com.example.springbatch.dto.TransactionRequestDto;
import com.example.springbatch.entity.TransactionRecord;
import com.example.springbatch.repository.TransactionRecordRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ReconciliationProcessor implements ItemProcessor<TransactionRequestDto, ReconciliationProcessingResult> {
	private final TransactionRecordRepository transactionRecordRepository;
	@Value("#{jobParameters['run.id']}")
	private Long runId;

	@Override
	public ReconciliationProcessingResult process(TransactionRequestDto input) {
		String transactionId = input.getTransactionId();
		BigDecimal csv2Amount = new BigDecimal(input.getAmount());
		TransactionRecord transaction = transactionRecordRepository.findByRunIdAndTransactionId(runId, transactionId)
				.orElse(null);
		if (transaction == null) {
			return new ReconciliationProcessingResult(input, null, "MISSING_IN_DATABASE", "REPORT",
					"Transaction not found in database", null);
		}
		BigDecimal databaseAmount = transaction.getAmount();
		if (databaseAmount.compareTo(csv2Amount) == 0) {
			return new ReconciliationProcessingResult(input, transaction, "MATCH", "NONE", null, databaseAmount);
		}
		transaction.setAmount(csv2Amount);
		transaction.setUpdatedAt(LocalDateTime.now());
		TransactionRecord updatedTransaction = transactionRecordRepository.save(transaction);
		return new ReconciliationProcessingResult(input, updatedTransaction, "MISMATCH", "UPDATE_AMOUNT",
				"Database amount updated to CSV2 amount", databaseAmount);
	}
}
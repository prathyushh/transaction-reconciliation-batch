package com.example.springbatch.batch.writer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.example.springbatch.constants.Messages;
import com.example.springbatch.entity.TransactionRecord;
import com.example.springbatch.repository.AuditLogRepository;
import com.example.springbatch.repository.TransactionRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationItemWriter implements ItemWriter<ReconciliationProcessingResult> {
	private final TransactionRecordRepository transactionRecordRepository;
	private final ReconciliationCsvWriter reconciliationCsvWriter;
	private final AuditLogRepository auditLogRepository;

	@Override
	@Transactional
	public void write(Chunk<? extends ReconciliationProcessingResult> chunk) throws Exception {
		if (chunk.isEmpty()) {
			return;
		}
		List<ReconciliationProcessingResult> results = List.copyOf(chunk.getItems());
		long matchedCount = 0;
		long mismatchedCount = 0;
		long missingCount = 0;
		for (ReconciliationProcessingResult result : results) {
			switch (result.status()) {
			case MATCH -> matchedCount++;
			case MISMATCH -> {
				TransactionRecord transaction = result.transaction();
				if (transaction != null && result.input() != null && result.input().getAmount() != null) {
					BigDecimal csv2Amount = new BigDecimal(result.input().getAmount());
					transaction.setAmount(csv2Amount);
					transaction.setUpdatedAt(LocalDateTime.now());
					transactionRecordRepository.save(transaction);
					mismatchedCount++;
				} else {
					log.warn("Cannot update transaction amount because the transaction, input, or CSV2 amount is null");
				}
			}
			case MISSING_IN_DATABASE -> missingCount++;
			default -> log.debug("No reconciliation counter update for status {}", result.status());
			}
		}
		reconciliationCsvWriter.writeResults(results);
		// throw new RuntimeException("TEST FAILURE: after reconciliation CSV write");
		updateAudit(results.size(), matchedCount, mismatchedCount, missingCount);
	}

	private void updateAudit(long total, long matched, long mismatched, long missing) {
		StepContext context = StepSynchronizationManager.getContext();
		if (context == null) {
			throw new IllegalStateException(Messages.STEP_CONTEXT_NOT_FOUND);
		}
		Long jobInstanceId = context.getStepExecution().getJobExecution().getJobInstance().getInstanceId();
		int updated = auditLogRepository.incrementCounts(jobInstanceId, total, 0, matched, mismatched, 0, 0, missing);
		if (updated != 1) {
			throw new IllegalStateException("Audit summary not found for job instance " + jobInstanceId);
		}
	}
}

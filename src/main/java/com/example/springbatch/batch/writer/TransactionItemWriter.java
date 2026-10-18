package com.example.springbatch.batch.writer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.example.springbatch.constants.Messages;
import com.example.springbatch.entity.TransactionRecord;
import com.example.springbatch.enums.ReconciliationAction;
import com.example.springbatch.enums.ReconciliationStatus;
import com.example.springbatch.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionItemWriter implements ItemWriter<ReconciliationProcessingResult> {
	private final NamedParameterJdbcTemplate jdbcTemplate;
	private final JdbcBatchItemWriter<TransactionRecord> jdbcTransactionWriter;
	private final ReconciliationCsvWriter reconciliationCsvWriter;
	private final AuditLogRepository auditLogRepository;

	@Override
	@Transactional
	public void write(Chunk<? extends ReconciliationProcessingResult> chunk) throws Exception {
		if (chunk.isEmpty()) {
			return;
		}
		List<ReconciliationProcessingResult> results = List.copyOf(chunk.getItems());
		List<ReconciliationProcessingResult> csvRows = new ArrayList<>();
		List<TransactionRecord> candidates = new ArrayList<>();
		Set<String> seenInChunk = new HashSet<>();
		long invalidCount = 0;
		long duplicateCount = 0;
		long loadedCount = 0;
		for (ReconciliationProcessingResult result : results) {
			if (!result.isValid()) {
				csvRows.add(result);
				invalidCount++;
				continue;
			}
			TransactionRecord transaction = result.transaction();
			String transactionId = transaction.getTransactionId();
			if (!seenInChunk.add(transactionId)) {
				csvRows.add(
						new ReconciliationProcessingResult(result.input(), null, ReconciliationStatus.DUPLICATE_IN_CSV1,
								ReconciliationAction.SKIP, Messages.DUPLICATE_TRANSACTION_ID, null));
				duplicateCount++;
				continue;
			}
			candidates.add(transaction);
		}
		if (!candidates.isEmpty()) {
			Set<String> ids = new HashSet<>();
			candidates.forEach(record -> ids.add(record.getTransactionId()));
			long jobInstanceId = candidates.get(0).getJobInstanceId();
			Set<String> existing = new HashSet<>(jdbcTemplate.query("""
					SELECT transaction_id
					FROM transaction_records
					WHERE job_instance_id = :jobInstanceId
					  AND transaction_id IN (:ids)
					""", new MapSqlParameterSource().addValue("jobInstanceId", jobInstanceId).addValue("ids", ids),
					(rs, rowNum) -> rs.getString("transaction_id")));
			List<TransactionRecord> insert = new ArrayList<>();
			for (TransactionRecord candidate : candidates) {
				String transactionId = candidate.getTransactionId();
				if (existing.contains(transactionId)) {
					ReconciliationProcessingResult original = results.stream().filter(
							result -> result.isValid() && result.transaction().getTransactionId().equals(transactionId))
							.findFirst().orElseThrow();
					csvRows.add(new ReconciliationProcessingResult(original.input(), null,
							ReconciliationStatus.DUPLICATE_IN_CSV1, ReconciliationAction.SKIP,
							Messages.TRANSACTION_ALREADY_EXISTS, null));
					duplicateCount++;
				} else {
					insert.add(candidate);
				}
			}
			if (!insert.isEmpty()) {
				jdbcTransactionWriter.write(new Chunk<>(insert));
				loadedCount = insert.size();
			}
		}
		reconciliationCsvWriter.writeResults(csvRows);
		updateAudit(results.size(), loadedCount, duplicateCount, invalidCount);
	}

	private void updateAudit(long total, long loaded, long duplicate, long invalid) {
		StepContext context = StepSynchronizationManager.getContext();
		if (context == null) {
			throw new IllegalStateException(Messages.STEP_CONTEXT_NOT_FOUND);
		}
		Long jobInstanceId = context.getStepExecution().getJobExecution().getJobInstance().getInstanceId();
		int updated = auditLogRepository.incrementCounts(jobInstanceId, total, loaded, 0, 0, duplicate, invalid, 0);
		if (updated != 1) {
			throw new IllegalStateException("Audit summary not found for job instance " + jobInstanceId);
		}
	}
}

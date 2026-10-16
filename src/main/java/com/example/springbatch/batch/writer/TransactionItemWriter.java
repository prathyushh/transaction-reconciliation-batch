
package com.example.springbatch.batch.writer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.springbatch.entity.TransactionRecord;
import com.example.springbatch.service.AuditLogService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionItemWriter implements ItemWriter<ReconciliationProcessingResult> {

	private final NamedParameterJdbcTemplate jdbcTemplate;
	private final JdbcBatchItemWriter<TransactionRecord> jdbcTransactionWriter;
	private final ReconciliationCsvWriter reconciliationCsvWriter;
	private final AuditLogService auditLogService;

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

		// 1. Separate invalid records and detect duplicates within this chunk.
		for (ReconciliationProcessingResult result : results) {

			if (!result.isValid()) {
				csvRows.add(result);
				invalidCount++;
				continue;
			}

			TransactionRecord transaction = result.transaction();
			String transactionId = transaction.getTransactionId();

			if (!seenInChunk.add(transactionId)) {
				csvRows.add(new ReconciliationProcessingResult(result.input(), null, "DUPLICATE_IN_CSV1", "SKIP",
						"Duplicate transaction_id in CSV1", null));

				duplicateCount++;
				continue;
			}

			candidates.add(transaction);
		}

		// 2. Check existing transaction IDs for this JobInstance.
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

			// 3. Separate database duplicates from records to insert.
			for (TransactionRecord candidate : candidates) {

				String transactionId = candidate.getTransactionId();

				if (existing.contains(transactionId)) {

					ReconciliationProcessingResult original = results.stream().filter(
							result -> result.isValid() && result.transaction().getTransactionId().equals(transactionId))
							.findFirst().orElseThrow();

					csvRows.add(new ReconciliationProcessingResult(original.input(), null, "DUPLICATE_IN_CSV1", "SKIP",
							"Transaction already exists for this job instance", null));

					duplicateCount++;

				} else {
					insert.add(candidate);
				}
			}

			// 4. Insert valid, nonduplicate transactions.
			if (!insert.isEmpty()) {
				jdbcTransactionWriter.write(new Chunk<>(insert));
				loadedCount = insert.size();
			}
		}

		// 5. Write invalid and duplicate records to the result CSV.
		reconciliationCsvWriter.writeResults(csvRows);

		// 6. Update audit summary counters.
		auditLogService.recordCsv1Chunk(results.size(), loadedCount, duplicateCount, invalidCount);
	}
}

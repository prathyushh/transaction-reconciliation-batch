package com.example.springbatch.batch;

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
import com.example.springbatch.entity.TransactionRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionItemWriter implements ItemWriter<ReconciliationProcessingResult> {
	private final NamedParameterJdbcTemplate jdbcTemplate;
	private final JdbcBatchItemWriter<TransactionRecord> jdbcTransactionWriter;
	private final ReconciliationCsvWriter reconciliationCsvWriter;

	@Override
	public void write(Chunk<? extends ReconciliationProcessingResult> chunk) throws Exception {
		if (chunk.isEmpty()) {
			return;
		}
		List<ReconciliationProcessingResult> csvRows = new ArrayList<>();
		List<TransactionRecord> candidates = new ArrayList<>();
		Set<String> seenInChunk = new HashSet<>();
		for (ReconciliationProcessingResult result : chunk.getItems()) {
			if (!result.isValid()) {
				csvRows.add(result);
			} else if (!seenInChunk.add(result.transaction().getTransactionId())) {
				csvRows.add(new ReconciliationProcessingResult(result.input(), null, "DUPLICATE_IN_CSV1", "SKIP",
						"Duplicate transaction_id in CSV1"));
			} else {
				candidates.add(result.transaction());
			}
		}
		if (!candidates.isEmpty()) {
			Set<String> ids = new HashSet<>();
			candidates.forEach(record -> ids.add(record.getTransactionId()));
			long runId = candidates.get(0).getRunId();
			Set<String> existing = new HashSet<>(jdbcTemplate.query(
					"SELECT transaction_id " + "FROM transaction_records " + "WHERE run_id = :runId "
							+ "AND transaction_id IN (:ids)",
					new MapSqlParameterSource().addValue("runId", runId).addValue("ids", ids),
					(rs, rowNum) -> rs.getString("transaction_id")));
			List<TransactionRecord> insert = new ArrayList<>();
			for (TransactionRecord candidate : candidates) {
				if (existing.contains(candidate.getTransactionId())) {
					chunk.getItems().stream()
							.filter(result -> result.isValid()
									&& result.transaction().getTransactionId().equals(candidate.getTransactionId()))
							.findFirst()
							.ifPresent(result -> csvRows.add(new ReconciliationProcessingResult(result.input(), null,
									"DUPLICATE_IN_CSV1", "SKIP", "Transaction already exists for this run_id")));
				} else {
					insert.add(candidate);
				}
			}
			if (!insert.isEmpty()) {
				jdbcTransactionWriter.write(new Chunk<>(insert));
			}
		}
		reconciliationCsvWriter.writeResults(csvRows);
	}
}
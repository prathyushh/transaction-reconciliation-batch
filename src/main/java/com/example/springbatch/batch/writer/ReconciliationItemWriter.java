package com.example.springbatch.batch.writer;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.springbatch.repository.TransactionRecordRepository;
import com.example.springbatch.service.AuditLogService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationItemWriter implements ItemWriter<ReconciliationProcessingResult> {
	private final TransactionRecordRepository transactionRecordRepository;
	private final ReconciliationCsvWriter reconciliationCsvWriter;
	private final AuditLogService auditLogService;

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
			case "MATCH" -> matchedCount++;
			case "MISMATCH" -> {
				if (result.transaction() != null) {
					result.transaction().setUpdatedAt(LocalDateTime.now());
					transactionRecordRepository.save(result.transaction());
					mismatchedCount++;
				}
			}
			case "MISSING_IN_DATABASE" -> missingCount++;
			default -> log.debug("No reconciliation counter update for status {}", result.status());
			}
		}
		reconciliationCsvWriter.writeResults(results);
		auditLogService.recordCsv2Chunk(results.size(), matchedCount, mismatchedCount, missingCount);
	}
}
package com.example.springbatch.service;

import java.time.LocalDateTime;

import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.springbatch.entity.AuditLog;
import com.example.springbatch.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuditLogService {
	private static final String SUMMARY_EVENT = "PROCESSING_SUMMARY";
	private final JdbcTemplate jdbcTemplate;
	private final AuditLogRepository auditLogRepository;

	@Transactional
	public void initializeSummary(Long jobExecutionId) {
		if (auditLogRepository.findByJobExecutionIdAndEventType(jobExecutionId, SUMMARY_EVENT).isPresent()) {
			return;
		}
		LocalDateTime now = LocalDateTime.now();
		AuditLog summary = AuditLog.builder().jobExecutionId(jobExecutionId).eventType(SUMMARY_EVENT).status("STARTED")
				.eventTimestamp(now).details("CSV1 loading and CSV2 reconciliation summary").totalRecords(0L)
				.loadedRecords(0L).matchedRecords(0L).mismatchedRecords(0L).duplicateRecords(0L).invalidRecords(0L)
				.missingInDatabaseRecords(0L).build();
		auditLogRepository.saveAndFlush(summary);
	}

	@Transactional
	public void recordCsv1Chunk(long total, long loaded, long duplicates, long invalid) {
		Long jobExecutionId = getCurrentJobExecutionId();
		int updated = jdbcTemplate.update("""
				UPDATE audit_log
				   SET total_records = COALESCE(total_records, 0) + ?,
				       loaded_records = COALESCE(loaded_records, 0) + ?,
				       duplicate_records = COALESCE(duplicate_records, 0) + ?,
				       invalid_records = COALESCE(invalid_records, 0) + ?,
				       event_timestamp = ?
				 WHERE job_execution_id = ?
				   AND event_type = ?
				""", total, loaded, duplicates, invalid, LocalDateTime.now(), jobExecutionId, SUMMARY_EVENT);
		verifyUpdate(updated, jobExecutionId);
	}

	@Transactional
	public void recordCsv2Chunk(long total, long matched, long mismatched, long missingInDatabase) {
		Long jobExecutionId = getCurrentJobExecutionId();
		int updated = jdbcTemplate.update("""
				UPDATE audit_log
				   SET total_records = COALESCE(total_records, 0) + ?,
				       matched_records = COALESCE(matched_records, 0) + ?,
				       mismatched_records = COALESCE(mismatched_records, 0) + ?,
				       missing_in_database_records =
				           COALESCE(missing_in_database_records, 0) + ?,
				       event_timestamp = ?
				 WHERE job_execution_id = ?
				   AND event_type = ?
				""", total, matched, mismatched, missingInDatabase, LocalDateTime.now(), jobExecutionId, SUMMARY_EVENT);

		verifyUpdate(updated, jobExecutionId);
	}

	@Transactional
	public void finishSummary(Long jobExecutionId, String status) {
		int updated = jdbcTemplate.update("""
				UPDATE audit_log
				   SET status = ?,
				       event_timestamp = ?
				 WHERE job_execution_id = ?
				   AND event_type = ?
				""", status, LocalDateTime.now(), jobExecutionId, SUMMARY_EVENT);
		verifyUpdate(updated, jobExecutionId);
	}

	@Transactional(readOnly = true)
	public AuditLog getSummary(Long jobExecutionId) {
		return auditLogRepository.findByJobExecutionIdAndEventType(jobExecutionId, SUMMARY_EVENT).orElseThrow(
				() -> new IllegalStateException("Processing summary not found for job execution " + jobExecutionId));
	}

	private Long getCurrentJobExecutionId() {
		var context = StepSynchronizationManager.getContext();
		if (context == null) {
			throw new IllegalStateException("No active Spring Batch step context found.");
		}
		return context.getStepExecution().getJobExecution().getId();
	}

	private void verifyUpdate(int updated, Long jobExecutionId) {
		if (updated != 1) {
			throw new IllegalStateException("Expected one PROCESSING_SUMMARY row for job execution " + jobExecutionId
					+ ", but updated " + updated + " rows.");
		}
	}
}
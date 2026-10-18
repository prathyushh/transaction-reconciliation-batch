package com.example.springbatch.batch.listener;

import java.time.LocalDateTime;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

import com.example.springbatch.constants.Messages;
import com.example.springbatch.entity.AuditLog;
import com.example.springbatch.repository.AuditLogRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProcessingCounterJobListener implements JobExecutionListener {
	private final AuditLogRepository auditLogRepository;

	@Override
	public void beforeJob(JobExecution jobExecution) {
		Long jobInstanceId = jobExecution.getJobInstance().getInstanceId();
		AuditLog audit = auditLogRepository.findByJobInstanceId(jobInstanceId)
				.orElseGet(() -> createAudit(jobExecution, jobInstanceId));
		audit.setJobExecutionId(jobExecution.getId());
		audit.setStatus("STARTED");
		audit.setEventTimestamp(LocalDateTime.now());
		auditLogRepository.save(audit);
	}

	private AuditLog createAudit(JobExecution jobExecution, Long jobInstanceId) {
		LocalDateTime now = LocalDateTime.now();
		return AuditLog.builder().jobInstanceId(jobInstanceId).jobExecutionId(jobExecution.getId())
				.eventType(Messages.SUMMARY_EVENT).status("STARTED").eventTimestamp(now).details(Messages.AUDIT_DETAIL)
				.totalRecords(0L).loadedRecords(0L).matchedRecords(0L).mismatchedRecords(0L).duplicateRecords(0L)
				.invalidRecords(0L).missingInDatabaseRecords(0L).build();
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		Long jobInstanceId = jobExecution.getJobInstance().getInstanceId();
		auditLogRepository.findByJobInstanceId(jobInstanceId).ifPresent(audit -> {
			audit.setStatus(jobExecution.getStatus().name());
			if (jobExecution.getEndTime() != null) {
				audit.setEventTimestamp(jobExecution.getEndTime());
			} else {
				audit.setEventTimestamp(LocalDateTime.now());
			}
			auditLogRepository.save(audit);
		});
	}
}

package com.example.springbatch.batch.listener;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.springbatch.entity.AuditLog;
import com.example.springbatch.repository.AuditLogRepository;
import com.example.springbatch.service.AuditLogService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ProcessingCounterJobListener implements JobExecutionListener {
	private static final String SUMMARY_EVENT = "PROCESSING_SUMMARY";
	private final AuditLogService auditLogService;
	private final AuditLogRepository auditLogRepository;
	private final JobRepository jobRepository;

	@Override
	@Transactional
	public void beforeJob(JobExecution jobExecution) {
		Long currentExecutionId = jobExecution.getId();
		List<Long> executionIds = jobRepository.getJobExecutions(jobExecution.getJobInstance()).stream()
				.map(JobExecution::getId).filter(id -> id != null && !id.equals(currentExecutionId)).toList();
		AuditLog existingSummary = executionIds.isEmpty() ? null
				: auditLogRepository.findFirstByJobExecutionIdInAndEventTypeOrderByIdAsc(executionIds, SUMMARY_EVENT)
						.orElse(null);
		if (existingSummary != null) {
			existingSummary.setJobExecutionId(currentExecutionId);
			existingSummary.setStatus("STARTED");
			existingSummary.setEventTimestamp(LocalDateTime.now());
			auditLogRepository.save(existingSummary);
		} else {
			auditLogService.initializeSummary(currentExecutionId);
		}
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		auditLogService.finishSummary(jobExecution.getId(), jobExecution.getStatus().name());
	}
}
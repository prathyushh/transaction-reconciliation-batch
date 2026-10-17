
package com.example.springbatch.batch.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.repository.JobRepository;

import com.example.springbatch.constants.Messages;
import com.example.springbatch.entity.AuditLog;
import com.example.springbatch.repository.AuditLogRepository;
import com.example.springbatch.service.AuditLogService;

class ProcessingCounterJobListenerTest {

	private AuditLogService service;
	private AuditLogRepository repository;
	private JobRepository jobRepository;
	private ProcessingCounterJobListener listener;

	@BeforeEach
	void setUp() {
		service = mock(AuditLogService.class);
		repository = mock(AuditLogRepository.class);
		jobRepository = mock(JobRepository.class);

		listener = new ProcessingCounterJobListener(service, repository, jobRepository);
	}

	@Test
	void shouldInitializeSummaryWhenNoPreviousExecutionExists() {
		JobExecution current = mock(JobExecution.class);
		JobInstance instance = mock(JobInstance.class);

		when(current.getId()).thenReturn(10L);
		when(current.getJobInstance()).thenReturn(instance);
		when(jobRepository.getJobExecutions(instance)).thenReturn(List.of(current));

		listener.beforeJob(current);

		verify(service).initializeSummary(10L);
		verify(repository, never()).save(any());
		verify(repository, never()).findFirstByJobExecutionIdInAndEventTypeOrderByIdAsc(anyList(), anyString());
	}

	@Test
	void shouldInitializeSummaryWhenPreviousExecutionHasNoSummary() {
		JobExecution current = mock(JobExecution.class);
		JobExecution previous = mock(JobExecution.class);
		JobInstance instance = mock(JobInstance.class);

		when(current.getId()).thenReturn(10L);
		when(previous.getId()).thenReturn(9L);
		when(current.getJobInstance()).thenReturn(instance);

		when(jobRepository.getJobExecutions(instance)).thenReturn(List.of(previous, current));

		when(repository.findFirstByJobExecutionIdInAndEventTypeOrderByIdAsc(List.of(9L), Messages.SUMMARY_EVENT))
				.thenReturn(Optional.empty());

		listener.beforeJob(current);

		verify(service).initializeSummary(10L);
		verify(repository, never()).save(any());
	}

	@Test
	void shouldReusePreviousSummaryForRestart() {
		JobExecution current = mock(JobExecution.class);
		JobExecution previous = mock(JobExecution.class);
		JobInstance instance = mock(JobInstance.class);
		AuditLog summary = new AuditLog();

		when(current.getId()).thenReturn(10L);
		when(previous.getId()).thenReturn(9L);
		when(current.getJobInstance()).thenReturn(instance);

		when(jobRepository.getJobExecutions(instance)).thenReturn(List.of(previous, current));

		when(repository.findFirstByJobExecutionIdInAndEventTypeOrderByIdAsc(List.of(9L), Messages.SUMMARY_EVENT))
				.thenReturn(Optional.of(summary));

		listener.beforeJob(current);

		assertEquals(10L, summary.getJobExecutionId());
		assertEquals("STARTED", summary.getStatus());
		assertNotNull(summary.getEventTimestamp());

		verify(repository).save(summary);
		verify(service, never()).initializeSummary(any());
	}

	@Test
	void shouldFinishSummaryAfterJob() {
		JobExecution execution = mock(JobExecution.class);

		when(execution.getId()).thenReturn(10L);
		when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);

		listener.afterJob(execution);

		verify(service).finishSummary(10L, "COMPLETED");
	}

	@Test
	void shouldIgnoreExecutionWithSameIdAsCurrentExecution() {
		JobExecution current = mock(JobExecution.class);
		JobExecution sameIdExecution = mock(JobExecution.class);
		JobInstance instance = mock(JobInstance.class);

		when(current.getId()).thenReturn(10L);
		when(sameIdExecution.getId()).thenReturn(10L);
		when(current.getJobInstance()).thenReturn(instance);

		when(jobRepository.getJobExecutions(instance)).thenReturn(List.of(sameIdExecution, current));

		listener.beforeJob(current);

		verify(service).initializeSummary(10L);
		verify(repository, never()).save(any());
		verify(repository, never()).findFirstByJobExecutionIdInAndEventTypeOrderByIdAsc(anyList(), anyString());
	}

	@Test
	void shouldIgnoreMultipleExecutionsWithSameIdAsCurrentExecution() {
		JobExecution current = mock(JobExecution.class);
		JobExecution previous1 = mock(JobExecution.class);
		JobExecution previous2 = mock(JobExecution.class);
		JobInstance instance = mock(JobInstance.class);

		when(current.getId()).thenReturn(20L);
		when(previous1.getId()).thenReturn(20L);
		when(previous2.getId()).thenReturn(20L);
		when(current.getJobInstance()).thenReturn(instance);

		when(jobRepository.getJobExecutions(instance)).thenReturn(List.of(previous1, previous2, current));

		listener.beforeJob(current);

		verify(service).initializeSummary(20L);
		verify(repository, never()).save(any());
		verify(repository, never()).findFirstByJobExecutionIdInAndEventTypeOrderByIdAsc(anyList(), anyString());
	}

	@Test
	void shouldReuseSummaryFromMultiplePreviousExecutions() {
		JobExecution current = mock(JobExecution.class);
		JobExecution previous1 = mock(JobExecution.class);
		JobExecution previous2 = mock(JobExecution.class);
		JobInstance instance = mock(JobInstance.class);
		AuditLog summary = new AuditLog();

		when(current.getId()).thenReturn(30L);
		when(previous1.getId()).thenReturn(10L);
		when(previous2.getId()).thenReturn(20L);
		when(current.getJobInstance()).thenReturn(instance);

		when(jobRepository.getJobExecutions(instance)).thenReturn(List.of(previous1, previous2, current));

		when(repository.findFirstByJobExecutionIdInAndEventTypeOrderByIdAsc(List.of(10L, 20L), Messages.SUMMARY_EVENT))
				.thenReturn(Optional.of(summary));

		listener.beforeJob(current);

		assertEquals(30L, summary.getJobExecutionId());
		assertEquals("STARTED", summary.getStatus());
		assertNotNull(summary.getEventTimestamp());

		verify(repository).save(summary);
		verify(service, never()).initializeSummary(any());
	}
}

package com.example.springbatch.batch.listener;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;

import com.example.springbatch.constants.Messages;
import com.example.springbatch.entity.AuditLog;
import com.example.springbatch.repository.AuditLogRepository;

class ProcessingCounterJobListenerTest {

	private AuditLogRepository repository;
	private ProcessingCounterJobListener listener;

	@BeforeEach
	void setUp() {
		repository = mock(AuditLogRepository.class);
		listener = new ProcessingCounterJobListener(repository);
	}

	private JobExecution execution(long executionId, long instanceId) {
		JobExecution execution = mock(JobExecution.class);
		JobInstance instance = mock(JobInstance.class);
		when(execution.getId()).thenReturn(executionId);
		when(execution.getJobInstance()).thenReturn(instance);
		when(instance.getInstanceId()).thenReturn(instanceId);

		return execution;
	}

	@Test
	void shouldCreateAuditForNewJobInstance() {
		JobExecution execution = execution(10L, 100L);
		when(repository.findByJobInstanceId(100L)).thenReturn(Optional.empty());
		listener.beforeJob(execution);
		verify(repository).save(argThat(audit -> audit.getJobInstanceId().equals(100L)
				&& audit.getJobExecutionId().equals(10L) && "STARTED".equals(audit.getStatus())
				&& Messages.SUMMARY_EVENT.equals(audit.getEventType())
				&& Messages.AUDIT_DETAIL.equals(audit.getDetails()) && audit.getEventTimestamp() != null
				&& audit.getTotalRecords() == 0L && audit.getLoadedRecords() == 0L && audit.getMatchedRecords() == 0L
				&& audit.getMismatchedRecords() == 0L && audit.getDuplicateRecords() == 0L
				&& audit.getInvalidRecords() == 0L && audit.getMissingInDatabaseRecords() == 0L));
	}

	@Test
	void shouldReuseAuditOnRestartWithoutResettingCounts() {
		JobExecution execution = execution(11L, 100L);
		AuditLog audit = AuditLog.builder().jobInstanceId(100L).jobExecutionId(10L).status("FAILED").totalRecords(50L)
				.loadedRecords(40L).matchedRecords(5L).build();
		when(repository.findByJobInstanceId(100L)).thenReturn(Optional.of(audit));
		listener.beforeJob(execution);
		assertEquals(11L, audit.getJobExecutionId());
		assertEquals("STARTED", audit.getStatus());
		assertNotNull(audit.getEventTimestamp());
		assertEquals(50L, audit.getTotalRecords());
		assertEquals(40L, audit.getLoadedRecords());
		assertEquals(5L, audit.getMatchedRecords());
		verify(repository).save(audit);
	}

	@Test
	void shouldSetEndTimeWhenAvailable() {
		JobExecution execution = execution(10L, 100L);
		LocalDateTime endTime = LocalDateTime.now();
		when(execution.getStatus()).thenReturn(BatchStatus.COMPLETED);
		when(execution.getEndTime()).thenReturn(endTime);
		AuditLog audit = AuditLog.builder().jobInstanceId(100L).status("STARTED").build();
		when(repository.findByJobInstanceId(100L)).thenReturn(Optional.of(audit));
		listener.afterJob(execution);
		assertEquals("COMPLETED", audit.getStatus());
		assertEquals(endTime, audit.getEventTimestamp());
		verify(repository).save(audit);
	}

	@Test
	void shouldUseCurrentTimeWhenEndTimeIsNull() {
		JobExecution execution = execution(10L, 100L);
		when(execution.getStatus()).thenReturn(BatchStatus.FAILED);
		when(execution.getEndTime()).thenReturn(null);
		AuditLog audit = AuditLog.builder().jobInstanceId(100L).status("STARTED").build();
		when(repository.findByJobInstanceId(100L)).thenReturn(Optional.of(audit));
		LocalDateTime before = LocalDateTime.now();
		listener.afterJob(execution);
		LocalDateTime after = LocalDateTime.now();
		assertEquals("FAILED", audit.getStatus());
		assertNotNull(audit.getEventTimestamp());
		assertFalse(audit.getEventTimestamp().isBefore(before));
		assertFalse(audit.getEventTimestamp().isAfter(after));
		verify(repository).save(audit);
	}

	@Test
	void shouldNotSaveWhenAuditIsMissingAfterJob() {
		JobExecution execution = execution(10L, 100L);
		when(repository.findByJobInstanceId(100L)).thenReturn(Optional.empty());
		assertDoesNotThrow(() -> listener.afterJob(execution));
		verify(repository, never()).save(any(AuditLog.class));
	}
}

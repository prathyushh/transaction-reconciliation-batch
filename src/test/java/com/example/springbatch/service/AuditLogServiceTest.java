package com.example.springbatch.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.springbatch.constants.Messages;
import com.example.springbatch.entity.AuditLog;
import com.example.springbatch.repository.AuditLogRepository;

import org.mockito.MockedStatic;

class AuditLogServiceTest {

	private JdbcTemplate jdbcTemplate;
	private AuditLogRepository repository;
	private AuditLogService service;

	@BeforeEach
	void setUp() {
		jdbcTemplate = mock(JdbcTemplate.class);
		repository = mock(AuditLogRepository.class);
		service = new AuditLogService(jdbcTemplate, repository);
	}

	@Test
	void shouldInitializeSummaryWhenNotPresent() {
		when(repository.findByJobExecutionIdAndEventType(10L, Messages.SUMMARY_EVENT)).thenReturn(Optional.empty());

		service.initializeSummary(10L);

		verify(repository).saveAndFlush(argThat(log -> log.getJobExecutionId().equals(10L)
				&& log.getEventType().equals(Messages.SUMMARY_EVENT) && log.getStatus().equals("STARTED")
				&& log.getTotalRecords() == 0L && log.getLoadedRecords() == 0L && log.getMatchedRecords() == 0L
				&& log.getMismatchedRecords() == 0L && log.getDuplicateRecords() == 0L && log.getInvalidRecords() == 0L
				&& log.getMissingInDatabaseRecords() == 0L));
	}

	@Test
	void shouldNotInitializeDuplicateSummary() {
		when(repository.findByJobExecutionIdAndEventType(10L, Messages.SUMMARY_EVENT))
				.thenReturn(Optional.of(new AuditLog()));

		service.initializeSummary(10L);

		verify(repository, never()).saveAndFlush(any());
	}

	@Test
	void shouldReturnExistingSummary() {
		AuditLog log = new AuditLog();
		when(repository.findByJobExecutionIdAndEventType(10L, Messages.SUMMARY_EVENT)).thenReturn(Optional.of(log));

		assertSame(log, service.getSummary(10L));
	}

	@Test
	void shouldThrowWhenSummaryDoesNotExist() {
		when(repository.findByJobExecutionIdAndEventType(10L, Messages.SUMMARY_EVENT)).thenReturn(Optional.empty());

		IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.getSummary(10L));

		assertEquals(Messages.NO_PROCESSING_SUMMARY_FOUND + 10L, exception.getMessage());
	}

	@Test
	void shouldRejectCsv1UpdateWhenStepContextMissing() {
		try (MockedStatic<StepSynchronizationManager> context = mockStatic(StepSynchronizationManager.class)) {
			context.when(StepSynchronizationManager::getContext).thenReturn(null);

			IllegalStateException exception = assertThrows(IllegalStateException.class,
					() -> service.recordCsv1Chunk(5, 3, 1, 1));

			assertEquals(Messages.STEP_CONTEXT_NOT_FOUND, exception.getMessage());
		}
	}

	@Test
	void shouldRejectCsv2UpdateWhenStepContextMissing() {
		try (MockedStatic<StepSynchronizationManager> context = mockStatic(StepSynchronizationManager.class)) {
			context.when(StepSynchronizationManager::getContext).thenReturn(null);

			assertThrows(IllegalStateException.class, () -> service.recordCsv2Chunk(5, 2, 2, 1));
		}
	}

	@Test
	void shouldRecordCsv1Chunk() {
		withStepContext(() -> {
			when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

			service.recordCsv1Chunk(5, 3, 1, 1);

			verify(jdbcTemplate).update(anyString(), any(Object[].class));
		});
	}

	@Test
	void shouldRecordCsv2Chunk() {
		withStepContext(() -> {
			when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

			service.recordCsv2Chunk(5, 2, 2, 1);

			verify(jdbcTemplate).update(anyString(), any(Object[].class));
		});
	}

	@Test
	void shouldFinishSummary() {
		when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

		service.finishSummary(10L, "COMPLETED");

		verify(jdbcTemplate).update(anyString(), any(Object[].class));
	}

	@Test
	void shouldRejectCsv1UpdateWhenNoSummaryWasUpdated() {
		withStepContext(() -> {
			when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

			assertThrows(IllegalStateException.class, () -> service.recordCsv1Chunk(1, 1, 0, 0));
		});
	}

	@Test
	void shouldRejectCsv2UpdateWhenMultipleSummariesWereUpdated() {
		withStepContext(() -> {
			when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(2);

			assertThrows(IllegalStateException.class, () -> service.recordCsv2Chunk(1, 1, 0, 0));
		});
	}

	@Test
	void shouldRejectFinishWhenSummaryWasNotUpdated() {
		when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);

		assertThrows(IllegalStateException.class, () -> service.finishSummary(10L, "FAILED"));
	}

	private void withStepContext(Runnable action) {
		StepContext stepContext = mock(StepContext.class);
		var stepExecution = mock(org.springframework.batch.core.step.StepExecution.class);
		var jobExecution = mock(org.springframework.batch.core.job.JobExecution.class);

		when(stepContext.getStepExecution()).thenReturn(stepExecution);
		when(stepExecution.getJobExecution()).thenReturn(jobExecution);
		when(jobExecution.getId()).thenReturn(10L);

		try (MockedStatic<StepSynchronizationManager> context = mockStatic(StepSynchronizationManager.class)) {
			context.when(StepSynchronizationManager::getContext).thenReturn(stepContext);
			action.run();
		}
	}
}
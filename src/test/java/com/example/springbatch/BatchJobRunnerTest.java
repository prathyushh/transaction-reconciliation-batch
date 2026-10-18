package com.example.springbatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.test.util.ReflectionTestUtils;

class BatchJobRunnerTest {
	private JobOperator operator;
	private Job job;
	private BatchJobRunner runner;

	@BeforeEach
	void setUp() {
		operator = mock(JobOperator.class);
		job = mock(Job.class);
		when(job.getName()).thenReturn("transactionReconciliationJob");
		runner = new BatchJobRunner(operator, job);
		ReflectionTestUtils.setField(runner, "runJob", true);
		ReflectionTestUtils.setField(runner, "runId", 100L);
	}

	@Test
	void shouldNotRunWhenDisabled() throws Exception {
		ReflectionTestUtils.setField(runner, "runJob", false);
		runner.run();
		verifyNoInteractions(operator);
	}

	@Test
	void shouldRunJobWhenEnabled() throws Exception {
		runner.run();
		verify(operator).run(eq(job), any());
	}

	@Test
	void shouldPassConfiguredRunIdAsJobParameter() throws Exception {
		runner.run();
		org.mockito.ArgumentCaptor<org.springframework.batch.core.job.parameters.JobParameters> captor = org.mockito.ArgumentCaptor
				.forClass(org.springframework.batch.core.job.parameters.JobParameters.class);
		verify(operator).run(eq(job), captor.capture());
		assertEquals(100L, captor.getValue().getLong("run.id"));
	}

	@Test
	void shouldPropagateJobOperatorFailure() throws Exception {
		when(operator.run(eq(job), any())).thenThrow(new IllegalStateException("Job launch failed"));
		IllegalStateException exception = assertThrows(IllegalStateException.class, () -> runner.run());
		assertEquals("Job launch failed", exception.getMessage());
	}
}
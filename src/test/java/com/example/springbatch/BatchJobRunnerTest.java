package com.example.springbatch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.springbatch.batch.writer.ReconciliationCsvWriter;

class BatchJobRunnerTest {

	private JobOperator operator;
	private Job job;
	private JobRepository repository;
	private ReconciliationCsvWriter csvWriter;
	private BatchJobRunner runner;

	@BeforeEach
	void setUp() {
		operator = mock(JobOperator.class);
		job = mock(Job.class);
		repository = mock(JobRepository.class);
		csvWriter = mock(ReconciliationCsvWriter.class);

		when(job.getName()).thenReturn("transactionReconciliationJob");

		runner = new BatchJobRunner(operator, job, repository, csvWriter);

		ReflectionTestUtils.setField(runner, "runJob", true);
		ReflectionTestUtils.setField(runner, "runId", 100L);
	}

	@Test
	void shouldNotRunWhenDisabled() throws Exception {
		ReflectionTestUtils.setField(runner, "runJob", false);

		runner.run();

		verifyNoInteractions(operator, repository, csvWriter);
	}

	@Test
	void shouldInitializeCsvForNewJobInstance() throws Exception {
		when(repository.getJobInstance(eq("transactionReconciliationJob"), any())).thenReturn(null);

		runner.run();

		verify(csvWriter).initializeOutput();
		verify(operator).run(eq(job), any());
	}

	@Test
	void shouldPreserveCsvForExistingJobInstance() throws Exception {
		when(repository.getJobInstance(eq("transactionReconciliationJob"), any())).thenReturn(mock(JobInstance.class));

		runner.run();

		verify(csvWriter, never()).initializeOutput();
		verify(operator).run(eq(job), any());
	}

	@Test
	void shouldPropagateJobOperatorFailure() throws Exception {
		when(repository.getJobInstance(eq("transactionReconciliationJob"), any())).thenReturn(mock(JobInstance.class));

		when(operator.run(eq(job), any())).thenThrow(new IllegalStateException("Job launch failed"));

		assertThrows(IllegalStateException.class, () -> runner.run());
	}
}
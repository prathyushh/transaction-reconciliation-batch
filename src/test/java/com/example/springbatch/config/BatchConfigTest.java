package com.example.springbatch.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.springbatch.batch.listener.ProcessingCounterJobListener;
import com.example.springbatch.batch.processor.ReconciliationProcessor;
import com.example.springbatch.batch.processor.TransactionItemProcessor;
import com.example.springbatch.batch.tasklet.FileValidationTasklet;
import com.example.springbatch.batch.writer.ReconciliationProcessingResult;
import com.example.springbatch.dto.TransactionRequestDto;
import com.example.springbatch.repository.TransactionRecordRepository;

class BatchConfigTest {

	private final BatchConfig config = new BatchConfig();

	@AfterEach
	void cleanUp() {
		StepSynchronizationManager.close();
	}

	private void registerStepContext() {
		JobInstance instance = mock(JobInstance.class);
		JobExecution jobExecution = mock(JobExecution.class);
		StepExecution stepExecution = mock(StepExecution.class);

		when(instance.getInstanceId()).thenReturn(100L);
		when(jobExecution.getJobInstance()).thenReturn(instance);
		when(stepExecution.getJobExecution()).thenReturn(jobExecution);

		StepSynchronizationManager.register(stepExecution);
	}

	@Test
	void shouldRejectTransactionProcessorWithoutStepContext() {
		assertThrows(IllegalStateException.class, config::transactionItemProcessor);
	}

	@Test
	void shouldCreateTransactionProcessorWithStepContext() {
		registerStepContext();

		TransactionItemProcessor processor = config.transactionItemProcessor();

		assertNotNull(processor);
	}

	@Test
	void shouldRejectReconciliationProcessorWithoutStepContext() {
		TransactionRecordRepository repository = mock(TransactionRecordRepository.class);

		assertThrows(IllegalStateException.class, () -> config.reconciliationProcessor(repository));
	}

	@Test
	void shouldCreateReconciliationProcessorWithStepContext() {
		registerStepContext();

		TransactionRecordRepository repository = mock(TransactionRecordRepository.class);

		ReconciliationProcessor processor = config.reconciliationProcessor(repository);

		assertNotNull(processor);
	}

	@Test
	void shouldCreateJobWithFourSteps() {
		JobRepository repository = mock(JobRepository.class);
		Step csv1Validation = mock(Step.class);
		Step csv1Loading = mock(Step.class);
		Step csv2Validation = mock(Step.class);
		Step csv2Reconciliation = mock(Step.class);
		ProcessingCounterJobListener listener = mock(ProcessingCounterJobListener.class);

		Job job = config.transactionReconciliationJob(repository, csv1Validation, csv1Loading, csv2Validation,
				csv2Reconciliation, listener);

		assertNotNull(job);
		assertEquals("transactionReconciliationJob", job.getName());
	}

	@Test
	void shouldCreateCsv1ValidationStep() {
		Step step = config.csv1FileValidationStep(mock(JobRepository.class), mock(PlatformTransactionManager.class),
				mock(FileValidationTasklet.class));

		assertNotNull(step);
		assertEquals("csv1FileValidationStep", step.getName());
	}

	@Test
	void shouldCreateCsv2ValidationStep() {
		Step step = config.csv2FileValidationStep(mock(JobRepository.class), mock(PlatformTransactionManager.class),
				mock(FileValidationTasklet.class));

		assertNotNull(step);
		assertEquals("csv2FileValidationStep", step.getName());
	}

	@Test
	void shouldCreateCsv1LoadingStep() {
		@SuppressWarnings("unchecked")
		FlatFileItemReader<TransactionRequestDto> reader = mock(FlatFileItemReader.class);

		@SuppressWarnings("unchecked")
		AsyncItemProcessor<TransactionRequestDto, ReconciliationProcessingResult> processor = mock(
				AsyncItemProcessor.class);

		@SuppressWarnings("unchecked")
		AsyncItemWriter<ReconciliationProcessingResult> writer = mock(AsyncItemWriter.class);

		Step step = config.csv1LoadingStep(mock(JobRepository.class), mock(PlatformTransactionManager.class), reader,
				processor, writer, 100);

		assertNotNull(step);
		assertEquals("csv1LoadingStep", step.getName());
	}

	@Test
	void shouldCreateCsv2ReconciliationStep() {
		@SuppressWarnings("unchecked")
		FlatFileItemReader<TransactionRequestDto> reader = mock(FlatFileItemReader.class);

		@SuppressWarnings("unchecked")
		AsyncItemProcessor<TransactionRequestDto, ReconciliationProcessingResult> processor = mock(
				AsyncItemProcessor.class);

		@SuppressWarnings("unchecked")
		AsyncItemWriter<ReconciliationProcessingResult> writer = mock(AsyncItemWriter.class);

		Step step = config.csv2ReconciliationStep(mock(JobRepository.class), mock(PlatformTransactionManager.class),
				reader, processor, writer, 100);

		assertNotNull(step);
		assertEquals("csv2ReconciliationStep", step.getName());
	}
}
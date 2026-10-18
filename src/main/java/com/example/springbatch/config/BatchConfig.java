package com.example.springbatch.config;

import java.util.concurrent.Future;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.example.springbatch.batch.listener.ProcessingCounterJobListener;
import com.example.springbatch.batch.processor.ReconciliationProcessor;
import com.example.springbatch.batch.processor.TransactionItemProcessor;
import com.example.springbatch.batch.tasklet.FileValidationTasklet;
import com.example.springbatch.batch.writer.ReconciliationProcessingResult;
import com.example.springbatch.dto.TransactionRequestDto;
import com.example.springbatch.repository.TransactionRecordRepository;

@Configuration
public class BatchConfig {

	@Bean
	public Step csv1FileValidationStep(JobRepository repository, PlatformTransactionManager transactionManager,
			@Qualifier("csv1FileValidationTasklet") FileValidationTasklet tasklet) {
		return new StepBuilder("csv1FileValidationStep", repository).tasklet(tasklet, transactionManager).build();
	}

	@Bean
	public Step csv1LoadingStep(JobRepository repository, PlatformTransactionManager transactionManager,
			@Qualifier("csv1ItemReader") FlatFileItemReader<TransactionRequestDto> csv1ItemReader,
			@Qualifier("asyncTransactionProcessor") AsyncItemProcessor<TransactionRequestDto, ReconciliationProcessingResult> asyncTransactionProcessor,
			@Qualifier("asyncTransactionWriter") AsyncItemWriter<ReconciliationProcessingResult> asyncTransactionWriter,
			@Value("${app.batch.chunk-size}") int chunkSize) {
		return new StepBuilder("csv1LoadingStep", repository)
				.<TransactionRequestDto, Future<ReconciliationProcessingResult>>chunk(chunkSize)
				.transactionManager(transactionManager).reader(csv1ItemReader).processor(asyncTransactionProcessor)
				.writer(asyncTransactionWriter).build();
	}

	@Bean
	public Step csv2FileValidationStep(JobRepository repository, PlatformTransactionManager transactionManager,
			@Qualifier("csv2FileValidationTasklet") FileValidationTasklet tasklet) {
		return new StepBuilder("csv2FileValidationStep", repository).tasklet(tasklet, transactionManager).build();
	}

	@Bean
	public Step csv2ReconciliationStep(JobRepository repository, PlatformTransactionManager transactionManager,
			@Qualifier("csv2ItemReader") FlatFileItemReader<TransactionRequestDto> csv2ItemReader,
			@Qualifier("asyncReconciliationProcessor") AsyncItemProcessor<TransactionRequestDto, ReconciliationProcessingResult> asyncReconciliationProcessor,
			@Qualifier("asyncReconciliationWriter") AsyncItemWriter<ReconciliationProcessingResult> asyncReconciliationWriter,
			@Value("${app.batch.chunk-size}") int chunkSize) {
		return new StepBuilder("csv2ReconciliationStep", repository)
				.<TransactionRequestDto, Future<ReconciliationProcessingResult>>chunk(chunkSize)
				.transactionManager(transactionManager).reader(csv2ItemReader).processor(asyncReconciliationProcessor)
				.writer(asyncReconciliationWriter).build();
	}

	@Bean
	@StepScope
	public TransactionItemProcessor transactionItemProcessor() {
		return new TransactionItemProcessor(getCurrentJobInstanceId());
	}

	@Bean
	@StepScope
	public ReconciliationProcessor reconciliationProcessor(TransactionRecordRepository transactionRecordRepository) {
		return new ReconciliationProcessor(transactionRecordRepository, getCurrentJobInstanceId());
	}

	private long getCurrentJobInstanceId() {
		StepContext context = StepSynchronizationManager.getContext();
		if (context == null) {
			throw new IllegalStateException("No active Spring Batch step context is available");
		}
		return context.getStepExecution().getJobExecution().getJobInstance().getInstanceId();
	}

	@Bean
	public Job transactionReconciliationJob(JobRepository repository, Step csv1FileValidationStep, Step csv1LoadingStep,
			Step csv2FileValidationStep, Step csv2ReconciliationStep,
			ProcessingCounterJobListener processingCounterJobListener) {
		return new JobBuilder("transactionReconciliationJob", repository).listener(processingCounterJobListener)
				.start(csv1FileValidationStep).next(csv1LoadingStep).next(csv2FileValidationStep)
				.next(csv2ReconciliationStep).build();
	}
}

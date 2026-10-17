package com.example.springbatch.config;

import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.example.springbatch.batch.processor.ReconciliationProcessor;
import com.example.springbatch.batch.processor.TransactionItemProcessor;
import com.example.springbatch.batch.writer.ReconciliationItemWriter;
import com.example.springbatch.batch.writer.ReconciliationProcessingResult;
import com.example.springbatch.batch.writer.TransactionItemWriter;
import com.example.springbatch.dto.TransactionRequestDto;

@Configuration
public class AsyncProcessingConfig {
	@Bean
	public TaskExecutor transactionProcessorExecutor(@Value("${app.batch.processor.core-pool-size}") int corePoolSize,
			@Value("${app.batch.processor.max-pool-size}") int maxPoolSize,
			@Value("${app.batch.processor.queue-capacity}") int queueCapacity) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(corePoolSize);
		executor.setMaxPoolSize(maxPoolSize);
		executor.setQueueCapacity(queueCapacity);
		executor.setThreadNamePrefix("transaction-processor-");
		return executor;
	}

	@Bean
	public AsyncItemProcessor<TransactionRequestDto, ReconciliationProcessingResult> asyncTransactionProcessor(
			TransactionItemProcessor transactionItemProcessor, TaskExecutor transactionProcessorExecutor) {
		AsyncItemProcessor<TransactionRequestDto, ReconciliationProcessingResult> processor = new AsyncItemProcessor<>(
				transactionItemProcessor);
		processor.setTaskExecutor(transactionProcessorExecutor);
		return processor;
	}

	@Bean
	public AsyncItemWriter<ReconciliationProcessingResult> asyncTransactionWriter(
			TransactionItemWriter transactionItemWriter) {
		return new AsyncItemWriter<>(transactionItemWriter);
	}

	@Bean
	public AsyncItemProcessor<TransactionRequestDto, ReconciliationProcessingResult> asyncReconciliationProcessor(
			ReconciliationProcessor reconciliationProcessor, TaskExecutor transactionProcessorExecutor) {
		AsyncItemProcessor<TransactionRequestDto, ReconciliationProcessingResult> processor = new AsyncItemProcessor<>(
				reconciliationProcessor);
		processor.setTaskExecutor(transactionProcessorExecutor);
		return processor;
	}

	@Bean
	public AsyncItemWriter<ReconciliationProcessingResult> asyncReconciliationWriter(
			ReconciliationItemWriter reconciliationItemWriter) {
		return new AsyncItemWriter<>(reconciliationItemWriter);
	}
}

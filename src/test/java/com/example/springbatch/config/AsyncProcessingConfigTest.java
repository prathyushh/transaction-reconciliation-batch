package com.example.springbatch.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.example.springbatch.batch.processor.ReconciliationProcessor;
import com.example.springbatch.batch.processor.TransactionItemProcessor;
import com.example.springbatch.batch.writer.ReconciliationItemWriter;
import com.example.springbatch.batch.writer.TransactionItemWriter;

class AsyncProcessingConfigTest {

	private final AsyncProcessingConfig config = new AsyncProcessingConfig();

	@Test
	void shouldConfigureExecutor() {
		TaskExecutor executor = config.transactionProcessorExecutor(2, 4, 20);

		assertInstanceOf(ThreadPoolTaskExecutor.class, executor);

		ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;
		assertEquals(2, pool.getCorePoolSize());
		assertEquals(4, pool.getMaxPoolSize());
		assertEquals(20, pool.getQueueCapacity());
		assertEquals("transaction-processor-", pool.getThreadNamePrefix());

		pool.shutdown();
	}

	@Test
	void shouldCreateTransactionProcessor() {
		TransactionItemProcessor delegate = mock(TransactionItemProcessor.class);
		TaskExecutor executor = mock(TaskExecutor.class);

		AsyncItemProcessor<?, ?> processor = config.asyncTransactionProcessor(delegate, executor);

		assertNotNull(processor);
	}

	@Test
	void shouldCreateTransactionWriter() {
		TransactionItemWriter delegate = mock(TransactionItemWriter.class);

		AsyncItemWriter<?> writer = config.asyncTransactionWriter(delegate);

		assertNotNull(writer);
	}

	@Test
	void shouldCreateReconciliationProcessor() {
		ReconciliationProcessor delegate = mock(ReconciliationProcessor.class);
		TaskExecutor executor = mock(TaskExecutor.class);

		AsyncItemProcessor<?, ?> processor = config.asyncReconciliationProcessor(delegate, executor);

		assertNotNull(processor);
	}

	@Test
	void shouldCreateReconciliationWriter() {
		ReconciliationItemWriter delegate = mock(ReconciliationItemWriter.class);

		AsyncItemWriter<?> writer = config.asyncReconciliationWriter(delegate);

		assertNotNull(writer);
	}
}
package com.example.springbatch.config;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import com.example.springbatch.batch.FileValidationTasklet;
import com.example.springbatch.batch.ReconciliationProcessingResult;
import com.example.springbatch.batch.TransactionItemProcessor;
import com.example.springbatch.batch.TransactionItemWriter;
import com.example.springbatch.dto.TransactionRequestDto;
import com.example.springbatch.service.FileValidationService;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.batch.integration.async.AsyncItemProcessor;
import org.springframework.batch.integration.async.AsyncItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfig {
	@Bean
	public FileValidationTasklet csv1FileValidationTasklet(@Value("${app.input.csv1-path}") String path,
			@Value("${app.csv.expected-headers}") String headers, FileValidationService fileValidationService) {
		return new FileValidationTasklet(new FileSystemResource(path), parseHeaders(headers), fileValidationService);
	}

	@Bean
	public FileValidationTasklet csv2FileValidationTasklet(@Value("${app.input.csv2-path}") String path,
			@Value("${app.csv.expected-headers}") String headers, FileValidationService fileValidationService) {
		return new FileValidationTasklet(new FileSystemResource(path), parseHeaders(headers), fileValidationService);
	}

	private List<String> parseHeaders(String headers) {
		return Arrays.stream(headers.split(",", -1)).map(String::trim).toList();
	}

	@Bean
	@StepScope
	public FlatFileItemReader<TransactionRequestDto> csv1ItemReader(@Value("${app.input.csv1-path}") String path) {
		DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
		tokenizer.setNames("transactionId", "accountNumber", "transactionDate", "amount", "currency", "transactionType",
				"description");
		BeanWrapperFieldSetMapper<TransactionRequestDto> fieldSetMapper = new BeanWrapperFieldSetMapper<>();
		fieldSetMapper.setTargetType(TransactionRequestDto.class);
		return new FlatFileItemReaderBuilder<TransactionRequestDto>().name("csv1ItemReader")
				.resource(new FileSystemResource(path)).linesToSkip(1).lineMapper((line, lineNumber) -> {
					TransactionRequestDto dto = fieldSetMapper.mapFieldSet(tokenizer.tokenize(line));
					dto.setLineNumber((long) lineNumber);
					return dto;
				}).build();
	}

	@Bean
	@StepScope
	public TransactionItemProcessor transactionItemProcessor(@Value("#{jobParameters['run.id']}") Long runId) {
		return new TransactionItemProcessor(runId);
	}

	@Bean
	public TaskExecutor transactionProcessorExecutor(@Value("${app.batch.processor.core-pool-size:4}") int corePoolSize,
			@Value("${app.batch.processor.max-pool-size:4}") int maxPoolSize,
			@Value("${app.batch.processor.queue-capacity:100}") int queueCapacity) {
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
	public Step csv1FileValidationStep(JobRepository repository, PlatformTransactionManager transactionManager,
			@Qualifier("csv1FileValidationTasklet") FileValidationTasklet tasklet) {
		return new StepBuilder("csv1FileValidationStep", repository).tasklet(tasklet, transactionManager)
				.allowStartIfComplete(true).build();
	}

	@Bean
	public Step csv1LoadingStep(JobRepository repository, PlatformTransactionManager transactionManager,
			FlatFileItemReader<TransactionRequestDto> csv1ItemReader,
			AsyncItemProcessor<TransactionRequestDto, ReconciliationProcessingResult> asyncTransactionProcessor,
			AsyncItemWriter<ReconciliationProcessingResult> asyncTransactionWriter,
			@Value("${app.batch.chunk-size:100}") int chunkSize) {
		return new StepBuilder("csv1LoadingStep", repository)
				.<TransactionRequestDto, Future<ReconciliationProcessingResult>>chunk(chunkSize)
				.transactionManager(transactionManager).reader(csv1ItemReader).processor(asyncTransactionProcessor)
				.writer(asyncTransactionWriter).build();
	}

	@Bean
	public Step csv2FileValidationStep(JobRepository repository, PlatformTransactionManager transactionManager,
			@Qualifier("csv2FileValidationTasklet") FileValidationTasklet tasklet) {
		return new StepBuilder("csv2FileValidationStep", repository).tasklet(tasklet, transactionManager)
				.allowStartIfComplete(true).build();
	}

	@Bean
	public Job transactionReconciliationJob(JobRepository repository, Step csv1FileValidationStep, Step csv1LoadingStep,
			Step csv2FileValidationStep) {
		return new JobBuilder("transactionReconciliationJob", repository).start(csv1FileValidationStep)
				.next(csv1LoadingStep).next(csv2FileValidationStep).build();
	}
}

package com.example.springbatch.config;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.infrastructure.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import com.example.springbatch.dto.TransactionRequestDto;

@Configuration
public class ItemReaderConfig {
	@Bean
	@StepScope
	public FlatFileItemReader<TransactionRequestDto> csv1ItemReader(@Value("${app.input.csv1-path}") String path) {
		return createTransactionReader(path, "csv1ItemReader");
	}

	@Bean
	@StepScope
	public FlatFileItemReader<TransactionRequestDto> csv2ItemReader(@Value("${app.input.csv2-path}") String path) {
		return createTransactionReader(path, "csv2ItemReader");
	}

	private FlatFileItemReader<TransactionRequestDto> createTransactionReader(String path, String readerName) {
		DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
		tokenizer.setNames("transactionId", "accountNumber", "transactionDate", "amount", "currency", "transactionType",
				"description");
		BeanWrapperFieldSetMapper<TransactionRequestDto> mapper = new BeanWrapperFieldSetMapper<>();
		mapper.setTargetType(TransactionRequestDto.class);
		return new FlatFileItemReaderBuilder<TransactionRequestDto>().name(readerName)
				.resource(new FileSystemResource(path)).linesToSkip(1).lineMapper((line, lineNumber) -> {
					TransactionRequestDto dto = mapper.mapFieldSet(tokenizer.tokenize(line));
					dto.setLineNumber((long) lineNumber);
					return dto;
				}).build();
	}
}
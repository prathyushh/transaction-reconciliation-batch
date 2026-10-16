package com.example.springbatch.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import com.example.springbatch.batch.tasklet.FileValidationTasklet;
import com.example.springbatch.service.FileValidationService;

@Configuration
public class FileValidationConfig {
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
}
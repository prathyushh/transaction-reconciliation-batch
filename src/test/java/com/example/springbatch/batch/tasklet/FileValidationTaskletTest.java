package com.example.springbatch.batch.tasklet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import com.example.springbatch.service.FileValidationService;

class FileValidationTaskletTest {

	private FileValidationService service;
	private FileValidationTasklet tasklet;
	private StepContribution contribution;
	private ChunkContext chunkContext;
	private Resource resource;
	private List<String> headers;

	@BeforeEach
	void setUp() {
		service = mock(FileValidationService.class);
		resource = new ByteArrayResource(new byte[0]);
		headers = List.of("transaction_id", "amount");

		tasklet = new FileValidationTasklet(resource, headers, service);
		contribution = mock(StepContribution.class);
		chunkContext = mock(ChunkContext.class);
	}

	@Test
	void shouldValidateFileAndFinish() {
		RepeatStatus status = tasklet.execute(contribution, chunkContext);

		assertEquals(RepeatStatus.FINISHED, status);
		verify(service).validateFile(resource, headers);
	}

	@Test
	void shouldPropagateValidationFailure() {
		doThrow(new IllegalArgumentException("Invalid file")).when(service).validateFile(resource, headers);

		assertThrows(IllegalArgumentException.class, () -> tasklet.execute(contribution, chunkContext));

		verify(service).validateFile(resource, headers);
	}
}
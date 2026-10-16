package com.example.springbatch.batch;

import java.util.List;
import com.example.springbatch.service.FileValidationService;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;
import org.springframework.core.io.Resource;

@RequiredArgsConstructor
public class FileValidationTasklet implements Tasklet {
	private final Resource resource;
	private final List<String> expectedHeaders;
	private final FileValidationService fileValidationService;

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		fileValidationService.validateFile(resource, expectedHeaders);
		return RepeatStatus.FINISHED;
	}
}

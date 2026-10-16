package com.example.springbatch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.example.springbatch.batch.ReconciliationCsvWriter;

@Component
@RequiredArgsConstructor
@Slf4j
public class BatchJobRunner implements CommandLineRunner {
	private final JobOperator jobOperator;
	private final Job transactionReconciliationJob;
	private final JobRepository jobRepository;
	private final ReconciliationCsvWriter reconciliationCsvWriter;
	@Value("${app.batch.run}")
	private boolean runJob;
	@Value("${app.batch.run-id}")
	private long runId;

	@Override
	public void run(String... args) throws Exception {
		if (!runJob) {
			log.info("Batch job execution is disabled.");
			return;
		}
		JobParameters parameters = new JobParametersBuilder().addLong("run.id", runId).toJobParameters();
		boolean existingInstance = jobRepository.getJobInstance(transactionReconciliationJob.getName(),
				parameters) != null;
		if (!existingInstance) {
			reconciliationCsvWriter.initializeOutput();
			log.info("New job instance. Output CSV initialized.");
		} else {
			log.info("Existing job instance. Preserving output CSV for restart.");
		}
		jobOperator.run(transactionReconciliationJob, parameters);
	}
}

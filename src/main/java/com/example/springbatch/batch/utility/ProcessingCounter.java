
package com.example.springbatch.batch.utility;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.stereotype.Component;

import lombok.Getter;

@Component
@JobScope
@Getter
public class ProcessingCounter {
	private final AtomicLong totalRecords = new AtomicLong();
	private final AtomicLong loadedRecords = new AtomicLong();
	private final AtomicLong matchedRecords = new AtomicLong();
	private final AtomicLong mismatchedRecords = new AtomicLong();
	private final AtomicLong duplicateRecords = new AtomicLong();
	private final AtomicLong invalidRecords = new AtomicLong();
	private final AtomicLong missingInDatabaseRecords = new AtomicLong();

	public void incrementTotalRecords() {
		totalRecords.incrementAndGet();
	}

	public void incrementTotalRecords(long count) {
		totalRecords.addAndGet(count);
	}

	public void incrementLoadedRecords() {
		loadedRecords.incrementAndGet();
	}

	public void incrementLoadedRecords(long count) {
		loadedRecords.addAndGet(count);
	}

	public void incrementMatchedRecords() {
		matchedRecords.incrementAndGet();
	}

	public void incrementMismatchedRecords() {
		mismatchedRecords.incrementAndGet();
	}

	public void incrementDuplicateRecords() {
		duplicateRecords.incrementAndGet();
	}

	public void incrementInvalidRecords() {
		invalidRecords.incrementAndGet();
	}

	public void incrementMissingInDatabaseRecords() {
		missingInDatabaseRecords.incrementAndGet();
	}
}

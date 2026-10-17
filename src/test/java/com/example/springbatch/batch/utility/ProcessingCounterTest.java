package com.example.springbatch.batch.utility;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProcessingCounterTest {

	@Test
	void shouldStartAllCountersAtZero() {
		ProcessingCounter counter = new ProcessingCounter();

		assertEquals(0, counter.getTotalRecords().get());
		assertEquals(0, counter.getLoadedRecords().get());
		assertEquals(0, counter.getMatchedRecords().get());
		assertEquals(0, counter.getMismatchedRecords().get());
		assertEquals(0, counter.getDuplicateRecords().get());
		assertEquals(0, counter.getInvalidRecords().get());
		assertEquals(0, counter.getMissingInDatabaseRecords().get());
	}

	@Test
	void shouldIncrementAllCounters() {
		ProcessingCounter counter = new ProcessingCounter();

		counter.incrementTotalRecords();
		counter.incrementLoadedRecords();
		counter.incrementMatchedRecords();
		counter.incrementMismatchedRecords();
		counter.incrementDuplicateRecords();
		counter.incrementInvalidRecords();
		counter.incrementMissingInDatabaseRecords();

		assertEquals(1, counter.getTotalRecords().get());
		assertEquals(1, counter.getLoadedRecords().get());
		assertEquals(1, counter.getMatchedRecords().get());
		assertEquals(1, counter.getMismatchedRecords().get());
		assertEquals(1, counter.getDuplicateRecords().get());
		assertEquals(1, counter.getInvalidRecords().get());
		assertEquals(1, counter.getMissingInDatabaseRecords().get());
	}

	@Test
	void shouldAddCounts() {
		ProcessingCounter counter = new ProcessingCounter();

		counter.incrementTotalRecords(10);
		counter.incrementTotalRecords(5);
		counter.incrementLoadedRecords(3);
		counter.incrementLoadedRecords(2);

		assertEquals(15, counter.getTotalRecords().get());
		assertEquals(5, counter.getLoadedRecords().get());
	}

	@Test
	void shouldSupportNegativeAndZeroBulkCounts() {
		ProcessingCounter counter = new ProcessingCounter();

		counter.incrementTotalRecords(0);
		counter.incrementTotalRecords(-1);

		assertEquals(-1, counter.getTotalRecords().get());
	}
}
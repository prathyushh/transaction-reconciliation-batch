package com.example.springbatch.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class AuditLogTest {

	@Test
	void shouldBuildAuditLogWithAllFields() {
		LocalDateTime now = LocalDateTime.now();

		AuditLog log = AuditLog.builder().id(1L).jobExecutionId(10L).eventType("PROCESSING_SUMMARY").status("STARTED")
				.eventTimestamp(now).details("Test").totalRecords(10L).loadedRecords(5L).matchedRecords(2L)
				.mismatchedRecords(1L).duplicateRecords(1L).invalidRecords(1L).missingInDatabaseRecords(1L).build();

		assertEquals(1L, log.getId());
		assertEquals(10L, log.getJobExecutionId());
		assertEquals("PROCESSING_SUMMARY", log.getEventType());
		assertEquals("STARTED", log.getStatus());
		assertEquals(now, log.getEventTimestamp());
		assertEquals("Test", log.getDetails());
		assertEquals(10L, log.getTotalRecords());
		assertEquals(5L, log.getLoadedRecords());
		assertEquals(2L, log.getMatchedRecords());
		assertEquals(1L, log.getMismatchedRecords());
		assertEquals(1L, log.getDuplicateRecords());
		assertEquals(1L, log.getInvalidRecords());
		assertEquals(1L, log.getMissingInDatabaseRecords());
	}

	@Test
	void shouldSupportConstructorsAndEquality() {
		assertNotNull(new AuditLog());

		AuditLog first = AuditLog.builder().id(1L).status("STARTED").build();
		AuditLog second = AuditLog.builder().id(1L).status("STARTED").build();

		assertEquals(first, second);
		assertEquals(first.hashCode(), second.hashCode());
		assertTrue(first.toString().contains("STARTED"));

		second.setStatus("FAILED");
		assertNotEquals(first, second);
	}
}
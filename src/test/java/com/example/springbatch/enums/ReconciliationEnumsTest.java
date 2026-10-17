package com.example.springbatch.enums;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ReconciliationEnumsTest {

	@Test
	void shouldContainAllActions() {
		assertArrayEquals(
				new ReconciliationAction[] { ReconciliationAction.INSERT, ReconciliationAction.SKIP,
						ReconciliationAction.UPDATE_AMOUNT, ReconciliationAction.REPORT, ReconciliationAction.NONE },
				ReconciliationAction.values());
	}

	@Test
	void shouldContainAllStatuses() {
		assertArrayEquals(
				new ReconciliationStatus[] { ReconciliationStatus.VALID, ReconciliationStatus.INVALID_RECORD,
						ReconciliationStatus.DUPLICATE_IN_CSV1, ReconciliationStatus.MATCH,
						ReconciliationStatus.MISMATCH, ReconciliationStatus.MISSING_IN_DATABASE },
				ReconciliationStatus.values());
	}

	@Test
	void shouldConvertNamesToEnumValues() {
		assertEquals(ReconciliationAction.INSERT, ReconciliationAction.valueOf("INSERT"));
		assertEquals(ReconciliationAction.UPDATE_AMOUNT, ReconciliationAction.valueOf("UPDATE_AMOUNT"));
		assertEquals(ReconciliationStatus.MATCH, ReconciliationStatus.valueOf("MATCH"));
		assertEquals(ReconciliationStatus.MISMATCH, ReconciliationStatus.valueOf("MISMATCH"));
	}
}
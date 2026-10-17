
package com.example.springbatch.batch.writer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.example.springbatch.constants.Messages;
import com.example.springbatch.dto.TransactionRequestDto;
import com.example.springbatch.entity.TransactionRecord;
import com.example.springbatch.enums.ReconciliationAction;
import com.example.springbatch.enums.ReconciliationStatus;
import com.example.springbatch.service.AuditLogService;

class TransactionItemWriterTest {

	private NamedParameterJdbcTemplate jdbcTemplate;
	private JdbcBatchItemWriter<TransactionRecord> databaseWriter;
	private ReconciliationCsvWriter csvWriter;
	private AuditLogService auditService;
	private TransactionItemWriter writer;

	@BeforeEach
	void setUp() {
		jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
		databaseWriter = mock(JdbcBatchItemWriter.class);
		csvWriter = mock(ReconciliationCsvWriter.class);
		auditService = mock(AuditLogService.class);

		writer = new TransactionItemWriter(jdbcTemplate, databaseWriter, csvWriter, auditService);
	}

	private ReconciliationProcessingResult valid(String id) {
		TransactionRequestDto dto = new TransactionRequestDto();
		dto.setTransactionId(id);
		dto.setAmount("100");

		TransactionRecord record = TransactionRecord.builder().jobInstanceId(10L).transactionId(id)
				.amount(new BigDecimal("100")).build();

		return new ReconciliationProcessingResult(dto, record, ReconciliationStatus.VALID, ReconciliationAction.INSERT,
				null, null);
	}

	private ReconciliationProcessingResult invalid(String id) {
		TransactionRequestDto dto = new TransactionRequestDto();
		dto.setTransactionId(id);

		return new ReconciliationProcessingResult(dto, null, ReconciliationStatus.INVALID_RECORD,
				ReconciliationAction.SKIP, "Invalid", null);
	}

	private void mockDatabaseResults(List<String> existingIds) {
		when(jdbcTemplate.query(anyString(), any(MapSqlParameterSource.class),
				ArgumentMatchers.<RowMapper<String>>any())).thenAnswer(invocation -> {
					@SuppressWarnings("unchecked")
					RowMapper<String> mapper = invocation.getArgument(2);

					List<String> mapped = new ArrayList<>();

					for (int i = 0; i < existingIds.size(); i++) {
						java.sql.ResultSet rs = mock(java.sql.ResultSet.class);

						when(rs.getString("transaction_id")).thenReturn(existingIds.get(i));

						mapped.add(mapper.mapRow(rs, i));
					}

					return mapped;
				});
	}

	@Test
	void shouldIgnoreEmptyChunk() throws Exception {
		writer.write(new Chunk<>());

		verifyNoInteractions(jdbcTemplate, databaseWriter, csvWriter, auditService);
	}

	@Test
	void shouldInsertNewTransactions() throws Exception {
		mockDatabaseResults(List.of());

		writer.write(new Chunk<>(List.of(valid("TX1"), valid("TX2"))));

		verify(databaseWriter).write(argThat(chunk -> chunk.getItems().size() == 2));

		verify(csvWriter).writeResults(List.of());
		verify(auditService).recordCsv1Chunk(2, 2, 0, 0);
	}

	@Test
	void shouldReportInvalidRecords() throws Exception {
		writer.write(new Chunk<>(List.of(invalid("TX1"))));

		verifyNoInteractions(jdbcTemplate, databaseWriter);

		verify(csvWriter).writeResults(
				argThat(rows -> rows.size() == 1 && rows.get(0).status() == ReconciliationStatus.INVALID_RECORD));

		verify(auditService).recordCsv1Chunk(1, 0, 0, 1);
	}

	@Test
	void shouldDetectDuplicatesWithinSameChunk() throws Exception {
		mockDatabaseResults(List.of());

		writer.write(new Chunk<>(List.of(valid("TX1"), valid("TX1"))));

		verify(databaseWriter).write(argThat(chunk -> chunk.getItems().size() == 1));

		verify(csvWriter).writeResults(
				argThat(rows -> rows.size() == 1 && rows.get(0).status() == ReconciliationStatus.DUPLICATE_IN_CSV1
						&& Messages.DUPLICATE_TRANSACTION_ID.equals(rows.get(0).errorMessage())));

		verify(auditService).recordCsv1Chunk(2, 1, 1, 0);
	}

	@Test
	void shouldDetectTransactionsAlreadyInDatabase() throws Exception {
		mockDatabaseResults(List.of("TX1"));

		writer.write(new Chunk<>(List.of(valid("TX1"))));

		verify(databaseWriter, never()).write(any());

		verify(csvWriter).writeResults(argThat(
				rows -> rows.size() == 1 && Messages.TRANSACTION_ALREADY_EXISTS.equals(rows.get(0).errorMessage())));

		verify(auditService).recordCsv1Chunk(1, 0, 1, 0);
	}

	@Test
	void shouldNotQueryDatabaseWhenNoValidRecordsExist() throws Exception {
		writer.write(new Chunk<>(List.of(invalid("TX1"))));

		verifyNoInteractions(jdbcTemplate, databaseWriter);
		verify(auditService).recordCsv1Chunk(1, 0, 0, 1);
	}

	@Test
	void shouldHandleMixedValidAndInvalidRecords() throws Exception {
		mockDatabaseResults(List.of());

		writer.write(new Chunk<>(List.of(valid("TX1"), invalid("TX2"), valid("TX3"))));

		verify(databaseWriter).write(argThat(chunk -> chunk.getItems().size() == 2));

		verify(csvWriter).writeResults(
				argThat(rows -> rows.size() == 1 && rows.get(0).status() == ReconciliationStatus.INVALID_RECORD));

		verify(auditService).recordCsv1Chunk(3, 2, 0, 1);
	}

	@Test
	void shouldInsertOnlyTransactionsNotAlreadyInDatabase() throws Exception {
		mockDatabaseResults(List.of("TX1"));

		writer.write(new Chunk<>(List.of(valid("TX1"), valid("TX2"))));

		verify(databaseWriter).write(argThat(
				chunk -> chunk.getItems().size() == 1 && "TX2".equals(chunk.getItems().get(0).getTransactionId())));

		verify(auditService).recordCsv1Chunk(2, 1, 1, 0);
	}

	@Test
	void shouldReportDuplicateAndInsertDistinctTransactions() throws Exception {
		mockDatabaseResults(List.of());

		writer.write(new Chunk<>(List.of(valid("TX1"), valid("TX1"), valid("TX2"), valid("TX2"))));

		verify(databaseWriter).write(argThat(chunk -> chunk.getItems().size() == 2));

		verify(csvWriter).writeResults(argThat(rows -> rows.size() == 2
				&& rows.stream().allMatch(row -> row.status() == ReconciliationStatus.DUPLICATE_IN_CSV1)));

		verify(auditService).recordCsv1Chunk(4, 2, 2, 0);
	}

	@Test
	void shouldReportExistingTransactionAndInsertOtherCandidate() throws Exception {
		mockDatabaseResults(List.of("TX1"));

		writer.write(new Chunk<>(List.of(valid("TX1"), valid("TX2"), valid("TX3"))));

		verify(databaseWriter).write(argThat(chunk -> chunk.getItems().size() == 2));

		verify(csvWriter).writeResults(argThat(
				rows -> rows.size() == 1 && Messages.TRANSACTION_ALREADY_EXISTS.equals(rows.get(0).errorMessage())));

		verify(auditService).recordCsv1Chunk(3, 2, 1, 0);
	}

	@Test
	void shouldWriteEmptyCsvRowsWhenAllRecordsAreValidAndNew() throws Exception {
		mockDatabaseResults(List.of());

		writer.write(new Chunk<>(List.of(valid("TX1"))));

		verify(csvWriter).writeResults(List.of());
	}

	@Test
	void shouldQueryDatabaseOnceForDistinctCandidates() throws Exception {
		mockDatabaseResults(List.of());

		writer.write(new Chunk<>(List.of(valid("TX1"), valid("TX1"), valid("TX2"))));

		verify(jdbcTemplate, times(1)).query(anyString(), any(MapSqlParameterSource.class),
				ArgumentMatchers.<RowMapper<String>>any());
	}

	@Test
	void shouldCoverOriginalResultLookupBranchesForExistingTransaction() throws Exception {
		mockDatabaseResults(List.of("TX2"));

		writer.write(new Chunk<>(List.of(invalid("TX2"), valid("TX1"), valid("TX2"))));

		// TX1 is new, so it should be inserted.
		verify(databaseWriter).write(argThat(
				chunk -> chunk.getItems().size() == 1 && "TX1".equals(chunk.getItems().get(0).getTransactionId())));

		verify(csvWriter).writeResults(argThat(rows -> rows.size() == 2
				&& rows.stream().anyMatch(row -> row.status() == ReconciliationStatus.INVALID_RECORD)
				&& rows.stream().anyMatch(row -> row.status() == ReconciliationStatus.DUPLICATE_IN_CSV1
						&& Messages.TRANSACTION_ALREADY_EXISTS.equals(row.errorMessage()))));

		verify(auditService).recordCsv1Chunk(3, 1, 1, 1);
	}
}

package com.example.springbatch.batch.writer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.core.step.StepExecution;
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
import com.example.springbatch.repository.AuditLogRepository;

class TransactionItemWriterTest {

    private NamedParameterJdbcTemplate jdbcTemplate;
    private JdbcBatchItemWriter<TransactionRecord> databaseWriter;
    private ReconciliationCsvWriter csvWriter;
    private AuditLogRepository auditRepository;
    private TransactionItemWriter writer;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        databaseWriter = mock(JdbcBatchItemWriter.class);
        csvWriter = mock(ReconciliationCsvWriter.class);
        auditRepository = mock(AuditLogRepository.class);

        JobInstance instance = mock(JobInstance.class);
        JobExecution execution = mock(JobExecution.class);
        StepExecution step = mock(StepExecution.class);

        when(instance.getInstanceId()).thenReturn(100L);
        when(execution.getJobInstance()).thenReturn(instance);
        when(step.getJobExecution()).thenReturn(execution);

        StepSynchronizationManager.register(step);

        when(auditRepository.incrementCounts(
                anyLong(), anyLong(), anyLong(), anyLong(),
                anyLong(), anyLong(), anyLong(), anyLong()
        )).thenReturn(1);

        writer = new TransactionItemWriter(
                jdbcTemplate,
                databaseWriter,
                csvWriter,
                auditRepository
        );
    }

    @AfterEach
    void clearStepContext() {
        StepSynchronizationManager.close();
    }

    private ReconciliationProcessingResult valid(String id) {
        TransactionRequestDto dto = new TransactionRequestDto();
        dto.setTransactionId(id);
        dto.setAmount("100");

        TransactionRecord record = TransactionRecord.builder()
                .jobInstanceId(100L)
                .transactionId(id)
                .amount(new BigDecimal("100"))
                .build();

        return new ReconciliationProcessingResult(
                dto,
                record,
                ReconciliationStatus.VALID,
                ReconciliationAction.INSERT,
                null,
                null
        );
    }

    private ReconciliationProcessingResult invalid(String id) {
        TransactionRequestDto dto = new TransactionRequestDto();
        dto.setTransactionId(id);

        return new ReconciliationProcessingResult(
                dto,
                null,
                ReconciliationStatus.INVALID_RECORD,
                ReconciliationAction.SKIP,
                "Invalid",
                null
        );
    }

    private void mockDatabaseResults(List<String> ids) {
        when(jdbcTemplate.query(
                anyString(),
                any(MapSqlParameterSource.class),
                ArgumentMatchers.<RowMapper<String>>any()
        )).thenReturn(ids);
    }

    @Test
    void shouldIgnoreEmptyChunk() throws Exception {
        writer.write(new Chunk<>());

        verifyNoInteractions(
                jdbcTemplate,
                databaseWriter,
                csvWriter,
                auditRepository
        );
    }

    @Test
    void shouldInsertNewTransactionsAndUpdateAudit() throws Exception {
        mockDatabaseResults(List.of());

        writer.write(new Chunk<>(List.of(
                valid("TX1"),
                valid("TX2")
        )));

        verify(databaseWriter).write(argThat(
                chunk -> chunk.getItems().size() == 2
        ));

        verify(csvWriter).writeResults(List.of());

        verify(auditRepository).incrementCounts(
                100L, 2L, 2L, 0L, 0L, 0L, 0L, 0L
        );
    }

    @Test
    void shouldCountInvalidRecords() throws Exception {
        writer.write(new Chunk<>(List.of(invalid("TX1"))));

        verifyNoInteractions(jdbcTemplate, databaseWriter);

        verify(csvWriter).writeResults(argThat(
                rows -> rows.size() == 1
                        && rows.get(0).status()
                        == ReconciliationStatus.INVALID_RECORD
        ));

        verify(auditRepository).incrementCounts(
                100L, 1L, 0L, 0L, 0L, 0L, 1L, 0L
        );
    }

    @Test
    void shouldDetectDuplicateWithinChunk() throws Exception {
        mockDatabaseResults(List.of());

        writer.write(new Chunk<>(List.of(
                valid("TX1"),
                valid("TX1")
        )));

        verify(databaseWriter).write(argThat(
                chunk -> chunk.getItems().size() == 1
        ));

        verify(csvWriter).writeResults(argThat(
                rows -> rows.size() == 1
                        && rows.get(0).status()
                        == ReconciliationStatus.DUPLICATE_IN_CSV1
        ));

        verify(auditRepository).incrementCounts(
                100L, 2L, 1L, 0L, 0L, 1L, 0L, 0L
        );
    }

    @Test
    void shouldDetectExistingDatabaseTransaction() throws Exception {
        mockDatabaseResults(List.of("TX1"));

        writer.write(new Chunk<>(List.of(valid("TX1"))));

        verify(databaseWriter, never()).write(any());

        verify(csvWriter).writeResults(argThat(
                rows -> rows.size() == 1
                        && Messages.TRANSACTION_ALREADY_EXISTS.equals(
                                rows.get(0).errorMessage()
                        )
        ));

        verify(auditRepository).incrementCounts(
                100L, 1L, 0L, 0L, 0L, 1L, 0L, 0L
        );
    }

    @Test
    void shouldThrowWhenStepContextIsMissing() throws Exception {
        StepSynchronizationManager.close();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> writer.write(new Chunk<>(List.of(invalid("TX1"))))
        );

        assertEquals(
                Messages.STEP_CONTEXT_NOT_FOUND,
                exception.getMessage()
        );

        verify(csvWriter).writeResults(argThat(
                rows -> rows.size() == 1
        ));

        verifyNoInteractions(auditRepository);
    }

    @Test
    void shouldThrowWhenAuditSummaryIsNotUpdated() throws Exception {
        when(auditRepository.incrementCounts(
                anyLong(), anyLong(), anyLong(), anyLong(),
                anyLong(), anyLong(), anyLong(), anyLong()
        )).thenReturn(0);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> writer.write(new Chunk<>(List.of(invalid("TX1"))))
        );

        assertTrue(exception.getMessage().contains(
                "Audit summary not found for job instance 100"
        ));

        verify(auditRepository).incrementCounts(
                100L, 1L, 0L, 0L, 0L, 0L, 1L, 0L
        );
    }

    @Test
    void shouldSkipInvalidResultsWhenSearchingForExistingTransaction()
            throws Exception {
        mockDatabaseResults(List.of("TX1"));

        writer.write(new Chunk<>(List.of(
                invalid("INVALID_TX"),
                valid("TX1")
        )));

        verify(databaseWriter, never()).write(any());

        verify(csvWriter).writeResults(argThat(
                rows -> rows.size() == 2
                        && rows.stream().anyMatch(
                                row -> row.status()
                                        == ReconciliationStatus.INVALID_RECORD
                        )
                        && rows.stream().anyMatch(
                                row -> row.status()
                                        == ReconciliationStatus.DUPLICATE_IN_CSV1
                        )
        ));

        verify(auditRepository).incrementCounts(
                100L, 2L, 0L, 0L, 0L, 1L, 1L, 0L
        );
    }

    @Test
    void shouldInsertNonExistingTransactionAndSkipExistingOne()
            throws Exception {
        mockDatabaseResults(List.of("TX2"));

        writer.write(new Chunk<>(List.of(
                valid("TX1"),
                valid("TX2")
        )));

        verify(databaseWriter).write(argThat(
                chunk -> chunk.getItems().size() == 1
                        && "TX1".equals(
                                chunk.getItems().get(0).getTransactionId()
                        )
        ));

        verify(csvWriter).writeResults(argThat(
                rows -> rows.size() == 1
                        && rows.get(0).status()
                                == ReconciliationStatus.DUPLICATE_IN_CSV1
        ));

        verify(auditRepository).incrementCounts(
                100L, 2L, 1L, 0L, 0L, 1L, 0L, 0L
        );
    }
}

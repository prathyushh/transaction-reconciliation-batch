package com.example.springbatch.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;

import com.example.springbatch.entity.TransactionRecord;

class JdbcWriterConfigTest {

	@Test
	void shouldCreateJdbcTransactionWriter() {
		JdbcWriterConfig config = new JdbcWriterConfig();
		DataSource dataSource = mock(DataSource.class);

		JdbcBatchItemWriter<TransactionRecord> writer = config.jdbcTransactionWriter(dataSource);

		assertNotNull(writer);
	}
}
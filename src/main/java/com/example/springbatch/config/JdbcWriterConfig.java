package com.example.springbatch.config;

import javax.sql.DataSource;
import com.example.springbatch.entity.TransactionRecord;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JdbcWriterConfig {
	@Bean
	public JdbcBatchItemWriter<TransactionRecord> jdbcTransactionWriter(DataSource dataSource) {
		return new JdbcBatchItemWriterBuilder<TransactionRecord>().dataSource(dataSource)
				.sql("INSERT INTO transaction_records " + "(run_id, transaction_id, account_number, "
						+ "transaction_date, amount, currency, transaction_type, "
						+ "description, created_at, updated_at) " + "VALUES (:runId, :transactionId, :accountNumber, "
						+ ":transactionDate, :amount, :currency, "
						+ ":transactionType, :description, :createdAt, :updatedAt) "
						+ "ON CONFLICT (run_id, transaction_id) DO NOTHING")
				.beanMapped().assertUpdates(false).build();
	}
}
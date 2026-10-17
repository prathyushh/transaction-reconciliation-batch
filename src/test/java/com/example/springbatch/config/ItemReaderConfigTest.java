package com.example.springbatch.config;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;

import com.example.springbatch.dto.TransactionRequestDto;

class ItemReaderConfigTest {

	@TempDir
	Path tempDir;

	private final ItemReaderConfig config = new ItemReaderConfig();

	private Path createCsv() throws Exception {
		Path csv = tempDir.resolve("transactions.csv");

		Files.writeString(csv,
				"transaction_id,account_number,transaction_date,amount,currency,transaction_type,description\n"
						+ "TX1,12345,2026-01-15,100.50,INR,CREDIT,Test\n");

		return csv;
	}

	@Test
	void shouldReadCsv1() throws Exception {
		FlatFileItemReader<TransactionRequestDto> reader = config.csv1ItemReader(createCsv().toString());

		reader.open(new ExecutionContext());

		try {
			TransactionRequestDto dto = reader.read();

			assertNotNull(dto);
			assertEquals("TX1", dto.getTransactionId());
			assertEquals("12345", dto.getAccountNumber());
			assertEquals("2026-01-15", dto.getTransactionDate());
			assertEquals("100.50", dto.getAmount());
			assertEquals("INR", dto.getCurrency());
			assertEquals("CREDIT", dto.getTransactionType());
			assertEquals("Test", dto.getDescription());

			assertNull(reader.read());
		} finally {
			reader.close();
		}
	}

	@Test
	void shouldReadCsv2() throws Exception {
		FlatFileItemReader<TransactionRequestDto> reader = config.csv2ItemReader(createCsv().toString());

		assertNotNull(reader);
		assertEquals("csv2ItemReader", reader.getName());
	}
}
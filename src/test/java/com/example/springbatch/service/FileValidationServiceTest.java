package com.example.springbatch.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import com.example.springbatch.constants.Messages;
import com.example.springbatch.exception.InvalidCsvFileException;

class FileValidationServiceTest {

	@TempDir
	Path tempDir;

	private FileValidationService service;

	private final List<String> headers = List.of("transaction_id", "account_number", "amount");

	@BeforeEach
	void setUp() throws Exception {
		service = new FileValidationService();

		var field = FileValidationService.class.getDeclaredField("maxFileSize");
		field.setAccessible(true);
		field.setLong(service, 1024L);
	}

	private Path createFile(String filename, String content) throws IOException {
		Path file = tempDir.resolve(filename);
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file;
	}

	@Test
	void shouldAcceptValidCsv() throws Exception {
		Path file = createFile("valid.csv", "transaction_id,account_number,amount\nTX1,123,100");

		assertDoesNotThrow(() -> service.validateFile(new FileSystemResource(file), headers));
	}

	@Test
	void shouldAcceptUppercaseCsvExtension() throws Exception {
		Path file = createFile("valid.CSV", "transaction_id,account_number,amount\nTX1,123,100");

		assertDoesNotThrow(() -> service.validateFile(new FileSystemResource(file), headers));
	}

	@Test
	void shouldAcceptBomBeforeHeader() throws Exception {
		Path file = createFile("bom.csv", "\uFEFFtransaction_id,account_number,amount\nTX1,123,100");

		assertDoesNotThrow(() -> service.validateFile(new FileSystemResource(file), headers));
	}

	@Test
	void shouldRejectMissingFile() {
		Path file = tempDir.resolve("missing.csv");

		InvalidCsvFileException exception = assertThrows(InvalidCsvFileException.class,
				() -> service.validateFile(new FileSystemResource(file), headers));

		assertTrue(exception.getMessage().startsWith(Messages.INPUT_CSV_NOT_READABLE));
	}

	@Test
	void shouldRejectWrongExtension() throws Exception {
		Path file = createFile("input.txt", "transaction_id,account_number,amount");

		InvalidCsvFileException exception = assertThrows(InvalidCsvFileException.class,
				() -> service.validateFile(new FileSystemResource(file), headers));

		assertTrue(exception.getMessage().startsWith(Messages.INPUT_CSV_EXTENSION_NOT_CORRECT));
	}

	@Test
	void shouldRejectFileWithoutExtension() throws Exception {
		Path file = createFile("input", "transaction_id,account_number,amount");

		assertThrows(InvalidCsvFileException.class, () -> service.validateFile(new FileSystemResource(file), headers));
	}

	@Test
	void shouldRejectFileExceedingMaximumSize() throws Exception {
		Path file = createFile("large.csv", "transaction_id,account_number,amount\n" + "X".repeat(1100));

		InvalidCsvFileException exception = assertThrows(InvalidCsvFileException.class,
				() -> service.validateFile(new FileSystemResource(file), headers));

		assertTrue(exception.getMessage().startsWith(Messages.INPUT_CSV_FILE_SIZE_EXCEEDS));
	}

	@Test
	void shouldRejectEmptyFile() throws Exception {
		Path file = createFile("empty.csv", "");

		InvalidCsvFileException exception = assertThrows(InvalidCsvFileException.class,
				() -> service.validateFile(new FileSystemResource(file), headers));

		assertTrue(exception.getMessage().startsWith(Messages.CSV_EMPTY_NO_HEADER));
	}

	@Test
	void shouldRejectBlankHeader() throws Exception {
		Path file = createFile("blank.csv", "   \nTX1,123,100");

		assertThrows(InvalidCsvFileException.class, () -> service.validateFile(new FileSystemResource(file), headers));
	}

	static Stream<Arguments> invalidHeaders() {
		return Stream.of(Arguments.of("amount,account_number,transaction_id"),
				Arguments.of("transaction_id,account_number"),
				Arguments.of("transaction_id,account_number,amount,extra"),
				Arguments.of("transaction_id,account_number,wrong"),
				Arguments.of("Transaction_id,account_number,amount"), Arguments.of("transaction_id,,amount"),
				Arguments.of("transaction_id,account_number,"));
	}

	@ParameterizedTest
	@MethodSource("invalidHeaders")
	void shouldRejectIncorrectHeaders(String header) throws Exception {
		Path file = createFile("headers.csv", header + "\nTX1,123,100");

		assertThrows(InvalidCsvFileException.class, () -> service.validateFile(new FileSystemResource(file), headers),
				"Expected rejection for header: " + header);
	}

	@Test
	void shouldRejectResourceWithNullFilename() {
		Resource resource = new AbstractResource() {
			@Override
			public String getDescription() {
				return "resource without filename";
			}

			@Override
			public String getFilename() {
				return null;
			}

			@Override
			public InputStream getInputStream() {
				return new ByteArrayInputStream(new byte[0]);
			}
		};

		InvalidCsvFileException exception = assertThrows(InvalidCsvFileException.class,
				() -> service.validateFile(resource, headers));

		assertTrue(exception.getMessage().startsWith(Messages.INPUT_CSV_EXTENSION_NOT_CORRECT));
	}

	@Test
	void shouldRejectResourceWhenContentLengthFails() {
		Resource resource = new AbstractResource() {
			@Override
			public String getDescription() {
				return "broken.csv";
			}

			@Override
			public String getFilename() {
				return "broken.csv";
			}

			@Override
			public long contentLength() throws IOException {
				throw new IOException("Cannot read size");
			}

			@Override
			public InputStream getInputStream() {
				return new ByteArrayInputStream(new byte[0]);
			}
		};

		InvalidCsvFileException exception = assertThrows(InvalidCsvFileException.class,
				() -> service.validateFile(resource, headers));

		assertTrue(exception.getMessage().startsWith(Messages.INVALID_CSV_FILE));
	}

	@Test
	void shouldRejectResourceWhenInputStreamFails() {
		Resource resource = new AbstractResource() {
			@Override
			public String getDescription() {
				return "broken.csv";
			}

			@Override
			public String getFilename() {
				return "broken.csv";
			}

			@Override
			public InputStream getInputStream() throws IOException {
				throw new IOException("Cannot open file");
			}
		};

		InvalidCsvFileException exception = assertThrows(InvalidCsvFileException.class,
				() -> service.validateFile(resource, headers));

		assertNotNull(exception.getMessage());
		assertTrue(exception.getMessage().contains("broken.csv"));
	}

	@Test
	void shouldRejectResourceThatIsNotReadable() {
		Resource resource = new AbstractResource() {
			@Override
			public String getDescription() {
				return "unreadable.csv";
			}

			@Override
			public boolean exists() {
				return true;
			}

			@Override
			public boolean isReadable() {
				return false;
			}

			@Override
			public String getFilename() {
				return "unreadable.csv";
			}

			@Override
			public InputStream getInputStream() {
				return new ByteArrayInputStream(new byte[0]);
			}
		};

		InvalidCsvFileException exception = assertThrows(InvalidCsvFileException.class,
				() -> service.validateFile(resource, headers));

		assertTrue(exception.getMessage().startsWith(Messages.INPUT_CSV_NOT_READABLE));
	}

	@Test
	void shouldRejectHeaderWithMissingExpectedColumn() throws Exception {
		Path file = createFile("header-only.csv", "transaction_id,account_number");

		assertThrows(InvalidCsvFileException.class, () -> service.validateFile(new FileSystemResource(file), headers));
	}
}
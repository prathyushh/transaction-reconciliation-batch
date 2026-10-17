package com.example.springbatch.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import com.example.springbatch.constants.Messages;
import com.example.springbatch.exception.InvalidCsvFileException;

@Service
public class FileValidationService {
	@Value("${app.csv.max-file-size}")
	private long maxFileSize;

	public void validateFile(Resource resource, List<String> expectedHeaders) {
		if (!resource.exists() || !resource.isReadable()) {
			throw new InvalidCsvFileException(
				Messages.INPUT_CSV_NOT_READABLE + resource.getDescription());
		}
		String filename = resource.getFilename();
		if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
			throw new InvalidCsvFileException(Messages.INPUT_CSV_EXTENSION_NOT_CORRECT + resource.getDescription());
		}
		try {
			long fileSize = resource.contentLength();

			if (fileSize > maxFileSize) {
				throw new InvalidCsvFileException(
						Messages.INPUT_CSV_FILE_SIZE_EXCEEDS + (maxFileSize / (1024 * 1024))
								+ " MB. Actual size: " + String.format("%.2f MB", fileSize / (1024.0 * 1024.0)));
			}
		} catch (InvalidCsvFileException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new InvalidCsvFileException(Messages.INVALID_CSV_FILE + filename);
		}
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
			String header = reader.readLine();
			if (header == null || header.isBlank()) {
				throw new InvalidCsvFileException(Messages.CSV_EMPTY_NO_HEADER + filename);
			}
			if (header.startsWith("\uFEFF")) {
				header = header.substring(1);
			}
			List<String> actualHeaders = Arrays.stream(header.split(",", -1)).map(String::trim).toList();
			if (!expectedHeaders.equals(actualHeaders)) {
				throw new InvalidCsvFileException("Invalid CSV headers in " + filename + ". Expected: "
						+ expectedHeaders + ", Found: " + actualHeaders);
			}
		} catch (InvalidCsvFileException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new InvalidCsvFileException(Messages.CSV_VALIDATION_FAILED + filename);
		}
	}
}

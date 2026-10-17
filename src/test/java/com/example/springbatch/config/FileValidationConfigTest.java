package com.example.springbatch.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;

import com.example.springbatch.batch.tasklet.FileValidationTasklet;
import com.example.springbatch.service.FileValidationService;

class FileValidationConfigTest {

    private final FileValidationConfig config = new FileValidationConfig();

    @Test
    void shouldCreateCsv1ValidationTasklet() {
        FileValidationService service = mock(FileValidationService.class);

        FileValidationTasklet tasklet =
                config.csv1FileValidationTasklet(
                        "csv1.csv",
                        " transaction_id, amount ,currency ",
                        service);

        assertNotNull(tasklet);
    }

    @Test
    void shouldCreateCsv2ValidationTasklet() {
        FileValidationService service = mock(FileValidationService.class);

        FileValidationTasklet tasklet =
                config.csv2FileValidationTasklet(
                        "csv2.csv",
                        "transaction_id,amount",
                        service);

        assertNotNull(tasklet);
    }
}
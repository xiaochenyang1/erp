package com.tuowei.erp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "erp.import")
public record ImportProperties(
        long maxFileSizeBytes,
        int maxRows,
        int maxCellLength,
        int commitBatchSize
) {

    public static final long DEFAULT_MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    public static final int DEFAULT_MAX_ROWS = 5_000;
    public static final int DEFAULT_MAX_CELL_LENGTH = 4_096;
    public static final int DEFAULT_COMMIT_BATCH_SIZE = 500;

    public ImportProperties {
        if (maxFileSizeBytes < 1) {
            maxFileSizeBytes = DEFAULT_MAX_FILE_SIZE_BYTES;
        }
        if (maxRows < 1) {
            maxRows = DEFAULT_MAX_ROWS;
        }
        if (maxCellLength < 1) {
            maxCellLength = DEFAULT_MAX_CELL_LENGTH;
        }
        if (commitBatchSize < 1) {
            commitBatchSize = DEFAULT_COMMIT_BATCH_SIZE;
        }
    }

    public static ImportProperties defaults() {
        return new ImportProperties(
                DEFAULT_MAX_FILE_SIZE_BYTES,
                DEFAULT_MAX_ROWS,
                DEFAULT_MAX_CELL_LENGTH,
                DEFAULT_COMMIT_BATCH_SIZE
        );
    }
}

package com.tuowei.erp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "erp.report")
public record ReportProperties(int maxExportRows, int exportBatchSize) {

    public ReportProperties {
        if (maxExportRows < 1) {
            maxExportRows = 5000;
        }
        if (exportBatchSize < 1) {
            exportBatchSize = 500;
        }
    }
}

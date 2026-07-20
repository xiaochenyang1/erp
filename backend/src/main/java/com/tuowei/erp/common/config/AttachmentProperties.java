package com.tuowei.erp.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "erp.attachment")
public record AttachmentProperties(
        String storageRoot,
        long maxFileSizeBytes,
        /** 逗号分隔业务类型，业务过账/提交前必须至少上传 minRequiredCount 个附件 */
        String requiredBusinessTypes,
        Integer minRequiredCount
) {

    public AttachmentProperties {
        if (storageRoot == null || storageRoot.isBlank()) {
            storageRoot = "./data/attachments";
        }
        if (maxFileSizeBytes < 1) {
            maxFileSizeBytes = 20L * 1024 * 1024;
        }
        if (requiredBusinessTypes == null || requiredBusinessTypes.isBlank()) {
            requiredBusinessTypes = "EXPENSE";
        }
        if (minRequiredCount == null || minRequiredCount < 1) {
            minRequiredCount = 1;
        }
    }

    public Set<String> requiredBusinessTypeSet() {
        return Arrays.stream(requiredBusinessTypes.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    public int requiredMinCount() {
        return minRequiredCount == null ? 1 : minRequiredCount;
    }
}

package com.tuowei.erp.common.cache;

import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class CacheKeyBuilder {

    private static final String PREFIX = "erp";

    private CacheKeyBuilder() {
    }

    public static String accountBookScoped(Long companyId, Long accountBookId, String... segments) {
        if (companyId == null) {
            throw new IllegalArgumentException("cache key companyId must not be null");
        }
        if (accountBookId == null) {
            throw new IllegalArgumentException("cache key accountBookId must not be null");
        }
        if (companyId <= 0) {
            throw new IllegalArgumentException("cache key companyId must be positive");
        }
        if (accountBookId <= 0) {
            throw new IllegalArgumentException("cache key accountBookId must be positive");
        }
        return PREFIX
                + ":"
                + companyId
                + ":"
                + accountBookId
                + ":"
                + normalizeSegments(segments);
    }

    public static String global(String... segments) {
        return PREFIX
                + ":global:"
                + normalizeSegments(segments);
    }

    private static String normalizeSegments(String[] segments) {
        if (segments == null || segments.length == 0) {
            throw new IllegalArgumentException("cache key segment must not be blank");
        }
        return Arrays.stream(segments)
                .map(CacheKeyBuilder::normalizeSegment)
                .collect(Collectors.joining(":"));
    }

    private static String normalizeSegment(String segment) {
        if (!StringUtils.hasText(segment)) {
            throw new IllegalArgumentException("cache key segment must not be blank");
        }
        String normalized = segment.trim();
        if (normalized.contains(":")) {
            throw new IllegalArgumentException("cache key segment must not contain ':'");
        }
        return normalized;
    }

    // 便捷方法：基础数据缓存键
    public static String product(Long id) {
        return global("product", String.valueOf(id));
    }

    public static String customer(Long id) {
        return global("customer", String.valueOf(id));
    }

    public static String supplier(Long id) {
        return global("supplier", String.valueOf(id));
    }

    public static String warehouse(Long id) {
        return global("warehouse", String.valueOf(id));
    }

    public static String user(Long id) {
        return global("user", String.valueOf(id));
    }
}

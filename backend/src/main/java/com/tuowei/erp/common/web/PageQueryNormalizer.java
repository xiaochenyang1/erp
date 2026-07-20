package com.tuowei.erp.common.web;

/**
 * 分页参数标准化工具类
 */
public final class PageQueryNormalizer {
    private static final long DEFAULT_PAGE_NO = 1L;
    private static final long DEFAULT_PAGE_SIZE = 20L;
    private static final long MAX_PAGE_SIZE = 200L;

    private PageQueryNormalizer() {
    }

    public static long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? DEFAULT_PAGE_NO : pageNo;
    }

    public static long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1
            ? DEFAULT_PAGE_SIZE
            : Math.min(pageSize, MAX_PAGE_SIZE);
    }
}

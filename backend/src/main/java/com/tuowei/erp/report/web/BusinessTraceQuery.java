package com.tuowei.erp.report.web;

public class BusinessTraceQuery {

    private String keyword;

    public BusinessTraceQuery() {
    }

    public BusinessTraceQuery(String keyword) {
        this.keyword = keyword;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }
}

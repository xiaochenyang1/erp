package com.tuowei.erp.common.security;

import java.util.Locale;

public enum DataScopeRule {
    ALL,
    DEPT,
    POST,
    WAREHOUSE,
    SELF;

    public static DataScopeRule from(String value) {
        return DataScopeRule.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}

package com.tuowei.erp.imports.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.imports.model.ImportJobRowEntity;
import com.tuowei.erp.imports.web.ImportRowErrorResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract class AbstractImportHandler implements ImportTypeHandler {

    protected final ImportValidationSupport support;

    protected AbstractImportHandler(ImportValidationSupport support) {
        this.support = support;
    }

    protected Set<String> seen(ImportValidationContext context, String key) {
        Object existing = context.attributes().get(key);
        if (existing instanceof Set<?> set) {
            @SuppressWarnings("unchecked")
            Set<String> cast = (Set<String>) set;
            return cast;
        }
        Set<String> created = new LinkedHashSet<>();
        context.attributes().put(key, created);
        return created;
    }

    protected String text(Map<String, Object> normalized, String key) {
        Object value = normalized.get(key);
        return value == null ? null : value.toString();
    }

    protected Long longValue(Map<String, Object> normalized, String key) {
        Object value = normalized.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    protected BigDecimal decimalValue(Map<String, Object> normalized, String key) {
        Object value = normalized.get(key);
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    protected LocalDate dateValue(Map<String, Object> normalized, String key) {
        Object value = normalized.get(key);
        if (value == null) {
            return null;
        }
        return LocalDate.parse(value.toString());
    }

    protected Map<String, Object> normalized(ImportJobRowEntity row) {
        return support.normalizedFromJson(row.getNormalizedJson());
    }

    protected boolean exists(Long count) {
        return count != null && count > 0;
    }

    protected <T> LambdaQueryWrapper<T> activeCompanyQuery(
            Long companyId,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Long> companyColumn,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, Integer> deletedColumn
    ) {
        return new LambdaQueryWrapper<T>()
                .eq(companyColumn, companyId)
                .eq(deletedColumn, 0);
    }

    protected void rejectNegative(String column, BigDecimal value, List<ImportRowErrorResponse> errors) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(new ImportRowErrorResponse(column, column + "不能小于0"));
        }
    }

    protected void setCreateAudit(Object entity, AuditMetadata audit, LocalDateTime now) {
        // Intentionally unused. Concrete handlers set fields directly because entities do not share a common base type.
    }
}

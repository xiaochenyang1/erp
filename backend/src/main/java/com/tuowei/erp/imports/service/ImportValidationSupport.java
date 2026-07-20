package com.tuowei.erp.imports.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.imports.web.ImportRowErrorResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ImportValidationSupport {

    private static final TypeReference<List<ImportRowErrorResponse>> ERROR_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public ImportValidationSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String required(Map<String, String> raw, String column, List<ImportRowErrorResponse> errors) {
        String value = raw.get(column);
        if (!StringUtils.hasText(value)) {
            errors.add(new ImportRowErrorResponse(column, column + "不能为空"));
            return null;
        }
        return value.trim();
    }

    public String optionalText(Map<String, String> raw, String column) {
        String value = raw.get(column);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public String optionalText(Map<String, String> raw, String column, String defaultValue) {
        String value = optionalText(raw, column);
        return value == null ? defaultValue : value;
    }

    public BigDecimal amount(Map<String, String> raw, String column, List<ImportRowErrorResponse> errors) {
        return decimal(raw, column, errors, 2);
    }

    public BigDecimal optionalAmount(Map<String, String> raw, String column, BigDecimal defaultValue, List<ImportRowErrorResponse> errors) {
        String value = raw.get(column);
        if (!StringUtils.hasText(value)) {
            return ScalePrecision.amount(defaultValue);
        }
        return amount(raw, column, errors);
    }

    public BigDecimal quantity(Map<String, String> raw, String column, List<ImportRowErrorResponse> errors) {
        return decimal(raw, column, errors, 4);
    }

    public BigDecimal optionalQuantity(Map<String, String> raw, String column, BigDecimal defaultValue, List<ImportRowErrorResponse> errors) {
        String value = raw.get(column);
        if (!StringUtils.hasText(value)) {
            return ScalePrecision.quantity(defaultValue);
        }
        return quantity(raw, column, errors);
    }

    public LocalDate date(Map<String, String> raw, String column, List<ImportRowErrorResponse> errors) {
        String value = required(raw, column, errors);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ex) {
            errors.add(new ImportRowErrorResponse(column, column + "格式不正确，必须是yyyy-MM-dd"));
            return null;
        }
    }

    public Long optionalLong(Map<String, String> raw, String column, List<ImportRowErrorResponse> errors) {
        String value = raw.get(column);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ex) {
            errors.add(new ImportRowErrorResponse(column, column + "格式不正确，必须是整数"));
            return null;
        }
    }

    public Long requiredLong(Map<String, String> raw, String column, List<ImportRowErrorResponse> errors) {
        String value = required(raw, column, errors);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (Exception ex) {
            errors.add(new ImportRowErrorResponse(column, column + "格式不正确，必须是整数"));
            return null;
        }
    }

    public BigDecimal positiveAmount(Map<String, String> raw, String column, List<ImportRowErrorResponse> errors) {
        BigDecimal value = amount(raw, column, errors);
        if (value != null && value.compareTo(BigDecimal.ZERO) <= 0) {
            errors.add(new ImportRowErrorResponse(column, column + "必须大于0"));
        }
        return value;
    }

    public BigDecimal nonNegativeAmount(Map<String, String> raw, String column, List<ImportRowErrorResponse> errors) {
        BigDecimal value = amount(raw, column, errors);
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            errors.add(new ImportRowErrorResponse(column, column + "不能小于0"));
        }
        return value;
    }

    public void duplicateInFile(Set<String> seen, String key, String column, List<ImportRowErrorResponse> errors) {
        if (!seen.add(key)) {
            errors.add(new ImportRowErrorResponse(column, "文件内存在重复数据"));
        }
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("导入数据序列化失败", ex);
        }
    }

    public Map<String, String> rawFromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        return readMap(json, new TypeReference<>() {
        });
    }

    public Map<String, Object> normalizedFromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        return readMap(json, new TypeReference<>() {
        });
    }

    public List<ImportRowErrorResponse> errorsFromJson(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, ERROR_LIST_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("导入错误信息解析失败", ex);
        }
    }

    public BigDecimal scaleAmount(BigDecimal value) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(value));
    }

    public BigDecimal scaleQuantity(BigDecimal value) {
        return ScalePrecision.quantity(ScalePrecision.zeroDefault(value));
    }

    public Map<String, Object> linkedMap() {
        return new LinkedHashMap<>();
    }

    public List<ImportRowErrorResponse> errorList() {
        return new ArrayList<>();
    }

    private BigDecimal decimal(Map<String, String> raw, String column, List<ImportRowErrorResponse> errors, int scale) {
        String value = raw.get(column);
        if (!StringUtils.hasText(value)) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal parsed = new BigDecimal(value.trim());
            return scale == 4 ? ScalePrecision.quantity(parsed) : ScalePrecision.amount(parsed);
        } catch (Exception ex) {
            errors.add(new ImportRowErrorResponse(column, column + "格式不正确，必须是数字"));
            return BigDecimal.ZERO;
        }
    }

    private <T> Map<String, T> readMap(String json, TypeReference<Map<String, T>> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception ex) {
            throw new IllegalStateException("导入数据解析失败", ex);
        }
    }
}

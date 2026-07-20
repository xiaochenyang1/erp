package com.tuowei.erp.system.config.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SequenceRuleCreateRequest(
        @NotBlank(message = "bizType不能为空") String bizType,
        @NotBlank(message = "prefix不能为空") String prefix,
        @NotBlank(message = "datePattern不能为空") String datePattern,
        @NotNull(message = "seqLength不能为空") @Min(value = 1, message = "seqLength必须大于0") Integer seqLength,
        Long currentValue
) {
}

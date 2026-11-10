package com.tuowei.erp.finance.financialstatement.web;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record StatementMappingUpdateRequest(
        @NotNull(message = "科目映射列表不能为空") List<String> subjectCodes
) {
}

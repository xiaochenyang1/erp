package com.tuowei.erp.finance.cashflow.web;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CashFlowSubjectCodesRequest(
        @NotNull(message = "科目列表不能为空") List<String> subjectCodes
) {
}

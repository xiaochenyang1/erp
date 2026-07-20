package com.tuowei.erp.finance.subject.web;

import jakarta.validation.constraints.NotBlank;

public record AccountSubjectUpdateRequest(
        @NotBlank(message = "subjectName不能为空") String subjectName,
        Long parentId,
        @NotBlank(message = "subjectType不能为空") String subjectType,
        @NotBlank(message = "balanceDirection不能为空") String balanceDirection,
        String remark
) {
}

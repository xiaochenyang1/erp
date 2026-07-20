package com.tuowei.erp.finance.subject.web;

public record AccountSubjectResponse(
        Long id,
        String subjectCode,
        String subjectName,
        Long parentId,
        String subjectType,
        String balanceDirection,
        String status,
        String remark
) {
}

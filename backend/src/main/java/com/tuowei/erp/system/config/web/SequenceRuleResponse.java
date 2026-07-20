package com.tuowei.erp.system.config.web;

public record SequenceRuleResponse(
        Long id,
        Long companyId,
        Long accountBookId,
        String bizType,
        String prefix,
        String datePattern,
        Integer seqLength,
        Long currentValue,
        String status
) {
}

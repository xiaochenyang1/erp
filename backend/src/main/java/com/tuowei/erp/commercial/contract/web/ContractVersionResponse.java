package com.tuowei.erp.commercial.contract.web;

import java.time.LocalDateTime;
import java.util.List;

public record ContractVersionResponse(
        Long id,
        Long contractId,
        Integer versionNo,
        String eventType,
        String status,
        ContractVersionHeaderResponse header,
        List<ContractVersionLineResponse> lines,
        List<String> changedFields,
        Long createdBy,
        LocalDateTime createdTime
) {}

package com.tuowei.erp.issue.sla.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyPageQuery;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyResponse;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Compatibility facade delegating SLA policy reads to the query service and writes to the command service. */
@Service
public class ExceptionSlaPolicyService {

    private final ExceptionSlaPolicyQueryService queryService;
    private final ExceptionSlaPolicyCommandService commandService;

    public ExceptionSlaPolicyService(
            ExceptionSlaPolicyQueryService queryService,
            ExceptionSlaPolicyCommandService commandService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional
    public PageResponse<ExceptionSlaPolicyResponse> list(ExceptionSlaPolicyPageQuery query) {
        return commandService.list(query);
    }

    @Transactional
    public ExceptionSlaPolicyResponse update(Long id, ExceptionSlaPolicyUpdateRequest request) {
        return commandService.update(id, request);
    }

    @Transactional(readOnly = true)
    public LocalDateTime resolveDueTime(String category, String priority, LocalDateTime createdAt, AuditMetadata audit) {
        return queryService.resolveDueTime(category, priority, createdAt, audit);
    }

    @Transactional(readOnly = true)
    public ExceptionSlaEscalationPolicy resolveEscalation(ExceptionTicketEntity ticket, AuditMetadata audit) {
        return queryService.resolveEscalation(ticket, audit);
    }
}

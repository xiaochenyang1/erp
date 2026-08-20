package com.tuowei.erp.issue.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.issue.web.ExceptionTicketActionRequest;
import com.tuowei.erp.issue.web.ExceptionTicketAssignRequest;
import com.tuowei.erp.issue.web.ExceptionTicketCreateRequest;
import com.tuowei.erp.issue.web.ExceptionTicketPageQuery;
import com.tuowei.erp.issue.web.ExceptionTicketResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Compatibility facade for exception ticket commands and queries. */
@Service
public class ExceptionTicketService {

    private final ExceptionTicketQueryService exceptionTicketQueryService;
    private final ExceptionTicketCommandService exceptionTicketCommandService;

    public ExceptionTicketService(
            ExceptionTicketQueryService exceptionTicketQueryService,
            ExceptionTicketCommandService exceptionTicketCommandService
    ) {
        this.exceptionTicketQueryService = exceptionTicketQueryService;
        this.exceptionTicketCommandService = exceptionTicketCommandService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExceptionTicketResponse> list(ExceptionTicketPageQuery query) {
        ExceptionTicketPageQuery safeQuery = query == null ? new ExceptionTicketPageQuery() : query;
        return exceptionTicketQueryService.list(safeQuery);
    }

    @Transactional
    public ExceptionTicketResponse create(ExceptionTicketCreateRequest request) {
        return exceptionTicketCommandService.create(request);
    }

    @Transactional
    public ExceptionTicketResponse create(ExceptionTicketCreateRequest request, AuditMetadata audit) {
        return exceptionTicketCommandService.create(request, audit);
    }

    @Transactional
    public ExceptionTicketResponse assign(Long id, ExceptionTicketAssignRequest request) {
        return exceptionTicketCommandService.assign(id, request);
    }

    @Transactional
    public ExceptionTicketResponse start(Long id, ExceptionTicketActionRequest request) {
        return exceptionTicketCommandService.start(id, request);
    }

    @Transactional
    public ExceptionTicketResponse resolve(Long id, ExceptionTicketActionRequest request) {
        return exceptionTicketCommandService.resolve(id, request);
    }

    @Transactional
    public ExceptionTicketResponse close(Long id, ExceptionTicketActionRequest request) {
        return exceptionTicketCommandService.close(id, request);
    }

    @Transactional
    public int escalateOverdueTickets(LocalDateTime now) {
        return exceptionTicketCommandService.escalateOverdueTickets(now);
    }
}

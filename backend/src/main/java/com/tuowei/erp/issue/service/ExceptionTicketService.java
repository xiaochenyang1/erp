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

/**
 * 异常工单门面：读侧委托 {@link ExceptionTicketQueryService}，写侧委托
 * {@link ExceptionTicketPostingService}，仅为控制器与规则调度保留薄入口。
 *
 * 公共方法签名与拆分前逐一对应，控制器、规则扫描、超时调度三个调用方无需改动。
 */
@Service
public class ExceptionTicketService {

    private final ExceptionTicketQueryService exceptionTicketQueryService;
    private final ExceptionTicketPostingService exceptionTicketPostingService;

    public ExceptionTicketService(
            ExceptionTicketQueryService exceptionTicketQueryService,
            ExceptionTicketPostingService exceptionTicketPostingService
    ) {
        this.exceptionTicketQueryService = exceptionTicketQueryService;
        this.exceptionTicketPostingService = exceptionTicketPostingService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExceptionTicketResponse> list(ExceptionTicketPageQuery query) {
        return exceptionTicketQueryService.list(query);
    }

    @Transactional
    public ExceptionTicketResponse create(ExceptionTicketCreateRequest request) {
        return exceptionTicketPostingService.create(request);
    }

    @Transactional
    public ExceptionTicketResponse create(ExceptionTicketCreateRequest request, AuditMetadata audit) {
        return exceptionTicketPostingService.create(request, audit);
    }

    @Transactional
    public ExceptionTicketResponse assign(Long id, ExceptionTicketAssignRequest request) {
        return exceptionTicketPostingService.assign(id, request);
    }

    @Transactional
    public ExceptionTicketResponse start(Long id, ExceptionTicketActionRequest request) {
        return exceptionTicketPostingService.start(id, request);
    }

    @Transactional
    public ExceptionTicketResponse resolve(Long id, ExceptionTicketActionRequest request) {
        return exceptionTicketPostingService.resolve(id, request);
    }

    @Transactional
    public ExceptionTicketResponse close(Long id, ExceptionTicketActionRequest request) {
        return exceptionTicketPostingService.close(id, request);
    }

    @Transactional
    public int escalateOverdueTickets(LocalDateTime now) {
        return exceptionTicketPostingService.escalateOverdueTickets(now);
    }
}

package com.tuowei.erp.sales.order.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.sales.order.web.SalesOrderApproveRequest;
import com.tuowei.erp.sales.order.web.SalesOrderCreateRequest;
import com.tuowei.erp.sales.order.web.SalesOrderCreditPreviewRequest;
import com.tuowei.erp.sales.order.web.SalesOrderCreditPreviewResponse;
import com.tuowei.erp.sales.order.web.SalesOrderPageQuery;
import com.tuowei.erp.sales.order.web.SalesOrderRejectRequest;
import com.tuowei.erp.sales.order.web.SalesOrderResponse;
import com.tuowei.erp.sales.order.web.SalesOrderSubmitRequest;
import com.tuowei.erp.sales.order.web.SalesOrderUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for sales order queries, commands and workflow transitions. */
@Service
public class SalesOrderService {
    private final SalesOrderQueryService queryService;
    private final SalesOrderCommandService commandService;
    private final SalesOrderWorkflowService workflowService;

    public SalesOrderService(
            SalesOrderQueryService queryService,
            SalesOrderCommandService commandService,
            SalesOrderWorkflowService workflowService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.workflowService = workflowService;
    }

    @Transactional
    public SalesOrderResponse create(SalesOrderCreateRequest request) {
        return commandService.create(request);
    }

    @Transactional(readOnly = true)
    public SalesOrderCreditPreviewResponse previewCredit(SalesOrderCreditPreviewRequest request) {
        return commandService.previewCredit(request);
    }

    @Transactional(readOnly = true)
    public SalesOrderResponse getById(Long id) {
        return queryService.getById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesOrderResponse> list(SalesOrderPageQuery query) {
        return queryService.list(query == null ? new SalesOrderPageQuery() : query);
    }

    @Transactional
    public SalesOrderResponse update(Long id, SalesOrderUpdateRequest request) {
        return commandService.update(id, request);
    }

    @Transactional
    public SalesOrderResponse submit(Long id, SalesOrderSubmitRequest request) {
        return workflowService.submit(id, request);
    }

    @Transactional
    public SalesOrderResponse approve(Long id, SalesOrderApproveRequest request) {
        return workflowService.approve(id, request);
    }

    @Transactional
    public SalesOrderResponse approveWorkflowTask(Long taskId, Long id, SalesOrderApproveRequest request) {
        return workflowService.approveWorkflowTask(taskId, id, request);
    }

    @Transactional
    public SalesOrderResponse reject(Long id, SalesOrderRejectRequest request) {
        return workflowService.reject(id, request);
    }

    @Transactional
    public SalesOrderResponse rejectWorkflowTask(Long taskId, Long id, SalesOrderRejectRequest request) {
        return workflowService.rejectWorkflowTask(taskId, id, request);
    }

    @Transactional
    public SalesOrderResponse unapprove(Long id) {
        return workflowService.unapprove(id);
    }

    @Transactional
    public SalesOrderResponse cancel(Long id) {
        return workflowService.cancel(id);
    }
}

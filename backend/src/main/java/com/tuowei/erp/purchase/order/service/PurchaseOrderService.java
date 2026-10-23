package com.tuowei.erp.purchase.order.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderApproveRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderPageQuery;
import com.tuowei.erp.purchase.order.web.PurchaseOrderRejectRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderSubmitRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderTraceResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderUpdateRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

@Service
public class PurchaseOrderService {

    private final PurchaseOrderQueryService purchaseOrderQueryService;
    private final PurchaseOrderCommandService purchaseOrderCommandService;
    private final PurchaseOrderTraceService purchaseOrderTraceService;
    private final PurchaseOrderWorkflowService purchaseOrderWorkflowService;

    @Autowired
    public PurchaseOrderService(
            PurchaseOrderQueryService purchaseOrderQueryService,
            PurchaseOrderCommandService purchaseOrderCommandService,
            PurchaseOrderTraceService purchaseOrderTraceService,
            PurchaseOrderWorkflowService purchaseOrderWorkflowService
    ) {
        this.purchaseOrderQueryService = purchaseOrderQueryService;
        this.purchaseOrderCommandService = purchaseOrderCommandService;
        this.purchaseOrderTraceService = purchaseOrderTraceService;
        this.purchaseOrderWorkflowService = purchaseOrderWorkflowService;
    }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public PurchaseOrderService(
            PurchaseOrderMapper purchaseOrderMapper,
            PurchaseOrderLineMapper purchaseOrderLineMapper,
            SupplierMapper supplierMapper,
            ProductValidator productValidator,
            PurchaseOrderNumberService purchaseOrderNumberService,
            AuditMetadataFactory auditMetadataFactory,
            PurchaseOrderQueryService purchaseOrderQueryService,
            PurchaseOrderTraceService purchaseOrderTraceService,
            PurchaseOrderWorkflowService purchaseOrderWorkflowService,
            PurchasePriceEvaluator purchasePriceEvaluator
    ) {
        this.purchaseOrderQueryService = purchaseOrderQueryService;
        this.purchaseOrderCommandService = new PurchaseOrderCommandService(
                purchaseOrderMapper,
                purchaseOrderLineMapper,
                supplierMapper,
                productValidator,
                purchaseOrderNumberService,
                auditMetadataFactory,
                purchaseOrderQueryService,
                purchasePriceEvaluator,
                null,
                null
        );
        this.purchaseOrderTraceService = purchaseOrderTraceService;
        this.purchaseOrderWorkflowService = purchaseOrderWorkflowService;
    }

    @Transactional
    public PurchaseOrderResponse create(PurchaseOrderCreateRequest request) {
        return purchaseOrderCommandService.create(request);
    }

    @Transactional
    public PurchaseOrderResponse createFromInquiry(
            PurchaseOrderCreateRequest request,
            PurchaseOrderInquirySource source
    ) {
        return purchaseOrderCommandService.createFromInquiry(request, source);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getById(Long id) {
        return purchaseOrderQueryService.getById(id);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getBySourceInquiry(Long orderId, Long inquiryId) {
        return purchaseOrderQueryService.getBySourceInquiry(orderId, inquiryId);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderTraceResponse trace(Long id) {
        return purchaseOrderTraceService.trace(getById(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> list(PurchaseOrderPageQuery query) {
        PurchaseOrderPageQuery safeQuery = query == null ? new PurchaseOrderPageQuery() : query;
        return purchaseOrderQueryService.list(safeQuery);
    }

    @Transactional
    public PurchaseOrderResponse update(Long id, PurchaseOrderUpdateRequest request) {
        return purchaseOrderCommandService.update(id, request);
    }

    @Transactional
    public PurchaseOrderResponse submit(Long id, PurchaseOrderSubmitRequest request) {
        return purchaseOrderWorkflowService.submit(id, request);
    }

    @Transactional
    public PurchaseOrderResponse approve(Long id, PurchaseOrderApproveRequest request) {
        return purchaseOrderWorkflowService.approve(id, request);
    }

    @Transactional
    public PurchaseOrderResponse approveWorkflowTask(Long taskId, Long id, PurchaseOrderApproveRequest request) {
        return purchaseOrderWorkflowService.approveWorkflowTask(taskId, id, request);
    }

    @Transactional
    public PurchaseOrderResponse unapprove(Long id) {
        return purchaseOrderWorkflowService.unapprove(id);
    }

    @Transactional
    public PurchaseOrderResponse reject(Long id, PurchaseOrderRejectRequest request) {
        return purchaseOrderWorkflowService.reject(id, request);
    }

    @Transactional
    public PurchaseOrderResponse rejectWorkflowTask(Long taskId, Long id, PurchaseOrderRejectRequest request) {
        return purchaseOrderWorkflowService.rejectWorkflowTask(taskId, id, request);
    }

    @Transactional
    public PurchaseOrderResponse cancel(Long id) {
        return purchaseOrderWorkflowService.cancel(id);
    }

    @Transactional
    public PurchaseOrderResponse close(Long id) {
        return purchaseOrderWorkflowService.close(id);
    }

    public StreamingResponseBody exportOrders(PurchaseOrderPageQuery query) {
        return purchaseOrderQueryService.exportOrders(query);
    }

}

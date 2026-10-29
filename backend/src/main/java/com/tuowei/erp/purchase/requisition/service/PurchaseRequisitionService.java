package com.tuowei.erp.purchase.requisition.service;

import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.requisition.mapper.PurchaseRequisitionLineMapper;
import com.tuowei.erp.purchase.requisition.mapper.PurchaseRequisitionMapper;
import com.tuowei.erp.purchase.requisition.web.PurchaseRequisitionCreateRequest;
import com.tuowei.erp.purchase.requisition.web.PurchaseRequisitionPageQuery;
import com.tuowei.erp.purchase.requisition.web.PurchaseRequisitionResponse;
import com.tuowei.erp.purchase.requisition.web.PurchaseRequisitionUpdateRequest;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Compatibility facade for requisition queries and commands. */
@Service
public class PurchaseRequisitionService {

    private final PurchaseRequisitionQueryService queryService;
    private final PurchaseRequisitionCommandService commandService;

    @Autowired
    public PurchaseRequisitionService(
            PurchaseRequisitionQueryService queryService,
            PurchaseRequisitionCommandService commandService
    ) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    /** Keeps direct construction in existing non-Spring tests compatible. */
    public PurchaseRequisitionService(
            PurchaseRequisitionMapper requisitionMapper,
            PurchaseRequisitionLineMapper lineMapper,
            ProductMapper productMapper,
            SupplierMapper supplierMapper,
            PurchaseOrderService purchaseOrderService,
            SequenceNumberGenerator sequenceNumberGenerator,
            WorkflowService workflowService,
            AuditMetadataFactory auditMetadataFactory,
            AttachmentService attachmentService
    ) {
        this.queryService = new PurchaseRequisitionQueryService(
                requisitionMapper, lineMapper, productMapper, auditMetadataFactory
        );
        this.commandService = new PurchaseRequisitionCommandService(
                requisitionMapper, lineMapper, supplierMapper, purchaseOrderService,
                sequenceNumberGenerator, workflowService, auditMetadataFactory, attachmentService, queryService
        );
    }

    @Transactional
    public PurchaseRequisitionResponse create(PurchaseRequisitionCreateRequest request) {
        return commandService.create(request);
    }

    @Transactional
    public PurchaseRequisitionResponse update(Long id, PurchaseRequisitionUpdateRequest request) {
        return commandService.update(id, request);
    }

    @Transactional
    public PurchaseRequisitionResponse submit(Long id) {
        return commandService.submit(id);
    }

    @Transactional
    public PurchaseRequisitionResponse approve(Long id) {
        return commandService.approve(id);
    }

    @Transactional
    public PurchaseRequisitionResponse approveWorkflowTask(Long taskId, Long id, String comment) {
        return commandService.approveWorkflowTask(taskId, id, comment);
    }

    @Transactional
    public PurchaseRequisitionResponse reject(Long id) {
        return commandService.reject(id);
    }

    @Transactional
    public PurchaseRequisitionResponse rejectWorkflowTask(Long taskId, Long id, String comment) {
        return commandService.rejectWorkflowTask(taskId, id, comment);
    }

    @Transactional
    public PurchaseRequisitionResponse cancel(Long id) {
        return commandService.cancel(id);
    }

    @Transactional
    public PurchaseRequisitionResponse convertToPurchaseOrder(Long id) {
        return commandService.convertToPurchaseOrder(id);
    }

    @Transactional(readOnly = true)
    public PurchaseRequisitionResponse getById(Long id) {
        return queryService.getById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseRequisitionResponse> list(PurchaseRequisitionPageQuery query) {
        return queryService.list(query == null ? new PurchaseRequisitionPageQuery() : query);
    }
}

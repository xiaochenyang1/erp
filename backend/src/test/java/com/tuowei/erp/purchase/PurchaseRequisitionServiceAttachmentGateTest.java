package com.tuowei.erp.purchase;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.requisition.mapper.PurchaseRequisitionLineMapper;
import com.tuowei.erp.purchase.requisition.mapper.PurchaseRequisitionMapper;
import com.tuowei.erp.purchase.requisition.model.PurchaseRequisitionEntity;
import com.tuowei.erp.purchase.requisition.service.PurchaseRequisitionService;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseRequisitionServiceAttachmentGateTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9931L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 21, 0)
    );
    private static final Long REQUISITION_ID = 8801L;

    private final PurchaseRequisitionMapper requisitionMapper = mock(PurchaseRequisitionMapper.class);
    private final PurchaseRequisitionLineMapper lineMapper = mock(PurchaseRequisitionLineMapper.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final SupplierMapper supplierMapper = mock(SupplierMapper.class);
    private final PurchaseOrderService purchaseOrderService = mock(PurchaseOrderService.class);
    private final SequenceNumberGenerator sequenceNumberGenerator = mock(SequenceNumberGenerator.class);
    private final WorkflowService workflowService = mock(WorkflowService.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final AttachmentService attachmentService = mock(AttachmentService.class);

    @Test
    void submitStopsAtAttachmentGateWithoutLeavingDraft() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        PurchaseRequisitionEntity draft = requisition("DRAFT");
        when(requisitionMapper.selectById(REQUISITION_ID)).thenReturn(draft);
        doThrow(new IllegalArgumentException("业务类型 PURCHASE_REQUISITION 要求至少上传 1 个附件，当前 0 个"))
                .when(attachmentService)
                .requireIfConfigured("PURCHASE_REQUISITION", REQUISITION_ID);

        assertThatThrownBy(() -> service().submit(REQUISITION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PURCHASE_REQUISITION");

        assertThat(draft.getStatus()).isEqualTo("DRAFT");
        verify(requisitionMapper, never()).updateById(any(PurchaseRequisitionEntity.class));
        verify(workflowService, never()).submit(any(), any(), any(), any(), any());
    }

    @Test
    void submitRejectsNonDraftBeforeReachingAttachmentGate() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(requisitionMapper.selectById(REQUISITION_ID)).thenReturn(requisition("SUBMITTED"));

        assertThatThrownBy(() -> service().submit(REQUISITION_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("当前请购单状态不允许提交审批");

        verify(attachmentService, never()).requireIfConfigured(any(), any());
    }

    private PurchaseRequisitionService service() {
        return new PurchaseRequisitionService(
                requisitionMapper,
                lineMapper,
                productMapper,
                supplierMapper,
                purchaseOrderService,
                sequenceNumberGenerator,
                workflowService,
                auditMetadataFactory,
                attachmentService
        );
    }

    private PurchaseRequisitionEntity requisition(String status) {
        PurchaseRequisitionEntity entity = new PurchaseRequisitionEntity();
        entity.setId(REQUISITION_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setRequisitionNo("PR-8801");
        entity.setRequisitionDate(LocalDate.of(2026, 6, 8));
        entity.setStatus(status);
        entity.setApprovalStatus("DRAFT");
        entity.setDeletedFlag(0);
        return entity;
    }
}

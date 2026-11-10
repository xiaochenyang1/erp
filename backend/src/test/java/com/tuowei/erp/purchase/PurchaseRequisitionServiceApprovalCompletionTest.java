package com.tuowei.erp.purchase;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.requisition.mapper.PurchaseRequisitionLineMapper;
import com.tuowei.erp.purchase.requisition.mapper.PurchaseRequisitionMapper;
import com.tuowei.erp.purchase.requisition.model.PurchaseRequisitionEntity;
import com.tuowei.erp.purchase.requisition.model.PurchaseRequisitionLineEntity;
import com.tuowei.erp.purchase.requisition.service.PurchaseRequisitionService;
import com.tuowei.erp.purchase.requisition.web.PurchaseRequisitionResponse;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseRequisitionServiceApprovalCompletionTest {

    private static final Long REQUISITION_ID = 8101L;
    private static final Long WORKFLOW_TASK_ID = 8201L;
    private static final AuditMetadata AUDIT = new AuditMetadata(
            701L,
            801L,
            901L,
            LocalDateTime.parse("2026-08-20T15:00:00")
    );

    @Mock
    private PurchaseRequisitionMapper requisitionMapper;
    @Mock
    private PurchaseRequisitionLineMapper lineMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private PurchaseOrderService purchaseOrderService;
    @Mock
    private SequenceNumberGenerator sequenceNumberGenerator;
    @Mock
    private WorkflowService workflowService;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;
    @Mock
    private AttachmentService attachmentService;

    private PurchaseRequisitionService service;
    private PurchaseRequisitionEntity requisition;

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(PurchaseRequisitionLineEntity.class) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                PurchaseRequisitionLineEntity.class.getName()
        );
        assistant.setCurrentNamespace(PurchaseRequisitionLineEntity.class.getName());
        TableInfoHelper.initTableInfo(assistant, PurchaseRequisitionLineEntity.class);
    }

    @BeforeEach
    void setUp() {
        requisition = submittedRequisition();
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(requisitionMapper.selectById(REQUISITION_ID)).thenReturn(requisition);
        when(lineMapper.selectList(any())).thenReturn(List.of());
        service = new PurchaseRequisitionService(
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

    @Test
    void directApprovalKeepsRequisitionSubmittedWhenWorkflowIsNotComplete() {
        when(workflowService.approve("PURCHASE_REQUISITION", REQUISITION_ID, null)).thenReturn(false);

        PurchaseRequisitionResponse actual = service.approve(REQUISITION_ID);

        assertThat(actual.status()).isEqualTo("SUBMITTED");
        assertThat(actual.approvalStatus()).isEqualTo("IN_APPROVAL");
        assertThat(requisition.getStatus()).isEqualTo("SUBMITTED");
        assertThat(requisition.getApprovalStatus()).isEqualTo("IN_APPROVAL");
        verify(requisitionMapper, never()).updateById(requisition);
    }

    @Test
    void taskApprovalKeepsRequisitionSubmittedWhenWorkflowIsNotComplete() {
        when(workflowService.approveTaskForBusiness(
                WORKFLOW_TASK_ID,
                "PURCHASE_REQUISITION",
                REQUISITION_ID,
                "first task approval"
        )).thenReturn(false);

        PurchaseRequisitionResponse actual = service.approveWorkflowTask(
                WORKFLOW_TASK_ID,
                REQUISITION_ID,
                "first task approval"
        );

        assertThat(actual.status()).isEqualTo("SUBMITTED");
        assertThat(actual.approvalStatus()).isEqualTo("IN_APPROVAL");
        assertThat(requisition.getStatus()).isEqualTo("SUBMITTED");
        assertThat(requisition.getApprovalStatus()).isEqualTo("IN_APPROVAL");
        verify(requisitionMapper, never()).updateById(requisition);
    }

    @Test
    void directApprovalUpdatesRequisitionAfterWorkflowCompletes() {
        when(workflowService.approve("PURCHASE_REQUISITION", REQUISITION_ID, null)).thenReturn(true);
        when(requisitionMapper.updateById(requisition)).thenReturn(1);

        PurchaseRequisitionResponse actual = service.approve(REQUISITION_ID);

        assertThat(actual.status()).isEqualTo("APPROVED");
        assertThat(actual.approvalStatus()).isEqualTo("APPROVED");
        assertThat(requisition.getStatus()).isEqualTo("APPROVED");
        assertThat(requisition.getApprovalStatus()).isEqualTo("APPROVED");
        assertThat(requisition.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(requisition.getUpdatedTime()).isEqualTo(AUDIT.now());
        InOrder order = inOrder(requisitionMapper, workflowService);
        order.verify(requisitionMapper).selectById(REQUISITION_ID);
        order.verify(workflowService).approve("PURCHASE_REQUISITION", REQUISITION_ID, null);
        order.verify(requisitionMapper).updateById(requisition);
    }

    @Test
    void taskApprovalUpdatesRequisitionAfterWorkflowCompletes() {
        when(workflowService.approveTaskForBusiness(
                WORKFLOW_TASK_ID,
                "PURCHASE_REQUISITION",
                REQUISITION_ID,
                "final task approval"
        )).thenReturn(true);
        when(requisitionMapper.updateById(requisition)).thenReturn(1);

        PurchaseRequisitionResponse actual = service.approveWorkflowTask(
                WORKFLOW_TASK_ID,
                REQUISITION_ID,
                "final task approval"
        );

        assertThat(actual.status()).isEqualTo("APPROVED");
        assertThat(actual.approvalStatus()).isEqualTo("APPROVED");
        assertThat(requisition.getStatus()).isEqualTo("APPROVED");
        assertThat(requisition.getApprovalStatus()).isEqualTo("APPROVED");
        assertThat(requisition.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(requisition.getUpdatedTime()).isEqualTo(AUDIT.now());
        InOrder order = inOrder(requisitionMapper, workflowService);
        order.verify(requisitionMapper).selectById(REQUISITION_ID);
        order.verify(workflowService).approveTaskForBusiness(
                WORKFLOW_TASK_ID,
                "PURCHASE_REQUISITION",
                REQUISITION_ID,
                "final task approval"
        );
        order.verify(requisitionMapper).updateById(requisition);
    }

    private PurchaseRequisitionEntity submittedRequisition() {
        PurchaseRequisitionEntity entity = new PurchaseRequisitionEntity();
        entity.setId(REQUISITION_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setRequisitionNo("PR-8101");
        entity.setRequisitionDate(LocalDate.of(2026, 8, 20));
        entity.setNeededDate(LocalDate.of(2026, 8, 25));
        entity.setStatus("SUBMITTED");
        entity.setApprovalStatus("IN_APPROVAL");
        entity.setDeletedFlag(0);
        entity.setVersion(0);
        return entity;
    }
}

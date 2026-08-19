package com.tuowei.erp.purchase;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderNumberService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderInquirySource;
import com.tuowei.erp.purchase.order.service.PurchaseOrderQueryService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderTraceService;
import com.tuowei.erp.purchase.order.service.PurchasePriceEvaluator;
import com.tuowei.erp.purchase.order.service.PurchaseOrderWorkflowService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderApproveRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderPageQuery;
import com.tuowei.erp.purchase.order.web.PurchaseOrderRejectRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import com.tuowei.erp.purchase.order.web.PurchaseOrderSubmitRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderTraceResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseOrderServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9932L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 21, 30)
    );
    private static final Long SUPPLIER_ID = 4101L;
    private static final Long PRODUCT_ID = 4201L;

    private final PurchaseOrderMapper purchaseOrderMapper = mock(PurchaseOrderMapper.class);
    private final PurchaseOrderLineMapper purchaseOrderLineMapper = mock(PurchaseOrderLineMapper.class);
    private final SupplierMapper supplierMapper = mock(SupplierMapper.class);
    private final ProductValidator productValidator = mock(ProductValidator.class);
    private final PurchaseOrderNumberService purchaseOrderNumberService = mock(PurchaseOrderNumberService.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final PurchaseOrderQueryService purchaseOrderQueryService = mock(PurchaseOrderQueryService.class);
    private final PurchaseOrderTraceService purchaseOrderTraceService = mock(PurchaseOrderTraceService.class);
    private final PurchaseOrderWorkflowService purchaseOrderWorkflowService = mock(PurchaseOrderWorkflowService.class);
    private final PurchasePriceEvaluator purchasePriceEvaluator = mock(PurchasePriceEvaluator.class);

    @Test
    void getByIdDelegatesToQueryService() {
        PurchaseOrderResponse expected = response("DRAFT", "DRAFT", "NOT_RECEIVED");
        when(purchaseOrderQueryService.getById(4301L)).thenReturn(expected);

        PurchaseOrderResponse actual = service().getById(4301L);

        assertThat(actual).isSameAs(expected);
        verify(purchaseOrderQueryService).getById(4301L);
        verify(purchaseOrderMapper, never()).selectById(any());
        verify(purchaseOrderLineMapper, never()).selectList(any());
    }

    @Test
    void getBySourceInquiryDelegatesToQueryService() {
        PurchaseOrderResponse expected = response("DRAFT", "DRAFT", "NOT_RECEIVED");
        when(purchaseOrderQueryService.getBySourceInquiry(4301L, 5101L)).thenReturn(expected);

        PurchaseOrderResponse actual = service().getBySourceInquiry(4301L, 5101L);

        assertThat(actual).isSameAs(expected);
        verify(purchaseOrderQueryService).getBySourceInquiry(4301L, 5101L);
    }

    @Test
    void listAndExportKeepThePurchaseOrderFacade() {
        PurchaseOrderPageQuery query = new PurchaseOrderPageQuery();
        PageResponse<PurchaseOrderResponse> page = new PageResponse<>(1, 20, 0, List.of());
        StreamingResponseBody export = outputStream -> outputStream.flush();
        when(purchaseOrderQueryService.list(query)).thenReturn(page);
        when(purchaseOrderQueryService.exportOrders(query)).thenReturn(export);

        assertThat(service().list(query)).isSameAs(page);
        assertThat(service().exportOrders(query)).isSameAs(export);
        verify(purchaseOrderQueryService).list(query);
        verify(purchaseOrderQueryService).exportOrders(query);
    }

    @Test
    void traceLoadsTheAuthorizedOrderBeforeDelegatingTheDocumentChain() {
        PurchaseOrderResponse order = response("DRAFT", "DRAFT", "NOT_RECEIVED");
        PurchaseOrderTraceResponse expected = new PurchaseOrderTraceResponse(order, null, null, null);
        when(purchaseOrderQueryService.getById(4301L)).thenReturn(order);
        when(purchaseOrderTraceService.trace(order)).thenReturn(expected);

        PurchaseOrderTraceResponse actual = service().trace(4301L);

        assertThat(actual).isSameAs(expected);
        verify(purchaseOrderQueryService).getById(4301L);
        verify(purchaseOrderTraceService).trace(order);
    }

    @Test
    void createRejectsSupplierFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        when(supplierMapper.selectById(SUPPLIER_ID))
                .thenReturn(activeSupplier(SUPPLIER_ID, AUDIT.companyId(), 999L));

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("供应商不存在或已停用");
    }

    @Test
    void createRejectsProductFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        when(supplierMapper.selectById(SUPPLIER_ID))
                .thenReturn(activeSupplier(SUPPLIER_ID, AUDIT.companyId(), AUDIT.accountBookId()));
        when(productValidator.requireProducts(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("商品不存在或已停用"));
        stubOrderInsert();

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在或已停用");
    }

    @Test
    void createFromInquiryPersistsHeaderAndLineProvenance() {
        stubAudit();
        when(supplierMapper.selectById(SUPPLIER_ID))
                .thenReturn(activeSupplier(SUPPLIER_ID, AUDIT.companyId(), AUDIT.accountBookId()));
        when(purchaseOrderNumberService.nextOrderNo(LocalDate.of(2026, 6, 8)))
                .thenReturn("PO202606080001");
        stubOrderInsert();
        when(purchaseOrderLineMapper.insert(any(PurchaseOrderLineEntity.class))).thenAnswer(invocation -> {
            PurchaseOrderLineEntity line = invocation.getArgument(0);
            line.setId(4401L);
            return 1;
        });
        PurchaseOrderResponse expected = response("DRAFT", "NOT_SUBMITTED", "NOT_RECEIVED");
        when(purchaseOrderQueryService.toResponse(any(), any(), any())).thenReturn(expected);

        PurchaseOrderResponse actual = service().createFromInquiry(
                createRequest(),
                new PurchaseOrderInquirySource(5101L, "RFQ202606080001", 5201L, List.of(5301L))
        );

        ArgumentCaptor<PurchaseOrderEntity> orderCaptor = ArgumentCaptor.forClass(PurchaseOrderEntity.class);
        verify(purchaseOrderMapper).insert(orderCaptor.capture());
        PurchaseOrderEntity insertedOrder = orderCaptor.getValue();
        assertThat(insertedOrder.getSourceInquiryId()).isEqualTo(5101L);
        assertThat(insertedOrder.getSourceInquiryNo()).isEqualTo("RFQ202606080001");
        assertThat(insertedOrder.getSourceQuoteId()).isEqualTo(5201L);
        assertThat(insertedOrder.getReceiptStatus()).isEqualTo("NOT_RECEIVED");

        ArgumentCaptor<PurchaseOrderLineEntity> lineCaptor = ArgumentCaptor.forClass(PurchaseOrderLineEntity.class);
        verify(purchaseOrderLineMapper).insert(lineCaptor.capture());
        assertThat(lineCaptor.getValue().getSourceInquiryId()).isEqualTo(5101L);
        assertThat(lineCaptor.getValue().getSourceInquiryLineId()).isEqualTo(5301L);
        verify(purchaseOrderQueryService).toResponse(
                insertedOrder,
                "tenant supplier",
                List.of(lineCaptor.getValue())
        );
        assertThat(actual).isSameAs(expected);
    }

    @Test
    void workflowActionsKeepThePurchaseOrderFacade() {
        PurchaseOrderSubmitRequest submitRequest = new PurchaseOrderSubmitRequest("submit");
        PurchaseOrderApproveRequest approveRequest = new PurchaseOrderApproveRequest("approve");
        PurchaseOrderRejectRequest rejectRequest = new PurchaseOrderRejectRequest("reject");
        PurchaseOrderResponse expected = response("APPROVED", "APPROVED", "NOT_RECEIVED");
        when(purchaseOrderWorkflowService.submit(4301L, submitRequest)).thenReturn(expected);
        when(purchaseOrderWorkflowService.approve(4301L, approveRequest)).thenReturn(expected);
        when(purchaseOrderWorkflowService.approveWorkflowTask(5501L, 4301L, approveRequest))
                .thenReturn(expected);
        when(purchaseOrderWorkflowService.unapprove(4301L)).thenReturn(expected);
        when(purchaseOrderWorkflowService.reject(4301L, rejectRequest)).thenReturn(expected);
        when(purchaseOrderWorkflowService.rejectWorkflowTask(5502L, 4301L, rejectRequest))
                .thenReturn(expected);
        when(purchaseOrderWorkflowService.cancel(4301L)).thenReturn(expected);
        when(purchaseOrderWorkflowService.close(4301L)).thenReturn(expected);

        assertThat(service().submit(4301L, submitRequest)).isSameAs(expected);
        assertThat(service().approve(4301L, approveRequest)).isSameAs(expected);
        assertThat(service().approveWorkflowTask(5501L, 4301L, approveRequest)).isSameAs(expected);
        assertThat(service().unapprove(4301L)).isSameAs(expected);
        assertThat(service().reject(4301L, rejectRequest)).isSameAs(expected);
        assertThat(service().rejectWorkflowTask(5502L, 4301L, rejectRequest)).isSameAs(expected);
        assertThat(service().cancel(4301L)).isSameAs(expected);
        assertThat(service().close(4301L)).isSameAs(expected);

        verify(purchaseOrderWorkflowService).submit(4301L, submitRequest);
        verify(purchaseOrderWorkflowService).approve(4301L, approveRequest);
        verify(purchaseOrderWorkflowService).approveWorkflowTask(5501L, 4301L, approveRequest);
        verify(purchaseOrderWorkflowService).unapprove(4301L);
        verify(purchaseOrderWorkflowService).reject(4301L, rejectRequest);
        verify(purchaseOrderWorkflowService).rejectWorkflowTask(5502L, 4301L, rejectRequest);
        verify(purchaseOrderWorkflowService).cancel(4301L);
        verify(purchaseOrderWorkflowService).close(4301L);
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private void stubOrderInsert() {
        when(purchaseOrderMapper.insert(any(PurchaseOrderEntity.class))).thenAnswer(invocation -> {
            PurchaseOrderEntity order = invocation.getArgument(0);
            order.setId(4301L);
            return 1;
        });
    }

    private PurchaseOrderService service() {
        return new PurchaseOrderService(
                purchaseOrderMapper,
                purchaseOrderLineMapper,
                supplierMapper,
                productValidator,
                purchaseOrderNumberService,
                auditMetadataFactory,
                purchaseOrderQueryService,
                purchaseOrderTraceService,
                purchaseOrderWorkflowService,
                purchasePriceEvaluator
        );
    }

    private PurchaseOrderCreateRequest createRequest() {
        return new PurchaseOrderCreateRequest(
                SUPPLIER_ID,
                LocalDate.of(2026, 6, 8),
                LocalDate.of(2026, 6, 9),
                "tenant boundary",
                List.of(new PurchaseOrderLineRequest(
                        PRODUCT_ID,
                        BigDecimal.ONE,
                        BigDecimal.TEN,
                        BigDecimal.ZERO,
                        "line"
                ))
        );
    }

    private PurchaseOrderResponse response(String status, String approvalStatus, String receiptStatus) {
        return new PurchaseOrderResponse(
                4301L,
                "PO-4301",
                SUPPLIER_ID,
                "tenant supplier",
                LocalDate.of(2026, 6, 8),
                LocalDate.of(2026, 6, 9),
                status,
                approvalStatus,
                receiptStatus,
                null,
                null,
                null,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ZERO,
                "tenant boundary",
                List.of()
        );
    }

    private SupplierEntity activeSupplier(Long id, Long companyId, Long accountBookId) {
        SupplierEntity supplier = new SupplierEntity();
        supplier.setId(id);
        supplier.setCompanyId(companyId);
        supplier.setAccountBookId(accountBookId);
        supplier.setSupplierName("tenant supplier");
        supplier.setStatus("ACTIVE");
        supplier.setDeletedFlag(0);
        return supplier;
    }

}

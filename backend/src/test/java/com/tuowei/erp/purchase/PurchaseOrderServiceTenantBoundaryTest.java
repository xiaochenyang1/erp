package com.tuowei.erp.purchase;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
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
import com.tuowei.erp.purchase.order.web.PurchaseOrderCreateRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderLineRequest;
import com.tuowei.erp.purchase.order.web.PurchaseOrderPageQuery;
import com.tuowei.erp.purchase.order.web.PurchaseOrderResponse;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

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
    private static final CurrentUser CURRENT_USER = new CurrentUser(
            AUDIT.userId(),
            AUDIT.companyId(),
            AUDIT.accountBookId(),
            11L,
            12L,
            "purchase_order_scope_user",
            "采购订单用户"
    );
    private static final Long SUPPLIER_ID = 4101L;
    private static final Long PRODUCT_ID = 4201L;

    private final PurchaseOrderMapper purchaseOrderMapper = mock(PurchaseOrderMapper.class);
    private final PurchaseOrderLineMapper purchaseOrderLineMapper = mock(PurchaseOrderLineMapper.class);
    private final SupplierMapper supplierMapper = mock(SupplierMapper.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final ProductValidator productValidator = mock(ProductValidator.class);
    private final PurchaseOrderNumberService purchaseOrderNumberService = mock(PurchaseOrderNumberService.class);
    private final AuditMetadataFactory auditMetadataFactory = mock(AuditMetadataFactory.class);
    private final PurchaseOrderQueryService purchaseOrderQueryService = mock(PurchaseOrderQueryService.class);
    private final PurchaseOrderTraceService purchaseOrderTraceService = mock(PurchaseOrderTraceService.class);
    private final WorkflowService workflowService = mock(WorkflowService.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PurchaseOrderEntity.class);
        initTableInfo(PurchaseOrderLineEntity.class);
    }

    @Test
    void getByIdScopesLineQueryByCompanyAndAccountBook() {
        when(purchaseOrderMapper.selectById(4301L)).thenReturn(order());
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine()));

        service().getById(4301L);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseOrderLineEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseOrderLineMapper).selectList(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
        verify(purchaseOrderQueryService).assertCanView(any(PurchaseOrderEntity.class));
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
        when(purchaseOrderMapper.selectById(4301L)).thenReturn(order());
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine()));

        service().trace(4301L);

        ArgumentCaptor<PurchaseOrderResponse> orderCaptor =
                ArgumentCaptor.forClass(PurchaseOrderResponse.class);
        verify(purchaseOrderTraceService).trace(orderCaptor.capture());
        assertThat(orderCaptor.getValue().id()).isEqualTo(4301L);
        assertThat(orderCaptor.getValue().lines()).singleElement()
                .satisfies(line -> assertThat(line.id()).isEqualTo(4401L));
    }

    @Test
    void createRejectsSupplierFromDifferentAccountBookWithinSameCompany() {
        stubAudit();
        when(supplierMapper.selectById(SUPPLIER_ID))
                .thenReturn(activeSupplier(SUPPLIER_ID, AUDIT.companyId(), 999L));
        when(productMapper.selectById(PRODUCT_ID))
                .thenReturn(activeProduct(PRODUCT_ID, AUDIT.companyId(), AUDIT.accountBookId()));
        stubOrderInsert();

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

        var response = service().createFromInquiry(
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
        assertThat(response.sourceInquiryId()).isEqualTo(5101L);
        assertThat(response.sourceInquiryNo()).isEqualTo("RFQ202606080001");
        assertThat(response.sourceQuoteId()).isEqualTo(5201L);
        assertThat(response.lines()).singleElement()
                .satisfies(line -> {
                    assertThat(line.sourceInquiryId()).isEqualTo(5101L);
                    assertThat(line.sourceInquiryLineId()).isEqualTo(5301L);
                });
    }

    @Test
    void unapproveApprovedUnreceivedOrderReturnsToDraft() {
        stubAudit();
        PurchaseOrderEntity approved = order();
        approved.setStatus("APPROVED");
        approved.setApprovalStatus("APPROVED");
        approved.setReceiptStatus("NOT_RECEIVED");
        when(purchaseOrderMapper.selectById(4301L)).thenReturn(approved);
        when(purchaseOrderMapper.updateById(any(PurchaseOrderEntity.class))).thenReturn(1);
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine()));

        service().unapprove(4301L);

        assertThat(approved.getStatus()).isEqualTo("DRAFT");
        assertThat(approved.getApprovalStatus()).isEqualTo("NOT_SUBMITTED");
        verify(purchaseOrderMapper).updateById(approved);
    }

    @Test
    void unapproveRejectsReceivedOrderWithoutUpdating() {
        PurchaseOrderEntity received = order();
        received.setStatus("APPROVED");
        received.setApprovalStatus("APPROVED");
        received.setReceiptStatus("PARTIAL_RECEIVED");
        when(purchaseOrderMapper.selectById(4301L)).thenReturn(received);

        assertThatThrownBy(() -> service().unapprove(4301L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("已入库采购订单不允许反审核");
        verify(purchaseOrderMapper, never()).updateById(any(PurchaseOrderEntity.class));
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
                productMapper,
                productValidator,
                purchaseOrderNumberService,
                auditMetadataFactory,
                purchaseOrderQueryService,
                purchaseOrderTraceService,
                workflowService,
                mock(PurchasePriceEvaluator.class)
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

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    private PurchaseOrderEntity order() {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setId(4301L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setOrderNo("PO-4301");
        entity.setSupplierId(SUPPLIER_ID);
        entity.setOrderDate(LocalDate.of(2026, 6, 8));
        entity.setStatus("DRAFT");
        entity.setApprovalStatus("DRAFT");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseOrderLineEntity orderLine() {
        PurchaseOrderLineEntity entity = new PurchaseOrderLineEntity();
        entity.setId(4401L);
        entity.setOrderId(4301L);
        entity.setLineNo(1);
        entity.setProductId(PRODUCT_ID);
        entity.setQty(BigDecimal.ONE);
        entity.setPrice(BigDecimal.TEN);
        entity.setTaxRate(BigDecimal.ZERO);
        entity.setAmount(BigDecimal.TEN);
        entity.setTaxAmount(BigDecimal.ZERO);
        entity.setReceivedQty(BigDecimal.ZERO);
        return entity;
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

    private ProductEntity activeProduct(Long id, Long companyId, Long accountBookId) {
        ProductEntity product = new ProductEntity();
        product.setId(id);
        product.setCompanyId(companyId);
        product.setAccountBookId(accountBookId);
        product.setStatus("ACTIVE");
        product.setDeletedFlag(0);
        return product;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}

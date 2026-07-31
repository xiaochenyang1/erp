package com.tuowei.erp.purchase;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderLookupService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderReceiptStatusService;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptLineEntity;
import com.tuowei.erp.purchase.receipt.service.PurchaseReceiptNumberService;
import com.tuowei.erp.purchase.receipt.service.PurchaseReceiptQueryService;
import com.tuowei.erp.purchase.receipt.service.PurchaseReceiptService;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptCreateRequest;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptLineRequest;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptPageQuery;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptResponse;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseReceiptServiceTenantBoundaryTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9701L,
            101L,
            202L,
            11L,
            12L,
            "purchase_receipt_scope_user",
            "采购入库用户"
    );
    private static final ErpPrincipal PRINCIPAL = new ErpPrincipal(
            CURRENT_USER.userId(),
            CURRENT_USER.companyId(),
            CURRENT_USER.accountBookId(),
            CURRENT_USER.deptId(),
            CURRENT_USER.postId(),
            CURRENT_USER.username(),
            CURRENT_USER.realName(),
            "N/A",
            Set.of(),
            DataScopeSnapshot.all()
    );
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 8, 17, 0);

    @Mock
    private PurchaseReceiptMapper purchaseReceiptMapper;

    @Mock
    private PurchaseReceiptLineMapper purchaseReceiptLineMapper;

    @Mock
    private PurchaseOrderLineMapper purchaseOrderLineMapper;

    @Mock
    private ProductValidator productValidator;

    @Mock
    private WarehouseMapper warehouseMapper;

    @Mock
    private InventoryPostingService inventoryPostingService;

    @Mock
    private InventorySerialNumberService inventorySerialNumberService;

    @Mock
    private PurchaseOrderLookupService purchaseOrderLookupService;

    @Mock
    private PurchaseOrderReceiptStatusService purchaseOrderReceiptStatusService;

    @Mock
    private PurchaseReceiptNumberService purchaseReceiptNumberService;

    @Mock
    private FinancePostingService financePostingService;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private CurrentUserContext currentUserContext;

    @Mock
    private DataScopeService dataScopeService;

    @Mock
    private ScopedUserResolver scopedUserResolver;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AccountPeriodGuard accountPeriodGuard;

    @Mock
    private com.tuowei.erp.qc.inspection.service.QcInspectionGate qcInspectionGate;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PurchaseReceiptLineEntity.class);
    }

    @Test
    void listDelegatesToQueryService() {
        PurchaseReceiptQueryService queryService = mock(PurchaseReceiptQueryService.class);
        PurchaseReceiptPageQuery query = new PurchaseReceiptPageQuery();
        PageResponse<PurchaseReceiptResponse> expected = new PageResponse<>(1L, 20L, 0L, List.of());
        when(queryService.list(query)).thenReturn(expected);

        PageResponse<PurchaseReceiptResponse> result = service(queryService).list(query);

        assertThat(result).isSameAs(expected);
        verify(queryService).list(query);
    }

    @Test
    void getByIdDelegatesToQueryService() {
        PurchaseReceiptQueryService queryService = mock(PurchaseReceiptQueryService.class);
        PurchaseReceiptResponse expected = response("DRAFT");
        when(queryService.getById(7001L)).thenReturn(expected);

        PurchaseReceiptResponse result = service(queryService).getById(7001L);

        assertThat(result).isSameAs(expected);
        verify(queryService).getById(7001L);
    }

    @Test
    void exportDelegatesToQueryService() {
        PurchaseReceiptQueryService queryService = mock(PurchaseReceiptQueryService.class);
        PurchaseReceiptPageQuery query = new PurchaseReceiptPageQuery();
        StreamingResponseBody expected = outputStream -> { };
        when(queryService.exportReceipts(query)).thenReturn(expected);

        StreamingResponseBody result = service(queryService).exportReceipts(query);

        assertThat(result).isSameAs(expected);
        verify(queryService).exportReceipts(query);
    }

    @Test
    void getByIdScopesLineQueryByCompanyAndAccountBook() {
        stubCurrentUser();
        when(purchaseReceiptMapper.selectById(7001L)).thenReturn(receipt());
        when(purchaseReceiptLineMapper.selectList(any())).thenReturn(List.of(receiptLine()));

        service().getById(7001L);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseReceiptLineEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseReceiptLineMapper).selectList(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void createRejectsWarehouseFromDifferentAccountBookWithinSameCompany() {
        stubCurrentUser();
        stubAudit();
        when(purchaseOrderLookupService.requireOrder(6001L)).thenReturn(order());
        when(warehouseMapper.selectById(3001L)).thenReturn(warehouse(9999L));

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仓库不存在或已停用");
    }

    @Test
    void postRejectsOrderLineProductFromDifferentAccountBookWithinSameCompany() {
        stubCurrentUser();
        stubAudit();
        when(purchaseReceiptMapper.selectById(7001L)).thenReturn(receipt());
        when(purchaseOrderLookupService.requireOrder(6001L)).thenReturn(order());
        when(warehouseMapper.selectById(3001L)).thenReturn(warehouse(CURRENT_USER.accountBookId()));
        when(purchaseReceiptLineMapper.selectList(any())).thenReturn(List.of(receiptLine()));
        when(purchaseOrderLookupService.loadOrderLinesAsMap(any(PurchaseOrderEntity.class))).thenReturn(Map.of(9001L, orderLine()));
        when(productValidator.requireProducts(any(), any(), any())).thenThrow(new IllegalArgumentException("商品不存在或已停用"));

        assertThatThrownBy(() -> service().post(7001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在或已停用");
    }

    private void stubCurrentUser() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
    }

    private void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(new AuditMetadata(
                CURRENT_USER.userId(),
                CURRENT_USER.companyId(),
                CURRENT_USER.accountBookId(),
                NOW
        ));
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    private PurchaseReceiptCreateRequest createRequest() {
        return new PurchaseReceiptCreateRequest(
                6001L,
                3001L,
                LocalDate.of(2026, 6, 8),
                "tenant boundary",
                List.of(new PurchaseReceiptLineRequest(
                        9001L,
                        new BigDecimal("2.0000"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        "line"
                ))
        );
    }

    private PurchaseOrderEntity order() {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setId(6001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setOrderNo("PO-6001");
        entity.setStatus("APPROVED");
        entity.setApprovalStatus("APPROVED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseOrderLineEntity orderLine() {
        PurchaseOrderLineEntity entity = new PurchaseOrderLineEntity();
        entity.setId(9001L);
        entity.setOrderId(6001L);
        entity.setProductId(4001L);
        entity.setQty(new BigDecimal("5.0000"));
        entity.setPrice(new BigDecimal("10.00"));
        entity.setTaxRate(BigDecimal.ZERO);
        entity.setAmount(new BigDecimal("50.00"));
        entity.setTaxAmount(BigDecimal.ZERO);
        entity.setReceivedQty(BigDecimal.ZERO);
        return entity;
    }

    private PurchaseReceiptEntity receipt() {
        PurchaseReceiptEntity entity = new PurchaseReceiptEntity();
        entity.setId(7001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setReceiptNo("GR-7001");
        entity.setOrderId(6001L);
        entity.setWarehouseId(3001L);
        entity.setReceiptDate(LocalDate.of(2026, 6, 8));
        entity.setStatus("DRAFT");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseReceiptLineEntity receiptLine() {
        PurchaseReceiptLineEntity entity = new PurchaseReceiptLineEntity();
        entity.setId(8001L);
        entity.setReceiptId(7001L);
        entity.setOrderLineId(9001L);
        entity.setProductId(4001L);
        entity.setQty(new BigDecimal("2.0000"));
        entity.setPrice(new BigDecimal("10.00"));
        entity.setTaxRate(BigDecimal.ZERO);
        entity.setAmount(new BigDecimal("20.00"));
        entity.setTaxAmount(BigDecimal.ZERO);
        return entity;
    }

    private WarehouseEntity warehouse(Long accountBookId) {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(3001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setWarehouseName("A仓");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseReceiptService service() {
        PurchaseReceiptQueryService queryService = new PurchaseReceiptQueryService(
                purchaseReceiptMapper,
                purchaseReceiptLineMapper,
                currentUserContext,
                dataScopeService,
                scopedUserResolver,
                userMapper
        );
        return service(queryService);
    }

    private PurchaseReceiptService service(PurchaseReceiptQueryService queryService) {
        return new PurchaseReceiptService(
                purchaseReceiptMapper,
                purchaseReceiptLineMapper,
                purchaseOrderLineMapper,
                warehouseMapper,
                inventoryPostingService,
                inventorySerialNumberService,
                purchaseOrderLookupService,
                purchaseOrderReceiptStatusService,
                purchaseReceiptNumberService,
                financePostingService,
                auditMetadataFactory,
                accountPeriodGuard,
                qcInspectionGate,
                productValidator,
                queryService
        );
    }

    private PurchaseReceiptResponse response(String status) {
        return new PurchaseReceiptResponse(
                7001L,
                "GR-7001",
                6001L,
                3001L,
                LocalDate.of(2026, 6, 8),
                status,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                List.of()
        );
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

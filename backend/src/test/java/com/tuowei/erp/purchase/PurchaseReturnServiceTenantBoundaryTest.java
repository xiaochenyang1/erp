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
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderLookupService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderReceiptStatusService;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptLineEntity;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnLineMapper;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnLineEntity;
import com.tuowei.erp.purchase.returnorder.service.PurchaseReturnNumberService;
import com.tuowei.erp.purchase.returnorder.service.PurchaseReturnService;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnCreateRequest;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnResponse;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnLineRequest;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseReturnServiceTenantBoundaryTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9601L,
            101L,
            202L,
            11L,
            12L,
            "purchase_return_scope_user",
            "采购退货用户"
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
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 8, 16, 0);

    @Mock
    private PurchaseReturnMapper purchaseReturnMapper;

    @Mock
    private PurchaseReturnLineMapper purchaseReturnLineMapper;

    @Mock
    private PurchaseReceiptMapper purchaseReceiptMapper;

    @Mock
    private PurchaseReceiptLineMapper purchaseReceiptLineMapper;

    @Mock
    private PurchaseOrderMapper purchaseOrderMapper;

    @Mock
    private PurchaseOrderLineMapper purchaseOrderLineMapper;

    @Mock
    private WarehouseMapper warehouseMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductValidator productValidator;

    @Mock
    private InventoryPostingService inventoryPostingService;

    @Mock
    private InventorySerialNumberService inventorySerialNumberService;

    @Mock
    private PurchaseOrderLookupService purchaseOrderLookupService;

    @Mock
    private PurchaseOrderReceiptStatusService purchaseOrderReceiptStatusService;

    @Mock
    private PurchaseReturnNumberService purchaseReturnNumberService;

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

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PurchaseReceiptLineEntity.class);
        initTableInfo(PurchaseReturnLineEntity.class);
    }

    @Test
    void getByIdScopesReturnLineQueryByCompanyAndAccountBook() {
        stubCurrentUser();
        when(purchaseReturnMapper.selectById(9001L)).thenReturn(returnOrder());
        when(purchaseReceiptMapper.selectById(7001L)).thenReturn(receipt());
        when(purchaseReturnLineMapper.selectList(any())).thenReturn(List.of(returnLine()));

        service().getById(9001L);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseReturnLineEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseReturnLineMapper).selectList(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void getByIdDoesNotEnrichLineFromDifferentAccountBookReceiptLine() {
        stubCurrentUser();
        when(purchaseReturnMapper.selectById(9001L)).thenReturn(returnOrder());
        when(purchaseReceiptMapper.selectById(7001L)).thenReturn(receipt());
        when(purchaseReturnLineMapper.selectList(any())).thenReturn(List.of(returnLine()));
        when(purchaseReceiptLineMapper.selectOne(any())).thenReturn(null);

        PurchaseReturnResponse response = service().getById(9001L);

        assertThat(response.lines()).hasSize(1);
        assertThat(response.lines().get(0).receiptQty()).isNull();
        assertThat(response.lines().get(0).availableReturnQty()).isNull();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseReceiptLineEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseReceiptLineMapper).selectOne(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void getByIdRejectsReceiptFromDifferentAccountBookWithinSameCompany() {
        stubCurrentUser();
        when(purchaseReturnMapper.selectById(9001L)).thenReturn(returnOrder());
        when(purchaseReceiptMapper.selectById(7001L)).thenReturn(receipt(9999L));

        assertThatThrownBy(() -> service().getById(9001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("采购入库单未过账，不能创建采购退货单");
    }

    @Test
    void createRejectsReceiptLineProductFromDifferentAccountBookWithinSameCompany() {
        stubCurrentUser();
        stubCreateContext();
        when(productValidator.requireProducts(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("商品不存在或已停用"));

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在或已停用");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseReceiptLineEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseReceiptLineMapper).selectList(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void postRejectsWhenAvailableStockIsBelowReturnQuantity() {
        stubCurrentUser();
        stubPostContext();
        when(inventoryPostingService.getQtyAvailable(
                3001L,
                4001L,
                CURRENT_USER.companyId(),
                CURRENT_USER.accountBookId()
        )).thenReturn(new BigDecimal("1.0000"));

        assertThatThrownBy(() -> service().post(9001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存不足，不能执行采购退货");
    }

    private void stubCurrentUser() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
    }

    private void stubCreateContext() {
        when(auditMetadataFactory.current()).thenReturn(new AuditMetadata(
                CURRENT_USER.userId(),
                CURRENT_USER.companyId(),
                CURRENT_USER.accountBookId(),
                NOW
        ));
        when(purchaseReceiptMapper.selectById(7001L)).thenReturn(receipt());
        when(purchaseReceiptLineMapper.selectList(any())).thenReturn(List.of(receiptLine()));
        when(purchaseOrderMapper.selectById(6001L)).thenReturn(order());
        when(warehouseMapper.selectById(3001L)).thenReturn(warehouse());
        when(purchaseReturnNumberService.nextReturnNo(LocalDate.of(2026, 6, 8))).thenReturn("PR-20260608-001");
    }

    private void stubPostContext() {
        when(auditMetadataFactory.current()).thenReturn(new AuditMetadata(
                CURRENT_USER.userId(),
                CURRENT_USER.companyId(),
                CURRENT_USER.accountBookId(),
                NOW
        ));
        when(purchaseReturnMapper.selectById(9001L)).thenReturn(returnOrder());
        when(purchaseReceiptMapper.selectById(7001L)).thenReturn(receipt());
        when(purchaseReturnLineMapper.selectList(any())).thenReturn(List.of(returnLine()));
        when(purchaseReceiptLineMapper.selectList(any())).thenReturn(List.of(receiptLine()));
        when(purchaseOrderLookupService.requireOrder(6001L)).thenReturn(order());
        when(purchaseOrderLookupService.loadOrderLinesAsMap(any(PurchaseOrderEntity.class))).thenReturn(Map.of());
    }

    private PurchaseReturnCreateRequest createRequest() {
        return new PurchaseReturnCreateRequest(
                7001L,
                LocalDate.of(2026, 6, 8),
                "tenant boundary",
                List.of(new PurchaseReturnLineRequest(
                        8001L,
                        new BigDecimal("2.0000"),
                        "line"
                ))
        );
    }

    private PurchaseReceiptEntity receipt() {
        return receipt(CURRENT_USER.accountBookId());
    }

    private PurchaseReceiptEntity receipt(Long accountBookId) {
        PurchaseReceiptEntity entity = new PurchaseReceiptEntity();
        entity.setId(7001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setReceiptNo("GR-7001");
        entity.setOrderId(6001L);
        entity.setWarehouseId(3001L);
        entity.setStatus("POSTED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseReceiptLineEntity receiptLine() {
        return receiptLine(CURRENT_USER.accountBookId(), new BigDecimal("5.0000"), BigDecimal.ZERO);
    }

    private PurchaseReceiptLineEntity receiptLine(Long accountBookId, BigDecimal qty, BigDecimal returnedQty) {
        PurchaseReceiptLineEntity entity = new PurchaseReceiptLineEntity();
        entity.setId(8001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setReceiptId(7001L);
        entity.setLineNo(1);
        entity.setOrderLineId(9001L);
        entity.setProductId(4001L);
        entity.setQty(qty);
        entity.setPrice(new BigDecimal("10.00"));
        entity.setTaxRate(new BigDecimal("0.0000"));
        entity.setAmount(new BigDecimal("50.00"));
        entity.setTaxAmount(new BigDecimal("0.00"));
        entity.setReturnedQty(returnedQty);
        return entity;
    }

    private PurchaseReturnEntity returnOrder() {
        PurchaseReturnEntity entity = new PurchaseReturnEntity();
        entity.setId(9001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setReturnNo("PR-9001");
        entity.setReceiptId(7001L);
        entity.setWarehouseId(3001L);
        entity.setReturnDate(LocalDate.of(2026, 6, 8));
        entity.setStatus("DRAFT");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseReturnLineEntity returnLine() {
        PurchaseReturnLineEntity entity = new PurchaseReturnLineEntity();
        entity.setId(9101L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setReturnId(9001L);
        entity.setLineNo(1);
        entity.setReceiptLineId(8001L);
        entity.setOrderLineId(9001L);
        entity.setProductId(4001L);
        entity.setQty(new BigDecimal("2.0000"));
        entity.setPrice(new BigDecimal("10.00"));
        entity.setTaxRate(new BigDecimal("0.0000"));
        entity.setAmount(new BigDecimal("20.00"));
        entity.setTaxAmount(new BigDecimal("0.00"));
        return entity;
    }

    private PurchaseOrderEntity order() {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setId(6001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setOrderNo("PO-6001");
        entity.setDeletedFlag(0);
        return entity;
    }

    private WarehouseEntity warehouse() {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(3001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setWarehouseName("A仓");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private ProductEntity product(Long accountBookId) {
        ProductEntity entity = new ProductEntity();
        entity.setId(4001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setProductName("P-4001");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseReturnService service() {
        return new PurchaseReturnService(
                purchaseReturnMapper,
                purchaseReturnLineMapper,
                purchaseReceiptMapper,
                purchaseReceiptLineMapper,
                purchaseOrderMapper,
                purchaseOrderLineMapper,
                warehouseMapper,
                productMapper,
                productValidator,
                inventoryPostingService,
                inventorySerialNumberService,
                purchaseOrderLookupService,
                purchaseOrderReceiptStatusService,
                purchaseReturnNumberService,
                financePostingService,
                auditMetadataFactory,
                currentUserContext,
                dataScopeService,
                scopedUserResolver,
                userMapper,
                accountPeriodGuard
        );
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
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

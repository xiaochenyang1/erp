package com.tuowei.erp.purchase;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.service.PurchaseOrderLookupService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderReceiptStatusService;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.service.PurchaseReceiptNumberService;
import com.tuowei.erp.purchase.receipt.service.PurchaseReceiptService;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptPageQuery;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnLineMapper;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.purchase.returnorder.service.PurchaseReturnNumberService;
import com.tuowei.erp.purchase.returnorder.service.PurchaseReturnPostingService;
import com.tuowei.erp.purchase.returnorder.service.PurchaseReturnQueryService;
import com.tuowei.erp.purchase.returnorder.service.PurchaseReturnService;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnPageQuery;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseReceiptReturnServiceExportTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9701L,
            101L,
            202L,
            11L,
            12L,
            "purchase_export_user",
            "采购导出用户"
    );
    private static final DataScopeSnapshot SNAPSHOT = DataScopeSnapshot.all();
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
            SNAPSHOT
    );

    @Mock
    private PurchaseReceiptMapper purchaseReceiptMapper;

    @Mock
    private PurchaseReceiptLineMapper purchaseReceiptLineMapper;

    @Mock
    private PurchaseReturnMapper purchaseReturnMapper;

    @Mock
    private PurchaseReturnLineMapper purchaseReturnLineMapper;

    @Mock
    private PurchaseReceiptMapper returnReceiptMapper;

    @Mock
    private PurchaseOrderMapper purchaseOrderMapper;

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

    @Mock
    private PurchaseReturnPostingService purchaseReturnPostingService;

    @Mock
    private com.tuowei.erp.qc.inspection.service.QcInspectionGate qcInspectionGate;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PurchaseReceiptEntity.class);
        initTableInfo(PurchaseReturnEntity.class);
    }

    @Test
    void exportReceiptsWritesScopedCsvRows() throws Exception {
        stubScope();
        when(dataScopeService.applyPurchaseReceiptScope(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    LambdaQueryWrapper<PurchaseReceiptEntity> wrapper = invocation.getArgument(0);
                    CurrentUser currentUser = invocation.getArgument(1);
                    return wrapper.eq(PurchaseReceiptEntity::getCompanyId, currentUser.companyId())
                            .eq(PurchaseReceiptEntity::getAccountBookId, currentUser.accountBookId());
                });
        when(purchaseReceiptMapper.selectList(any())).thenReturn(List.of(receipt()));

        PurchaseReceiptPageQuery query = new PurchaseReceiptPageQuery();
        query.setKeyword("GR-001");
        query.setOrderId(6001L);
        query.setWarehouseId(3001L);
        query.setStatus("posted");
        query.setReceiptDateFrom(LocalDate.of(2026, 6, 1));
        query.setReceiptDateTo(LocalDate.of(2026, 6, 30));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        receiptService().exportReceipts(query).writeTo(outputStream);

        String csv = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFreceiptNo,orderId,warehouseId,receiptDate,status,totalQuantity,totalAmount,totalTaxAmount,remark\r\n");
        assertThat(csv).contains("GR-001,6001,3001,2026-06-18,POSTED,6.0000,120.00,15.60,receipt export\r\n");
        verify(dataScopeService).applyPurchaseReceiptScope(any(), any(), any(), any(), any());
        verifyScopedSelectList(purchaseReceiptMapper, PurchaseReceiptEntity.class);
    }

    @Test
    void exportReturnsWritesScopedCsvRows() throws Exception {
        stubScope();
        when(dataScopeService.applyPurchaseReturnScope(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    LambdaQueryWrapper<PurchaseReturnEntity> wrapper = invocation.getArgument(0);
                    CurrentUser currentUser = invocation.getArgument(1);
                    return wrapper.eq(PurchaseReturnEntity::getCompanyId, currentUser.companyId())
                            .eq(PurchaseReturnEntity::getAccountBookId, currentUser.accountBookId());
                });
        when(purchaseReturnMapper.selectList(any())).thenReturn(List.of(returnOrder()));

        PurchaseReturnPageQuery query = new PurchaseReturnPageQuery();
        query.setKeyword("PR-001");
        query.setReceiptId(7001L);
        query.setWarehouseId(3001L);
        query.setStatus("posted");
        query.setReturnDateFrom(LocalDate.of(2026, 6, 1));
        query.setReturnDateTo(LocalDate.of(2026, 6, 30));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        returnService().exportReturns(query).writeTo(outputStream);

        String csv = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFFreturnNo,receiptId,warehouseId,returnDate,status,totalQuantity,totalAmount,totalTaxAmount,remark\r\n");
        assertThat(csv).contains("PR-001,7001,3001,2026-06-20,POSTED,1.0000,20.00,2.60,return export\r\n");
        verify(dataScopeService).applyPurchaseReturnScope(any(), any(), any(), any(), any());
        verifyScopedSelectList(purchaseReturnMapper, PurchaseReturnEntity.class);
    }

    private void stubScope() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
        when(scopedUserResolver.resolve(CURRENT_USER, SNAPSHOT)).thenReturn(new ScopedUserResolver.ScopedUserIds(Set.of(), Set.of()));
    }

    private void verifyScopedSelectList(PurchaseReceiptMapper mapper, Class<PurchaseReceiptEntity> entityClass) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseReceiptEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    private void verifyScopedSelectList(PurchaseReturnMapper mapper, Class<PurchaseReturnEntity> entityClass) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseReturnEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag");
    }

    private PurchaseReceiptService receiptService() {
        return new PurchaseReceiptService(
                purchaseReceiptMapper,
                purchaseReceiptLineMapper,
                purchaseOrderMapper,
                purchaseOrderLineMapper,
                warehouseMapper,
                inventoryPostingService,
                inventorySerialNumberService,
                purchaseOrderLookupService,
                purchaseOrderReceiptStatusService,
                purchaseReceiptNumberService,
                financePostingService,
                auditMetadataFactory,
                currentUserContext,
                dataScopeService,
                scopedUserResolver,
                userMapper,
                accountPeriodGuard,
                qcInspectionGate,
                productValidator
        );
    }

    private PurchaseReturnService returnService() {
        PurchaseReturnQueryService queryService = new PurchaseReturnQueryService(
                purchaseReturnMapper,
                purchaseReturnLineMapper,
                returnReceiptMapper,
                purchaseReceiptLineMapper,
                purchaseOrderMapper,
                warehouseMapper,
                productValidator,
                currentUserContext,
                dataScopeService,
                scopedUserResolver,
                userMapper
        );
        return new PurchaseReturnService(
                purchaseReturnMapper,
                purchaseReturnLineMapper,
                returnReceiptMapper,
                purchaseReceiptLineMapper,
                productValidator,
                purchaseReturnNumberService,
                auditMetadataFactory,
                queryService,
                purchaseReturnPostingService
        );
    }

    private PurchaseReceiptEntity receipt() {
        PurchaseReceiptEntity entity = new PurchaseReceiptEntity();
        entity.setId(7001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setReceiptNo("GR-001");
        entity.setOrderId(6001L);
        entity.setWarehouseId(3001L);
        entity.setReceiptDate(LocalDate.of(2026, 6, 18));
        entity.setStatus("POSTED");
        entity.setTotalQuantity(new BigDecimal("6.0000"));
        entity.setTotalAmount(new BigDecimal("120.00"));
        entity.setTotalTaxAmount(new BigDecimal("15.60"));
        entity.setRemark("receipt export");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseReturnEntity returnOrder() {
        PurchaseReturnEntity entity = new PurchaseReturnEntity();
        entity.setId(9001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setReturnNo("PR-001");
        entity.setReceiptId(7001L);
        entity.setWarehouseId(3001L);
        entity.setReturnDate(LocalDate.of(2026, 6, 20));
        entity.setStatus("POSTED");
        entity.setTotalQuantity(new BigDecimal("1.0000"));
        entity.setTotalAmount(new BigDecimal("20.00"));
        entity.setTotalTaxAmount(new BigDecimal("2.60"));
        entity.setRemark("return export");
        entity.setDeletedFlag(0);
        return entity;
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

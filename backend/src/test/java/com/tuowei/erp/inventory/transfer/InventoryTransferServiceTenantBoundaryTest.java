package com.tuowei.erp.inventory.transfer;

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
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.transfer.mapper.InventoryTransferLineMapper;
import com.tuowei.erp.inventory.transfer.mapper.InventoryTransferMapper;
import com.tuowei.erp.inventory.transfer.model.InventoryTransferEntity;
import com.tuowei.erp.inventory.transfer.model.InventoryTransferLineEntity;
import com.tuowei.erp.inventory.transfer.service.InventoryTransferNumberService;
import com.tuowei.erp.inventory.transfer.service.InventoryTransferService;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferCreateRequest;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferLineRequest;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryTransferServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9931L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 21, 0)
    );
    private static final CurrentUser CURRENT_USER = new CurrentUser(
            AUDIT.userId(),
            AUDIT.companyId(),
            AUDIT.accountBookId(),
            11L,
            12L,
            "inventory_transfer_user",
            "库存调拨用户"
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
    private static final Long TRANSFER_ID = 8001L;
    private static final Long FROM_WAREHOUSE_ID = 6001L;
    private static final Long TO_WAREHOUSE_ID = 6002L;
    private static final Long PRODUCT_ID = 7001L;
    private static final LocalDate BIZ_DATE = LocalDate.of(2026, 6, 8);

    @Mock
    private InventoryTransferMapper transferMapper;

    @Mock
    private InventoryTransferLineMapper lineMapper;

    @Mock
    private InventoryTransferNumberService numberService;

    @Mock
    private InventoryPostingService inventoryPostingService;

    @Mock
    private InventorySerialNumberService inventorySerialNumberService;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private CurrentUserContext currentUserContext;

    @Mock
    private DataScopeService dataScopeService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private WarehouseMapper warehouseMapper;

    @Mock
    private ProductValidator productValidator;

    @Mock
    private AccountPeriodGuard accountPeriodGuard;

    @Mock
    private AttachmentService attachmentService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryTransferLineEntity.class);
        initTableInfo(WarehouseEntity.class);
        initTableInfo(ProductEntity.class);
    }

    @Test
    void createRejectsWarehouseFromDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(warehouseMapper.selectById(FROM_WAREHOUSE_ID)).thenReturn(activeWarehouse(FROM_WAREHOUSE_ID, AUDIT.accountBookId()));
        when(warehouseMapper.selectById(TO_WAREHOUSE_ID)).thenReturn(activeWarehouse(TO_WAREHOUSE_ID, 9999L));

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仓库不存在或已停用");
    }

    @Test
    void createRejectsProductFromDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(warehouseMapper.selectById(FROM_WAREHOUSE_ID)).thenReturn(activeWarehouse(FROM_WAREHOUSE_ID, AUDIT.accountBookId()));
        when(warehouseMapper.selectById(TO_WAREHOUSE_ID)).thenReturn(activeWarehouse(TO_WAREHOUSE_ID, AUDIT.accountBookId()));
        when(productValidator.requireProducts(any(), any(), any())).thenThrow(new IllegalArgumentException("商品不存在或已停用"));

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在或已停用");
    }

    @Test
    void createRejectsNullRequestBeforeInsertingTransfer() {
        assertThatThrownBy(() -> service().create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存调拨单明细不能为空");

        verify(transferMapper, never()).insert(any(InventoryTransferEntity.class));
        verify(lineMapper, never()).insert(any(InventoryTransferLineEntity.class));
    }

    @Test
    void createRejectsNullLinesBeforeInsertingTransfer() {
        assertThatThrownBy(() -> service().create(new InventoryTransferCreateRequest(
                        FROM_WAREHOUSE_ID,
                        TO_WAREHOUSE_ID,
                        BIZ_DATE,
                        null,
                        "missing lines"
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存调拨单明细不能为空");

        verify(transferMapper, never()).insert(any(InventoryTransferEntity.class));
        verify(lineMapper, never()).insert(any(InventoryTransferLineEntity.class));
    }

    @Test
    void createRejectsEmptyLinesBeforeInsertingTransfer() {
        assertThatThrownBy(() -> service().create(new InventoryTransferCreateRequest(
                        FROM_WAREHOUSE_ID,
                        TO_WAREHOUSE_ID,
                        BIZ_DATE,
                        List.of(),
                        "empty lines"
                )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存调拨单明细不能为空");

        verify(transferMapper, never()).insert(any(InventoryTransferEntity.class));
        verify(lineMapper, never()).insert(any(InventoryTransferLineEntity.class));
    }

    @Test
    void getByIdRejectsDifferentAccountBookWithinSameCompany() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(transferMapper.selectById(TRANSFER_ID)).thenReturn(transfer(9999L));

        assertThatThrownBy(() -> service().getById(TRANSFER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存调拨单不存在");
    }

    @Test
    void getByIdScopesLineQueryByCompanyAndAccountBook() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
        when(transferMapper.selectById(TRANSFER_ID)).thenReturn(transfer(AUDIT.accountBookId()));
        when(lineMapper.selectList(any())).thenReturn(List.of());

        service().getById(TRANSFER_ID);

        @SuppressWarnings({"unchecked", "rawtypes"})
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<InventoryTransferLineEntity>> wrapperCaptor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(lineMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("transfer_id")
                .contains("company_id")
                .contains("account_book_id");
    }

    @Test
    void postRejectsDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(transferMapper.selectById(TRANSFER_ID)).thenReturn(transfer(9999L));

        assertThatThrownBy(() -> service().post(TRANSFER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存调拨单不存在");
    }

    @Test
    void postStopsAtAttachmentGateBeforeCheckingAccountingPeriod() {
        InventoryTransferEntity transfer = transfer(AUDIT.accountBookId());
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
        when(transferMapper.selectById(TRANSFER_ID)).thenReturn(transfer);
        doThrow(new IllegalArgumentException("业务类型 INVENTORY_TRANSFER 要求至少上传 1 个附件，当前 0 个"))
                .when(attachmentService)
                .requireIfConfigured("INVENTORY_TRANSFER", TRANSFER_ID);

        assertThatThrownBy(() -> service().post(TRANSFER_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INVENTORY_TRANSFER");

        assertThat(transfer.getStatus()).isEqualTo("DRAFT");
        verify(accountPeriodGuard, never()).requireOpen(any(), any());
        verify(transferMapper, never()).updateById(any(InventoryTransferEntity.class));
    }

    private InventoryTransferService service() {
        return new InventoryTransferService(
                transferMapper,
                lineMapper,
                numberService,
                inventoryPostingService,
                inventorySerialNumberService,
                auditMetadataFactory,
                currentUserContext,
                dataScopeService,
                userMapper,
                warehouseMapper,
                productValidator,
                accountPeriodGuard,
                attachmentService
        );
    }

    private InventoryTransferCreateRequest createRequest() {
        return new InventoryTransferCreateRequest(
                FROM_WAREHOUSE_ID,
                TO_WAREHOUSE_ID,
                BIZ_DATE,
                List.of(new InventoryTransferLineRequest(
                        PRODUCT_ID,
                        new BigDecimal("1.0000"),
                        new BigDecimal("10.0000"),
                        "tenant boundary"
                )),
                "tenant boundary"
        );
    }

    private InventoryTransferEntity transfer(Long accountBookId) {
        InventoryTransferEntity transfer = new InventoryTransferEntity();
        transfer.setId(TRANSFER_ID);
        transfer.setCompanyId(AUDIT.companyId());
        transfer.setAccountBookId(accountBookId);
        transfer.setTransferNo("TRF-20260608-001");
        transfer.setFromWarehouseId(FROM_WAREHOUSE_ID);
        transfer.setToWarehouseId(TO_WAREHOUSE_ID);
        transfer.setTransferDate(BIZ_DATE);
        transfer.setStatus("DRAFT");
        transfer.setDeletedFlag(0);
        transfer.setTotalQuantity(BigDecimal.ZERO);
        transfer.setTotalAmount(BigDecimal.ZERO);
        transfer.setCreatedBy(AUDIT.userId());
        return transfer;
    }

    private WarehouseEntity activeWarehouse(Long id, Long accountBookId) {
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(id);
        warehouse.setCompanyId(AUDIT.companyId());
        warehouse.setAccountBookId(accountBookId);
        warehouse.setStatus("ACTIVE");
        warehouse.setDeletedFlag(0);
        return warehouse;
    }

    private ProductEntity activeProduct(Long accountBookId) {
        ProductEntity product = new ProductEntity();
        product.setId(PRODUCT_ID);
        product.setCompanyId(AUDIT.companyId());
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

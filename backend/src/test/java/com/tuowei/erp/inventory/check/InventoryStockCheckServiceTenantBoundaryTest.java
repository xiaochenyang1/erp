package com.tuowei.erp.inventory.check;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.inventory.adjust.service.InventoryAdjustmentService;
import com.tuowei.erp.inventory.check.mapper.InventoryStockCheckLineMapper;
import com.tuowei.erp.inventory.check.mapper.InventoryStockCheckMapper;
import com.tuowei.erp.inventory.check.model.InventoryStockCheckEntity;
import com.tuowei.erp.inventory.check.model.InventoryStockCheckLineEntity;
import com.tuowei.erp.inventory.check.service.InventoryStockCheckNumberService;
import com.tuowei.erp.inventory.check.service.InventoryStockCheckService;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckCreateRequest;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckLineRequest;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryStockCheckServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9931L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 21, 0)
    );
    private static final Long CHECK_ID = 8001L;
    private static final Long WAREHOUSE_ID = 6001L;
    private static final Long PRODUCT_ID = 7001L;
    private static final LocalDate BIZ_DATE = LocalDate.of(2026, 6, 8);

    @Mock
    private InventoryStockCheckMapper checkMapper;

    @Mock
    private InventoryStockCheckLineMapper lineMapper;

    @Mock
    private InventoryStockCheckNumberService numberService;

    @Mock
    private InventoryPostingService inventoryPostingService;

    @Mock
    private InventoryAdjustmentService adjustmentService;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private WarehouseMapper warehouseMapper;

    @Mock
    private ProductValidator productValidator;

    @Mock
    private AccountPeriodGuard accountPeriodGuard;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryStockCheckLineEntity.class);
        initTableInfo(WarehouseEntity.class);
        initTableInfo(ProductEntity.class);
    }

    @Test
    void createRejectsWarehouseFromDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(activeWarehouse(9999L));

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仓库不存在或已停用");
    }

    @Test
    void createRejectsProductFromDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(activeWarehouse(AUDIT.accountBookId()));
        when(productValidator.requireProducts(any(), any(), any())).thenThrow(new IllegalArgumentException("商品不存在或已停用"));

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在或已停用");
    }

    @Test
    void createRejectsNullRequest() {
        assertThatThrownBy(() -> service().create(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存盘点请求不能为空");
    }

    @Test
    void createRejectsNullLines() {
        assertThatThrownBy(() -> service().create(new InventoryStockCheckCreateRequest(
                WAREHOUSE_ID,
                BIZ_DATE,
                "null lines",
                null
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存盘点明细不能为空");
    }

    @Test
    void createRejectsEmptyLines() {
        assertThatThrownBy(() -> service().create(new InventoryStockCheckCreateRequest(
                WAREHOUSE_ID,
                BIZ_DATE,
                "empty lines",
                List.of()
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存盘点明细不能为空");
    }

    @Test
    void createRejectsNullLine() {
        assertThatThrownBy(() -> service().create(new InventoryStockCheckCreateRequest(
                WAREHOUSE_ID,
                BIZ_DATE,
                "null line",
                Collections.singletonList(null)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存盘点明细不能为空");
    }

    @Test
    void getByIdRejectsDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(checkMapper.selectById(CHECK_ID)).thenReturn(stockCheck(9999L));

        assertThatThrownBy(() -> service().getById(CHECK_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存盘点单不存在");
    }

    @Test
    void getByIdScopesLineQueryByCompanyAndAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(checkMapper.selectById(CHECK_ID)).thenReturn(stockCheck(AUDIT.accountBookId()));
        when(lineMapper.selectList(any())).thenReturn(List.of());

        service().getById(CHECK_ID);

        @SuppressWarnings({"unchecked", "rawtypes"})
        org.mockito.ArgumentCaptor<LambdaQueryWrapper<InventoryStockCheckLineEntity>> wrapperCaptor =
                org.mockito.ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(lineMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("check_id")
                .contains("company_id")
                .contains("account_book_id");
    }

    @Test
    void postAdjustmentRejectsDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(checkMapper.selectById(CHECK_ID)).thenReturn(stockCheck(9999L));

        assertThatThrownBy(() -> service().postAdjustment(CHECK_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存盘点单不存在");
    }

    private InventoryStockCheckService service() {
        return new InventoryStockCheckService(
                checkMapper,
                lineMapper,
                numberService,
                inventoryPostingService,
                adjustmentService,
                auditMetadataFactory,
                warehouseMapper,
                productValidator,
                accountPeriodGuard
        );
    }

    private InventoryStockCheckCreateRequest createRequest() {
        return new InventoryStockCheckCreateRequest(
                WAREHOUSE_ID,
                BIZ_DATE,
                "tenant boundary",
                List.of(new InventoryStockCheckLineRequest(
                        PRODUCT_ID,
                        new BigDecimal("1.0000"),
                        new BigDecimal("10.0000"),
                        "tenant boundary"
                ))
        );
    }

    private InventoryStockCheckEntity stockCheck(Long accountBookId) {
        InventoryStockCheckEntity check = new InventoryStockCheckEntity();
        check.setId(CHECK_ID);
        check.setCompanyId(AUDIT.companyId());
        check.setAccountBookId(accountBookId);
        check.setCheckNo("CHK-20260608-001");
        check.setWarehouseId(WAREHOUSE_ID);
        check.setCheckDate(BIZ_DATE);
        check.setStatus("COUNTED");
        check.setDeletedFlag(0);
        return check;
    }

    private WarehouseEntity activeWarehouse(Long accountBookId) {
        WarehouseEntity warehouse = new WarehouseEntity();
        warehouse.setId(WAREHOUSE_ID);
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

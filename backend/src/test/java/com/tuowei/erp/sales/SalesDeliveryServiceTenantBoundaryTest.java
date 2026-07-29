package com.tuowei.erp.sales;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationMapper;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEntity;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.qc.inspection.service.QcInspectionGate;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryNumberService;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryQueryService;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryService;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryCreateRequest;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryLineRequest;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryPageQuery;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesDeliveryServiceTenantBoundaryTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9501L,
            101L,
            202L,
            11L,
            12L,
            "sales_delivery_scope_user",
            "销售出库用户"
    );
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 8, 15, 0);

    @Mock
    private SalesDeliveryMapper salesDeliveryMapper;

    @Mock
    private SalesDeliveryLineMapper salesDeliveryLineMapper;

    @Mock
    private SalesOrderMapper salesOrderMapper;

    @Mock
    private SalesOrderLineMapper salesOrderLineMapper;

    @Mock
    private WarehouseMapper warehouseMapper;

    @Mock
    private InventoryReservationMapper inventoryReservationMapper;

    @Mock
    private InventoryPostingService inventoryPostingService;

    @Mock
    private InventorySerialNumberService inventorySerialNumberService;

    @Mock
    private SalesDeliveryNumberService salesDeliveryNumberService;

    @Mock
    private SalesDeliveryQueryService salesDeliveryQueryService;

    @Mock
    private FinancePostingService financePostingService;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private AccountPeriodGuard accountPeriodGuard;

    @Mock
    private ProductValidator productValidator;

    @Mock
    private QcInspectionGate qcInspectionGate;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(SalesDeliveryEntity.class);
        initTableInfo(SalesDeliveryLineEntity.class);
        initTableInfo(InventoryReservationEntity.class);
        initTableInfo(SalesOrderLineEntity.class);
    }

    @Test
    void getByIdDelegatesToQueryService() {
        service().getById(7001L);

        verify(salesDeliveryQueryService).getById(7001L);
    }

    @Test
    void listDelegatesTheOriginalQueryToQueryService() {
        SalesDeliveryPageQuery query = new SalesDeliveryPageQuery();

        service().list(query);

        verify(salesDeliveryQueryService).list(query);
    }

    @Test
    void createScopesReservationAndDraftDeliveryChecksByCompanyAndAccountBook() {
        when(auditMetadataFactory.current()).thenReturn(new AuditMetadata(
                CURRENT_USER.userId(),
                CURRENT_USER.companyId(),
                CURRENT_USER.accountBookId(),
                NOW
        ));
        when(salesOrderMapper.selectById(7001L)).thenReturn(order());
        when(warehouseMapper.selectById(3001L)).thenReturn(warehouse());
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine()));
        when(salesDeliveryMapper.selectList(any())).thenReturn(List.of());
        when(inventoryReservationMapper.selectList(any())).thenReturn(List.of(reservation()));
        when(salesDeliveryNumberService.nextDeliveryNo(LocalDate.of(2026, 6, 8))).thenReturn("SD-20260608-001");

        service().create(new SalesDeliveryCreateRequest(
                7001L,
                3001L,
                LocalDate.of(2026, 6, 8),
                "scope test", null, null, null, List.of(new SalesDeliveryLineRequest(
                        8001L,
                        new BigDecimal("2.0000"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        "line"
                ))
        ));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesDeliveryEntity>> draftDeliveryWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesDeliveryMapper).selectList(draftDeliveryWrapperCaptor.capture());
        assertTenantScoped(draftDeliveryWrapperCaptor.getValue());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryReservationEntity>> reservationWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryReservationMapper).selectList(reservationWrapperCaptor.capture());
        assertTenantScoped(reservationWrapperCaptor.getValue());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesOrderLineEntity>> orderLineWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesOrderLineMapper).selectList(orderLineWrapperCaptor.capture());
        assertTenantScoped(orderLineWrapperCaptor.getValue());
        verify(salesDeliveryQueryService).assertCanView(any(SalesOrderEntity.class));
        verify(salesDeliveryQueryService).assertCanView(any(SalesDeliveryEntity.class));
    }

    @Test
    void createRejectsWarehouseFromDifferentAccountBookWithinSameCompany() {
        when(auditMetadataFactory.current()).thenReturn(new AuditMetadata(
                CURRENT_USER.userId(),
                CURRENT_USER.companyId(),
                CURRENT_USER.accountBookId(),
                NOW
        ));
        when(salesOrderMapper.selectById(7001L)).thenReturn(order());
        when(warehouseMapper.selectById(3001L)).thenReturn(warehouse(9999L));

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("仓库不存在或已停用");
    }

    @Test
    void createRejectsProductFromDifferentAccountBookWithinSameCompany() {
        stubCreateContext();
        when(warehouseMapper.selectById(3001L)).thenReturn(warehouse(CURRENT_USER.accountBookId()));
        when(productValidator.requireProducts(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("商品不存在或已停用"));

        assertThatThrownBy(() -> service().create(createRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在或已停用");
    }

    private void stubCreateContext() {
        when(auditMetadataFactory.current()).thenReturn(new AuditMetadata(
                CURRENT_USER.userId(),
                CURRENT_USER.companyId(),
                CURRENT_USER.accountBookId(),
                NOW
        ));
        when(salesOrderMapper.selectById(7001L)).thenReturn(order());
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine()));
        when(salesDeliveryMapper.selectList(any())).thenReturn(List.of());
        when(inventoryReservationMapper.selectList(any())).thenReturn(List.of(reservation()));
        when(salesDeliveryNumberService.nextDeliveryNo(LocalDate.of(2026, 6, 8))).thenReturn("SD-20260608-001");
    }

    private SalesDeliveryCreateRequest createRequest() {
        return new SalesDeliveryCreateRequest(
                7001L,
                3001L,
                LocalDate.of(2026, 6, 8),
                "scope test", null, null, null, List.of(new SalesDeliveryLineRequest(
                        8001L,
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

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    private SalesOrderEntity order() {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setId(7001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setOrderNo("SO-7001");
        entity.setWarehouseId(3001L);
        entity.setStatus("APPROVED");
        entity.setApprovalStatus("APPROVED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesDeliveryEntity delivery() {
        SalesDeliveryEntity entity = new SalesDeliveryEntity();
        entity.setId(7001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setDeliveryNo("SD-7001");
        entity.setOrderId(7001L);
        entity.setWarehouseId(3001L);
        entity.setDeliveryDate(LocalDate.of(2026, 6, 8));
        entity.setStatus("DRAFT");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesOrderLineEntity orderLine() {
        SalesOrderLineEntity entity = new SalesOrderLineEntity();
        entity.setId(8001L);
        entity.setOrderId(7001L);
        entity.setLineNo(1);
        entity.setProductId(4001L);
        entity.setQty(new BigDecimal("5.0000"));
        entity.setPrice(new BigDecimal("10.00"));
        entity.setTaxRate(new BigDecimal("0.0000"));
        entity.setAmount(new BigDecimal("50.00"));
        entity.setTaxAmount(new BigDecimal("0.00"));
        entity.setDeliveredQty(new BigDecimal("0.0000"));
        return entity;
    }

    private SalesDeliveryLineEntity deliveryLine() {
        SalesDeliveryLineEntity entity = new SalesDeliveryLineEntity();
        entity.setId(8101L);
        entity.setDeliveryId(7001L);
        entity.setLineNo(1);
        entity.setOrderLineId(8001L);
        entity.setProductId(4001L);
        entity.setQty(new BigDecimal("2.0000"));
        entity.setPrice(new BigDecimal("10.00"));
        entity.setTaxRate(new BigDecimal("0.0000"));
        entity.setAmount(new BigDecimal("20.00"));
        entity.setTaxAmount(BigDecimal.ZERO);
        entity.setReturnedQty(BigDecimal.ZERO);
        return entity;
    }

    private WarehouseEntity warehouse() {
        return warehouse(CURRENT_USER.accountBookId());
    }

    private WarehouseEntity warehouse(Long accountBookId) {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(3001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private InventoryReservationEntity reservation() {
        InventoryReservationEntity entity = new InventoryReservationEntity();
        entity.setId(9001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setWarehouseId(3001L);
        entity.setProductId(4001L);
        entity.setSourceType("SALES_ORDER");
        entity.setSourceId(7001L);
        entity.setSourceLineId(8001L);
        entity.setRemainingQty(new BigDecimal("5.0000"));
        entity.setStatus("ACTIVE");
        return entity;
    }

    private SalesDeliveryService service() {
        return new SalesDeliveryService(
                salesDeliveryMapper,
                salesDeliveryLineMapper,
                salesOrderMapper,
                salesOrderLineMapper,
                warehouseMapper,
                inventoryReservationMapper,
                inventoryPostingService,
                inventorySerialNumberService,
                salesDeliveryNumberService,
                salesDeliveryQueryService,
                financePostingService,
                auditMetadataFactory,
                accountPeriodGuard,
                productValidator,
                qcInspectionGate
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

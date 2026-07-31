package com.tuowei.erp.sales;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationMapper;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEntity;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.qc.inspection.service.QcInspectionGate;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryPostingService;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryQueryService;
import com.tuowei.erp.sales.delivery.web.SalesDeliveryResponse;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesDeliveryPostingServiceTest {

    private static final Long COMPANY_ID = 101L;
    private static final Long ACCOUNT_BOOK_ID = 202L;
    private static final Long USER_ID = 9501L;
    private static final Long DELIVERY_ID = 7001L;
    private static final Long ORDER_ID = 7101L;
    private static final Long WAREHOUSE_ID = 3001L;
    private static final LocalDate DELIVERY_DATE = LocalDate.of(2026, 6, 8);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 8, 15, 0);
    private static final AuditMetadata AUDIT = new AuditMetadata(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID, NOW);

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
        initTableInfo(SalesDeliveryLineEntity.class);
        initTableInfo(SalesOrderLineEntity.class);
        initTableInfo(InventoryReservationEntity.class);
    }

    @Test
    void postCoordinatesTwoLinesUsingActualCostAndPreservesLotMetadata() {
        SalesDeliveryEntity delivery = delivery();
        SalesOrderEntity order = order();
        SalesDeliveryLineEntity autoFefoLine = deliveryLine(
                8101L,
                8201L,
                4001L,
                "2.0000",
                "20.00",
                null,
                null,
                null,
                3101L,
                "SN-A1\nSN-A2"
        );
        SalesDeliveryLineEntity explicitLotLine = deliveryLine(
                8102L,
                8202L,
                4002L,
                "3.0000",
                "45.00",
                "LOT-B",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 12, 31),
                3102L,
                "SN-B1\nSN-B2\nSN-B3"
        );
        SalesOrderLineEntity firstOrderLine = orderLine(8201L, 4001L, "2.0000");
        SalesOrderLineEntity secondOrderLine = orderLine(8202L, 4002L, "3.0000");
        List<SalesDeliveryLineEntity> deliveryLines = List.of(autoFefoLine, explicitLotLine);
        List<SalesOrderLineEntity> orderLines = List.of(firstOrderLine, secondOrderLine);
        stubPreflight(delivery, order, deliveryLines, orderLines);
        when(inventoryPostingService.getQtyOnHand(WAREHOUSE_ID, 4001L, COMPANY_ID, ACCOUNT_BOOK_ID))
                .thenReturn(new BigDecimal("10.0000"));
        when(inventoryPostingService.getQtyOnHand(WAREHOUSE_ID, 4002L, COMPANY_ID, ACCOUNT_BOOK_ID))
                .thenReturn(new BigDecimal("10.0000"));
        when(inventoryReservationMapper.selectList(any()))
                .thenReturn(List.of(reservation(9001L, 8201L, "2.0000")))
                .thenReturn(List.of(reservation(9002L, 8202L, "3.0000")));
        when(salesDeliveryMapper.updateById(any(SalesDeliveryEntity.class))).thenReturn(1);
        when(salesOrderLineMapper.updateById(any(SalesOrderLineEntity.class))).thenReturn(1);
        when(salesOrderMapper.updateById(any(SalesOrderEntity.class))).thenReturn(1);
        when(inventoryPostingService.postOutbound(any(InventoryPostingCommand.class), same(AUDIT), any()))
                .thenReturn(new BigDecimal("12.34"))
                .thenReturn(new BigDecimal("23.45"));
        SalesDeliveryResponse expected = response();
        when(salesDeliveryQueryService.getById(DELIVERY_ID)).thenReturn(expected);

        SalesDeliveryResponse result = service().post(DELIVERY_ID);

        assertThat(result).isSameAs(expected);
        assertThat(delivery.getStatus()).isEqualTo("POSTED");
        assertThat(firstOrderLine.getDeliveredQty()).isEqualByComparingTo("2.0000");
        assertThat(secondOrderLine.getDeliveredQty()).isEqualByComparingTo("3.0000");
        assertThat(order.getDeliveryStatus()).isEqualTo("FULL_DELIVERED");

        ArgumentCaptor<InventoryPostingCommand> commandCaptor =
                ArgumentCaptor.forClass(InventoryPostingCommand.class);
        verify(inventoryPostingService, times(2)).postOutbound(
                commandCaptor.capture(),
                same(AUDIT),
                eq("库存不足，不能执行销售出库")
        );
        assertThat(commandCaptor.getAllValues()).hasSize(2);
        InventoryPostingCommand autoCommand = commandCaptor.getAllValues().get(0);
        assertThat(autoCommand.warehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(autoCommand.productId()).isEqualTo(4001L);
        assertThat(autoCommand.bizType()).isEqualTo("SALES_DELIVERY");
        assertThat(autoCommand.bizNo()).isEqualTo("SD-7001");
        assertThat(autoCommand.bizLineId()).isEqualTo(8101L);
        assertThat(autoCommand.qty()).isEqualByComparingTo("2.0000");
        assertThat(autoCommand.amount()).isEqualByComparingTo("20.00");
        assertThat(autoCommand.bizDate()).isEqualTo(DELIVERY_DATE);
        assertThat(autoCommand.lotNo()).isNull();
        assertThat(autoCommand.productionDate()).isNull();
        assertThat(autoCommand.expiryDate()).isNull();
        assertThat(autoCommand.locationId()).isEqualTo(3101L);

        InventoryPostingCommand explicitCommand = commandCaptor.getAllValues().get(1);
        assertThat(explicitCommand.productId()).isEqualTo(4002L);
        assertThat(explicitCommand.bizLineId()).isEqualTo(8102L);
        assertThat(explicitCommand.lotNo()).isEqualTo("LOT-B");
        assertThat(explicitCommand.productionDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(explicitCommand.expiryDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(explicitCommand.locationId()).isEqualTo(3102L);

        verify(inventoryPostingService).releaseReservation("SALES_ORDER", 8201L, autoFefoLine.getQty(), AUDIT);
        verify(inventoryPostingService).releaseReservation("SALES_ORDER", 8202L, explicitLotLine.getQty(), AUDIT);
        verify(inventorySerialNumberService).issueOutboundSerials(
                4001L, "SN-A1\nSN-A2", "SALES_DELIVERY", "SD-7001", autoFefoLine.getQty(), AUDIT
        );
        verify(inventorySerialNumberService).issueOutboundSerials(
                4002L, "SN-B1\nSN-B2\nSN-B3", "SALES_DELIVERY", "SD-7001", explicitLotLine.getQty(), AUDIT
        );

        ArgumentCaptor<BigDecimal> costCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(financePostingService).recordSalesDelivery(same(delivery), same(order), costCaptor.capture(), same(AUDIT));
        assertThat(costCaptor.getValue()).isEqualByComparingTo("35.79");
        verify(accountPeriodGuard).requireOpen(DELIVERY_DATE, "销售出库过账");
        verify(salesDeliveryQueryService).assertCanView(delivery);
        verify(salesDeliveryQueryService).assertCanView(order);
        verify(salesDeliveryQueryService).getById(DELIVERY_ID);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesDeliveryLineEntity>> deliveryLineWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesDeliveryLineMapper).selectList(deliveryLineWrapperCaptor.capture());
        assertTenantScoped(deliveryLineWrapperCaptor.getValue());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesOrderLineEntity>> orderLineWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesOrderLineMapper, times(2)).selectList(orderLineWrapperCaptor.capture());
        assertThat(orderLineWrapperCaptor.getAllValues()).allSatisfy(this::assertTenantScoped);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryReservationEntity>> reservationWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryReservationMapper, times(2)).selectList(reservationWrapperCaptor.capture());
        assertThat(reservationWrapperCaptor.getAllValues()).allSatisfy(this::assertTenantScoped);
    }

    @Test
    void postRejectsInsufficientStockWithoutWrites() {
        SalesDeliveryEntity delivery = delivery();
        SalesOrderEntity order = order();
        SalesDeliveryLineEntity line = deliveryLine(
                8101L, 8201L, 4001L, "2.0000", "20.00", null, null, null, 3101L, null
        );
        stubPreflight(delivery, order, List.of(line), List.of(orderLine(8201L, 4001L, "2.0000")));
        when(inventoryPostingService.getQtyOnHand(WAREHOUSE_ID, 4001L, COMPANY_ID, ACCOUNT_BOOK_ID))
                .thenReturn(new BigDecimal("1.0000"));

        assertThatThrownBy(() -> service().post(DELIVERY_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存不足，不能执行销售出库");

        verify(qcInspectionGate, never()).assertDeliveryInspected(any(), any(), any());
        assertNoPostingWrites();
    }

    @Test
    void postRejectsInsufficientReservationWithoutWrites() {
        SalesDeliveryEntity delivery = delivery();
        SalesOrderEntity order = order();
        SalesDeliveryLineEntity line = deliveryLine(
                8101L, 8201L, 4001L, "2.0000", "20.00", null, null, null, 3101L, null
        );
        stubPreflight(delivery, order, List.of(line), List.of(orderLine(8201L, 4001L, "2.0000")));
        when(inventoryPostingService.getQtyOnHand(WAREHOUSE_ID, 4001L, COMPANY_ID, ACCOUNT_BOOK_ID))
                .thenReturn(new BigDecimal("10.0000"));
        when(inventoryReservationMapper.selectList(any()))
                .thenReturn(List.of(reservation(9001L, 8201L, "1.0000")));

        assertThatThrownBy(() -> service().post(DELIVERY_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("销售订单预占数量不足，不能执行销售出库");

        verify(qcInspectionGate, never()).assertDeliveryInspected(any(), any(), any());
        assertNoPostingWrites();
    }

    @Test
    void postStopsWhenQcGateRejectsWithoutWrites() {
        SalesDeliveryEntity delivery = delivery();
        SalesOrderEntity order = order();
        SalesDeliveryLineEntity line = deliveryLine(
                8101L, 8201L, 4001L, "2.0000", "20.00", null, null, null, 3101L, null
        );
        List<SalesDeliveryLineEntity> lines = List.of(line);
        stubPreflight(delivery, order, lines, List.of(orderLine(8201L, 4001L, "2.0000")));
        when(inventoryPostingService.getQtyOnHand(WAREHOUSE_ID, 4001L, COMPANY_ID, ACCOUNT_BOOK_ID))
                .thenReturn(new BigDecimal("10.0000"));
        when(inventoryReservationMapper.selectList(any()))
                .thenReturn(List.of(reservation(9001L, 8201L, "2.0000")));
        doThrow(new IllegalArgumentException("出库质检合格数量与出库数量不一致，不能过账"))
                .when(qcInspectionGate)
                .assertDeliveryInspected(delivery, lines, AUDIT);

        assertThatThrownBy(() -> service().post(DELIVERY_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("出库质检合格数量与出库数量不一致，不能过账");

        verify(qcInspectionGate).assertDeliveryInspected(delivery, lines, AUDIT);
        assertNoPostingWrites();
    }

    private void stubPreflight(
            SalesDeliveryEntity delivery,
            SalesOrderEntity order,
            List<SalesDeliveryLineEntity> deliveryLines,
            List<SalesOrderLineEntity> orderLines
    ) {
        when(salesDeliveryMapper.selectById(DELIVERY_ID)).thenReturn(delivery);
        when(salesOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse());
        when(salesDeliveryLineMapper.selectList(any())).thenReturn(deliveryLines);
        when(salesOrderLineMapper.selectList(any())).thenReturn(orderLines);
    }

    private void assertNoPostingWrites() {
        verify(salesDeliveryMapper, never()).updateById(any(SalesDeliveryEntity.class));
        verify(salesOrderLineMapper, never()).updateById(any(SalesOrderLineEntity.class));
        verify(salesOrderMapper, never()).updateById(any(SalesOrderEntity.class));
        verify(inventoryPostingService, never()).releaseReservation(any(), any(), any(), any());
        verify(inventoryPostingService, never()).postOutbound(any(), any(), any());
        verify(inventorySerialNumberService, never()).issueOutboundSerials(any(), any(), any(), any(), any(), any());
        verify(financePostingService, never()).recordSalesDelivery(any(), any(), any(), any());
        verify(salesDeliveryQueryService, never()).getById(any());
    }

    private SalesDeliveryPostingService service() {
        return new SalesDeliveryPostingService(
                salesDeliveryMapper,
                salesDeliveryLineMapper,
                salesOrderMapper,
                salesOrderLineMapper,
                warehouseMapper,
                inventoryReservationMapper,
                inventoryPostingService,
                inventorySerialNumberService,
                salesDeliveryQueryService,
                financePostingService,
                auditMetadataFactory,
                accountPeriodGuard,
                productValidator,
                qcInspectionGate
        );
    }

    private SalesDeliveryEntity delivery() {
        SalesDeliveryEntity entity = new SalesDeliveryEntity();
        entity.setId(DELIVERY_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setDeliveryNo("SD-7001");
        entity.setOrderId(ORDER_ID);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setDeliveryDate(DELIVERY_DATE);
        entity.setStatus("DRAFT");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesOrderEntity order() {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setId(ORDER_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setOrderNo("SO-7101");
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setStatus("APPROVED");
        entity.setApprovalStatus("APPROVED");
        entity.setDeliveryStatus("NOT_DELIVERED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesDeliveryLineEntity deliveryLine(
            Long id,
            Long orderLineId,
            Long productId,
            String qty,
            String amount,
            String lotNo,
            LocalDate productionDate,
            LocalDate expiryDate,
            Long locationId,
            String serialNos
    ) {
        SalesDeliveryLineEntity entity = new SalesDeliveryLineEntity();
        entity.setId(id);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setDeliveryId(DELIVERY_ID);
        entity.setLineNo(id.equals(8101L) ? 1 : 2);
        entity.setOrderLineId(orderLineId);
        entity.setProductId(productId);
        entity.setQty(new BigDecimal(qty));
        entity.setAmount(new BigDecimal(amount));
        entity.setLotNo(lotNo);
        entity.setProductionDate(productionDate);
        entity.setExpiryDate(expiryDate);
        entity.setLocationId(locationId);
        entity.setSerialNos(serialNos);
        entity.setRemark("delivery line " + id);
        return entity;
    }

    private SalesOrderLineEntity orderLine(Long id, Long productId, String qty) {
        SalesOrderLineEntity entity = new SalesOrderLineEntity();
        entity.setId(id);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setOrderId(ORDER_ID);
        entity.setLineNo(id.equals(8201L) ? 1 : 2);
        entity.setProductId(productId);
        entity.setQty(new BigDecimal(qty));
        entity.setDeliveredQty(BigDecimal.ZERO);
        return entity;
    }

    private InventoryReservationEntity reservation(Long id, Long orderLineId, String remainingQty) {
        InventoryReservationEntity entity = new InventoryReservationEntity();
        entity.setId(id);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setSourceType("SALES_ORDER");
        entity.setSourceId(ORDER_ID);
        entity.setSourceLineId(orderLineId);
        entity.setRemainingQty(new BigDecimal(remainingQty));
        entity.setStatus("ACTIVE");
        return entity;
    }

    private WarehouseEntity warehouse() {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(WAREHOUSE_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesDeliveryResponse response() {
        return new SalesDeliveryResponse(
                DELIVERY_ID,
                "SD-7001",
                ORDER_ID,
                WAREHOUSE_ID,
                DELIVERY_DATE,
                "POSTED",
                new BigDecimal("5.0000"),
                new BigDecimal("65.00"),
                BigDecimal.ZERO,
                null,
                null,
                null,
                "PENDING_SHIP",
                List.of()
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

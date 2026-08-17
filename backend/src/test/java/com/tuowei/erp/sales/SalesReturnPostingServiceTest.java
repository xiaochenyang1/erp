package com.tuowei.erp.sales;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnLineMapper;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnMapper;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import com.tuowei.erp.sales.returnorder.model.SalesReturnLineEntity;
import com.tuowei.erp.sales.returnorder.service.SalesReturnPostingService;
import com.tuowei.erp.sales.returnorder.service.SalesReturnQueryService;
import com.tuowei.erp.sales.returnorder.web.SalesReturnResponse;
import com.tuowei.erp.system.attachment.service.AttachmentService;
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
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesReturnPostingServiceTest {

    private static final Long COMPANY_ID = 101L;
    private static final Long ACCOUNT_BOOK_ID = 202L;
    private static final Long USER_ID = 9501L;
    private static final Long RETURN_ID = 9001L;
    private static final Long DELIVERY_ID = 7001L;
    private static final Long ORDER_ID = 6001L;
    private static final Long WAREHOUSE_ID = 3001L;
    private static final LocalDate RETURN_DATE = LocalDate.of(2026, 6, 8);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 8, 15, 0);
    private static final AuditMetadata AUDIT = new AuditMetadata(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID, NOW);

    @Mock
    private SalesReturnMapper salesReturnMapper;

    @Mock
    private SalesReturnLineMapper salesReturnLineMapper;

    @Mock
    private SalesDeliveryMapper salesDeliveryMapper;

    @Mock
    private SalesDeliveryLineMapper salesDeliveryLineMapper;

    @Mock
    private SalesOrderMapper salesOrderMapper;

    @Mock
    private SalesOrderLineMapper salesOrderLineMapper;

    @Mock
    private InventoryTransactionMapper inventoryTransactionMapper;

    @Mock
    private InventoryPostingService inventoryPostingService;

    @Mock
    private InventorySerialNumberService inventorySerialNumberService;

    @Mock
    private FinancePostingService financePostingService;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private SalesReturnQueryService salesReturnQueryService;

    @Mock
    private AccountPeriodGuard accountPeriodGuard;

    @Mock
    private AttachmentService attachmentService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(SalesReturnLineEntity.class);
        initTableInfo(SalesDeliveryLineEntity.class);
        initTableInfo(SalesOrderLineEntity.class);
        initTableInfo(InventoryTransactionEntity.class);
    }

    @Test
    void postCoordinatesTwoLinesUsingOriginalCostsAndPreservesLotSerialAndLocationMetadata() {
        SalesReturnEntity salesReturn = salesReturn();
        SalesDeliveryEntity delivery = delivery();
        SalesOrderEntity order = order();
        SalesReturnLineEntity explicitLotLine = returnLine(
                9101L,
                7101L,
                6101L,
                4001L,
                "2.0000",
                "40.00",
                "  LOT-A  ",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 12, 31),
                3101L,
                "SN-A1\nSN-A2"
        );
        SalesReturnLineEntity inferredLotLine = returnLine(
                9102L,
                7102L,
                6102L,
                4002L,
                "3.0000",
                "90.00",
                null,
                null,
                null,
                3102L,
                "SN-B1\nSN-B2\nSN-B3"
        );
        SalesDeliveryLineEntity firstDeliveryLine = deliveryLine(7101L, 6101L, 4001L, "5.0000", "1.0000");
        SalesDeliveryLineEntity secondDeliveryLine = deliveryLine(7102L, 6102L, 4002L, "3.0000", "0.0000");
        SalesOrderLineEntity firstOrderLine = orderLine(6101L, 4001L, "5.0000", "5.0000");
        SalesOrderLineEntity secondOrderLine = orderLine(6102L, 4002L, "3.0000", "3.0000");
        List<SalesReturnLineEntity> returnLines = List.of(explicitLotLine, inferredLotLine);
        List<SalesDeliveryLineEntity> deliveryLines = List.of(firstDeliveryLine, secondDeliveryLine);
        List<SalesOrderLineEntity> orderLines = List.of(firstOrderLine, secondOrderLine);
        stubPreflight(salesReturn, delivery, order, returnLines, deliveryLines, orderLines);

        InventoryTransactionEntity firstCostPart = deliveryTransaction(
                7201L, 7101L, 4001L, "LOT-A", "1.0000", "6.00",
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 11, 30)
        );
        InventoryTransactionEntity secondCostPart = deliveryTransaction(
                7202L, 7101L, 4001L, "LOT-A", "3.0000", "18.00",
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 11, 30)
        );
        LocalDate inferredProductionDate = LocalDate.of(2026, 5, 5);
        LocalDate inferredExpiryDate = LocalDate.of(2027, 5, 5);
        InventoryTransactionEntity inferredLotTransaction = deliveryTransaction(
                7203L, 7102L, 4002L, "LOT-B", "3.0000", "21.45",
                inferredProductionDate, inferredExpiryDate
        );
        when(inventoryTransactionMapper.selectList(any()))
                .thenReturn(List.of(firstCostPart, secondCostPart))
                .thenReturn(List.of(inferredLotTransaction))
                .thenReturn(List.of(inferredLotTransaction));
        when(salesReturnMapper.updateById(any(SalesReturnEntity.class))).thenReturn(1);
        when(salesDeliveryLineMapper.updateById(any(SalesDeliveryLineEntity.class))).thenReturn(1);
        when(salesOrderLineMapper.updateById(any(SalesOrderLineEntity.class))).thenReturn(1);
        when(salesOrderMapper.updateById(any(SalesOrderEntity.class))).thenReturn(1);
        SalesReturnResponse expected = response();
        when(salesReturnQueryService.getById(RETURN_ID)).thenReturn(expected);

        SalesReturnResponse result = service().post(RETURN_ID);

        assertThat(result).isSameAs(expected);
        assertThat(salesReturn.getStatus()).isEqualTo("POSTED");
        assertThat(salesReturn.getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(salesReturn.getUpdatedTime()).isEqualTo(NOW);
        assertThat(firstDeliveryLine.getReturnedQty()).isEqualByComparingTo("3.0000");
        assertThat(secondDeliveryLine.getReturnedQty()).isEqualByComparingTo("3.0000");
        assertThat(firstOrderLine.getDeliveredQty()).isEqualByComparingTo("3.0000");
        assertThat(secondOrderLine.getDeliveredQty()).isEqualByComparingTo("0.0000");
        assertThat(order.getDeliveryStatus()).isEqualTo("PARTIAL_DELIVERED");
        assertThat(explicitLotLine.getLotNo()).isEqualTo("  LOT-A  ");
        assertThat(inferredLotLine.getLotNo()).isEqualTo("LOT-B");
        assertThat(inferredLotLine.getProductionDate()).isEqualTo(inferredProductionDate);
        assertThat(inferredLotLine.getExpiryDate()).isEqualTo(inferredExpiryDate);

        ArgumentCaptor<InventoryPostingCommand> commandCaptor =
                ArgumentCaptor.forClass(InventoryPostingCommand.class);
        verify(inventoryPostingService, times(2)).postInbound(commandCaptor.capture(), same(AUDIT));
        assertThat(commandCaptor.getAllValues()).hasSize(2);
        InventoryPostingCommand explicitCommand = commandCaptor.getAllValues().get(0);
        assertThat(explicitCommand.warehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(explicitCommand.productId()).isEqualTo(4001L);
        assertThat(explicitCommand.bizType()).isEqualTo("SALES_RETURN");
        assertThat(explicitCommand.bizNo()).isEqualTo("SR-9001");
        assertThat(explicitCommand.bizLineId()).isEqualTo(9101L);
        assertThat(explicitCommand.qty()).isEqualByComparingTo("2.0000");
        assertThat(explicitCommand.amount()).isEqualByComparingTo("12.00");
        assertThat(explicitCommand.remark()).isEqualTo("return line 9101");
        assertThat(explicitCommand.bizDate()).isEqualTo(RETURN_DATE);
        assertThat(explicitCommand.lotNo()).isEqualTo("  LOT-A  ");
        assertThat(explicitCommand.productionDate()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(explicitCommand.expiryDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(explicitCommand.locationId()).isEqualTo(3101L);

        InventoryPostingCommand inferredCommand = commandCaptor.getAllValues().get(1);
        assertThat(inferredCommand.warehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(inferredCommand.productId()).isEqualTo(4002L);
        assertThat(inferredCommand.bizType()).isEqualTo("SALES_RETURN");
        assertThat(inferredCommand.bizNo()).isEqualTo("SR-9001");
        assertThat(inferredCommand.bizLineId()).isEqualTo(9102L);
        assertThat(inferredCommand.qty()).isEqualByComparingTo("3.0000");
        assertThat(inferredCommand.amount()).isEqualByComparingTo("21.45");
        assertThat(inferredCommand.remark()).isEqualTo("return line 9102");
        assertThat(inferredCommand.bizDate()).isEqualTo(RETURN_DATE);
        assertThat(inferredCommand.lotNo()).isEqualTo("LOT-B");
        assertThat(inferredCommand.productionDate()).isEqualTo(inferredProductionDate);
        assertThat(inferredCommand.expiryDate()).isEqualTo(inferredExpiryDate);
        assertThat(inferredCommand.locationId()).isEqualTo(3102L);

        verify(inventorySerialNumberService).registerInboundSerials(
                4001L,
                WAREHOUSE_ID,
                3101L,
                "SN-A1\nSN-A2",
                "SALES_RETURN",
                "SR-9001",
                explicitLotLine.getQty(),
                AUDIT
        );
        verify(inventorySerialNumberService).registerInboundSerials(
                4002L,
                WAREHOUSE_ID,
                3102L,
                "SN-B1\nSN-B2\nSN-B3",
                "SALES_RETURN",
                "SR-9001",
                inferredLotLine.getQty(),
                AUDIT
        );
        ArgumentCaptor<BigDecimal> costCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(financePostingService).recordSalesReturn(
                same(salesReturn), same(order), costCaptor.capture(), same(AUDIT)
        );
        assertThat(costCaptor.getValue()).isEqualByComparingTo("33.45");
        verify(accountPeriodGuard).requireOpen(RETURN_DATE, "销售退货过账");
        verify(salesReturnQueryService).assertCanView(salesReturn);
        verify(salesReturnQueryService).assertCanView(delivery);
        verify(salesReturnQueryService).assertCanView(order);
        verify(salesReturnQueryService).getById(RETURN_ID);

        assertScopedQueries();
    }

    @Test
    void postRejectsMissingSecondLineInventoryTransactionBeforeAnyWrite() {
        SalesReturnEntity salesReturn = salesReturn();
        SalesDeliveryEntity delivery = delivery();
        SalesOrderEntity order = order();
        SalesReturnLineEntity firstReturnLine = returnLine(
                9101L, 7101L, 6101L, 4001L, "1.0000", "20.00", "LOT-A",
                null, null, 3101L, null
        );
        SalesReturnLineEntity secondReturnLine = returnLine(
                9102L, 7102L, 6102L, 4002L, "1.0000", "30.00", "LOT-B",
                null, null, 3102L, null
        );
        stubPreflight(
                salesReturn,
                delivery,
                order,
                List.of(firstReturnLine, secondReturnLine),
                List.of(
                        deliveryLine(7101L, 6101L, 4001L, "2.0000", "0.0000"),
                        deliveryLine(7102L, 6102L, 4002L, "2.0000", "0.0000")
                ),
                List.of(
                        orderLine(6101L, 4001L, "2.0000", "2.0000"),
                        orderLine(6102L, 4002L, "2.0000", "2.0000")
                )
        );
        when(inventoryTransactionMapper.selectList(any()))
                .thenReturn(List.of(deliveryTransaction(
                        7201L, 7101L, 4001L, "LOT-A", "2.0000", "12.00", null, null
                )))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service().post(RETURN_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("销售出库库存分录不存在，不能按成本冲回");

        verify(inventoryTransactionMapper, times(2)).selectList(any());
        assertNoPostingWrites();
    }

    @Test
    void postRejectsAmbiguousOriginalLotsBeforeAnyWrite() {
        SalesReturnEntity salesReturn = salesReturn();
        SalesReturnLineEntity returnLine = returnLine(
                9101L, 7101L, 6101L, 4001L, "1.0000", "20.00", null,
                null, null, 3101L, null
        );
        stubPreflight(
                salesReturn,
                delivery(),
                order(),
                List.of(returnLine),
                List.of(deliveryLine(7101L, 6101L, 4001L, "2.0000", "0.0000")),
                List.of(orderLine(6101L, 4001L, "2.0000", "2.0000"))
        );
        when(inventoryTransactionMapper.selectList(any())).thenReturn(List.of(
                deliveryTransaction(7201L, 7101L, 4001L, "LOT-A", "1.0000", "6.00", null, null),
                deliveryTransaction(7202L, 7101L, 4001L, "LOT-B", "1.0000", "7.00", null, null)
        ));

        assertThatThrownBy(() -> service().post(RETURN_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("销售退货必须指定批次号，原销售出库明细已拆分多个批次");

        verify(inventoryTransactionMapper).selectList(any());
        assertNoPostingWrites();
    }

    @Test
    void postRejectsAccumulatedReturnQtyBeforeAnyWrite() {
        SalesReturnEntity salesReturn = salesReturn();
        SalesReturnLineEntity firstReturnLine = returnLine(
                9101L, 7101L, 6101L, 4001L, "2.0000", "20.00", "LOT-A",
                null, null, 3101L, null
        );
        SalesReturnLineEntity secondReturnLine = returnLine(
                9102L, 7101L, 6101L, 4001L, "2.0000", "20.00", "LOT-A",
                null, null, 3101L, null
        );
        stubPreflight(
                salesReturn,
                delivery(),
                order(),
                List.of(firstReturnLine, secondReturnLine),
                List.of(deliveryLine(7101L, 6101L, 4001L, "3.0000", "0.0000")),
                List.of(orderLine(6101L, 4001L, "10.0000", "10.0000"))
        );
        when(inventoryTransactionMapper.selectList(any())).thenReturn(List.of(
                deliveryTransaction(7201L, 7101L, 4001L, "LOT-A", "3.0000", "30.00", null, null)
        ));

        assertThatThrownBy(() -> service().post(RETURN_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("退货数量超过销售出库明细剩余可退数量");

        verify(inventoryTransactionMapper).selectList(any());
        assertNoPostingWrites();
    }

    @Test
    void postRejectsAccumulatedDeliveredQtyBeforeAnyWrite() {
        SalesReturnEntity salesReturn = salesReturn();
        SalesReturnLineEntity firstReturnLine = returnLine(
                9101L, 7101L, 6101L, 4001L, "2.0000", "20.00", "LOT-A",
                null, null, 3101L, null
        );
        SalesReturnLineEntity secondReturnLine = returnLine(
                9102L, 7101L, 6101L, 4001L, "2.0000", "20.00", "LOT-A",
                null, null, 3101L, null
        );
        stubPreflight(
                salesReturn,
                delivery(),
                order(),
                List.of(firstReturnLine, secondReturnLine),
                List.of(deliveryLine(7101L, 6101L, 4001L, "10.0000", "0.0000")),
                List.of(orderLine(6101L, 4001L, "10.0000", "3.0000"))
        );
        when(inventoryTransactionMapper.selectList(any())).thenReturn(List.of(
                deliveryTransaction(7201L, 7101L, 4001L, "LOT-A", "10.0000", "100.00", null, null)
        ));

        assertThatThrownBy(() -> service().post(RETURN_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("退货数量超过销售订单已出库数量");

        verify(inventoryTransactionMapper).selectList(any());
        assertNoPostingWrites();
    }

    @Test
    void postStopsAfterHeaderOptimisticLockFailureWithoutDownstreamWrites() {
        SalesReturnEntity salesReturn = salesReturn();
        SalesReturnLineEntity returnLine = returnLine(
                9101L, 7101L, 6101L, 4001L, "1.0000", "20.00", "LOT-A",
                null, null, 3101L, null
        );
        stubPreflight(
                salesReturn,
                delivery(),
                order(),
                List.of(returnLine),
                List.of(deliveryLine(7101L, 6101L, 4001L, "2.0000", "0.0000")),
                List.of(orderLine(6101L, 4001L, "2.0000", "2.0000"))
        );
        when(inventoryTransactionMapper.selectList(any())).thenReturn(List.of(
                deliveryTransaction(7201L, 7101L, 4001L, "LOT-A", "2.0000", "12.00", null, null)
        ));
        when(salesReturnMapper.updateById(salesReturn)).thenReturn(0);

        assertThatThrownBy(() -> service().post(RETURN_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("销售退货单已被其他操作修改，请刷新后重试");

        verify(salesReturnMapper).updateById(salesReturn);
        assertNoDownstreamWrites();
    }

    private void stubPreflight(
            SalesReturnEntity salesReturn,
            SalesDeliveryEntity delivery,
            SalesOrderEntity order,
            List<SalesReturnLineEntity> returnLines,
            List<SalesDeliveryLineEntity> deliveryLines,
            List<SalesOrderLineEntity> orderLines
    ) {
        when(salesReturnMapper.selectById(RETURN_ID)).thenReturn(salesReturn);
        when(salesDeliveryMapper.selectById(DELIVERY_ID)).thenReturn(delivery);
        when(salesOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(salesReturnLineMapper.selectList(any())).thenReturn(returnLines);
        when(salesDeliveryLineMapper.selectList(any())).thenReturn(deliveryLines);
        when(salesOrderLineMapper.selectList(any())).thenReturn(orderLines);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private void assertScopedQueries() {
        verify(salesReturnMapper).selectById(RETURN_ID);
        verify(salesDeliveryMapper).selectById(DELIVERY_ID);
        verify(salesOrderMapper, times(2)).selectById(ORDER_ID);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesReturnLineEntity>> returnLineWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesReturnLineMapper).selectList(returnLineWrapperCaptor.capture());
        assertTenantScoped(returnLineWrapperCaptor.getValue());

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
        ArgumentCaptor<LambdaQueryWrapper<InventoryTransactionEntity>> inventoryWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryTransactionMapper, times(3)).selectList(inventoryWrapperCaptor.capture());
        assertThat(inventoryWrapperCaptor.getAllValues()).allSatisfy(wrapper -> {
            assertTenantScoped(wrapper);
            assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                    .contains("biz_type")
                    .contains("biz_line_id")
                    .contains("direction");
        });
    }

    private void assertNoPostingWrites() {
        verify(salesReturnMapper, never()).updateById(any(SalesReturnEntity.class));
        assertNoDownstreamWrites();
    }

    private void assertNoDownstreamWrites() {
        verify(salesReturnLineMapper, never()).updateById(any(SalesReturnLineEntity.class));
        verify(salesDeliveryMapper, never()).updateById(any(SalesDeliveryEntity.class));
        verify(salesDeliveryLineMapper, never()).updateById(any(SalesDeliveryLineEntity.class));
        verify(salesOrderLineMapper, never()).updateById(any(SalesOrderLineEntity.class));
        verify(salesOrderMapper, never()).updateById(any(SalesOrderEntity.class));
        verify(inventoryTransactionMapper, never()).updateById(any(InventoryTransactionEntity.class));
        verify(inventoryPostingService, never()).postInbound(any(), any());
        verify(inventorySerialNumberService, never()).registerInboundSerials(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
        verify(financePostingService, never()).recordSalesReturn(any(), any(), any(), any());
        verify(salesReturnQueryService, never()).getById(any());
    }

    @Test
    void postStopsAtAttachmentGateBeforeCheckingAccountingPeriod() {
        SalesReturnEntity entity = salesReturn();
        when(salesReturnMapper.selectById(RETURN_ID)).thenReturn(entity);
        doThrow(new IllegalArgumentException("业务类型 SALES_RETURN 要求至少上传 1 个附件，当前 0 个"))
                .when(attachmentService)
                .requireIfConfigured("SALES_RETURN", RETURN_ID);

        assertThatThrownBy(() -> service().post(RETURN_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SALES_RETURN");

        verify(accountPeriodGuard, never()).requireOpen(any(), any());
        assertNoPostingWrites();
    }

    private SalesReturnPostingService service() {
        return new SalesReturnPostingService(
                salesReturnMapper,
                salesReturnLineMapper,
                salesDeliveryMapper,
                salesDeliveryLineMapper,
                salesOrderMapper,
                salesOrderLineMapper,
                inventoryTransactionMapper,
                inventoryPostingService,
                inventorySerialNumberService,
                financePostingService,
                auditMetadataFactory,
                salesReturnQueryService,
                accountPeriodGuard,
                attachmentService
        );
    }

    private SalesReturnEntity salesReturn() {
        SalesReturnEntity entity = new SalesReturnEntity();
        entity.setId(RETURN_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setReturnNo("SR-9001");
        entity.setDeliveryId(DELIVERY_ID);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setReturnDate(RETURN_DATE);
        entity.setStatus("DRAFT");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesDeliveryEntity delivery() {
        SalesDeliveryEntity entity = new SalesDeliveryEntity();
        entity.setId(DELIVERY_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setDeliveryNo("SD-7001");
        entity.setOrderId(ORDER_ID);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setStatus("POSTED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesOrderEntity order() {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setId(ORDER_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setOrderNo("SO-6001");
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setStatus("APPROVED");
        entity.setApprovalStatus("APPROVED");
        entity.setDeliveryStatus("FULL_DELIVERED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesReturnLineEntity returnLine(
            Long id,
            Long deliveryLineId,
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
        SalesReturnLineEntity entity = new SalesReturnLineEntity();
        entity.setId(id);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setReturnId(RETURN_ID);
        entity.setLineNo(id.equals(9101L) ? 1 : 2);
        entity.setDeliveryLineId(deliveryLineId);
        entity.setOrderLineId(orderLineId);
        entity.setProductId(productId);
        entity.setQty(new BigDecimal(qty));
        entity.setAmount(new BigDecimal(amount));
        entity.setLotNo(lotNo);
        entity.setProductionDate(productionDate);
        entity.setExpiryDate(expiryDate);
        entity.setLocationId(locationId);
        entity.setSerialNos(serialNos);
        entity.setRemark("return line " + id);
        return entity;
    }

    private SalesDeliveryLineEntity deliveryLine(
            Long id,
            Long orderLineId,
            Long productId,
            String qty,
            String returnedQty
    ) {
        SalesDeliveryLineEntity entity = new SalesDeliveryLineEntity();
        entity.setId(id);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setDeliveryId(DELIVERY_ID);
        entity.setLineNo(id.equals(7101L) ? 1 : 2);
        entity.setOrderLineId(orderLineId);
        entity.setProductId(productId);
        entity.setQty(new BigDecimal(qty));
        entity.setReturnedQty(new BigDecimal(returnedQty));
        return entity;
    }

    private SalesOrderLineEntity orderLine(
            Long id,
            Long productId,
            String qty,
            String deliveredQty
    ) {
        SalesOrderLineEntity entity = new SalesOrderLineEntity();
        entity.setId(id);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setOrderId(ORDER_ID);
        entity.setLineNo(id.equals(6101L) ? 1 : 2);
        entity.setProductId(productId);
        entity.setQty(new BigDecimal(qty));
        entity.setDeliveredQty(new BigDecimal(deliveredQty));
        return entity;
    }

    private InventoryTransactionEntity deliveryTransaction(
            Long id,
            Long deliveryLineId,
            Long productId,
            String lotNo,
            String qty,
            String amount,
            LocalDate productionDate,
            LocalDate expiryDate
    ) {
        InventoryTransactionEntity entity = new InventoryTransactionEntity();
        entity.setId(id);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setProductId(productId);
        entity.setBizType("SALES_DELIVERY");
        entity.setBizNo("SD-7001");
        entity.setBizLineId(deliveryLineId);
        entity.setDirection("OUT");
        entity.setQty(new BigDecimal(qty));
        entity.setAmount(new BigDecimal(amount));
        entity.setLotNo(lotNo);
        entity.setProductionDate(productionDate);
        entity.setExpiryDate(expiryDate);
        return entity;
    }

    private SalesReturnResponse response() {
        return new SalesReturnResponse(
                RETURN_ID,
                "SR-9001",
                DELIVERY_ID,
                WAREHOUSE_ID,
                RETURN_DATE,
                "POSTED",
                new BigDecimal("5.0000"),
                new BigDecimal("130.00"),
                BigDecimal.ZERO,
                null,
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

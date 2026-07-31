package com.tuowei.erp.purchase;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
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
import com.tuowei.erp.purchase.returnorder.service.PurchaseReturnPostingService;
import com.tuowei.erp.purchase.returnorder.service.PurchaseReturnQueryService;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseReturnPostingServiceTest {

    private static final Long COMPANY_ID = 101L;
    private static final Long ACCOUNT_BOOK_ID = 202L;
    private static final Long USER_ID = 9501L;
    private static final Long RETURN_ID = 9001L;
    private static final Long RECEIPT_ID = 7001L;
    private static final Long ORDER_ID = 6001L;
    private static final Long WAREHOUSE_ID = 3001L;
    private static final LocalDate RETURN_DATE = LocalDate.of(2026, 6, 8);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 8, 15, 0);
    private static final AuditMetadata AUDIT = new AuditMetadata(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID, NOW);

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
    private InventoryPostingService inventoryPostingService;

    @Mock
    private InventorySerialNumberService inventorySerialNumberService;

    @Mock
    private PurchaseOrderReceiptStatusService purchaseOrderReceiptStatusService;

    @Mock
    private FinancePostingService financePostingService;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private PurchaseReturnQueryService purchaseReturnQueryService;

    @Mock
    private AccountPeriodGuard accountPeriodGuard;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PurchaseReturnLineEntity.class);
        initTableInfo(PurchaseReceiptLineEntity.class);
        initTableInfo(PurchaseOrderLineEntity.class);
    }

    @Test
    void postCoordinatesTwoLinesAndPreservesInventoryMetadata() {
        PurchaseReturnEntity purchaseReturn = purchaseReturn();
        PurchaseReceiptEntity receipt = receipt();
        PurchaseOrderEntity order = order();
        PurchaseReturnLineEntity firstReturnLine = returnLine(
                9101L, 7101L, 6101L, 4001L, "2.0000", "20.00", "LOT-A",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 12, 31), 3101L, "SN-A1\nSN-A2"
        );
        PurchaseReturnLineEntity secondReturnLine = returnLine(
                9102L, 7102L, 6102L, 4002L, "3.0000", "45.00", "LOT-B",
                LocalDate.of(2026, 5, 2), LocalDate.of(2027, 1, 31), 3102L, "SN-B1\nSN-B2\nSN-B3"
        );
        PurchaseReceiptLineEntity firstReceiptLine = receiptLine(7101L, 6101L, 4001L, "5.0000", "1.0000");
        PurchaseReceiptLineEntity secondReceiptLine = receiptLine(7102L, 6102L, 4002L, "4.0000", "0.5000");
        PurchaseOrderLineEntity firstOrderLine = orderLine(6101L, 4001L, "5.0000");
        PurchaseOrderLineEntity secondOrderLine = orderLine(6102L, 4002L, "4.0000");
        stubPreflight(
                purchaseReturn,
                receipt,
                order,
                List.of(firstReturnLine, secondReturnLine),
                List.of(firstReceiptLine, secondReceiptLine),
                List.of(firstOrderLine, secondOrderLine)
        );
        when(inventoryPostingService.getQtyAvailable(WAREHOUSE_ID, 4001L, COMPANY_ID, ACCOUNT_BOOK_ID))
                .thenReturn(new BigDecimal("8.0000"));
        when(inventoryPostingService.getQtyAvailable(WAREHOUSE_ID, 4002L, COMPANY_ID, ACCOUNT_BOOK_ID))
                .thenReturn(new BigDecimal("7.0000"));
        when(purchaseReturnMapper.updateById(any(PurchaseReturnEntity.class))).thenReturn(1);
        when(purchaseReceiptLineMapper.updateById(any(PurchaseReceiptLineEntity.class))).thenReturn(1);
        when(purchaseOrderLineMapper.updateById(any(PurchaseOrderLineEntity.class))).thenReturn(1);
        PurchaseReturnResponse expected = response();
        when(purchaseReturnQueryService.getById(RETURN_ID)).thenReturn(expected);

        PurchaseReturnResponse result = service().post(RETURN_ID);

        assertThat(result).isSameAs(expected);
        assertThat(purchaseReturn.getStatus()).isEqualTo("POSTED");
        assertThat(purchaseReturn.getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(purchaseReturn.getUpdatedTime()).isEqualTo(NOW);
        assertThat(firstReceiptLine.getReturnedQty()).isEqualByComparingTo("3.0000");
        assertThat(secondReceiptLine.getReturnedQty()).isEqualByComparingTo("3.5000");
        assertThat(firstOrderLine.getReceivedQty()).isEqualByComparingTo("3.0000");
        assertThat(secondOrderLine.getReceivedQty()).isEqualByComparingTo("1.0000");
        assertThat(firstReceiptLine.getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(firstReceiptLine.getUpdatedTime()).isEqualTo(NOW);
        assertThat(firstOrderLine.getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(firstOrderLine.getUpdatedTime()).isEqualTo(NOW);

        ArgumentCaptor<InventoryPostingCommand> commandCaptor =
                ArgumentCaptor.forClass(InventoryPostingCommand.class);
        verify(inventoryPostingService, times(2)).postOutbound(
                commandCaptor.capture(),
                same(AUDIT),
                same("库存不足，不能执行采购退货")
        );
        assertInventoryCommand(
                commandCaptor.getAllValues().get(0),
                firstReturnLine,
                4001L,
                "LOT-A",
                LocalDate.of(2026, 5, 1),
                LocalDate.of(2026, 12, 31),
                3101L
        );
        assertInventoryCommand(
                commandCaptor.getAllValues().get(1),
                secondReturnLine,
                4002L,
                "LOT-B",
                LocalDate.of(2026, 5, 2),
                LocalDate.of(2027, 1, 31),
                3102L
        );
        verify(inventorySerialNumberService).issueOutboundSerials(
                4001L, "SN-A1\nSN-A2", "PURCHASE_RETURN", "PR-9001", firstReturnLine.getQty(), AUDIT
        );
        verify(inventorySerialNumberService).issueOutboundSerials(
                4002L, "SN-B1\nSN-B2\nSN-B3", "PURCHASE_RETURN", "PR-9001", secondReturnLine.getQty(), AUDIT
        );
        verify(purchaseOrderReceiptStatusService).refreshReceiptStatus(ORDER_ID, AUDIT, NOW);
        verify(financePostingService).recordPurchaseReturn(purchaseReturn, order, AUDIT);
        verify(accountPeriodGuard).requireOpen(RETURN_DATE, "采购退货过账");
        verify(purchaseReturnQueryService).assertCanView(purchaseReturn);
        verify(purchaseReturnQueryService).assertCanView(receipt);
        verify(purchaseReturnQueryService).assertCanView(order);
        verify(purchaseReturnQueryService).getById(RETURN_ID);

        InOrder writeOrder = inOrder(
                purchaseReturnMapper,
                purchaseReceiptLineMapper,
                purchaseOrderLineMapper,
                inventoryPostingService,
                inventorySerialNumberService,
                purchaseOrderReceiptStatusService,
                financePostingService,
                purchaseReturnQueryService
        );
        writeOrder.verify(purchaseReturnMapper).updateById(purchaseReturn);
        writeOrder.verify(purchaseReceiptLineMapper).updateById(firstReceiptLine);
        writeOrder.verify(purchaseOrderLineMapper).updateById(firstOrderLine);
        writeOrder.verify(inventoryPostingService).postOutbound(any(), same(AUDIT), any());
        writeOrder.verify(inventorySerialNumberService).issueOutboundSerials(
                4001L, "SN-A1\nSN-A2", "PURCHASE_RETURN", "PR-9001", firstReturnLine.getQty(), AUDIT
        );
        writeOrder.verify(purchaseReceiptLineMapper).updateById(secondReceiptLine);
        writeOrder.verify(purchaseOrderLineMapper).updateById(secondOrderLine);
        writeOrder.verify(inventoryPostingService).postOutbound(any(), same(AUDIT), any());
        writeOrder.verify(inventorySerialNumberService).issueOutboundSerials(
                4002L, "SN-B1\nSN-B2\nSN-B3", "PURCHASE_RETURN", "PR-9001", secondReturnLine.getQty(), AUDIT
        );
        writeOrder.verify(purchaseOrderReceiptStatusService).refreshReceiptStatus(ORDER_ID, AUDIT, NOW);
        writeOrder.verify(financePostingService).recordPurchaseReturn(purchaseReturn, order, AUDIT);
        writeOrder.verify(purchaseReturnQueryService).getById(RETURN_ID);

        assertScopedQueries();
    }

    @Test
    void postRejectsAccumulatedReceiptReturnQtyBeforeAnyWrite() {
        PurchaseReturnLineEntity first = returnLine(
                9101L, 7101L, 6101L, 4001L, "2.0000", "20.00", null,
                null, null, null, null
        );
        PurchaseReturnLineEntity second = returnLine(
                9102L, 7101L, 6101L, 4001L, "2.0000", "20.00", null,
                null, null, null, null
        );
        stubPreflight(
                purchaseReturn(),
                receipt(),
                order(),
                List.of(first, second),
                List.of(receiptLine(7101L, 6101L, 4001L, "3.0000", "0.0000")),
                List.of(orderLine(6101L, 4001L, "10.0000"))
        );
        when(inventoryPostingService.getQtyAvailable(WAREHOUSE_ID, 4001L, COMPANY_ID, ACCOUNT_BOOK_ID))
                .thenReturn(new BigDecimal("10.0000"));

        assertThatThrownBy(() -> service().post(RETURN_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("退货数量超过采购入库明细剩余可退数量");

        assertNoPostingWrites();
    }

    @Test
    void postRejectsAccumulatedInventoryQtyBeforeAnyWrite() {
        PurchaseReturnLineEntity first = returnLine(
                9101L, 7101L, 6101L, 4001L, "2.0000", "20.00", null,
                null, null, null, null
        );
        PurchaseReturnLineEntity second = returnLine(
                9102L, 7102L, 6102L, 4001L, "2.0000", "20.00", null,
                null, null, null, null
        );
        stubPreflight(
                purchaseReturn(),
                receipt(),
                order(),
                List.of(first, second),
                List.of(
                        receiptLine(7101L, 6101L, 4001L, "5.0000", "0.0000"),
                        receiptLine(7102L, 6102L, 4001L, "5.0000", "0.0000")
                ),
                List.of(
                        orderLine(6101L, 4001L, "5.0000"),
                        orderLine(6102L, 4001L, "5.0000")
                )
        );
        when(inventoryPostingService.getQtyAvailable(WAREHOUSE_ID, 4001L, COMPANY_ID, ACCOUNT_BOOK_ID))
                .thenReturn(new BigDecimal("3.0000"));

        assertThatThrownBy(() -> service().post(RETURN_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存不足，不能执行采购退货");

        verify(inventoryPostingService, times(2))
                .getQtyAvailable(WAREHOUSE_ID, 4001L, COMPANY_ID, ACCOUNT_BOOK_ID);
        assertNoPostingWrites();
    }

    @Test
    void postRejectsMissingSecondReceiptLineBeforeAnyWrite() {
        PurchaseReturnLineEntity first = returnLine(
                9101L, 7101L, 6101L, 4001L, "1.0000", "10.00", null,
                null, null, null, null
        );
        PurchaseReturnLineEntity second = returnLine(
                9102L, 7102L, 6102L, 4002L, "1.0000", "15.00", null,
                null, null, null, null
        );
        stubPreflight(
                purchaseReturn(),
                receipt(),
                order(),
                List.of(first, second),
                List.of(receiptLine(7101L, 6101L, 4001L, "5.0000", "0.0000")),
                List.of(
                        orderLine(6101L, 4001L, "5.0000"),
                        orderLine(6102L, 4002L, "5.0000")
                )
        );
        when(inventoryPostingService.getQtyAvailable(WAREHOUSE_ID, 4001L, COMPANY_ID, ACCOUNT_BOOK_ID))
                .thenReturn(new BigDecimal("5.0000"));

        assertThatThrownBy(() -> service().post(RETURN_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("采购入库单明细不存在");

        assertNoPostingWrites();
    }

    @Test
    void postRejectsMissingSecondOrderLineBeforeAnyWrite() {
        PurchaseReturnLineEntity first = returnLine(
                9101L, 7101L, 6101L, 4001L, "1.0000", "10.00", null,
                null, null, null, null
        );
        PurchaseReturnLineEntity second = returnLine(
                9102L, 7102L, 6102L, 4002L, "1.0000", "15.00", null,
                null, null, null, null
        );
        stubPreflight(
                purchaseReturn(),
                receipt(),
                order(),
                List.of(first, second),
                List.of(
                        receiptLine(7101L, 6101L, 4001L, "5.0000", "0.0000"),
                        receiptLine(7102L, 6102L, 4002L, "5.0000", "0.0000")
                ),
                List.of(orderLine(6101L, 4001L, "5.0000"))
        );
        when(inventoryPostingService.getQtyAvailable(WAREHOUSE_ID, 4001L, COMPANY_ID, ACCOUNT_BOOK_ID))
                .thenReturn(new BigDecimal("5.0000"));

        assertThatThrownBy(() -> service().post(RETURN_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("采购订单明细不存在");

        assertNoPostingWrites();
    }

    @Test
    void postStopsAfterHeaderOptimisticLockFailureWithoutDownstreamWrites() {
        PurchaseReturnEntity purchaseReturn = purchaseReturn();
        stubPreflight(
                purchaseReturn,
                receipt(),
                order(),
                List.of(returnLine(
                        9101L, 7101L, 6101L, 4001L, "1.0000", "10.00", null,
                        null, null, null, null
                )),
                List.of(receiptLine(7101L, 6101L, 4001L, "5.0000", "0.0000")),
                List.of(orderLine(6101L, 4001L, "5.0000"))
        );
        when(inventoryPostingService.getQtyAvailable(WAREHOUSE_ID, 4001L, COMPANY_ID, ACCOUNT_BOOK_ID))
                .thenReturn(new BigDecimal("5.0000"));
        when(purchaseReturnMapper.updateById(purchaseReturn)).thenReturn(0);

        assertThatThrownBy(() -> service().post(RETURN_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("采购退货单已被其他操作修改，请刷新后重试");

        verify(purchaseReturnMapper).updateById(purchaseReturn);
        assertNoDownstreamWrites();
    }

    private void stubPreflight(
            PurchaseReturnEntity purchaseReturn,
            PurchaseReceiptEntity receipt,
            PurchaseOrderEntity order,
            List<PurchaseReturnLineEntity> returnLines,
            List<PurchaseReceiptLineEntity> receiptLines,
            List<PurchaseOrderLineEntity> orderLines
    ) {
        when(purchaseReturnMapper.selectById(RETURN_ID)).thenReturn(purchaseReturn);
        when(purchaseReceiptMapper.selectById(RECEIPT_ID)).thenReturn(receipt);
        when(purchaseOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(purchaseReturnLineMapper.selectList(any())).thenReturn(returnLines);
        when(purchaseReceiptLineMapper.selectList(any())).thenReturn(receiptLines);
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(orderLines);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private void assertInventoryCommand(
            InventoryPostingCommand command,
            PurchaseReturnLineEntity returnLine,
            Long productId,
            String lotNo,
            LocalDate productionDate,
            LocalDate expiryDate,
            Long locationId
    ) {
        assertThat(command.warehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(command.productId()).isEqualTo(productId);
        assertThat(command.bizType()).isEqualTo("PURCHASE_RETURN");
        assertThat(command.bizNo()).isEqualTo("PR-9001");
        assertThat(command.bizLineId()).isEqualTo(returnLine.getId());
        assertThat(command.qty()).isEqualByComparingTo(returnLine.getQty());
        assertThat(command.amount()).isEqualByComparingTo(returnLine.getAmount());
        assertThat(command.remark()).isEqualTo(returnLine.getRemark());
        assertThat(command.bizDate()).isEqualTo(RETURN_DATE);
        assertThat(command.lotNo()).isEqualTo(lotNo);
        assertThat(command.productionDate()).isEqualTo(productionDate);
        assertThat(command.expiryDate()).isEqualTo(expiryDate);
        assertThat(command.locationId()).isEqualTo(locationId);
    }

    private void assertScopedQueries() {
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseReturnLineEntity>> returnLineCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseReturnLineMapper).selectList(returnLineCaptor.capture());
        assertTenantScoped(returnLineCaptor.getValue());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseReceiptLineEntity>> receiptLineCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseReceiptLineMapper).selectList(receiptLineCaptor.capture());
        assertTenantScoped(receiptLineCaptor.getValue());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseOrderLineEntity>> orderLineCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseOrderLineMapper).selectList(orderLineCaptor.capture());
        assertTenantScoped(orderLineCaptor.getValue());
    }

    private void assertNoPostingWrites() {
        verify(purchaseReturnMapper, never()).updateById(any(PurchaseReturnEntity.class));
        assertNoDownstreamWrites();
    }

    private void assertNoDownstreamWrites() {
        verify(purchaseReceiptLineMapper, never()).updateById(any(PurchaseReceiptLineEntity.class));
        verify(purchaseOrderLineMapper, never()).updateById(any(PurchaseOrderLineEntity.class));
        verify(inventoryPostingService, never()).postOutbound(any(), any(), any());
        verify(inventorySerialNumberService, never()).issueOutboundSerials(
                any(), any(), any(), any(), any(), any()
        );
        verify(purchaseOrderReceiptStatusService, never()).refreshReceiptStatus(any(), any(), any());
        verify(financePostingService, never()).recordPurchaseReturn(any(), any(), any());
        verify(purchaseReturnQueryService, never()).getById(any());
    }

    private PurchaseReturnPostingService service() {
        PurchaseOrderLookupService purchaseOrderLookupService = new PurchaseOrderLookupService(
                purchaseOrderMapper,
                purchaseOrderLineMapper
        );
        return new PurchaseReturnPostingService(
                purchaseReturnMapper,
                purchaseReturnLineMapper,
                purchaseReceiptMapper,
                purchaseReceiptLineMapper,
                purchaseOrderLineMapper,
                inventoryPostingService,
                inventorySerialNumberService,
                purchaseOrderLookupService,
                purchaseOrderReceiptStatusService,
                financePostingService,
                auditMetadataFactory,
                purchaseReturnQueryService,
                accountPeriodGuard
        );
    }

    private PurchaseReturnEntity purchaseReturn() {
        PurchaseReturnEntity entity = new PurchaseReturnEntity();
        entity.setId(RETURN_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setReturnNo("PR-9001");
        entity.setReceiptId(RECEIPT_ID);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setReturnDate(RETURN_DATE);
        entity.setStatus("DRAFT");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseReceiptEntity receipt() {
        PurchaseReceiptEntity entity = new PurchaseReceiptEntity();
        entity.setId(RECEIPT_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setReceiptNo("GR-7001");
        entity.setOrderId(ORDER_ID);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setStatus("POSTED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseOrderEntity order() {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setId(ORDER_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setOrderNo("PO-6001");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseReturnLineEntity returnLine(
            Long id,
            Long receiptLineId,
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
        PurchaseReturnLineEntity entity = new PurchaseReturnLineEntity();
        entity.setId(id);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setReturnId(RETURN_ID);
        entity.setLineNo(id.equals(9101L) ? 1 : 2);
        entity.setReceiptLineId(receiptLineId);
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

    private PurchaseReceiptLineEntity receiptLine(
            Long id,
            Long orderLineId,
            Long productId,
            String qty,
            String returnedQty
    ) {
        PurchaseReceiptLineEntity entity = new PurchaseReceiptLineEntity();
        entity.setId(id);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setReceiptId(RECEIPT_ID);
        entity.setLineNo(id.equals(7101L) ? 1 : 2);
        entity.setOrderLineId(orderLineId);
        entity.setProductId(productId);
        entity.setQty(new BigDecimal(qty));
        entity.setReturnedQty(new BigDecimal(returnedQty));
        return entity;
    }

    private PurchaseOrderLineEntity orderLine(Long id, Long productId, String receivedQty) {
        PurchaseOrderLineEntity entity = new PurchaseOrderLineEntity();
        entity.setId(id);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setOrderId(ORDER_ID);
        entity.setLineNo(id.equals(6101L) ? 1 : 2);
        entity.setProductId(productId);
        entity.setReceivedQty(new BigDecimal(receivedQty));
        return entity;
    }

    private PurchaseReturnResponse response() {
        return new PurchaseReturnResponse(
                RETURN_ID,
                "PR-9001",
                RECEIPT_ID,
                "GR-7001",
                "PO-6001",
                WAREHOUSE_ID,
                "A仓",
                RETURN_DATE,
                "POSTED",
                new BigDecimal("5.0000"),
                new BigDecimal("65.00"),
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

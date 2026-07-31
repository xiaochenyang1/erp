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
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
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
import com.tuowei.erp.purchase.receipt.service.PurchaseReceiptPostingService;
import com.tuowei.erp.purchase.receipt.service.PurchaseReceiptQueryService;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptResponse;
import com.tuowei.erp.qc.inspection.service.QcInspectionGate;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseReceiptPostingServiceTest {

    private static final Long COMPANY_ID = 101L;
    private static final Long ACCOUNT_BOOK_ID = 202L;
    private static final Long USER_ID = 9501L;
    private static final Long RECEIPT_ID = 7001L;
    private static final Long ORDER_ID = 6001L;
    private static final Long WAREHOUSE_ID = 3001L;
    private static final LocalDate RECEIPT_DATE = LocalDate.of(2026, 6, 8);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 8, 15, 0);
    private static final AuditMetadata AUDIT = new AuditMetadata(USER_ID, COMPANY_ID, ACCOUNT_BOOK_ID, NOW);

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
    private PurchaseReceiptQueryService purchaseReceiptQueryService;

    @Mock
    private AccountPeriodGuard accountPeriodGuard;

    @Mock
    private QcInspectionGate qcInspectionGate;

    @Mock
    private ProductValidator productValidator;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PurchaseReceiptLineEntity.class);
        initTableInfo(PurchaseOrderLineEntity.class);
    }

    @Test
    void postCoordinatesTwoLinesPreservesInventoryMetadataAndWritesInOrder() {
        PurchaseReceiptEntity receipt = receipt();
        PurchaseOrderEntity order = order();
        PurchaseReceiptLineEntity firstReceiptLine = receiptLine(
                7101L, 6101L, 4001L, "2.0000", "20.00", "LOT-A",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 12, 31), 3101L, "SN-A1\nSN-A2"
        );
        PurchaseReceiptLineEntity secondReceiptLine = receiptLine(
                7102L, 6102L, 4002L, "3.0000", "45.00", "LOT-B",
                LocalDate.of(2026, 5, 2), LocalDate.of(2027, 1, 31), 3102L, "SN-B1\nSN-B2\nSN-B3"
        );
        PurchaseOrderLineEntity firstOrderLine = orderLine(6101L, 4001L, "10.0000", "1.0000");
        PurchaseOrderLineEntity secondOrderLine = orderLine(6102L, 4002L, "8.0000", "0.5000");
        List<PurchaseReceiptLineEntity> receiptLines = List.of(firstReceiptLine, secondReceiptLine);
        stubDocumentGraph(receipt, order, receiptLines, List.of(firstOrderLine, secondOrderLine));
        stubProducts(product(4001L, true), product(4002L, true));
        when(purchaseReceiptMapper.updateById(any(PurchaseReceiptEntity.class))).thenReturn(1);
        when(purchaseOrderLineMapper.updateById(any(PurchaseOrderLineEntity.class))).thenReturn(1);
        PurchaseReceiptResponse expected = response();
        when(purchaseReceiptQueryService.getById(RECEIPT_ID)).thenReturn(expected);

        PurchaseReceiptResponse result = service().post(RECEIPT_ID);

        assertThat(result).isSameAs(expected);
        assertThat(receipt.getStatus()).isEqualTo("POSTED");
        assertThat(receipt.getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(receipt.getUpdatedTime()).isEqualTo(NOW);
        assertThat(firstOrderLine.getReceivedQty()).isEqualByComparingTo("3.0000");
        assertThat(secondOrderLine.getReceivedQty()).isEqualByComparingTo("3.5000");
        assertThat(firstOrderLine.getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(firstOrderLine.getUpdatedTime()).isEqualTo(NOW);
        assertThat(secondOrderLine.getUpdatedBy()).isEqualTo(USER_ID);
        assertThat(secondOrderLine.getUpdatedTime()).isEqualTo(NOW);

        ArgumentCaptor<InventoryPostingCommand> commandCaptor =
                ArgumentCaptor.forClass(InventoryPostingCommand.class);
        verify(inventoryPostingService, times(2)).postInbound(commandCaptor.capture(), same(AUDIT));
        assertInventoryCommand(commandCaptor.getAllValues().get(0), firstReceiptLine);
        assertInventoryCommand(commandCaptor.getAllValues().get(1), secondReceiptLine);
        verify(inventorySerialNumberService).registerInboundSerials(
                4001L,
                WAREHOUSE_ID,
                3101L,
                "SN-A1\nSN-A2",
                "PURCHASE_RECEIPT",
                "GR-7001",
                firstReceiptLine.getQty(),
                AUDIT
        );
        verify(inventorySerialNumberService).registerInboundSerials(
                4002L,
                WAREHOUSE_ID,
                3102L,
                "SN-B1\nSN-B2\nSN-B3",
                "PURCHASE_RECEIPT",
                "GR-7001",
                secondReceiptLine.getQty(),
                AUDIT
        );
        verify(accountPeriodGuard).requireOpen(RECEIPT_DATE, "采购入库过账");
        verify(productValidator).requireProducts(List.of(4001L, 4002L), COMPANY_ID, ACCOUNT_BOOK_ID);
        verify(qcInspectionGate).assertReceiptInspected(receipt, receiptLines, AUDIT);
        verify(purchaseReceiptQueryService).assertCanView(receipt);
        verify(purchaseReceiptQueryService).assertCanView(order);
        verify(purchaseOrderReceiptStatusService).refreshReceiptStatus(ORDER_ID, AUDIT, NOW);
        verify(financePostingService).recordPurchaseReceipt(receipt, order, AUDIT);
        verify(purchaseReceiptQueryService).getById(RECEIPT_ID);

        InOrder postingOrder = inOrder(
                productValidator,
                qcInspectionGate,
                purchaseReceiptMapper,
                purchaseOrderLineMapper,
                inventoryPostingService,
                inventorySerialNumberService,
                purchaseOrderReceiptStatusService,
                financePostingService,
                purchaseReceiptQueryService
        );
        postingOrder.verify(productValidator).requireProducts(any(), eq(COMPANY_ID), eq(ACCOUNT_BOOK_ID));
        postingOrder.verify(qcInspectionGate).assertReceiptInspected(receipt, receiptLines, AUDIT);
        postingOrder.verify(purchaseReceiptMapper).updateById(receipt);
        postingOrder.verify(purchaseOrderLineMapper).updateById(firstOrderLine);
        postingOrder.verify(inventoryPostingService).postInbound(any(InventoryPostingCommand.class), same(AUDIT));
        postingOrder.verify(inventorySerialNumberService).registerInboundSerials(
                4001L, WAREHOUSE_ID, 3101L, "SN-A1\nSN-A2", "PURCHASE_RECEIPT", "GR-7001",
                firstReceiptLine.getQty(), AUDIT
        );
        postingOrder.verify(purchaseOrderLineMapper).updateById(secondOrderLine);
        postingOrder.verify(inventoryPostingService).postInbound(any(InventoryPostingCommand.class), same(AUDIT));
        postingOrder.verify(inventorySerialNumberService).registerInboundSerials(
                4002L, WAREHOUSE_ID, 3102L, "SN-B1\nSN-B2\nSN-B3", "PURCHASE_RECEIPT", "GR-7001",
                secondReceiptLine.getQty(), AUDIT
        );
        postingOrder.verify(purchaseOrderReceiptStatusService).refreshReceiptStatus(ORDER_ID, AUDIT, NOW);
        postingOrder.verify(financePostingService).recordPurchaseReceipt(receipt, order, AUDIT);
        postingOrder.verify(purchaseReceiptQueryService).getById(RECEIPT_ID);

        assertScopedLineQueries();
    }

    @Test
    void postRejectsMissingSecondOrderLineBeforeAnyWrite() {
        PurchaseReceiptLineEntity first = receiptLine(
                7101L, 6101L, 4001L, "1.0000", "10.00", null,
                null, null, null, null
        );
        PurchaseReceiptLineEntity second = receiptLine(
                7102L, 6102L, 4002L, "1.0000", "15.00", null,
                null, null, null, null
        );
        stubDocumentGraph(
                receipt(),
                order(),
                List.of(first, second),
                List.of(orderLine(6101L, 4001L, "5.0000", "0.0000"))
        );
        stubProducts(product(4001L, false), product(4002L, false));

        assertThatThrownBy(() -> service().post(RECEIPT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("采购订单明细不存在");

        verify(qcInspectionGate, never()).assertReceiptInspected(any(), any(), any());
        assertNoPostingWrites();
    }

    @Test
    void postRejectsAccumulatedReceiptQtyBeforeAnyWrite() {
        PurchaseReceiptLineEntity first = receiptLine(
                7101L, 6101L, 4001L, "2.0000", "20.00", null,
                null, null, null, null
        );
        PurchaseReceiptLineEntity second = receiptLine(
                7102L, 6101L, 4001L, "2.0000", "20.00", null,
                null, null, null, null
        );
        stubDocumentGraph(
                receipt(),
                order(),
                List.of(first, second),
                List.of(orderLine(6101L, 4001L, "5.0000", "2.0000"))
        );
        stubProducts(product(4001L, false));

        assertThatThrownBy(() -> service().post(RECEIPT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("入库数量超过采购订单剩余可入库数量");

        verify(qcInspectionGate, never()).assertReceiptInspected(any(), any(), any());
        assertNoPostingWrites();
    }

    @Test
    void postRejectsMissingLotOnSecondControlledProductBeforeAnyWrite() {
        PurchaseReceiptLineEntity first = receiptLine(
                7101L, 6101L, 4001L, "1.0000", "10.00", null,
                null, null, null, null
        );
        PurchaseReceiptLineEntity second = receiptLine(
                7102L, 6102L, 4002L, "1.0000", "15.00", "   ",
                null, null, null, null
        );
        stubDocumentGraph(
                receipt(),
                order(),
                List.of(first, second),
                List.of(
                        orderLine(6101L, 4001L, "5.0000", "0.0000"),
                        orderLine(6102L, 4002L, "5.0000", "0.0000")
                )
        );
        stubProducts(product(4001L, false), product(4002L, true));

        assertThatThrownBy(() -> service().post(RECEIPT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品启用批次管理，入库行必须填写批号");

        verify(qcInspectionGate, never()).assertReceiptInspected(any(), any(), any());
        assertNoPostingWrites();
    }

    @Test
    void postRejectsIqcGateBeforeAnyWrite() {
        PurchaseReceiptEntity receipt = receipt();
        PurchaseReceiptLineEntity line = receiptLine(
                7101L, 6101L, 4001L, "1.0000", "10.00", "LOT-A",
                null, null, null, null
        );
        List<PurchaseReceiptLineEntity> receiptLines = List.of(line);
        stubDocumentGraph(
                receipt,
                order(),
                receiptLines,
                List.of(orderLine(6101L, 4001L, "5.0000", "0.0000"))
        );
        stubProducts(product(4001L, true));
        doThrow(new IllegalArgumentException("存在需检验商品尚未完成质检，不能过账"))
                .when(qcInspectionGate)
                .assertReceiptInspected(receipt, receiptLines, AUDIT);

        assertThatThrownBy(() -> service().post(RECEIPT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("存在需检验商品尚未完成质检，不能过账");

        verify(qcInspectionGate).assertReceiptInspected(receipt, receiptLines, AUDIT);
        assertNoPostingWrites();
    }

    @Test
    void postRejectsBulkProductValidationBeforeAnyWrite() {
        PurchaseReceiptLineEntity line = receiptLine(
                7101L, 6101L, 4001L, "1.0000", "10.00", null,
                null, null, null, null
        );
        stubDocumentGraph(
                receipt(),
                order(),
                List.of(line),
                List.of(orderLine(6101L, 4001L, "5.0000", "0.0000"))
        );
        when(productValidator.requireProducts(any(), eq(COMPANY_ID), eq(ACCOUNT_BOOK_ID)))
                .thenThrow(new IllegalArgumentException("商品不存在或已停用"));

        assertThatThrownBy(() -> service().post(RECEIPT_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在或已停用");

        verify(productValidator).requireProducts(List.of(4001L), COMPANY_ID, ACCOUNT_BOOK_ID);
        verify(qcInspectionGate, never()).assertReceiptInspected(any(), any(), any());
        assertNoPostingWrites();
    }

    @Test
    void postStopsAfterHeaderOptimisticLockFailureWithoutDownstreamWrites() {
        PurchaseReceiptEntity receipt = receipt();
        PurchaseReceiptLineEntity line = receiptLine(
                7101L, 6101L, 4001L, "1.0000", "10.00", null,
                null, null, null, null
        );
        stubDocumentGraph(
                receipt,
                order(),
                List.of(line),
                List.of(orderLine(6101L, 4001L, "5.0000", "0.0000"))
        );
        stubProducts(product(4001L, false));
        when(purchaseReceiptMapper.updateById(receipt)).thenReturn(0);

        assertThatThrownBy(() -> service().post(RECEIPT_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("采购入库单已被其他操作修改，请刷新后重试");

        verify(purchaseReceiptMapper).updateById(receipt);
        verify(qcInspectionGate).assertReceiptInspected(eq(receipt), eq(List.of(line)), same(AUDIT));
        assertNoDownstreamWrites();
    }

    private void stubDocumentGraph(
            PurchaseReceiptEntity receipt,
            PurchaseOrderEntity order,
            List<PurchaseReceiptLineEntity> receiptLines,
            List<PurchaseOrderLineEntity> orderLines
    ) {
        when(purchaseReceiptMapper.selectById(RECEIPT_ID)).thenReturn(receipt);
        when(purchaseOrderMapper.selectById(ORDER_ID)).thenReturn(order);
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse());
        when(purchaseReceiptLineMapper.selectList(any())).thenReturn(receiptLines);
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(orderLines);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    private void stubProducts(ProductEntity... products) {
        Map<Long, ProductEntity> byId = new HashMap<>();
        for (ProductEntity product : products) {
            byId.put(product.getId(), product);
        }
        when(productValidator.requireProducts(any(), eq(COMPANY_ID), eq(ACCOUNT_BOOK_ID))).thenReturn(byId);
    }

    private void assertInventoryCommand(
            InventoryPostingCommand command,
            PurchaseReceiptLineEntity receiptLine
    ) {
        assertThat(command.warehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(command.productId()).isEqualTo(receiptLine.getProductId());
        assertThat(command.bizType()).isEqualTo("PURCHASE_RECEIPT");
        assertThat(command.bizNo()).isEqualTo("GR-7001");
        assertThat(command.bizLineId()).isEqualTo(receiptLine.getId());
        assertThat(command.qty()).isEqualByComparingTo(receiptLine.getQty());
        assertThat(command.amount()).isEqualByComparingTo(receiptLine.getAmount());
        assertThat(command.remark()).isEqualTo(receiptLine.getRemark());
        assertThat(command.bizDate()).isEqualTo(RECEIPT_DATE);
        assertThat(command.lotNo()).isEqualTo(receiptLine.getLotNo());
        assertThat(command.productionDate()).isEqualTo(receiptLine.getProductionDate());
        assertThat(command.expiryDate()).isEqualTo(receiptLine.getExpiryDate());
        assertThat(command.locationId()).isEqualTo(receiptLine.getLocationId());
    }

    private void assertScopedLineQueries() {
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
        verify(purchaseReceiptMapper, never()).updateById(any(PurchaseReceiptEntity.class));
        assertNoDownstreamWrites();
    }

    private void assertNoDownstreamWrites() {
        verify(purchaseOrderLineMapper, never()).updateById(any(PurchaseOrderLineEntity.class));
        verify(inventoryPostingService, never()).postInbound(any(), any());
        verify(inventorySerialNumberService, never()).registerInboundSerials(
                any(), any(), any(), any(), any(), any(), any(), any()
        );
        verify(purchaseOrderReceiptStatusService, never()).refreshReceiptStatus(any(), any(), any());
        verify(financePostingService, never()).recordPurchaseReceipt(any(), any(), any());
        verify(purchaseReceiptQueryService, never()).getById(any());
    }

    private PurchaseReceiptPostingService service() {
        PurchaseOrderLookupService purchaseOrderLookupService = new PurchaseOrderLookupService(
                purchaseOrderMapper,
                purchaseOrderLineMapper
        );
        return new PurchaseReceiptPostingService(
                purchaseReceiptMapper,
                purchaseReceiptLineMapper,
                purchaseOrderLineMapper,
                warehouseMapper,
                inventoryPostingService,
                inventorySerialNumberService,
                purchaseOrderLookupService,
                purchaseOrderReceiptStatusService,
                financePostingService,
                auditMetadataFactory,
                purchaseReceiptQueryService,
                accountPeriodGuard,
                qcInspectionGate,
                productValidator
        );
    }

    private PurchaseReceiptEntity receipt() {
        PurchaseReceiptEntity entity = new PurchaseReceiptEntity();
        entity.setId(RECEIPT_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setReceiptNo("GR-7001");
        entity.setOrderId(ORDER_ID);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setReceiptDate(RECEIPT_DATE);
        entity.setStatus("DRAFT");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseOrderEntity order() {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setId(ORDER_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setOrderNo("PO-6001");
        entity.setStatus("APPROVED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private WarehouseEntity warehouse() {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(WAREHOUSE_ID);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setWarehouseCode("WH-A");
        entity.setWarehouseName("A仓");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseReceiptLineEntity receiptLine(
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
        PurchaseReceiptLineEntity entity = new PurchaseReceiptLineEntity();
        entity.setId(id);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setReceiptId(RECEIPT_ID);
        entity.setLineNo(id.equals(7101L) ? 1 : 2);
        entity.setOrderLineId(orderLineId);
        entity.setProductId(productId);
        entity.setQty(new BigDecimal(qty));
        entity.setAmount(new BigDecimal(amount));
        entity.setLotNo(lotNo);
        entity.setProductionDate(productionDate);
        entity.setExpiryDate(expiryDate);
        entity.setLocationId(locationId);
        entity.setSerialNos(serialNos);
        entity.setRemark("receipt line " + id);
        return entity;
    }

    private PurchaseOrderLineEntity orderLine(
            Long id,
            Long productId,
            String qty,
            String receivedQty
    ) {
        PurchaseOrderLineEntity entity = new PurchaseOrderLineEntity();
        entity.setId(id);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setOrderId(ORDER_ID);
        entity.setLineNo(id.equals(6101L) ? 1 : 2);
        entity.setProductId(productId);
        entity.setQty(new BigDecimal(qty));
        entity.setReceivedQty(new BigDecimal(receivedQty));
        return entity;
    }

    private ProductEntity product(Long id, boolean lotControlled) {
        ProductEntity entity = new ProductEntity();
        entity.setId(id);
        entity.setCompanyId(COMPANY_ID);
        entity.setAccountBookId(ACCOUNT_BOOK_ID);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setLotControlled(lotControlled ? 1 : 0);
        return entity;
    }

    private PurchaseReceiptResponse response() {
        return new PurchaseReceiptResponse(
                RECEIPT_ID,
                "GR-7001",
                ORDER_ID,
                WAREHOUSE_ID,
                RECEIPT_DATE,
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
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(COMPANY_ID, ACCOUNT_BOOK_ID);
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

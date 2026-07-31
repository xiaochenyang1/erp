package com.tuowei.erp.finance.posting;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentEntity;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentLineEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class FinancePostingServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            501L,
            101L,
            202L,
            LocalDateTime.of(2026, 7, 31, 16, 0)
    );

    private final FinanceSubledgerPostingService subledgerPostingService = mock(FinanceSubledgerPostingService.class);
    private final FinanceVoucherPostingService voucherPostingService = mock(FinanceVoucherPostingService.class);
    private final FinancePostingService service = new FinancePostingService(
            subledgerPostingService,
            voucherPostingService
    );

    @Test
    void purchaseReceiptDelegatesTheSameScaledAmountsToSubledgerAndVoucherPosting() {
        PurchaseReceiptEntity receipt = new PurchaseReceiptEntity();
        receipt.setId(601L);
        receipt.setReceiptNo("PR-601");
        receipt.setReceiptDate(LocalDate.of(2026, 7, 31));
        receipt.setTotalAmount(new BigDecimal("100.123"));
        receipt.setTotalTaxAmount(new BigDecimal("13.456"));
        PurchaseOrderEntity order = new PurchaseOrderEntity();
        order.setSupplierId(701L);

        service.recordPurchaseReceipt(receipt, order, AUDIT);

        verify(subledgerPostingService).recordPayableIfAbsent(
                "PURCHASE_RECEIPT",
                601L,
                "PR-601",
                "INCREASE",
                701L,
                LocalDate.of(2026, 7, 31),
                new BigDecimal("113.58"),
                "采购入库形成应付",
                AUDIT
        );
        verify(voucherPostingService).recordPurchaseReceipt(
                receipt,
                new BigDecimal("100.12"),
                new BigDecimal("13.46"),
                new BigDecimal("113.58"),
                AUDIT
        );
    }

    @Test
    void purchaseReturnAndInventoryAdjustmentDelegateToTheirDedicatedWriters() {
        PurchaseReturnEntity purchaseReturn = new PurchaseReturnEntity();
        purchaseReturn.setId(602L);
        purchaseReturn.setReturnNo("RT-602");
        purchaseReturn.setReturnDate(LocalDate.of(2026, 7, 30));
        purchaseReturn.setTotalAmount(new BigDecimal("20.00"));
        purchaseReturn.setTotalTaxAmount(new BigDecimal("2.60"));
        PurchaseOrderEntity order = new PurchaseOrderEntity();
        order.setSupplierId(702L);

        service.recordPurchaseReturn(purchaseReturn, order, AUDIT);

        verify(subledgerPostingService).recordPayableIfAbsent(
                "PURCHASE_RETURN",
                602L,
                "RT-602",
                "DECREASE",
                702L,
                LocalDate.of(2026, 7, 30),
                new BigDecimal("22.60"),
                "采购退货冲减应付",
                AUDIT
        );
        verify(voucherPostingService).recordPurchaseReturn(
                purchaseReturn,
                new BigDecimal("20.00"),
                new BigDecimal("2.60"),
                new BigDecimal("22.60"),
                AUDIT
        );

        InventoryAdjustmentEntity adjustment = new InventoryAdjustmentEntity();
        List<InventoryAdjustmentLineEntity> lines = List.of(new InventoryAdjustmentLineEntity());
        service.recordInventoryAdjustment(adjustment, lines, AUDIT);
        verify(voucherPostingService).recordInventoryAdjustment(adjustment, lines, AUDIT);
    }

    @Test
    void salesDeliveryAndReturnPreserveSubledgerDirectionAndCostAmount() {
        SalesOrderEntity order = new SalesOrderEntity();
        order.setCustomerId(703L);
        SalesDeliveryEntity delivery = new SalesDeliveryEntity();
        delivery.setId(603L);
        delivery.setDeliveryNo("SD-603");
        delivery.setDeliveryDate(LocalDate.of(2026, 7, 29));
        delivery.setTotalAmount(new BigDecimal("80.00"));
        delivery.setTotalTaxAmount(new BigDecimal("10.40"));

        service.recordSalesDelivery(delivery, order, new BigDecimal("45.678"), AUDIT);

        verify(subledgerPostingService).recordReceivableIfAbsent(
                "SALES_DELIVERY",
                603L,
                "SD-603",
                "INCREASE",
                703L,
                LocalDate.of(2026, 7, 29),
                new BigDecimal("90.40"),
                "销售出库形成应收",
                AUDIT
        );
        verify(voucherPostingService).recordSalesDelivery(
                delivery,
                new BigDecimal("90.40"),
                new BigDecimal("45.678"),
                AUDIT
        );

        SalesReturnEntity salesReturn = new SalesReturnEntity();
        salesReturn.setId(604L);
        salesReturn.setReturnNo("SR-604");
        salesReturn.setReturnDate(LocalDate.of(2026, 7, 28));
        salesReturn.setTotalAmount(new BigDecimal("10.00"));
        salesReturn.setTotalTaxAmount(new BigDecimal("1.30"));

        service.recordSalesReturn(salesReturn, order, new BigDecimal("6.25"), AUDIT);

        verify(subledgerPostingService).recordReceivableIfAbsent(
                "SALES_RETURN",
                604L,
                "SR-604",
                "DECREASE",
                703L,
                LocalDate.of(2026, 7, 28),
                new BigDecimal("11.30"),
                "销售退货冲减应收",
                AUDIT
        );
        verify(voucherPostingService).recordSalesReturn(
                salesReturn,
                new BigDecimal("11.30"),
                new BigDecimal("6.25"),
                AUDIT
        );
    }

    @Test
    void productionPostingMapsAllFourBusinessEventsToTheirAccountingSubjects() {
        ProductionOrderEntity order = new ProductionOrderEntity();
        LocalDate bizDate = LocalDate.of(2026, 7, 27);

        service.recordProductionIssue(order, 605L, "PI-605", new BigDecimal("10.126"), bizDate, AUDIT);
        service.recordProductionCompletion(order, 606L, "PC-606", new BigDecimal("20.234"), bizDate, AUDIT);
        service.recordProductionCompletionReversal(order, 607L, "PCR-607", new BigDecimal("5.555"), bizDate, AUDIT);
        service.recordProductionReturn(order, 608L, "PRM-608", new BigDecimal("3.333"), bizDate, AUDIT);

        verify(voucherPostingService).recordTwoSidedVoucher(
                "PRODUCTION_ISSUE", 605L, "PI-605", bizDate, new BigDecimal("10.13"),
                "生产领料凭证", "5001", "1001", "生产领料结转生产成本", AUDIT
        );
        verify(voucherPostingService).recordTwoSidedVoucher(
                "PRODUCTION_COMPLETION", 606L, "PC-606", bizDate, new BigDecimal("20.23"),
                "生产完工凭证", "1001", "5001", "生产完工入库结转库存商品", AUDIT
        );
        verify(voucherPostingService).recordTwoSidedVoucher(
                "PRODUCTION_COMPLETION_REVERSAL", 607L, "PCR-607", bizDate, new BigDecimal("5.56"),
                "生产反完工凭证", "5001", "1001", "生产反完工冲回库存商品", AUDIT
        );
        verify(voucherPostingService).recordTwoSidedVoucher(
                "PRODUCTION_RETURN", 608L, "PRM-608", bizDate, new BigDecimal("3.33"),
                "生产退料凭证", "1001", "5001", "生产退料冲回生产成本", AUDIT
        );
    }

    @Test
    void nonPositiveProductionAmountsDoNotCreateVouchers() {
        ProductionOrderEntity order = new ProductionOrderEntity();

        service.recordProductionIssue(order, 609L, "PI-609", BigDecimal.ZERO, LocalDate.now(), AUDIT);
        service.recordProductionCompletion(order, 610L, "PC-610", null, LocalDate.now(), AUDIT);
        service.recordProductionCompletionReversal(order, 611L, "PCR-611", new BigDecimal("-1"), LocalDate.now(), AUDIT);
        service.recordProductionReturn(order, 612L, "PRM-612", BigDecimal.ZERO, LocalDate.now(), AUDIT);

        verify(voucherPostingService, never()).recordTwoSidedVoucher(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}

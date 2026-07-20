package com.tuowei.erp.finance.period;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.period.service.InventoryFinanceReconciliationService;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceResponse;
import com.tuowei.erp.finance.period.web.InventoryFinanceReconciliationResponse;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentEntity;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentLineEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.testsupport.WithErpUser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class InventoryFinanceReconciliationServiceTest {

    @Autowired
    private InventoryFinanceReconciliationService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FinancePostingService financePostingService;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("""
                delete from fin_voucher_entry
                where voucher_id in (
                    select id from fin_voucher
                    where source_type in ('PURCHASE_RECEIPT', 'PURCHASE_RETURN')
                      and source_id between 865400 and 865499
                )
                """);
        jdbcTemplate.update("""
                delete from fin_voucher
                where source_type in ('PURCHASE_RECEIPT', 'PURCHASE_RETURN')
                  and source_id between 865400 and 865499
                """);
        jdbcTemplate.update("""
                delete from fin_payable
                where source_type in ('PURCHASE_RECEIPT', 'PURCHASE_RETURN')
                  and source_id between 865400 and 865499
                """);
        jdbcTemplate.update("""
                delete from fin_voucher_entry
                where voucher_id in (
                    select id from fin_voucher
                    where source_type = 'INVENTORY_ADJUSTMENT'
                      and source_id between 865300 and 865399
                )
                """);
        jdbcTemplate.update("""
                delete from fin_voucher
                where source_type = 'INVENTORY_ADJUSTMENT'
                  and source_id between 865300 and 865399
                """);
        jdbcTemplate.update("delete from fin_voucher_entry where id between 865000 and 865999");
        jdbcTemplate.update("delete from fin_voucher where id between 865000 and 865999");
        jdbcTemplate.update("delete from inv_txn where id between 865000 and 865999");
        jdbcTemplate.update("delete from fin_account_period where id between 865000 and 865999");
    }

    @Test
    @WithErpUser
    void summarizesInventoryAndFinanceNetAmountsForPeriod() {
        seedPeriod();
        seedInventoryTxn(865101L, "PURCHASE_RECEIPT", "PR-865101", "IN", "100.00");
        seedVoucherWithInventoryEntry(865201L, "PURCHASE_RECEIPT", "PR-865101", "100.00", "0.00");
        seedInventoryTxn(865102L, "SALES_DELIVERY", "SD-865102", "OUT", "80.00");
        seedVoucherWithInventoryEntry(865202L, "SALES_DELIVERY", "SD-865102", "0.00", "70.00");

        InventoryFinanceReconciliationResponse response = service.summary(865001L);

        Assertions.assertThat(response.inventoryNetAmount()).isEqualByComparingTo("20.00");
        Assertions.assertThat(response.financeInventoryNetAmount()).isEqualByComparingTo("30.00");
        Assertions.assertThat(response.differenceAmount()).isEqualByComparingTo("-10.00");
        Assertions.assertThat(response.balanced()).isFalse();
    }

    @Test
    @WithErpUser
    void listsDifferencesByBusinessSource() {
        seedPeriod();
        seedInventoryTxn(865101L, "PURCHASE_RECEIPT", "PR-865101", "IN", "100.00");
        seedVoucherWithInventoryEntry(865201L, "PURCHASE_RECEIPT", "PR-865101", "100.00", "0.00");
        seedInventoryTxn(865102L, "SALES_DELIVERY", "SD-865102", "OUT", "80.00");
        seedVoucherWithInventoryEntry(865202L, "SALES_DELIVERY", "SD-865102", "0.00", "70.00");

        List<InventoryFinanceDifferenceResponse> differences = service.differences(865001L, null);

        Assertions.assertThat(differences).hasSize(1);
        InventoryFinanceDifferenceResponse difference = differences.get(0);
        Assertions.assertThat(difference.sourceKey()).isEqualTo("SALES_DELIVERY:SD-865102");
        Assertions.assertThat(difference.inventoryAmount()).isEqualByComparingTo("-80.00");
        Assertions.assertThat(difference.financeAmount()).isEqualByComparingTo("-70.00");
        Assertions.assertThat(difference.differenceAmount()).isEqualByComparingTo("-10.00");
        Assertions.assertThat(difference.differenceType()).isEqualTo("AMOUNT_MISMATCH");
    }

    @Test
    @WithErpUser
    void loadsDifferenceDetailWithInventoryTransactionsAndVoucherEntries() {
        seedPeriod();
        seedInventoryTxn(865102L, "SALES_DELIVERY", "SD-865102", "OUT", "80.00");
        seedVoucherWithInventoryEntry(865202L, "SALES_DELIVERY", "SD-865102", "0.00", "70.00");

        var detail = service.differenceDetail(865001L, "SALES_DELIVERY", "SD-865102");

        Assertions.assertThat(detail.sourceKey()).isEqualTo("SALES_DELIVERY:SD-865102");
        Assertions.assertThat(detail.inventoryAmount()).isEqualByComparingTo("-80.00");
        Assertions.assertThat(detail.financeAmount()).isEqualByComparingTo("-70.00");
        Assertions.assertThat(detail.differenceAmount()).isEqualByComparingTo("-10.00");
        Assertions.assertThat(detail.differenceType()).isEqualTo("AMOUNT_MISMATCH");
        Assertions.assertThat(detail.inventoryTransactions()).hasSize(1);
        Assertions.assertThat(detail.inventoryTransactions().get(0).direction()).isEqualTo("OUT");
        Assertions.assertThat(detail.inventoryTransactions().get(0).amount()).isEqualByComparingTo("80.00");
        Assertions.assertThat(detail.voucherEntries()).hasSize(1);
        Assertions.assertThat(detail.voucherEntries().get(0).voucherNo()).isEqualTo("VO-865202");
        Assertions.assertThat(detail.voucherEntries().get(0).creditAmount()).isEqualByComparingTo("70.00");
    }

    @Test
    @WithErpUser
    void inventoryAdjustmentFinancePostingKeepsReconciliationBalancedIdempotently() {
        seedPeriod();
        seedInventoryTxn(865301L, "INVENTORY_ADJUSTMENT", "IA-865301", "IN", "12.00");
        seedInventoryTxn(865302L, "INVENTORY_ADJUSTMENT", "IA-865301", "OUT", "5.00");

        InventoryAdjustmentEntity adjustment = inventoryAdjustment(865301L, "IA-865301");
        List<InventoryAdjustmentLineEntity> lines = List.of(
                adjustmentLine(865311L, 1, "IN", "12.00", "盘盈"),
                adjustmentLine(865312L, 2, "OUT", "5.00", "盘亏")
        );

        financePostingService.recordInventoryAdjustment(adjustment, lines, audit());
        financePostingService.recordInventoryAdjustment(adjustment, lines, audit());

        var detail = service.differenceDetail(865001L, "INVENTORY_ADJUSTMENT", "IA-865301");

        Assertions.assertThat(detail.inventoryAmount()).isEqualByComparingTo("7.00");
        Assertions.assertThat(detail.financeAmount()).isEqualByComparingTo("7.00");
        Assertions.assertThat(detail.differenceAmount()).isEqualByComparingTo("0.00");
        Assertions.assertThat(detail.voucherEntries()).hasSize(2);
        Assertions.assertThat(detail.voucherEntries().get(0).debitAmount()).isEqualByComparingTo("12.00");
        Assertions.assertThat(detail.voucherEntries().get(0).creditAmount()).isEqualByComparingTo("0.00");
        Assertions.assertThat(detail.voucherEntries().get(1).debitAmount()).isEqualByComparingTo("0.00");
        Assertions.assertThat(detail.voucherEntries().get(1).creditAmount()).isEqualByComparingTo("5.00");
    }

    @Test
    @WithErpUser
    void purchaseReceiptFinancePostingUsesNetInventoryAmountAndSeparateTaxEntry() {
        seedPeriod();
        seedInventoryTxn(865401L, "PURCHASE_RECEIPT", "PR-865401", "IN", "100.00");

        PurchaseOrderEntity order = purchaseOrder(865901L);
        PurchaseReceiptEntity receipt = purchaseReceipt(865401L, "PR-865401", "100.00", "13.00");

        financePostingService.recordPurchaseReceipt(receipt, order, audit());
        financePostingService.recordPurchaseReceipt(receipt, order, audit());

        var detail = service.differenceDetail(865001L, "PURCHASE_RECEIPT", "PR-865401");

        Assertions.assertThat(detail.inventoryAmount()).isEqualByComparingTo("100.00");
        Assertions.assertThat(detail.financeAmount()).isEqualByComparingTo("100.00");
        Assertions.assertThat(detail.differenceAmount()).isEqualByComparingTo("0.00");
        Assertions.assertThat(detail.voucherEntries()).hasSize(1);
        Assertions.assertThat(detail.voucherEntries().get(0).debitAmount()).isEqualByComparingTo("100.00");
        Assertions.assertThat(voucherEntryCount("PURCHASE_RECEIPT", 865401L)).isEqualTo(3);
        Assertions.assertThat(entryCount("PURCHASE_RECEIPT", 865401L, "1001", "debit_amount", "100.00")).isEqualTo(1);
        Assertions.assertThat(entryCount("PURCHASE_RECEIPT", 865401L, "222101", "debit_amount", "13.00")).isEqualTo(1);
        Assertions.assertThat(entryCount("PURCHASE_RECEIPT", 865401L, "2202", "credit_amount", "113.00")).isEqualTo(1);
    }

    @Test
    @WithErpUser
    void purchaseReturnFinancePostingUsesNetInventoryAmountAndSeparateTaxEntry() {
        seedPeriod();
        seedInventoryTxn(865402L, "PURCHASE_RETURN", "RT-865402", "OUT", "20.00");

        PurchaseOrderEntity order = purchaseOrder(865902L);
        PurchaseReturnEntity purchaseReturn = purchaseReturn(865402L, "RT-865402", "20.00", "2.60");

        financePostingService.recordPurchaseReturn(purchaseReturn, order, audit());
        financePostingService.recordPurchaseReturn(purchaseReturn, order, audit());

        var detail = service.differenceDetail(865001L, "PURCHASE_RETURN", "RT-865402");

        Assertions.assertThat(detail.inventoryAmount()).isEqualByComparingTo("-20.00");
        Assertions.assertThat(detail.financeAmount()).isEqualByComparingTo("-20.00");
        Assertions.assertThat(detail.differenceAmount()).isEqualByComparingTo("0.00");
        Assertions.assertThat(detail.voucherEntries()).hasSize(1);
        Assertions.assertThat(detail.voucherEntries().get(0).creditAmount()).isEqualByComparingTo("20.00");
        Assertions.assertThat(voucherEntryCount("PURCHASE_RETURN", 865402L)).isEqualTo(3);
        Assertions.assertThat(entryCount("PURCHASE_RETURN", 865402L, "2202", "debit_amount", "22.60")).isEqualTo(1);
        Assertions.assertThat(entryCount("PURCHASE_RETURN", 865402L, "1001", "credit_amount", "20.00")).isEqualTo(1);
        Assertions.assertThat(entryCount("PURCHASE_RETURN", 865402L, "222101", "credit_amount", "2.60")).isEqualTo(1);
    }

    private void seedPeriod() {
        jdbcTemplate.update("""
                insert into fin_account_period
                (id, company_id, account_book_id, period_year, period_month, start_date, end_date, status,
                 created_by, created_time, updated_by, updated_time, version)
                values (865001, 1, 1, 2036, '2036-05', ?, ?, 'OPEN', 0, ?, 0, ?, 0)
                """,
                LocalDate.of(2036, 5, 1),
                LocalDate.of(2036, 5, 31),
                LocalDateTime.of(2026, 5, 22, 9, 0),
                LocalDateTime.of(2026, 5, 22, 9, 0));
    }

    private void seedInventoryTxn(long id, String bizType, String bizNo, String direction, String amount) {
        jdbcTemplate.update("""
                insert into inv_txn
                (id, company_id, account_book_id, warehouse_id, product_id, biz_type, biz_no, biz_line_id,
                 direction, qty, amount, unit_cost, occurred_time, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, 1001, 2001, ?, ?, ?, ?, 1.0000, ?, ?, ?, 'reconciliation test', 0, ?, 0, ?, 0)
                """,
                id,
                bizType,
                bizNo,
                id,
                direction,
                new BigDecimal(amount),
                new BigDecimal(amount).abs(),
                LocalDateTime.of(2036, 5, 18, 10, 0),
                LocalDateTime.of(2026, 5, 22, 9, 0),
                LocalDateTime.of(2026, 5, 22, 9, 0));
    }

    private void seedVoucherWithInventoryEntry(long id, String sourceType, String sourceNo, String debit, String credit) {
        jdbcTemplate.update("""
                insert into fin_voucher
                (id, company_id, account_book_id, voucher_no, source_type, source_id, source_no, biz_date, amount,
                 status, deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, ?, ?, ?, ?, ?, 'POSTED', 0, 'reconciliation test', 0, ?, 0, ?, 0)
                """,
                id,
                "VO-" + id,
                sourceType,
                id,
                sourceNo,
                LocalDate.of(2036, 5, 18),
                new BigDecimal(debit).add(new BigDecimal(credit)),
                LocalDateTime.of(2026, 5, 22, 9, 0),
                LocalDateTime.of(2026, 5, 22, 9, 0));

        jdbcTemplate.update("""
                insert into fin_voucher_entry
                (id, company_id, account_book_id, voucher_id, biz_date, line_no, subject_id, subject_code, subject_name,
                 debit_amount, credit_amount, summary, created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, ?, 1, 910001, '1001', '库存商品', ?, ?, 'inventory entry', 0, ?, 0, ?, 0)
                """,
                id + 100,
                id,
                LocalDate.of(2036, 5, 18),
                new BigDecimal(debit),
                new BigDecimal(credit),
                LocalDateTime.of(2026, 5, 22, 9, 0),
                LocalDateTime.of(2026, 5, 22, 9, 0));
    }

    private AuditMetadata audit() {
        return new AuditMetadata(1L, 1L, 1L, LocalDateTime.of(2026, 5, 22, 10, 0));
    }

    private Integer voucherEntryCount(String sourceType, long sourceId) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from fin_voucher_entry e
                join fin_voucher v on v.id = e.voucher_id
                where v.source_type = ?
                  and v.source_id = ?
                """, Integer.class, sourceType, sourceId);
    }

    private Integer entryCount(String sourceType, long sourceId, String subjectCode, String amountColumn, String amount) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from fin_voucher_entry e
                join fin_voucher v on v.id = e.voucher_id
                where v.source_type = ?
                  and v.source_id = ?
                  and e.subject_code = ?
                  and %s = ?
                """.formatted(amountColumn), Integer.class, sourceType, sourceId, subjectCode, new BigDecimal(amount));
    }

    private InventoryAdjustmentEntity inventoryAdjustment(long id, String adjustmentNo) {
        InventoryAdjustmentEntity adjustment = new InventoryAdjustmentEntity();
        adjustment.setId(id);
        adjustment.setCompanyId(1L);
        adjustment.setAccountBookId(1L);
        adjustment.setAdjustmentNo(adjustmentNo);
        adjustment.setAdjustmentDate(LocalDate.of(2036, 5, 18));
        adjustment.setTotalQuantity(new BigDecimal("2.0000"));
        adjustment.setTotalAmount(new BigDecimal("17.00"));
        adjustment.setStatus("POSTED");
        adjustment.setDeletedFlag(0);
        return adjustment;
    }

    private InventoryAdjustmentLineEntity adjustmentLine(long id, int lineNo, String direction, String amount, String reason) {
        InventoryAdjustmentLineEntity line = new InventoryAdjustmentLineEntity();
        line.setId(id);
        line.setCompanyId(1L);
        line.setAccountBookId(1L);
        line.setAdjustmentId(865301L);
        line.setLineNo(lineNo);
        line.setProductId(2001L);
        line.setDirection(direction);
        line.setQty(new BigDecimal("1.0000"));
        line.setUnitCost(new BigDecimal(amount));
        line.setAmount(new BigDecimal(amount));
        line.setReason(reason);
        line.setRemark(reason);
        return line;
    }

    private PurchaseOrderEntity purchaseOrder(long supplierId) {
        PurchaseOrderEntity order = new PurchaseOrderEntity();
        order.setId(865800L + supplierId);
        order.setCompanyId(1L);
        order.setAccountBookId(1L);
        order.setSupplierId(supplierId);
        return order;
    }

    private PurchaseReceiptEntity purchaseReceipt(long id, String receiptNo, String amount, String taxAmount) {
        PurchaseReceiptEntity receipt = new PurchaseReceiptEntity();
        receipt.setId(id);
        receipt.setCompanyId(1L);
        receipt.setAccountBookId(1L);
        receipt.setReceiptNo(receiptNo);
        receipt.setReceiptDate(LocalDate.of(2036, 5, 18));
        receipt.setTotalAmount(new BigDecimal(amount));
        receipt.setTotalTaxAmount(new BigDecimal(taxAmount));
        return receipt;
    }

    private PurchaseReturnEntity purchaseReturn(long id, String returnNo, String amount, String taxAmount) {
        PurchaseReturnEntity purchaseReturn = new PurchaseReturnEntity();
        purchaseReturn.setId(id);
        purchaseReturn.setCompanyId(1L);
        purchaseReturn.setAccountBookId(1L);
        purchaseReturn.setReturnNo(returnNo);
        purchaseReturn.setReturnDate(LocalDate.of(2036, 5, 18));
        purchaseReturn.setTotalAmount(new BigDecimal(amount));
        purchaseReturn.setTotalTaxAmount(new BigDecimal(taxAmount));
        return purchaseReturn;
    }
}

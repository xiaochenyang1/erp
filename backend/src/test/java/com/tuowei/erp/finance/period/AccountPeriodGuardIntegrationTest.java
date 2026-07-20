package com.tuowei.erp.finance.period;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.finance.payment.service.PaymentService;
import com.tuowei.erp.finance.payment.web.PaymentAllocationRequest;
import com.tuowei.erp.finance.payment.web.PaymentCancelRequest;
import com.tuowei.erp.finance.payment.web.PaymentCreateRequest;
import com.tuowei.erp.finance.receipt.service.ReceiptService;
import com.tuowei.erp.finance.receipt.web.ReceiptAllocationRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptCancelRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptCreateRequest;
import com.tuowei.erp.purchase.receipt.service.PurchaseReceiptService;
import com.tuowei.erp.sales.delivery.service.SalesDeliveryService;
import com.tuowei.erp.testsupport.WithErpUser;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
class AccountPeriodGuardIntegrationTest {

    private static final LocalDate LOCKED_DATE = LocalDate.of(2034, 5, 18);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 22, 9, 0);

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private SalesDeliveryService salesDeliveryService;

    @Autowired
    private PurchaseReceiptService purchaseReceiptService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        cleanup();
        seedLockedPeriod();
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("""
                delete from fin_payment_allocation
                where payment_id in (
                    select id from fin_payment
                    where id between 866000 and 866999
                       or remark = 'period guard test'
                )
                """);
        jdbcTemplate.update("delete from fin_payment where id between 866000 and 866999 or remark = 'period guard test'");
        jdbcTemplate.update("delete from fin_payable where id between 866000 and 866999 or remark = 'period guard test'");
        jdbcTemplate.update("""
                delete from fin_receipt_allocation
                where receipt_id in (
                    select id from fin_receipt
                    where id between 866000 and 866999
                       or remark = 'period guard test'
                )
                """);
        jdbcTemplate.update("delete from fin_receipt where id between 866000 and 866999 or remark = 'period guard test'");
        jdbcTemplate.update("delete from fin_receivable where id between 866000 and 866999 or remark = 'period guard test'");
        jdbcTemplate.update("delete from sal_delivery where id between 866000 and 866999 or remark = 'period guard test'");
        jdbcTemplate.update("delete from pur_receipt where id between 866000 and 866999 or remark = 'period guard test'");
        jdbcTemplate.update("delete from fin_account_period where id between 866000 and 866999 or period_year = 2034");
    }

    @Test
    @WithErpUser(authorities = {"finance:payment:create"})
    void paymentCreateRejectsLockedPeriodDate() {
        seedPayable(866101L, "AP-866101", "100.00", "0.00", "UNSETTLED");

        assertLockedPeriodConflict(
                () -> paymentService.create(new PaymentCreateRequest(
                        7001L,
                        LOCKED_DATE,
                        new BigDecimal("50.00"),
                        "period guard test",
                        List.of(new PaymentAllocationRequest(866101L, new BigDecimal("50.00")))
                )),
                "付款单创建"
        );
    }

    @Test
    @WithErpUser(authorities = {"finance:receipt:create"})
    void receiptCreateRejectsLockedPeriodDate() {
        seedReceivable(866102L, "AR-866102", "100.00", "0.00", "UNSETTLED");

        assertLockedPeriodConflict(
                () -> receiptService.create(new ReceiptCreateRequest(
                        8001L,
                        LOCKED_DATE,
                        new BigDecimal("50.00"),
                        "period guard test",
                        List.of(new ReceiptAllocationRequest(866102L, new BigDecimal("50.00")))
                )),
                "收款单创建"
        );
    }

    @Test
    @WithErpUser(authorities = {"finance:payment:create"})
    void paymentCancelRejectsLockedOriginalPaymentDate() {
        seedPayable(866103L, "AP-866103", "100.00", "50.00", "PARTIALLY_SETTLED");
        seedPayment(866203L, "FP-866203", "50.00", "50.00", "POSTED");
        seedPaymentAllocation(866303L, 866203L, 866103L, "50.00");

        assertLockedPeriodConflict(
                () -> paymentService.cancel(866203L, new PaymentCancelRequest("period guard test")),
                "付款单作废"
        );
    }

    @Test
    @WithErpUser(authorities = {"finance:receipt:create"})
    void receiptCancelRejectsLockedOriginalReceiptDate() {
        seedReceivable(866104L, "AR-866104", "100.00", "50.00", "PARTIALLY_SETTLED");
        seedReceipt(866204L, "FR-866204", "50.00", "50.00", "POSTED");
        seedReceiptAllocation(866304L, 866204L, 866104L, "50.00");

        assertLockedPeriodConflict(
                () -> receiptService.cancel(866204L, new ReceiptCancelRequest("period guard test")),
                "收款单作废"
        );
    }

    @Test
    @WithErpUser(authorities = {"sales:delivery:post"})
    void salesDeliveryPostRejectsLockedPeriodDateBeforeOtherPostingWork() {
        seedSalesDelivery(866401L);

        assertLockedPeriodConflict(() -> salesDeliveryService.post(866401L), "销售出库过账");
    }

    @Test
    @WithErpUser(authorities = {"purchase:receipt:post"})
    void purchaseReceiptPostRejectsLockedPeriodDateBeforeOtherPostingWork() {
        seedPurchaseReceipt(866501L);

        assertLockedPeriodConflict(() -> purchaseReceiptService.post(866501L), "采购入库过账");
    }

    private void assertLockedPeriodConflict(ThrowableAssert.ThrowingCallable action, String bizAction) {
        Assertions.assertThatThrownBy(action)
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("2034-05 已锁定")
                .hasMessageContaining(bizAction);
    }

    private void seedLockedPeriod() {
        jdbcTemplate.update("""
                insert into fin_account_period
                (id, company_id, account_book_id, period_year, period_month, start_date, end_date, status,
                 created_by, created_time, updated_by, updated_time, version)
                values (866001, 1, 1, 2034, '2034-05', '2034-05-01', '2034-05-31', 'LOCKED',
                        0, ?, 0, ?, 0)
                """, NOW, NOW);
    }

    private void seedPayable(long id, String payableNo, String originalAmount, String settledAmount, String status) {
        jdbcTemplate.update("""
                insert into fin_payable
                (id, company_id, account_book_id, payable_no, source_type, source_id, source_no, direction,
                 supplier_id, biz_date, original_amount, settled_amount, status, deleted_flag, remark,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, 'PERIOD_GUARD', ?, ?, 'INCREASE',
                        7001, ?, ?, ?, ?, 0, 'period guard test',
                        9001, ?, 9001, ?, 0)
                """,
                id,
                payableNo,
                id,
                "SRC-" + id,
                LOCKED_DATE,
                new BigDecimal(originalAmount),
                new BigDecimal(settledAmount),
                status,
                NOW,
                NOW);
    }

    private void seedPayment(long id, String paymentNo, String amount, String allocatedAmount, String status) {
        jdbcTemplate.update("""
                insert into fin_payment
                (id, company_id, account_book_id, payment_no, supplier_id, payment_date, amount, allocated_amount,
                 status, deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, 7001, ?, ?, ?, ?, 0, 'period guard test', 9001, ?, 9001, ?, 0)
                """, id, paymentNo, LOCKED_DATE, new BigDecimal(amount), new BigDecimal(allocatedAmount), status, NOW, NOW);
    }

    private void seedPaymentAllocation(long id, long paymentId, long payableId, String amount) {
        jdbcTemplate.update("""
                insert into fin_payment_allocation
                (id, payment_id, payable_id, amount, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, ?, 9001, ?, 9001, ?, 0)
                """, id, paymentId, payableId, new BigDecimal(amount), NOW, NOW);
    }

    private void seedReceivable(long id, String receivableNo, String originalAmount, String settledAmount, String status) {
        jdbcTemplate.update("""
                insert into fin_receivable
                (id, company_id, account_book_id, receivable_no, source_type, source_id, source_no, direction,
                 customer_id, biz_date, original_amount, settled_amount, status, deleted_flag, remark,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, 'PERIOD_GUARD', ?, ?, 'INCREASE',
                        8001, ?, ?, ?, ?, 0, 'period guard test',
                        9001, ?, 9001, ?, 0)
                """,
                id,
                receivableNo,
                id,
                "SRC-" + id,
                LOCKED_DATE,
                new BigDecimal(originalAmount),
                new BigDecimal(settledAmount),
                status,
                NOW,
                NOW);
    }

    private void seedReceipt(long id, String receiptNo, String amount, String allocatedAmount, String status) {
        jdbcTemplate.update("""
                insert into fin_receipt
                (id, company_id, account_book_id, receipt_no, customer_id, receipt_date, amount, allocated_amount,
                 status, deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, 8001, ?, ?, ?, ?, 0, 'period guard test', 9001, ?, 9001, ?, 0)
                """, id, receiptNo, LOCKED_DATE, new BigDecimal(amount), new BigDecimal(allocatedAmount), status, NOW, NOW);
    }

    private void seedReceiptAllocation(long id, long receiptId, long receivableId, String amount) {
        jdbcTemplate.update("""
                insert into fin_receipt_allocation
                (id, receipt_id, receivable_id, amount, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, ?, 9001, ?, 9001, ?, 0)
                """, id, receiptId, receivableId, new BigDecimal(amount), NOW, NOW);
    }

    private void seedSalesDelivery(long id) {
        jdbcTemplate.update("""
                insert into sal_delivery
                (id, company_id, account_book_id, delivery_no, order_id, warehouse_id, delivery_date, status,
                 total_quantity, total_amount, total_tax_amount, deleted_flag, remark,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, 999999, 1001, ?, 'DRAFT',
                        1.0000, 10.00, 0.00, 0, 'period guard test',
                        9001, ?, 9001, ?, 0)
                """, id, "SD-" + id, LOCKED_DATE, NOW, NOW);
    }

    private void seedPurchaseReceipt(long id) {
        jdbcTemplate.update("""
                insert into pur_receipt
                (id, company_id, account_book_id, receipt_no, order_id, warehouse_id, receipt_date, status,
                 total_quantity, total_amount, total_tax_amount, deleted_flag, remark,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, 999999, 1001, ?, 'DRAFT',
                        1.0000, 10.00, 0.00, 0, 'period guard test',
                        9001, ?, 9001, ?, 0)
                """, id, "PR-" + id, LOCKED_DATE, NOW, NOW);
    }
}

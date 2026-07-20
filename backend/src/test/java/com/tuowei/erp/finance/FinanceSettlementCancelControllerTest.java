package com.tuowei.erp.finance;

import com.tuowei.erp.finance.payment.service.PaymentService;
import com.tuowei.erp.finance.payment.web.PaymentCancelRequest;
import com.tuowei.erp.finance.payment.web.PaymentResponse;
import com.tuowei.erp.finance.receipt.service.ReceiptService;
import com.tuowei.erp.finance.receipt.web.ReceiptCancelRequest;
import com.tuowei.erp.finance.receipt.web.ReceiptResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class FinanceSettlementCancelControllerTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private ReceiptService receiptService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from fin_receipt_allocation where id between 840000 and 840999");
        jdbcTemplate.update("delete from fin_receipt where id between 840000 and 840999");
        jdbcTemplate.update("delete from fin_receivable where id between 840000 and 840999");
        jdbcTemplate.update("delete from fin_payment_allocation where id between 840000 and 840999");
        jdbcTemplate.update("delete from fin_payment where id between 840000 and 840999");
        jdbcTemplate.update("delete from fin_payable where id between 840000 and 840999");
        jdbcTemplate.update("delete from fin_account_period where id = 840001");
    }

    @Test
    @WithErpUser(authorities = {"finance:payment:create", "finance:payment:view"})
    void cancelsPostedPaymentAndRevertsPayableSettlement() throws Exception {
        seedOpenPeriod();
        seedPayable(840101L, "AP-840101", "120.00", "60.00", "PARTIALLY_SETTLED");
        seedPayment(840201L, "FP202605180001", "50.00", "50.00", "POSTED");
        seedPaymentAllocation(840301L, 840201L, 840101L, "50.00");

        PaymentResponse response = paymentService.cancel(840201L, new PaymentCancelRequest("付款录入错误"));

        Assertions.assertThat(response.status()).isEqualTo("CANCELLED");
        Assertions.assertThat(response.cancelReason()).isEqualTo("付款录入错误");
        Assertions.assertThat(readAmount("fin_payable", "settled_amount", 840101L)).isEqualByComparingTo("10.00");
        Assertions.assertThat(readText("fin_payable", "status", 840101L)).isEqualTo("PARTIALLY_SETTLED");
    }

    @Test
    @WithErpUser(authorities = {"finance:payment:create", "finance:payment:view"})
    void repeatedPaymentCancelDoesNotRollbackSettlementTwice() throws Exception {
        seedOpenPeriod();
        seedPayable(840102L, "AP-840102", "120.00", "60.00", "PARTIALLY_SETTLED");
        seedPayment(840202L, "FP202605180002", "50.00", "50.00", "POSTED");
        seedPaymentAllocation(840302L, 840202L, 840102L, "50.00");

        PaymentResponse firstResponse = paymentService.cancel(840202L, new PaymentCancelRequest("第一次作废"));

        PaymentResponse repeatedResponse = paymentService.cancel(840202L, new PaymentCancelRequest("重复作废"));

        Assertions.assertThat(firstResponse.status()).isEqualTo("CANCELLED");
        Assertions.assertThat(firstResponse.cancelReason()).isEqualTo("第一次作废");
        Assertions.assertThat(repeatedResponse.status()).isEqualTo("CANCELLED");
        Assertions.assertThat(repeatedResponse.cancelReason()).isEqualTo("第一次作废");
        Assertions.assertThat(readAmount("fin_payable", "settled_amount", 840102L)).isEqualByComparingTo("10.00");
    }

    @Test
    @WithErpUser(authorities = {"finance:receipt:create", "finance:receipt:view"})
    void cancelsPostedReceiptAndRevertsReceivableSettlement() throws Exception {
        seedOpenPeriod();
        seedReceivable(840103L, "AR-840103", "120.00", "60.00", "PARTIALLY_SETTLED", 9001L);
        seedReceipt(840203L, "FR202605180001", "50.00", "50.00", "POSTED");
        seedReceiptAllocation(840303L, 840203L, 840103L, "50.00");

        ReceiptResponse response = receiptService.cancel(840203L, new ReceiptCancelRequest("收款录入错误"));

        Assertions.assertThat(response.status()).isEqualTo("CANCELLED");
        Assertions.assertThat(response.cancelReason()).isEqualTo("收款录入错误");
        Assertions.assertThat(readAmount("fin_receivable", "settled_amount", 840103L)).isEqualByComparingTo("10.00");
        Assertions.assertThat(readText("fin_receivable", "status", 840103L)).isEqualTo("PARTIALLY_SETTLED");
    }

    private void seedOpenPeriod() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 22, 9, 0);
        jdbcTemplate.update("""
                insert into fin_account_period
                (id, company_id, account_book_id, period_year, period_month, start_date, end_date, status,
                 created_by, created_time, updated_by, updated_time, version)
                values (840001, 1, 1, 2026, '2026-05', '2026-05-01', '2026-05-31', 'OPEN',
                        0, ?, 0, ?, 0)
                """, now, now);
    }

    private void seedPayable(long id, String payableNo, String originalAmount, String settledAmount, String status) {
        jdbcTemplate.update("""
                insert into fin_payable
                (id, company_id, account_book_id, payable_no, source_type, source_id, source_no, direction,
                 supplier_id, biz_date, original_amount, settled_amount, status, deleted_flag, remark,
                 created_by, updated_by, version)
                values (?, 1, 1, ?, 'TEST_PAYABLE', ?, ?, 'INCREASE',
                        7001, '2026-05-18', ?, ?, ?, 0, 'cancel settlement test',
                        9001, 9001, 0)
                """, id, payableNo, id, "SRC-" + id, new BigDecimal(originalAmount), new BigDecimal(settledAmount), status);
    }

    private void seedPayment(long id, String paymentNo, String amount, String allocatedAmount, String status) {
        jdbcTemplate.update("""
                insert into fin_payment
                (id, company_id, account_book_id, payment_no, supplier_id, payment_date, amount, allocated_amount,
                 status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, 7001, '2026-05-18', ?, ?, ?, 0, 'cancel settlement test', 9001, 9001, 0)
                """, id, paymentNo, new BigDecimal(amount), new BigDecimal(allocatedAmount), status);
    }

    private void seedPaymentAllocation(long id, long paymentId, long payableId, String amount) {
        jdbcTemplate.update("""
                insert into fin_payment_allocation
                (id, payment_id, payable_id, amount, created_by, updated_by, version)
                values (?, ?, ?, ?, 9001, 9001, 0)
                """, id, paymentId, payableId, new BigDecimal(amount));
    }

    private void seedReceivable(
            long id,
            String receivableNo,
            String originalAmount,
            String settledAmount,
            String status,
            long createdBy
    ) {
        jdbcTemplate.update("""
                insert into fin_receivable
                (id, company_id, account_book_id, receivable_no, source_type, source_id, source_no, direction,
                 customer_id, biz_date, original_amount, settled_amount, status, deleted_flag, remark,
                 created_by, updated_by, version)
                values (?, 1, 1, ?, 'TEST_RECEIVABLE', ?, ?, 'INCREASE',
                        8001, '2026-05-18', ?, ?, ?, 0, 'cancel settlement test',
                        ?, ?, 0)
                """, id, receivableNo, id, "SRC-" + id, new BigDecimal(originalAmount), new BigDecimal(settledAmount), status, createdBy, createdBy);
    }

    private void seedReceipt(long id, String receiptNo, String amount, String allocatedAmount, String status) {
        jdbcTemplate.update("""
                insert into fin_receipt
                (id, company_id, account_book_id, receipt_no, customer_id, receipt_date, amount, allocated_amount,
                 status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, 8001, '2026-05-18', ?, ?, ?, 0, 'cancel settlement test', 9001, 9001, 0)
                """, id, receiptNo, new BigDecimal(amount), new BigDecimal(allocatedAmount), status);
    }

    private void seedReceiptAllocation(long id, long receiptId, long receivableId, String amount) {
        jdbcTemplate.update("""
                insert into fin_receipt_allocation
                (id, receipt_id, receivable_id, amount, created_by, updated_by, version)
                values (?, ?, ?, ?, 9001, 9001, 0)
                """, id, receiptId, receivableId, new BigDecimal(amount));
    }

    private BigDecimal readAmount(String tableName, String columnName, long id) {
        return jdbcTemplate.queryForObject(
                "select " + columnName + " from " + tableName + " where id = ?",
                BigDecimal.class,
                id
        );
    }

    private String readText(String tableName, String columnName, long id) {
        return jdbcTemplate.queryForObject(
                "select " + columnName + " from " + tableName + " where id = ?",
                String.class,
                id
        );
    }
}

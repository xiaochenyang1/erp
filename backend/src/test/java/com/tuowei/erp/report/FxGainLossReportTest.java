package com.tuowei.erp.report;

import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 汇兑损益报表的真实 SQL 校验：UNION、数据范围子查询和过滤条件都要在数据库上跑通。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FxGainLossReportTest {

    private static final String REPORT_VIEW = "report:view";
    private static final LocalDateTime AUDIT_TIME = LocalDateTime.of(2026, 9, 18, 9, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from fin_receipt_allocation where id between 94240 and 94279");
        jdbcTemplate.update("delete from fin_payment_allocation where id between 94240 and 94279");
        jdbcTemplate.update("delete from fin_receipt where id between 94240 and 94279");
        jdbcTemplate.update("delete from fin_payment where id between 94240 and 94279");
        jdbcTemplate.update("delete from fin_receivable where id between 94240 and 94279");
        jdbcTemplate.update("delete from fin_payable where id between 94240 and 94279");
    }

    @Test
    @WithErpUser(authorities = {REPORT_VIEW})
    void listsRealisedExchangeDifferencesFromBothSidesNewestFirst() throws Exception {
        seedReceivable(94241L, "AR-FX-1", 9701L, "USD", "7.20", "1000.00", "1000.00");
        seedReceipt(94242L, "FR-FX-1", 9701L, LocalDate.of(2026, 9, 10), "USD", "7.30", "1000.00", "POSTED");
        seedReceiptAllocation(94243L, 94242L, 94241L, "1000.00", "7300.000000", "7200.000000", "100.000000");

        seedPayable(94244L, "AP-FX-1", 9801L, "EUR", "8.00", "500.00", "500.00");
        seedPayment(94245L, "FP-FX-1", 9801L, LocalDate.of(2026, 9, 12), "EUR", "8.10", "500.00", "POSTED");
        seedPaymentAllocation(94246L, 94245L, 94244L, "500.00", "4050.000000", "4000.000000", "50.000000");

        // 无汇兑差额的本位币核销不进报表。
        seedReceivable(94247L, "AR-FX-2", 9702L, "CNY", "1", "300.00", "300.00");
        seedReceipt(94248L, "FR-FX-2", 9702L, LocalDate.of(2026, 9, 14), "CNY", "1", "300.00", "POSTED");
        seedReceiptAllocation(94249L, 94248L, 94247L, "300.00", "300.000000", "300.000000", "0.000000");

        // 已作废的收款单，其汇兑损益已经冲回，不能再出现在报表里。
        seedReceivable(94250L, "AR-FX-3", 9703L, "USD", "7.20", "200.00", "0.00");
        seedReceipt(94251L, "FR-FX-3", 9703L, LocalDate.of(2026, 9, 16), "USD", "7.40", "200.00", "CANCELLED");
        seedReceiptAllocation(94252L, 94251L, 94250L, "200.00", "1480.000000", "1440.000000", "40.000000");

        mockMvc.perform(get("/api/reports/fx-gain-loss")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].settlementNo").value("FP-FX-1"))
                .andExpect(jsonPath("$.data.records[0].direction").value("PAYABLE"))
                .andExpect(jsonPath("$.data.records[0].currencyCode").value("EUR"))
                .andExpect(jsonPath("$.data.records[0].bookingRate").value(8.0))
                .andExpect(jsonPath("$.data.records[0].settlementRate").value(8.1))
                .andExpect(jsonPath("$.data.records[0].fxGainLossAmount").value(50.0))
                .andExpect(jsonPath("$.data.records[1].settlementNo").value("FR-FX-1"))
                .andExpect(jsonPath("$.data.records[1].direction").value("RECEIVABLE"))
                .andExpect(jsonPath("$.data.records[1].subledgerNo").value("AR-FX-1"))
                .andExpect(jsonPath("$.data.records[1].fxGainLossAmount").value(100.0));
    }

    @Test
    @WithErpUser(authorities = {REPORT_VIEW})
    void filtersByDirectionCurrencyAndSettlementDate() throws Exception {
        seedReceivable(94261L, "AR-FX-F1", 9711L, "USD", "7.20", "1000.00", "1000.00");
        seedReceipt(94262L, "FR-FX-F1", 9711L, LocalDate.of(2026, 8, 5), "USD", "7.30", "1000.00", "POSTED");
        seedReceiptAllocation(94263L, 94262L, 94261L, "1000.00", "7300.000000", "7200.000000", "100.000000");

        seedReceivable(94264L, "AR-FX-F2", 9712L, "EUR", "8.00", "100.00", "100.00");
        seedReceipt(94265L, "FR-FX-F2", 9712L, LocalDate.of(2026, 9, 5), "EUR", "8.20", "100.00", "POSTED");
        seedReceiptAllocation(94266L, 94265L, 94264L, "100.00", "820.000000", "800.000000", "20.000000");

        seedPayable(94267L, "AP-FX-F1", 9811L, "USD", "7.00", "400.00", "400.00");
        seedPayment(94268L, "FP-FX-F1", 9811L, LocalDate.of(2026, 9, 6), "USD", "7.10", "400.00", "POSTED");
        seedPaymentAllocation(94269L, 94268L, 94267L, "400.00", "2840.000000", "2800.000000", "40.000000");

        mockMvc.perform(get("/api/reports/fx-gain-loss")
                        .param("direction", "RECEIVABLE")
                        .param("currencyCode", "usd")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].settlementNo").value("FR-FX-F1"));

        mockMvc.perform(get("/api/reports/fx-gain-loss")
                        .param("settlementDateFrom", "2026-09-01")
                        .param("settlementDateTo", "2026-09-30")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[0].settlementNo").value("FP-FX-F1"))
                .andExpect(jsonPath("$.data.records[1].settlementNo").value("FR-FX-F2"));
    }

    @Test
    @WithErpUser(
            userId = 93889L,
            authorities = {REPORT_VIEW},
            allScope = false,
            selfScoped = true
    )
    void appliesSubledgerDataScopeToAllocationRows() throws Exception {
        seedReceivable(94271L, "AR-FX-S1", 9721L, "USD", "7.20", "1000.00", "1000.00", 93889L);
        seedReceipt(94272L, "FR-FX-S1", 9721L, LocalDate.of(2026, 9, 10), "USD", "7.30", "1000.00", "POSTED");
        seedReceiptAllocation(94273L, 94272L, 94271L, "1000.00", "7300.000000", "7200.000000", "100.000000");

        seedReceivable(94274L, "AR-FX-S2", 9722L, "USD", "7.20", "1000.00", "1000.00", 99999L);
        seedReceipt(94275L, "FR-FX-S2", 9722L, LocalDate.of(2026, 9, 11), "USD", "7.30", "1000.00", "POSTED");
        seedReceiptAllocation(94276L, 94275L, 94274L, "1000.00", "7300.000000", "7200.000000", "100.000000");

        mockMvc.perform(get("/api/reports/fx-gain-loss")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].subledgerNo").value("AR-FX-S1"));
    }

    private void seedReceivable(
            long id,
            String receivableNo,
            long customerId,
            String currencyCode,
            String exchangeRate,
            String originalAmount,
            String settledAmount
    ) {
        seedReceivable(id, receivableNo, customerId, currencyCode, exchangeRate, originalAmount, settledAmount, 1L);
    }

    private void seedReceivable(
            long id,
            String receivableNo,
            long customerId,
            String currencyCode,
            String exchangeRate,
            String originalAmount,
            String settledAmount,
            long createdBy
    ) {
        jdbcTemplate.update("""
                insert into fin_receivable
                (id, company_id, account_book_id, receivable_no, source_type, source_id, source_no, direction,
                 customer_id, biz_date, original_amount, settled_amount, currency_code, exchange_rate,
                 base_original_amount, base_settled_amount, status, deleted_flag, remark,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, 'OPENING_RECEIVABLE', ?, ?, 'INCREASE',
                        ?, '2026-09-01', ?, ?, ?, ?,
                        ?, ?, 'SETTLED', 0, 'fx gain loss report test',
                        ?, ?, ?, ?, 0)
                """,
                id,
                receivableNo,
                id,
                receivableNo,
                customerId,
                new BigDecimal(originalAmount),
                new BigDecimal(settledAmount),
                currencyCode,
                new BigDecimal(exchangeRate),
                new BigDecimal(originalAmount).multiply(new BigDecimal(exchangeRate)),
                new BigDecimal(settledAmount).multiply(new BigDecimal(exchangeRate)),
                createdBy,
                AUDIT_TIME,
                createdBy,
                AUDIT_TIME);
    }

    private void seedPayable(
            long id,
            String payableNo,
            long supplierId,
            String currencyCode,
            String exchangeRate,
            String originalAmount,
            String settledAmount
    ) {
        jdbcTemplate.update("""
                insert into fin_payable
                (id, company_id, account_book_id, payable_no, source_type, source_id, source_no, direction,
                 supplier_id, biz_date, original_amount, settled_amount, currency_code, exchange_rate,
                 base_original_amount, base_settled_amount, status, deleted_flag, remark,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, 'OPENING_PAYABLE', ?, ?, 'INCREASE',
                        ?, '2026-09-01', ?, ?, ?, ?,
                        ?, ?, 'SETTLED', 0, 'fx gain loss report test',
                        1, ?, 1, ?, 0)
                """,
                id,
                payableNo,
                id,
                payableNo,
                supplierId,
                new BigDecimal(originalAmount),
                new BigDecimal(settledAmount),
                currencyCode,
                new BigDecimal(exchangeRate),
                new BigDecimal(originalAmount).multiply(new BigDecimal(exchangeRate)),
                new BigDecimal(settledAmount).multiply(new BigDecimal(exchangeRate)),
                AUDIT_TIME,
                AUDIT_TIME);
    }

    private void seedReceipt(
            long id,
            String receiptNo,
            long customerId,
            LocalDate receiptDate,
            String currencyCode,
            String exchangeRate,
            String amount,
            String status
    ) {
        jdbcTemplate.update("""
                insert into fin_receipt
                (id, company_id, account_book_id, receipt_no, customer_id, receipt_date, amount, allocated_amount,
                 currency_code, exchange_rate, base_amount, status, deleted_flag, remark,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'fx gain loss report test',
                        1, ?, 1, ?, 0)
                """,
                id,
                receiptNo,
                customerId,
                receiptDate,
                new BigDecimal(amount),
                new BigDecimal(amount),
                currencyCode,
                new BigDecimal(exchangeRate),
                new BigDecimal(amount).multiply(new BigDecimal(exchangeRate)),
                status,
                AUDIT_TIME,
                AUDIT_TIME);
    }

    private void seedPayment(
            long id,
            String paymentNo,
            long supplierId,
            LocalDate paymentDate,
            String currencyCode,
            String exchangeRate,
            String amount,
            String status
    ) {
        jdbcTemplate.update("""
                insert into fin_payment
                (id, company_id, account_book_id, payment_no, supplier_id, payment_date, amount, allocated_amount,
                 currency_code, exchange_rate, base_amount, status, deleted_flag, remark,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'fx gain loss report test',
                        1, ?, 1, ?, 0)
                """,
                id,
                paymentNo,
                supplierId,
                paymentDate,
                new BigDecimal(amount),
                new BigDecimal(amount),
                currencyCode,
                new BigDecimal(exchangeRate),
                new BigDecimal(amount).multiply(new BigDecimal(exchangeRate)),
                status,
                AUDIT_TIME,
                AUDIT_TIME);
    }

    private void seedReceiptAllocation(
            long id,
            long receiptId,
            long receivableId,
            String amount,
            String baseAmount,
            String baseSettledAmount,
            String fxGainLossAmount
    ) {
        jdbcTemplate.update("""
                insert into fin_receipt_allocation
                (id, company_id, account_book_id, receipt_id, receivable_id, amount, base_amount,
                 base_settled_amount, fx_gain_loss_amount, created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, ?, ?, ?, ?, ?, 1, ?, 1, ?, 0)
                """,
                id,
                receiptId,
                receivableId,
                new BigDecimal(amount),
                new BigDecimal(baseAmount),
                new BigDecimal(baseSettledAmount),
                new BigDecimal(fxGainLossAmount),
                AUDIT_TIME,
                AUDIT_TIME);
    }

    private void seedPaymentAllocation(
            long id,
            long paymentId,
            long payableId,
            String amount,
            String baseAmount,
            String baseSettledAmount,
            String fxGainLossAmount
    ) {
        jdbcTemplate.update("""
                insert into fin_payment_allocation
                (id, company_id, account_book_id, payment_id, payable_id, amount, base_amount,
                 base_settled_amount, fx_gain_loss_amount, created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, ?, ?, ?, ?, ?, 1, ?, 1, ?, 0)
                """,
                id,
                paymentId,
                payableId,
                new BigDecimal(amount),
                new BigDecimal(baseAmount),
                new BigDecimal(baseSettledAmount),
                new BigDecimal(fxGainLossAmount),
                AUDIT_TIME,
                AUDIT_TIME);
    }
}

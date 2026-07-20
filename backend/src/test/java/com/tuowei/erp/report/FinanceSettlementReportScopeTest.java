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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FinanceSettlementReportScopeTest {

    private static final String REPORT_VIEW = "report:view";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from fin_payable where id between 94180 and 94199");
        jdbcTemplate.update("delete from fin_receivable where id between 94180 and 94199");
        jdbcTemplate.update("delete from sal_delivery where id between 94180 and 94199");
    }

    @Test
    @WithErpUser(
            userId = 93888L,
            authorities = {REPORT_VIEW},
            allScope = false,
            selfScoped = true
    )
    void selfScopedCreatorCanViewOwnOpeningReceivable() throws Exception {
        seedReceivable(
                94181L,
                "AR-RPT-OPENING-SELF-1",
                98101L,
                7201L,
                "OPENING_RECEIVABLE",
                "OPENING-AR-001",
                "900.00",
                "200.00",
                "PARTIALLY_SETTLED",
                93888L
        );

        mockMvc.perform(get("/api/reports/finance-settlements")
                        .param("direction", "RECEIVABLE")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].bizNo").value("AR-RPT-OPENING-SELF-1"))
                .andExpect(jsonPath("$.data.records[0].direction").value("RECEIVABLE"))
                .andExpect(jsonPath("$.data.records[0].sourceType").value("OPENING_RECEIVABLE"))
                .andExpect(jsonPath("$.data.records[0].remainingAmount").value(700.00));
    }

    @Test
    @WithErpUser(
            userId = 93888L,
            authorities = {REPORT_VIEW},
            allScope = false,
            selfScoped = true
    )
    void selfScopedCreatorCanViewReceivableFromOwnSalesDeliveryOnly() throws Exception {
        seedSalesDelivery(94186L, "SD-RPT-SCOPE-OWN", 93888L);
        seedSalesDelivery(94188L, "SD-RPT-SCOPE-OTHER", 99999L);
        seedReceivable(
                94187L,
                "AR-RPT-SALES-DELIVERY-OWN",
                94186L,
                7201L,
                "SALES_DELIVERY",
                "SD-RPT-SCOPE-OWN",
                LocalDate.of(2026, 5, 10),
                "800.00",
                "300.00",
                "PARTIALLY_SETTLED",
                99999L
        );
        seedReceivable(
                94189L,
                "AR-RPT-SALES-DELIVERY-OTHER",
                94188L,
                7201L,
                "SALES_DELIVERY",
                "SD-RPT-SCOPE-OTHER",
                LocalDate.of(2026, 5, 11),
                "900.00",
                "100.00",
                "PARTIALLY_SETTLED",
                93888L
        );

        mockMvc.perform(get("/api/reports/finance-settlements")
                        .param("direction", "RECEIVABLE")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].bizNo").value("AR-RPT-SALES-DELIVERY-OWN"))
                .andExpect(jsonPath("$.data.records[0].sourceType").value("SALES_DELIVERY"))
                .andExpect(jsonPath("$.data.records[0].remainingAmount").value(500.00));
    }

    @Test
    @WithErpUser(authorities = {REPORT_VIEW})
    void allDirectionReportMergesPayablesAndReceivablesByBizDate() throws Exception {
        seedPayable(
                94182L,
                "AP-RPT-MIXED-1",
                98201L,
                7301L,
                "TEST_PAYABLE",
                "OPENING-AP-001",
                LocalDate.of(2026, 5, 30),
                "300.00",
                "50.00",
                "PARTIALLY_SETTLED",
                1L
        );
        seedReceivable(
                94183L,
                "AR-RPT-MIXED-1",
                98202L,
                7201L,
                "TEST_RECEIVABLE",
                "OPENING-AR-002",
                LocalDate.of(2026, 5, 29),
                "400.00",
                "100.00",
                "PARTIALLY_SETTLED",
                1L
        );
        seedPayable(
                94184L,
                "AP-RPT-MIXED-2",
                98203L,
                7301L,
                "TEST_PAYABLE",
                "OPENING-AP-002",
                LocalDate.of(2026, 5, 28),
                "500.00",
                "200.00",
                "PARTIALLY_SETTLED",
                1L
        );
        seedReceivable(
                94185L,
                "AR-RPT-MIXED-2",
                98204L,
                7201L,
                "TEST_RECEIVABLE",
                "OPENING-AR-003",
                LocalDate.of(2026, 5, 27),
                "600.00",
                "250.00",
                "PARTIALLY_SETTLED",
                1L
        );

        mockMvc.perform(get("/api/reports/finance-settlements")
                        .param("pageNo", "2")
                        .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(4))
                .andExpect(jsonPath("$.data.records[0].bizNo").value("AP-RPT-MIXED-2"))
                .andExpect(jsonPath("$.data.records[0].direction").value("PAYABLE"))
                .andExpect(jsonPath("$.data.records[0].remainingAmount").value(300.00))
                .andExpect(jsonPath("$.data.records[1].bizNo").value("AR-RPT-MIXED-2"))
                .andExpect(jsonPath("$.data.records[1].direction").value("RECEIVABLE"))
                .andExpect(jsonPath("$.data.records[1].remainingAmount").value(350.00));
    }

    private void seedReceivable(
            long id,
            String receivableNo,
            long sourceId,
            long customerId,
            String sourceType,
            String sourceNo,
            LocalDate bizDate,
            String originalAmount,
            String settledAmount,
            String status,
            long createdBy
    ) {
        jdbcTemplate.update("""
                insert into fin_receivable
                (id, company_id, account_book_id, receivable_no, source_type, source_id, source_no, direction,
                 customer_id, biz_date, original_amount, settled_amount, status, deleted_flag, remark,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, ?, ?, ?, 'INCREASE',
                        ?, ?, ?, ?, ?, 0, 'report opening scope test',
                        ?, ?, ?, ?, 0)
                """,
                id,
                receivableNo,
                sourceType,
                sourceId,
                sourceNo,
                customerId,
                bizDate,
                new BigDecimal(originalAmount),
                new BigDecimal(settledAmount),
                status,
                createdBy,
                LocalDateTime.of(2026, 5, 9, 9, 0),
                createdBy,
                LocalDateTime.of(2026, 5, 9, 9, 0));
    }

    private void seedReceivable(
            long id,
            String receivableNo,
            long sourceId,
            long customerId,
            String sourceType,
            String sourceNo,
            String originalAmount,
            String settledAmount,
            String status,
            long createdBy
    ) {
        seedReceivable(
                id,
                receivableNo,
                sourceId,
                customerId,
                sourceType,
                sourceNo,
                LocalDate.of(2026, 5, 9),
                originalAmount,
                settledAmount,
                status,
                createdBy
        );
    }

    private void seedPayable(
            long id,
            String payableNo,
            long sourceId,
            long supplierId,
            String sourceType,
            String sourceNo,
            LocalDate bizDate,
            String originalAmount,
            String settledAmount,
            String status,
            long createdBy
    ) {
        jdbcTemplate.update("""
                insert into fin_payable
                (id, company_id, account_book_id, payable_no, source_type, source_id, source_no, direction,
                 supplier_id, biz_date, original_amount, settled_amount, status, deleted_flag, remark,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, ?, ?, ?, 'INCREASE',
                        ?, ?, ?, ?, ?, 0, 'report mixed pagination test',
                        ?, ?, ?, ?, 0)
                """,
                id,
                payableNo,
                sourceType,
                sourceId,
                sourceNo,
                supplierId,
                bizDate,
                new BigDecimal(originalAmount),
                new BigDecimal(settledAmount),
                status,
                createdBy,
                LocalDateTime.of(2026, 5, 9, 9, 0),
                createdBy,
                LocalDateTime.of(2026, 5, 9, 9, 0));
    }

    private void seedSalesDelivery(long id, String deliveryNo, long createdBy) {
        jdbcTemplate.update("""
                insert into sal_delivery
                (id, company_id, account_book_id, delivery_no, order_id, warehouse_id, delivery_date, status,
                 total_quantity, total_amount, total_tax_amount, deleted_flag, remark,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, ?, 1, '2026-05-10', 'POSTED',
                        1.0000, 100.00, 0.00, 0, 'report source scope test',
                        ?, ?, ?, ?, 0)
                """,
                id,
                deliveryNo,
                id,
                createdBy,
                LocalDateTime.of(2026, 5, 9, 9, 0),
                createdBy,
                LocalDateTime.of(2026, 5, 9, 9, 0));
    }
}

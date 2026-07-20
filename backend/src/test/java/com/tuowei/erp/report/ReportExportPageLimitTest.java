package com.tuowei.erp.report;

import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "erp.report.max-export-rows=5000")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportExportPageLimitTest {

    private static final String REPORT_VIEW = "report:view";
    private static final long SUPPLIER_ID = 95501L;
    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 5, 31);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 31, 9, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from pur_order where id between 95500 and 95750");
    }

    @Test
    @WithErpUser(authorities = {REPORT_VIEW})
    void exportCanReturnMoreRowsThanInteractivePageLimit() throws Exception {
        for (int index = 0; index < 201; index++) {
            seedPurchaseOrder(95500L + index, "PO-RPT-EXPORT-PAGE-LIMIT-%03d".formatted(index + 1));
        }

        MvcResult result = mockMvc.perform(get("/api/reports/purchase-orders/export")
                        .param("supplierId", Long.toString(SUPPLIER_ID)))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("PO-RPT-EXPORT-PAGE-LIMIT-001")));
    }

    private void seedPurchaseOrder(long id, String orderNo) {
        jdbcTemplate.update("""
                insert into pur_order
                (id, company_id, account_book_id, order_no, supplier_id, order_date, delivery_date, status,
                 approval_status, receipt_status, total_quantity, total_amount, total_tax_amount,
                 deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, ?, ?, ?, 'APPROVED',
                        'APPROVED', 'NOT_RECEIVED', ?, ?, ?,
                        0, 'report export page limit test', 95500, ?, 95500, ?, 0)
                """,
                id,
                orderNo,
                SUPPLIER_ID,
                ORDER_DATE,
                ORDER_DATE,
                new BigDecimal("1.0000"),
                new BigDecimal("10.00"),
                new BigDecimal("0.00"),
                NOW,
                NOW);
    }
}

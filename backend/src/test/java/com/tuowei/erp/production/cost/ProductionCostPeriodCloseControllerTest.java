package com.tuowei.erp.production.cost;

import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductionCostPeriodCloseControllerTest {
    private static final long PERIOD_ID = 894501L;
    private static final long FIRST_ORDER_ID = 894001L;
    private static final long SECOND_ORDER_ID = 894002L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        cleanup();
        seedPeriod();
        seedOrder(FIRST_ORDER_ID, "MO-894001", "105.00", "100.00", LocalDate.of(2026, 6, 20));
        seedOrder(SECOND_ORDER_ID, "MO-894002", "98.00", "100.00", LocalDate.of(2026, 6, 21));
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("""
                delete from fin_voucher_entry
                where voucher_id in (
                    select id from fin_voucher
                    where source_type = 'PRODUCTION_COST_CLOSE'
                      and source_id in (select id from prd_cost_close where order_id between 894000 and 894099)
                )
                """);
        jdbcTemplate.update("""
                delete from fin_voucher
                where source_type = 'PRODUCTION_COST_CLOSE'
                  and source_id in (select id from prd_cost_close where order_id between 894000 and 894099)
                """);
        jdbcTemplate.update("delete from sys_audit_log where business_type = 'PRODUCTION_COST_PERIOD_CLOSE' and business_id = ?", PERIOD_ID);
        jdbcTemplate.update("delete from sys_operation_log where request_uri like '/api/production/cost-close-periods/%'");
        jdbcTemplate.update("delete from prd_cost_close where order_id between 894000 and 894099");
        jdbcTemplate.update("delete from prd_completion where order_id between 894000 and 894099");
        jdbcTemplate.update("delete from prd_cost_entry where order_id between 894000 and 894099");
        jdbcTemplate.update("delete from prd_order where id between 894000 and 894099");
        jdbcTemplate.update("delete from fin_account_period where id = ?", PERIOD_ID);
    }

    @Test
    @WithErpUser(authorities = {"production:order:view", "production:order:cost:close"})
    void previewsBatchClosesWithPartialFailureAndReturnsAuditEvidence() throws Exception {
        mockMvc.perform(get("/api/production/cost-close-periods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(PERIOD_ID))
                .andExpect(jsonPath("$.data[0].periodMonth").value("2026-06"))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"));

        mockMvc.perform(get("/api/production/cost-close-periods/{periodId}/preview", PERIOD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidateCount").value(2))
                .andExpect(jsonPath("$.data.positiveVarianceAmount").value(5.00))
                .andExpect(jsonPath("$.data.negativeVarianceAmount").value(-2.00))
                .andExpect(jsonPath("$.data.netVarianceAmount").value(3.00));

        mockMvc.perform(post("/api/production/cost-close-periods/{periodId}/batch-close", PERIOD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderIds": [894001, 899999],
                                  "remark": "controller period close"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestedCount").value(2))
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failureCount").value(1))
                .andExpect(jsonPath("$.data.closedVarianceAmount").value(5.00))
                .andExpect(jsonPath("$.data.results[0].closeNo").isNotEmpty())
                .andExpect(jsonPath("$.data.results[1].errorCode").value("NOT_IN_PERIOD"));

        assertThat(jdbcTemplate.queryForObject(
                "select cost_close_status from prd_order where id = ?", String.class, FIRST_ORDER_ID
        )).isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject(
                "select cost_close_status from prd_order where id = ?", String.class, SECOND_ORDER_ID
        )).isEqualTo("OPEN");

        mockMvc.perform(get("/api/production/cost-close-periods/{periodId}/evidence", PERIOD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].batch.successCount").value(1))
                .andExpect(jsonPath("$.data.records[0].batch.failureCount").value(1))
                .andExpect(jsonPath("$.data.records[0].batch.results[1].errorCode").value("NOT_IN_PERIOD"));
    }

    private void seedPeriod() {
        jdbcTemplate.update("""
                insert into fin_account_period
                (id, company_id, account_book_id, period_year, period_month, start_date, end_date, status,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, 2026, '2026-06', '2026-06-01', '2026-06-30', 'OPEN', 0, ?, 0, ?, 0)
                """,
                PERIOD_ID,
                LocalDateTime.of(2026, 6, 1, 9, 0),
                LocalDateTime.of(2026, 6, 1, 9, 0));
    }

    private void seedOrder(long id, String orderNo, String inputAmount, String finishedAmount, LocalDate completionDate) {
        jdbcTemplate.update("""
                insert into prd_order
                (id, company_id, account_book_id, order_no, bom_id, product_id, material_warehouse_id,
                 finished_warehouse_id, planned_qty, completed_qty, planned_start_date, planned_finish_date,
                 status, issued_amount, labor_amount, overhead_amount, finished_amount,
                 finished_material_amount, finished_labor_amount, finished_overhead_amount,
                 cost_close_status, settled_variance_amount, deleted_flag, created_by, created_time,
                 updated_by, updated_time, version)
                values (?, 1, 1, ?, 1, ?, 1, 1, 1, 1, ?, ?, 'COMPLETED',
                        ?, 0, 0, ?, ?, 0, 0, 'OPEN', 0, 0, 0, ?, 0, ?, 0)
                """,
                id,
                orderNo,
                id + 1000,
                completionDate.minusDays(2),
                completionDate,
                new java.math.BigDecimal(inputAmount),
                new java.math.BigDecimal(finishedAmount),
                new java.math.BigDecimal(finishedAmount),
                LocalDateTime.of(2026, 6, 20, 9, 0),
                LocalDateTime.of(2026, 6, 20, 9, 0));
        jdbcTemplate.update("""
                insert into prd_completion
                (id, company_id, account_book_id, completion_no, order_id, completion_date,
                 completed_qty, completed_amount, material_cost_amount, labor_cost_amount, overhead_cost_amount,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, 1, 1, ?, ?, ?, 1, ?, ?, 0, 0, 0, ?, 0, ?, 0)
                """,
                id + 100,
                "PC-" + id,
                id,
                completionDate,
                new java.math.BigDecimal(finishedAmount),
                new java.math.BigDecimal(finishedAmount),
                LocalDateTime.of(2026, 6, 20, 9, 0),
                LocalDateTime.of(2026, 6, 20, 9, 0));
    }
}

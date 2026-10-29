package com.tuowei.erp.production.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductionOrderControllerTest {

    private static final long FINISHED_PRODUCT_ID = 893001L;
    private static final long MATERIAL_PRODUCT_ID = 893002L;
    private static final long MATERIAL_WAREHOUSE_ID = 893101L;
    private static final long FINISHED_WAREHOUSE_ID = 893102L;
    private static final long LOCATION_ID_OFFSET = 500000000000000000L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        cleanup();
        seedProduct(FINISHED_PRODUCT_ID, "PRD-FG-893001", "制造成品");
        seedProduct(MATERIAL_PRODUCT_ID, "PRD-MAT-893002", "制造材料");
        seedWarehouse(MATERIAL_WAREHOUSE_ID, "PRD-MWH-893101", "材料仓");
        seedWarehouse(FINISHED_WAREHOUSE_ID, "PRD-FWH-893102", "成品仓");
        seedMaterialBalance();
        seedPeriod(893501L, 2026, "2026-05", "OPEN");
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("""
                delete from fin_voucher_entry
                where voucher_id in (
                    select id from fin_voucher
                    where source_type in ('PRODUCTION_ISSUE', 'PRODUCTION_COMPLETION', 'PRODUCTION_COMPLETION_REVERSAL', 'PRODUCTION_RETURN')
                )
                """);
        jdbcTemplate.update("delete from fin_voucher where source_type in ('PRODUCTION_ISSUE', 'PRODUCTION_COMPLETION', 'PRODUCTION_COMPLETION_REVERSAL', 'PRODUCTION_RETURN')");
        jdbcTemplate.update("delete from inv_reservation_event where source_type = 'PRODUCTION_ORDER'");
        jdbcTemplate.update("delete from inv_reservation where source_type = 'PRODUCTION_ORDER'");
        jdbcTemplate.update("delete from inv_txn where biz_type in ('PRODUCTION_ISSUE', 'PRODUCTION_COMPLETION', 'PRODUCTION_COMPLETION_REVERSAL', 'PRODUCTION_RETURN')");
        jdbcTemplate.update("delete from prd_completion_reversal");
        jdbcTemplate.update("delete from prd_return_line");
        jdbcTemplate.update("delete from prd_return");
        jdbcTemplate.update("delete from prd_completion");
        jdbcTemplate.update("delete from prd_issue_line");
        jdbcTemplate.update("delete from prd_issue");
        jdbcTemplate.update("delete from inv_balance where warehouse_id between 893100 and 893199");
        jdbcTemplate.update("delete from prd_order_material");
        jdbcTemplate.update("delete from prd_order");
        jdbcTemplate.update("delete from prd_bom_line");
        jdbcTemplate.update("delete from prd_bom");
        jdbcTemplate.update("delete from md_location where warehouse_id between 893100 and 893199");
        jdbcTemplate.update("delete from md_warehouse where id between 893100 and 893199");
        jdbcTemplate.update("delete from md_product where id between 893000 and 893999");
        jdbcTemplate.update("delete from fin_account_period where id between 893500 and 893599");
    }

    @Test
    @WithErpUser(authorities = {
            "production:bom:manage", "production:bom:view",
            "production:order:create", "production:order:view", "production:order:release",
            "production:order:issue", "production:order:complete"
    })
    void createsReleasesIssuesAndCompletesOrderThroughHttpApi() throws Exception {
        long bomId = idOf(mockMvc.perform(post("/api/production/boms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 893001,
                                  "baseQty": 1.0000,
                                  "remark": "controller bom",
                                  "lines": [
                                    {"materialProductId": 893002, "qtyPer": 2.0000, "lossRate": 0.0000, "remark": "material"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines[0].lineNo").value(1))
                .andReturn());

        long orderId = idOf(mockMvc.perform(post("/api/production/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bomId": %d,
                                  "finishedWarehouseId": 893102,
                                  "materialWarehouseId": 893101,
                                  "plannedQty": 5.0000,
                                  "plannedStartDate": "2026-05-20",
                                  "plannedFinishDate": "2026-05-21",
                                  "remark": "controller order"
                                }
                                """.formatted(bomId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.materials[0].requiredQty").value(10.0000))
                .andReturn());

        mockMvc.perform(post("/api/production/orders/{id}/release", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RELEASED"));

        mockMvc.perform(post("/api/production/orders/{id}/issue", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MATERIAL_ISSUED"));

        mockMvc.perform(post("/api/production/orders/{id}/complete", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(get("/api/production/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    @WithErpUser(authorities = {
            "production:bom:manage", "production:bom:view",
            "production:order:create", "production:order:view", "production:order:release",
            "production:order:issue", "production:order:complete"
    })
    void issuesAndCompletesOrderInBatchesThroughHttpApi() throws Exception {
        long orderId = createReleasedOrder("2026-05-20", "2026-05-23");
        long materialLineId = orderMaterialId(orderId);

        mockMvc.perform(post("/api/production/orders/{id}/issue", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "issueDate": "2026-05-20",
                                  "remark": "first batch issue",
                                  "lines": [
                                    {"orderMaterialId": %d, "issueQty": 5.0000, "remark": "first half"}
                                  ]
                                }
                                """.formatted(materialLineId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MATERIAL_ISSUED"))
                .andExpect(jsonPath("$.data.materials[0].issuedQty").value(5.0000));

        mockMvc.perform(post("/api/production/orders/{id}/complete", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "completionDate": "2026-05-21",
                                  "completedQty": 2.5000,
                                  "remark": "first batch completion"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MATERIAL_ISSUED"))
                .andExpect(jsonPath("$.data.completedQty").value(2.5000));

        mockMvc.perform(post("/api/production/orders/{id}/issue", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "issueDate": "2026-05-22",
                                  "remark": "second batch issue",
                                  "lines": [
                                    {"orderMaterialId": %d, "issueQty": 5.0000, "remark": "second half"}
                                  ]
                                }
                                """.formatted(materialLineId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MATERIAL_ISSUED"))
                .andExpect(jsonPath("$.data.materials[0].issuedQty").value(10.0000));

        mockMvc.perform(post("/api/production/orders/{id}/complete", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "completionDate": "2026-05-23",
                                  "completedQty": 2.5000,
                                  "remark": "second batch completion"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedQty").value(5.0000));

        assertThat(rowCount("prd_issue", orderId)).isEqualTo(2);
        assertThat(rowCount("prd_completion", orderId)).isEqualTo(2);
        assertThat(rowCount("fin_voucher", "PRODUCTION_ISSUE")).isEqualTo(2);
        assertThat(rowCount("fin_voucher", "PRODUCTION_COMPLETION")).isEqualTo(2);
    }

    @Test
    @WithErpUser(authorities = {
            "production:bom:manage", "production:bom:view",
            "production:order:create", "production:order:view", "production:order:release",
            "production:order:issue", "production:order:complete", "production:order:reverse-completion",
            "production:order:return"
    })
    void reversesCompletedOrderThroughHttpApiAndAllowsMaterialReturn() throws Exception {
        long orderId = createReleasedOrder("2026-05-20", "2026-05-24");
        long materialLineId = orderMaterialId(orderId);

        mockMvc.perform(post("/api/production/orders/{id}/issue", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "issueDate": "2026-05-20",
                                  "remark": "issue before completion reversal",
                                  "lines": [
                                    {"orderMaterialId": %d, "issueQty": 10.0000, "remark": "issue all"}
                                  ]
                                }
                                """.formatted(materialLineId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MATERIAL_ISSUED"));

        mockMvc.perform(post("/api/production/orders/{id}/complete", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "completionDate": "2026-05-21",
                                  "completedQty": 5.0000,
                                  "remark": "completion before reversal"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mockMvc.perform(post("/api/production/orders/{id}/reverse-completion", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reversalDate": "2026-05-22",
                                  "remark": "reverse completed output"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MATERIAL_ISSUED"))
                .andExpect(jsonPath("$.data.completedQty").value(0.0000))
                .andExpect(jsonPath("$.data.finishedAmount").value(0.00));

        assertThat(rowCount("prd_completion_reversal", orderId)).isEqualTo(1);
        assertThat(rowCount("fin_voucher", "PRODUCTION_COMPLETION_REVERSAL")).isEqualTo(1);
        assertThat(inventoryTxnCount("PRODUCTION_COMPLETION_REVERSAL", "OUT")).isEqualTo(1);
        assertThat(onHandQty(FINISHED_WAREHOUSE_ID, FINISHED_PRODUCT_ID)).isEqualByComparingTo("0.0000");

        mockMvc.perform(post("/api/production/orders/{id}/return-materials", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnDate": "2026-05-23",
                                  "remark": "return material after completion reversal",
                                  "lines": [
                                    {"orderMaterialId": %d, "returnQty": 10.0000, "remark": "return all"}
                                  ]
                                }
                                """.formatted(materialLineId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RELEASED"))
                .andExpect(jsonPath("$.data.materials[0].issuedQty").value(0.0000));
    }

    @Test
    @WithErpUser(authorities = {"production:order:view"})
    void reverseCompletionRequiresReverseCompletionPermission() throws Exception {
        mockMvc.perform(post("/api/production/orders/{id}/reverse-completion", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reversalDate": "2026-05-22",
                                  "remark": "permission check"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithErpUser(authorities = {
            "production:bom:manage", "production:bom:view",
            "production:order:create", "production:order:view", "production:order:release",
            "production:order:issue", "production:order:return"
    })
    void returnsIssuedMaterialThroughHttpApiAndAllowsReissue() throws Exception {
        long orderId = createReleasedOrder("2026-05-20", "2026-05-23");
        long materialLineId = orderMaterialId(orderId);

        mockMvc.perform(post("/api/production/orders/{id}/issue", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "issueDate": "2026-05-20",
                                  "remark": "issue before return",
                                  "lines": [
                                    {"orderMaterialId": %d, "issueQty": 8.0000, "remark": "issue part"}
                                  ]
                                }
                                """.formatted(materialLineId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MATERIAL_ISSUED"))
                .andExpect(jsonPath("$.data.materials[0].issuedQty").value(8.0000));
        BigDecimal reservedAfterIssue = materialReservedQty();

        mockMvc.perform(post("/api/production/orders/{id}/return-materials", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnDate": "2026-05-21",
                                  "remark": "return unused material",
                                  "lines": [
                                    {"orderMaterialId": %d, "returnQty": 3.0000, "remark": "return part"}
                                  ]
                                }
                                """.formatted(materialLineId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MATERIAL_ISSUED"))
                .andExpect(jsonPath("$.data.materials[0].issuedQty").value(5.0000));

        assertThat(materialReservedQty()).isEqualByComparingTo(reservedAfterIssue.add(new BigDecimal("3.0000")));
        assertThat(rowCount("prd_return", orderId)).isEqualTo(1);
        assertThat(rowCount("fin_voucher", "PRODUCTION_RETURN")).isEqualTo(1);

        mockMvc.perform(post("/api/production/orders/{id}/issue", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "issueDate": "2026-05-22",
                                  "remark": "reissue returned material",
                                  "lines": [
                                    {"orderMaterialId": %d, "issueQty": 3.0000, "remark": "reissue part"}
                                  ]
                                }
                                """.formatted(materialLineId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materials[0].issuedQty").value(8.0000));
    }

    @Test
    @WithErpUser(authorities = {"production:order:view"})
    void returnMaterialsRequiresReturnPermission() throws Exception {
        mockMvc.perform(post("/api/production/orders/{id}/return-materials", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "returnDate": "2026-05-21",
                                  "remark": "permission check",
                                  "lines": [
                                    {"orderMaterialId": 1, "returnQty": 1.0000, "remark": "return"}
                                  ]
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithErpUser(authorities = {
            "production:bom:manage", "production:bom:view",
            "production:order:create", "production:order:view", "production:order:release",
            "production:order:issue"
    })
    void issueRejectsLockedPeriodBusinessDate() throws Exception {
        seedPeriod(893502L, 2034, "2034-05", "LOCKED");
        long orderId = createReleasedOrder("2034-05-20", "2034-05-21");

        mockMvc.perform(post("/api/production/orders/{id}/issue", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "issueDate": "2034-05-20",
                                  "remark": "locked issue"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("2034-05 已锁定")))
                .andExpect(jsonPath("$.message").value(containsString("生产领料")));
    }

    @Test
    @WithErpUser(authorities = {
            "production:bom:manage", "production:bom:view",
            "production:order:create", "production:order:view", "production:order:release",
            "production:order:issue", "production:order:complete"
    })
    void completionRejectsLockedPeriodBusinessDate() throws Exception {
        seedPeriod(893503L, 2034, "2034-05", "LOCKED");
        long orderId = createReleasedOrder("2026-05-20", "2034-05-21");

        mockMvc.perform(post("/api/production/orders/{id}/issue", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "issueDate": "2026-05-20",
                                  "remark": "open issue"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("MATERIAL_ISSUED"));

        mockMvc.perform(post("/api/production/orders/{id}/complete", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "completionDate": "2034-05-21",
                                  "remark": "locked completion"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("2034-05 已锁定")))
                .andExpect(jsonPath("$.message").value(containsString("生产完工入库")));
    }

    @Test
    @WithErpUser(authorities = {"production:order:view"})
    void orderCreateRequiresCreatePermission() throws Exception {
        mockMvc.perform(post("/api/production/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                          "bomId": 1,
                          "finishedWarehouseId": 1,
                          "materialWarehouseId": 1,
                          "plannedQty": 1.0000,
                          "plannedStartDate": "2026-05-20",
                          "plannedFinishDate": "2026-05-21",
                          "remark": "permission check"
                        }
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithErpUser(authorities = {
            "production:bom:manage", "production:bom:view",
            "production:order:create", "production:order:view"
    })
    void listsBomsAndOrdersForProductionPages() throws Exception {
        long bomId = idOf(mockMvc.perform(post("/api/production/boms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 893001,
                                  "baseQty": 1.0000,
                                  "remark": "list bom",
                                  "lines": [
                                    {"materialProductId": 893002, "qtyPer": 2.0000, "lossRate": 0.0000, "remark": "material"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn());

        long orderId = idOf(mockMvc.perform(post("/api/production/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bomId": %d,
                                  "finishedWarehouseId": 893102,
                                  "materialWarehouseId": 893101,
                                  "plannedQty": 5.0000,
                                  "plannedStartDate": "2026-05-20",
                                  "plannedFinishDate": "2026-05-21",
                                  "remark": "list order"
                                }
                                """.formatted(bomId)))
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(get("/api/production/boms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(bomId));

        mockMvc.perform(get("/api/production/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(orderId))
                .andExpect(jsonPath("$.data.records[0].status").value("DRAFT"));
    }

    private long idOf(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return root.path("data").path("id").asLong();
    }

    private long createReleasedOrder(String plannedStartDate, String plannedFinishDate) throws Exception {
        long bomId = idOf(mockMvc.perform(post("/api/production/boms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": 893001,
                                  "baseQty": 1.0000,
                                  "remark": "guard bom",
                                  "lines": [
                                    {"materialProductId": 893002, "qtyPer": 2.0000, "lossRate": 0.0000, "remark": "material"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn());

        long orderId = idOf(mockMvc.perform(post("/api/production/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bomId": %d,
                                  "finishedWarehouseId": 893102,
                                  "materialWarehouseId": 893101,
                                  "plannedQty": 5.0000,
                                  "plannedStartDate": "%s",
                                  "plannedFinishDate": "%s",
                                  "remark": "guard order"
                                }
                                """.formatted(bomId, plannedStartDate, plannedFinishDate)))
                .andExpect(status().isOk())
                .andReturn());

        mockMvc.perform(post("/api/production/orders/{id}/release", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RELEASED"));
        return orderId;
    }

    private long orderMaterialId(long orderId) {
        return jdbcTemplate.queryForObject(
                "select id from prd_order_material where order_id = ? and material_product_id = ?",
                Long.class,
                orderId,
                MATERIAL_PRODUCT_ID
        );
    }

    private Integer rowCount(String table, long orderId) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where order_id = ?", Integer.class, orderId);
    }

    private Integer rowCount(String table, String sourceType) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where source_type = ?", Integer.class, sourceType);
    }

    private Integer inventoryTxnCount(String bizType, String direction) {
        return jdbcTemplate.queryForObject(
                "select count(*) from inv_txn where biz_type = ? and direction = ?",
                Integer.class,
                bizType,
                direction
        );
    }

    private BigDecimal onHandQty(long warehouseId, long productId) {
        return jdbcTemplate.queryForObject("""
                select qty_on_hand
                from inv_balance
                where warehouse_id = ?
                  and product_id = ?
                """, BigDecimal.class, warehouseId, productId);
    }

    private BigDecimal materialReservedQty() {
        return jdbcTemplate.queryForObject("""
                select qty_reserved
                from inv_balance
                where warehouse_id = ?
                  and product_id = ?
                """, BigDecimal.class, MATERIAL_WAREHOUSE_ID, MATERIAL_PRODUCT_ID);
    }

    private void seedProduct(long id, String code, String name) {
        jdbcTemplate.update("""
                insert into md_product
                (id, company_id, account_book_id, product_code, product_name, product_type, category_name,
                 specification, unit_name, purchase_price, sale_price, tax_rate, status, deleted_flag,
                 remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 'STANDARD', '生产测试', '标准', '件', 10.00, 20.00, 13.00,
                        'ACTIVE', 0, '生产测试', 893001, 893001, 0)
                """, id, code, name);
    }

    private void seedWarehouse(long id, String code, String name) {
        jdbcTemplate.update("""
                insert into md_warehouse
                (id, company_id, account_book_id, warehouse_code, warehouse_name, dept_id, manager_user_id,
                 address, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 1, 1, '生产测试地址', 'ACTIVE', 0, '生产测试', 893001, 893001, 0)
                """, id, code, name);
        jdbcTemplate.update("""
                insert into md_location
                (id, company_id, account_book_id, warehouse_id, location_code, location_name,
                 is_default, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, 'MAIN', '默认库位', 1, 'ACTIVE', 0,
                        '生产测试默认库位', 893001, 893001, 0)
                """, id + LOCATION_ID_OFFSET, id);
    }

    private void seedMaterialBalance() {
        jdbcTemplate.update("""
                insert into inv_balance
                (id, company_id, account_book_id, warehouse_id, location_id, product_id, qty_on_hand, qty_reserved,
                 amount_on_hand, created_by, updated_by, version)
                values (893201, 1, 1, 893101, ?, 893002, 100.0000, 0.0000, 500.00, 893001, 893001, 0)
                """, MATERIAL_WAREHOUSE_ID + LOCATION_ID_OFFSET);
    }

    private void seedPeriod(long id, int year, String periodMonth, String status) {
        LocalDate start = LocalDate.parse(periodMonth + "-01");
        jdbcTemplate.update("""
                insert into fin_account_period
                (id, company_id, account_book_id, period_year, period_month, start_date, end_date, status,
                 created_by, updated_by, version)
                values (?, 1, 1, ?, ?, ?, ?, ?, 893001, 893001, 0)
                """, id, year, periodMonth, start, start.withDayOfMonth(start.lengthOfMonth()), status);
    }
}

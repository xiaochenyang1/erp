package com.tuowei.erp.production.routing;

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

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductionRoutingControllerTest {

    private static final long PRODUCT_ID = 896001L;
    private static final long BOM_ID = 896101L;
    private static final long WORK_CENTER_ONE_ID = 896201L;
    private static final long WORK_CENTER_TWO_ID = 896202L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        cleanup();
        seedProduct(PRODUCT_ID, "PRD-FG-896001", "控制器成品");
        seedBom(BOM_ID, "BOM-896101", PRODUCT_ID);
        seedWorkCenter(WORK_CENTER_ONE_ID, "WC-896201", "切割中心");
        seedWorkCenter(WORK_CENTER_TWO_ID, "WC-896202", "焊接中心");
    }

    @AfterEach
    void cleanup() {
        deleteIfExists("prd_routing_operation");
        deleteIfExists("prd_routing");
        deleteIfExists("prd_work_center");
        deleteIfExists("prd_bom_line");
        deleteIfExists("prd_bom");
        jdbcTemplate.update("delete from md_product where id between 896000 and 896999");
    }

    @Test
    @WithErpUser(authorities = {
            "production:routing:create",
            "production:routing:view",
            "production:routing:enable",
            "production:routing:disable"
    })
    void createsDetailsListsAndDisablesRoutingThroughHttpApi() throws Exception {
        long id = idOf(mockMvc.perform(post("/api/production/routings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "routingCode": " RT-001 ",
                                  "routingName": " 标准装配路线 ",
                                  "bomId": %d,
                                  "remark": " 首版路线 ",
                                  "operations": [
                                    {
                                      "operationCode": " OP-10 ",
                                      "operationName": " 切割 ",
                                      "workCenterId": %d,
                                      "standardMinutes": 12.50,
                                      "remark": " 首工序 "
                                    },
                                    {
                                      "operationCode": " OP-20 ",
                                      "operationName": " 焊接 ",
                                      "workCenterId": %d,
                                      "standardMinutes": 18.00,
                                      "remark": " 二工序 "
                                    }
                                  ]
                                }
                                """.formatted(BOM_ID, WORK_CENTER_ONE_ID, WORK_CENTER_TWO_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routingCode").value("RT-001"))
                .andExpect(jsonPath("$.data.routingName").value("标准装配路线"))
                .andExpect(jsonPath("$.data.bomId").value(BOM_ID))
                .andExpect(jsonPath("$.data.bomNo").value("BOM-896101"))
                .andExpect(jsonPath("$.data.productId").value(PRODUCT_ID))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.remark").value("首版路线"))
                .andExpect(jsonPath("$.data.operations[0].lineNo").value(1))
                .andExpect(jsonPath("$.data.operations[0].operationCode").value("OP-10"))
                .andExpect(jsonPath("$.data.operations[0].workCenterCode").value("WC-896201"))
                .andExpect(jsonPath("$.data.operations[1].lineNo").value(2))
                .andExpect(jsonPath("$.data.operations[1].operationCode").value("OP-20"))
                .andExpect(jsonPath("$.data.operations[1].workCenterCode").value("WC-896202"))
                .andReturn());

        mockMvc.perform(get("/api/production/routings/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.routingCode").value("RT-001"))
                .andExpect(jsonPath("$.data.routingName").value("标准装配路线"))
                .andExpect(jsonPath("$.data.bomNo").value("BOM-896101"))
                .andExpect(jsonPath("$.data.productId").value(PRODUCT_ID))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.operations[0].lineNo").value(1))
                .andExpect(jsonPath("$.data.operations[0].workCenterName").value("切割中心"))
                .andExpect(jsonPath("$.data.operations[1].lineNo").value(2))
                .andExpect(jsonPath("$.data.operations[1].workCenterName").value("焊接中心"));

        mockMvc.perform(get("/api/production/routings")
                        .param("bomId", String.valueOf(BOM_ID))
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(id))
                .andExpect(jsonPath("$.data.records[0].routingCode").value("RT-001"))
                .andExpect(jsonPath("$.data.records[0].bomNo").value("BOM-896101"))
                .andExpect(jsonPath("$.data.records[0].productId").value(PRODUCT_ID))
                .andExpect(jsonPath("$.data.records[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.records[0].operations[0].lineNo").value(1))
                .andExpect(jsonPath("$.data.records[0].operations[1].lineNo").value(2));

        mockMvc.perform(post("/api/production/routings/{id}/disable", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("DISABLED"))
                .andExpect(jsonPath("$.data.operations[0].lineNo").value(1))
                .andExpect(jsonPath("$.data.operations[1].lineNo").value(2));

        mockMvc.perform(post("/api/production/routings/{id}/enable", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    private long idOf(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return root.path("data").path("id").asLong();
    }

    private void seedProduct(long id, String code, String name) {
        jdbcTemplate.update("""
                insert into md_product
                (id, company_id, account_book_id, product_code, product_name, product_type, category_name,
                 specification, unit_name, purchase_price, sale_price, tax_rate, status, deleted_flag,
                 remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 'STANDARD', '生产测试', '标准', '件', 10.00, 20.00, 13.00,
                        'ACTIVE', 0, '生产测试', 896001, 896001, 0)
                """, id, code, name);
    }

    private void seedBom(long id, String bomNo, long productId) {
        jdbcTemplate.update("""
                insert into prd_bom
                (id, company_id, account_book_id, bom_no, product_id, base_qty, status, deleted_flag,
                 remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 1.0000, 'ACTIVE', 0, 'routing controller bom', 896001, 896001, 0)
                """, id, bomNo, productId);
    }

    private void seedWorkCenter(long id, String code, String name) {
        jdbcTemplate.update("""
                insert into prd_work_center
                (id, company_id, account_book_id, work_center_code, work_center_name, status, deleted_flag,
                 remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 'ACTIVE', 0, 'routing controller work center', 896001, 896001, 0)
                """, id, code, name);
    }

    private void deleteIfExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = database()
                  and table_name = ?
                """, Integer.class, tableName);
        if (count != null && count > 0) {
            jdbcTemplate.update("delete from " + tableName);
        }
    }
}

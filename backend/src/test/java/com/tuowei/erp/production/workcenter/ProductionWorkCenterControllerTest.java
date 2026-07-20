package com.tuowei.erp.production.workcenter;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductionWorkCenterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        cleanup();
    }

    @AfterEach
    void cleanup() {
        deleteIfExists("prd_routing_operation");
        deleteIfExists("prd_routing");
        deleteIfExists("prd_work_center");
        deleteIfExists("prd_bom_line");
        deleteIfExists("prd_bom");
        jdbcTemplate.update("delete from md_product where id between 894000 and 894999");
    }

    @Test
    @WithErpUser(authorities = {
            "production:work-center:create",
            "production:work-center:view",
            "production:work-center:update",
            "production:work-center:enable",
            "production:work-center:disable"
    })
    void createsUpdatesListsDisablesAndEnablesWorkCenterThroughHttpApi() throws Exception {
        long id = idOf(mockMvc.perform(post("/api/production/work-centers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workCenterCode": " WC-001 ",
                                  "workCenterName": " 焊接一车间 ",
                                  "remark": " 首条产线 "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workCenterCode").value("WC-001"))
                .andExpect(jsonPath("$.data.workCenterName").value("焊接一车间"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.remark").value("首条产线"))
                .andReturn());

        mockMvc.perform(put("/api/production/work-centers/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workCenterName": " 焊接总装线 ",
                                  "remark": " 已更新 "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.workCenterCode").value("WC-001"))
                .andExpect(jsonPath("$.data.workCenterName").value("焊接总装线"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.remark").value("已更新"));

        mockMvc.perform(get("/api/production/work-centers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.workCenterCode").value("WC-001"))
                .andExpect(jsonPath("$.data.workCenterName").value("焊接总装线"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.remark").value("已更新"));

        mockMvc.perform(get("/api/production/work-centers")
                        .param("keyword", "总装")
                        .param("status", " active ")
                        .param("pageNo", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(id))
                .andExpect(jsonPath("$.data.records[0].workCenterCode").value("WC-001"))
                .andExpect(jsonPath("$.data.records[0].workCenterName").value("焊接总装线"))
                .andExpect(jsonPath("$.data.records[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.records[0].remark").value("已更新"));

        mockMvc.perform(post("/api/production/work-centers/{id}/disable", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(post("/api/production/work-centers/{id}/enable", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @WithErpUser(authorities = {
            "production:work-center:create",
            "production:work-center:disable"
    })
    void returnsClientErrorWhenDisablingReferencedWorkCenter() throws Exception {
        long id = idOf(mockMvc.perform(post("/api/production/work-centers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "workCenterCode": "WC-894501",
                                  "workCenterName": "冲突工位",
                                  "remark": "controller conflict"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn());

        seedProduct(894501L, "PRD-FG-894501", "冲突成品");
        seedProduct(894502L, "PRD-MAT-894502", "冲突材料");
        seedBom(894601L, "BOM-894601", 894501L, 894502L);
        jdbcTemplate.update("""
                insert into prd_routing
                (id, company_id, account_book_id, routing_code, routing_name, bom_id, status, deleted_flag, remark, created_by, updated_by, version)
                values (894701, 1, 1, 'RT-894701', '冲突路线', 894601, 'ACTIVE', 0, 'routing', 1, 1, 0)
                """);
        jdbcTemplate.update("""
                insert into prd_routing_operation
                (id, company_id, account_book_id, routing_id, line_no, operation_code, operation_name, work_center_id, standard_minutes, remark, created_by, updated_by, version)
                values (894702, 1, 1, 894701, 1, 'OP-10', '工序', ?, 10.00, 'line', 1, 1, 0)
                """, id);

        mockMvc.perform(post("/api/production/work-centers/{id}/disable", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("工作中心已被启用工艺路线引用，不能停用"));
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
                values (?, 1, 1, ?, ?, 'STANDARD', '工作中心测试', '标准', '件', 10.00, 20.00, 13.00,
                        'ACTIVE', 0, '工作中心测试', 894001, 894001, 0)
                """, id, code, name);
    }

    private void seedBom(long id, String bomNo, long productId, long materialProductId) {
        jdbcTemplate.update("""
                insert into prd_bom
                (id, company_id, account_book_id, bom_no, product_id, base_qty, status, deleted_flag, remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, 1.0000, 'ACTIVE', 0, 'seed bom', 894001, 894001, 0)
                """, id, bomNo, productId);
        jdbcTemplate.update("""
                insert into prd_bom_line
                (id, company_id, account_book_id, bom_id, line_no, material_product_id, qty_per, loss_rate, remark, created_by, updated_by, version)
                values (894602, 1, 1, ?, 1, ?, 1.0000, 0.0000, 'seed line', 894001, 894001, 0)
                """, id, materialProductId);
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

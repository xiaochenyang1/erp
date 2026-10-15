package com.tuowei.erp.inventory.mrp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.testsupport.WithErpUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.StreamSupport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real HTTP, Flyway schema, calculation, persistence and order conversion; each test rolls back. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithErpUser(authorities = {"inventory:mrp:view", "inventory:mrp:run", "inventory:mrp:convert"})
class MrpPlanWorkflowTest {
    private static final long PRODUCT = 98101501, MATERIAL = 98101502, WAREHOUSE = 98101503,
            SUPPLIER = 98101504, CUSTOMER = 98101505, BOM = 98101506;
    private long nextId = 98101510;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        for (long id : List.of(PRODUCT, MATERIAL)) {
            insert("md_product", id, "product_code", "MRP-AUDIT-" + id, "product_name", "MRP " + id,
                    "product_type", "GOODS", "category_name", "test", "unit_name", "pcs",
                    "purchase_price", 1, "sale_price", 1, "tax_rate", 0, "status", "ACTIVE");
        }
        insert("md_warehouse", WAREHOUSE, "warehouse_code", "MRP-AUDIT-WH", "warehouse_name", "MRP warehouse", "dept_id", 1, "manager_user_id", 1, "status", "ACTIVE");
        insert("md_supplier", SUPPLIER, "supplier_code", "MRP-AUDIT-SP", "supplier_name", "MRP supplier",
                "settlement_method", "MONTH_END", "status", "ACTIVE");
        insert("md_customer", CUSTOMER, "customer_code", "MRP-AUDIT-CU", "customer_name", "MRP customer",
                "settlement_method", "MONTH_END", "credit_limit", 0, "status", "ACTIVE");
    }

    @Test
    void safetyShortageSurvivesRunReloadAndPurchaseConversion() throws Exception {
        stock(PRODUCT, "6", 1);
        insert("inv_alert_rule", nextId++, "warehouse_id", WAREHOUSE, "product_id", PRODUCT, "min_qty", 10, "enabled", 1);
        JsonNode run = data(post("/api/inventory/mrp/run"));
        JsonNode suggestion = line(run, "purchaseLines", PRODUCT);
        assertThat(suggestion.path("demandQty").decimalValue()).isEqualByComparingTo("10");
        assertThat(suggestion.path("netQty").decimalValue()).isEqualByComparingTo("4");
        String runPath = "/api/inventory/mrp/runs/" + run.path("id").asText();
        assertThat(line(data(get(runPath)), "purchaseLines", PRODUCT).path("netQty").decimalValue()).isEqualByComparingTo("4");
        String convert = runPath + "/lines/" + suggestion.path("id").asText() + "/convert";
        JsonNode converted = data(post(convert).contentType(MediaType.APPLICATION_JSON).content("{\"supplierId\":" + SUPPLIER + "}"));
        assertThat(converted.path("status").asText()).isEqualTo("CONVERTED");
        assertThat(jdbc.queryForObject("select qty from pur_order_line where order_id = ? and product_id = ?",
                BigDecimal.class, converted.path("convertedBizId").asLong(), PRODUCT)).isEqualByComparingTo("4");
        long before = jdbc.queryForObject("select count(*) from pur_order where supplier_id = ?", Long.class, SUPPLIER);
        mvc.perform(post(convert)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from pur_order where supplier_id = ?", Long.class, SUPPLIER)).isEqualTo(before);
    }

    @Test
    void sharedMaterialPurchaseCoversBothSalesAndProduction() throws Exception {
        sales(PRODUCT, "6"); sales(MATERIAL, "5"); stock(MATERIAL, "5", 1); bom("1", "1", "0");
        JsonNode run = data(post("/api/inventory/mrp/run"));
        assertThat(line(run, "purchaseLines", MATERIAL).path("netQty").decimalValue()).isEqualByComparingTo("6");
        assertThat(line(run, "purchaseLines", MATERIAL).path("demandQty").decimalValue()).isEqualByComparingTo("11");
        assertThat(line(run, "productionLines", PRODUCT).path("netQty").decimalValue()).isEqualByComparingTo("6");
    }

    @Test
    void plannedComponentQuantityMatchesConvertedProductionOrderIncludingBaseAndLoss() throws Exception {
        sales(PRODUCT, "6"); sales(MATERIAL, "5"); stock(MATERIAL, "5", 1); bom("10", "4", "0.1");
        JsonNode run = data(post("/api/inventory/mrp/run"));
        JsonNode material = line(run, "purchaseLines", MATERIAL);
        assertThat(material.path("netQty").decimalValue()).isEqualByComparingTo("2.64");
        JsonNode product = line(run, "productionLines", PRODUCT);
        String base = "/api/inventory/mrp/runs/" + run.path("id").asText() + "/lines/";
        JsonNode mo = data(post(base + product.path("id").asText() + "/convert")
                .contentType(MediaType.APPLICATION_JSON).content("{\"finishedWarehouseId\":" + WAREHOUSE + ",\"materialWarehouseId\":" + WAREHOUSE + "}"));
        assertThat(mo.path("convertedBizType").asText()).isEqualTo("PRODUCTION_ORDER");
        assertThat(jdbc.queryForObject("select required_qty from prd_order_material where order_id = ? and material_product_id = ?",
                BigDecimal.class, mo.path("convertedBizId").asLong(), MATERIAL)).isEqualByComparingTo("2.64");
        JsonNode po = data(post(base + material.path("id").asText() + "/convert")
                .contentType(MediaType.APPLICATION_JSON).content("{\"supplierId\":" + SUPPLIER + "}"));
        assertThat(jdbc.queryForObject("select qty from pur_order_line where order_id = ? and product_id = ?",
                BigDecimal.class, po.path("convertedBizId").asLong(), MATERIAL)).isEqualByComparingTo("2.64");
    }

    @Test
    void anotherAccountBooksInventoryDoesNotCoverCurrentShortage() throws Exception {
        stock(PRODUCT, "6", 1); stock(PRODUCT, "100", 2);
        insert("inv_alert_rule", nextId++, "warehouse_id", WAREHOUSE, "product_id", PRODUCT, "min_qty", 10, "enabled", 1);
        JsonNode suggestion = line(data(post("/api/inventory/mrp/run")), "purchaseLines", PRODUCT);
        assertThat(suggestion.path("onHandQty").decimalValue()).isEqualByComparingTo("6");
        assertThat(suggestion.path("netQty").decimalValue()).isEqualByComparingTo("4");
    }

    @Test
    void disabledSafetyRulesDoNotCreateDemand() throws Exception {
        stock(PRODUCT, "6", 1);
        insert("inv_alert_rule", nextId++, "warehouse_id", WAREHOUSE, "product_id", PRODUCT, "min_qty", 10, "enabled", 0);
        JsonNode run = data(post("/api/inventory/mrp/run"));
        assertThat(StreamSupport.stream(run.path("purchaseLines").spliterator(), false)
                .noneMatch(l -> l.path("productId").asLong() == PRODUCT)).isTrue();
    }

    private void sales(long product, String quantity) {
        long order = nextId++;
        insert("sal_order", order, "order_no", "MRP-AUDIT-SO-" + order, "customer_id", CUSTOMER,
                "order_date", "2030-01-01", "warehouse_id", WAREHOUSE, "status", "APPROVED", "approval_status", "APPROVED");
        insert("sal_order_line", nextId++, "order_id", order, "line_no", 1, "product_id", product,
                "qty", quantity, "price", 1, "tax_rate", 0, "amount", quantity, "tax_amount", 0);
    }
    private void stock(long product, String quantity, long book) {
        insert("inv_balance", nextId++, "account_book_id", book, "warehouse_id", WAREHOUSE,
                "product_id", product, "qty_on_hand", quantity, "amount_on_hand", quantity);
    }
    private void bom(String base, String per, String loss) {
        insert("prd_bom", BOM, "bom_no", "MRP-AUDIT-BOM", "product_id", PRODUCT, "base_qty", base, "status", "ACTIVE");
        insert("prd_bom_line", nextId++, "bom_id", BOM, "line_no", 1, "material_product_id", MATERIAL, "qty_per", per, "loss_rate", loss);
    }
    private void insert(String table, long id, Object... fields) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", id); values.put("company_id", 1L); values.put("account_book_id", 1L);
        for (int i = 0; i < fields.length; i += 2) values.put((String) fields[i], fields[i + 1]);
        jdbc.update("insert into " + table + " (" + String.join(",", values.keySet()) + ") values ("
                + String.join(",", Collections.nCopies(values.size(), "?")) + ")", values.values().toArray());
    }
    private JsonNode line(JsonNode run, String type, long product) {
        var matches = StreamSupport.stream(run.path(type).spliterator(), false)
                .filter(l -> l.path("productId").asLong() == product).toList();
        assertThat(matches).hasSize(1);
        return matches.get(0);
    }
    private JsonNode data(MockHttpServletRequestBuilder request) throws Exception {
        String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode result = json.readTree(body);
        assertThat(result.path("code").asText()).isEqualTo("0");
        return result.path("data");
    }
}

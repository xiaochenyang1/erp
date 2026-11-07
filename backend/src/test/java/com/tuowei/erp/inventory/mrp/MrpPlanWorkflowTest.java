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
@WithErpUser(authorities = {"inventory:mrp:view", "inventory:mrp:run", "inventory:mrp:convert",
        "purchase:order:create", "purchase:order:update", "production:order:create", "masterdata:product:update"})
class MrpPlanWorkflowTest {
    private static final long PRODUCT = 98101501, MATERIAL = 98101502, WAREHOUSE = 98101503,
            SUPPLIER = 98101504, CUSTOMER = 98101505, BOM = 98101506, RAW = 98101507,
            MATERIAL_WAREHOUSE = 98101508, MATERIAL_BOM = 98101509;
    private long nextId = 98101510;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        for (long id : List.of(PRODUCT, MATERIAL, RAW)) {
            insert("md_product", id, "product_code", "MRP-AUDIT-" + id, "product_name", "MRP " + id,
                    "product_type", "GOODS", "category_name", "test", "unit_name", "pcs",
                    "purchase_price", 1, "sale_price", 1, "tax_rate", 0, "status", "ACTIVE");
        }
        insert("md_warehouse", WAREHOUSE, "warehouse_code", "MRP-AUDIT-WH", "warehouse_name", "MRP warehouse", "dept_id", 1, "manager_user_id", 1, "status", "ACTIVE");
        insert("md_warehouse", MATERIAL_WAREHOUSE, "warehouse_code", "MRP-AUDIT-MWH", "warehouse_name", "MRP material warehouse", "dept_id", 1, "manager_user_id", 1, "status", "ACTIVE");
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
        assertThat(jdbc.queryForObject("select planned_warehouse_id from pur_order where id = ?", Long.class,
                converted.path("convertedBizId").asLong())).isEqualTo(WAREHOUSE);
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

    @Test
    void multilevelRunConvertsInAnyOrderAndDoesNotReplanTheSameShortage() throws Exception {
        sales(PRODUCT, "10"); stock(MATERIAL, "4", 1); stock(RAW, "10", 1); bom("1", "2", "0");
        insert("prd_bom", MATERIAL_BOM, "bom_no", "MRP-AUDIT-BOM-B", "product_id", MATERIAL, "base_qty", 1, "status", "ACTIVE");
        insert("prd_bom_line", nextId++, "bom_id", MATERIAL_BOM, "line_no", 1, "material_product_id", RAW, "qty_per", 3, "loss_rate", 0);
        JsonNode run = data(post("/api/inventory/mrp/run"));
        assertThat(run.path("algorithmVersion").asInt()).isEqualTo(2);
        JsonNode a = line(run,"productionLines",PRODUCT), b = line(run,"productionLines",MATERIAL), c = line(run,"purchaseLines",RAW);
        assertThat(a.path("netQty").decimalValue()).isEqualByComparingTo("10");
        assertThat(b.path("netQty").decimalValue()).isEqualByComparingTo("16");
        assertThat(c.path("netQty").decimalValue()).isEqualByComparingTo("38");
        assertThat(c.path("trace").path("level").asInt()).isEqualTo(2);
        assertThat(c.path("trace").path("sources").get(0).path("parentProductId").asLong()).isEqualTo(MATERIAL);
        String base = "/api/inventory/mrp/runs/" + run.path("id").asText() + "/lines/";
        JsonNode po = data(post(base+c.path("id").asText()+"/convert").contentType(MediaType.APPLICATION_JSON).content("{\"supplierId\":"+SUPPLIER+"}"));
        JsonNode moA = data(post(base+a.path("id").asText()+"/convert"));
        JsonNode moB = data(post(base+b.path("id").asText()+"/convert"));
        assertThat(jdbc.queryForObject("select qty from pur_order_line where order_id=?",BigDecimal.class,po.path("convertedBizId").asLong())).isEqualByComparingTo("38");
        assertThat(jdbc.queryForObject("select required_qty from prd_order_material where order_id=?",BigDecimal.class,moA.path("convertedBizId").asLong())).isEqualByComparingTo("20");
        assertThat(jdbc.queryForObject("select required_qty from prd_order_material where order_id=?",BigDecimal.class,moB.path("convertedBizId").asLong())).isEqualByComparingTo("48");
        JsonNode rerun = data(post("/api/inventory/mrp/run"));
        for (String type : List.of("purchaseLines", "productionLines")) {
            assertThat(StreamSupport.stream(rerun.path(type).spliterator(),false)
                    .noneMatch(n -> Set.of(PRODUCT,MATERIAL,RAW).contains(n.path("productId").asLong()))).isTrue();
        }
        assertThat(line(data(get("/api/inventory/mrp/runs/"+run.path("id").asText())),"purchaseLines",RAW).path("trace"))
                .isEqualTo(c.path("trace")); // Historical explanation survives conversion and recalculation.
    }

    @Test
    void rejectsSecondRunConversionAfterFirstRunCreatedPlannedSupply() throws Exception {
        sales(PRODUCT,"10");
        JsonNode first=data(post("/api/inventory/mrp/run")), second=data(post("/api/inventory/mrp/run"));
        data(post(convertPath(first,"purchaseLines",PRODUCT)).contentType(MediaType.APPLICATION_JSON).content("{\"supplierId\":"+SUPPLIER+"}"));
        long before=jdbc.queryForObject("select count(*) from pur_order where supplier_id=?",Long.class,SUPPLIER);
        mvc.perform(post(convertPath(second,"purchaseLines",PRODUCT)).contentType(MediaType.APPLICATION_JSON).content("{\"supplierId\":"+SUPPLIER+"}")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from pur_order where supplier_id=?",Long.class,SUPPLIER)).isEqualTo(before);
    }

    @Test
    void rejectsChangedBomAndCrossWarehouseConversion() throws Exception {
        sales(PRODUCT,"10"); bom("1","2","0");
        jdbc.update("update md_product set mrp_material_warehouse_id=? where id=?",MATERIAL_WAREHOUSE,PRODUCT);
        JsonNode run=data(post("/api/inventory/mrp/run"));
        assertThat(line(run,"purchaseLines",MATERIAL).path("warehouseId").asLong()).isEqualTo(MATERIAL_WAREHOUSE);
        mvc.perform(post(convertPath(run,"productionLines",PRODUCT)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"finishedWarehouseId\":"+MATERIAL_WAREHOUSE+"}")).andExpect(status().isBadRequest());
        jdbc.update("update prd_bom_line set qty_per=3 where bom_id=?",BOM);
        mvc.perform(post(convertPath(run,"productionLines",PRODUCT))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from prd_order where product_id=?",Long.class,PRODUCT)).isZero();
    }

    @Test
    void cycleAbortsRunWithoutPersistingPartialSuggestions() throws Exception {
        sales(PRODUCT,"10"); bom("1","2","0");
        insert("prd_bom",MATERIAL_BOM,"bom_no","MRP-AUDIT-CYCLE","product_id",MATERIAL,"base_qty",1,"status","ACTIVE");
        insert("prd_bom_line",nextId++,"bom_id",MATERIAL_BOM,"line_no",1,"material_product_id",PRODUCT,"qty_per",1,"loss_rate",0);
        long before=jdbc.queryForObject("select count(*) from inv_mrp_run",Long.class);
        mvc.perform(post("/api/inventory/mrp/run")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from inv_mrp_run",Long.class)).isEqualTo(before);
    }

    @Test
    void assignsWarehouseToApprovedLegacyPurchaseAndRequiresNewPlan() throws Exception {
        sales(PRODUCT,"10");
        JsonNode po=data(post("/api/purchase/orders").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                "supplierId",SUPPLIER,"orderDate","2026-11-06","lines",List.of(Map.of("productId",PRODUCT,"qty",4,"price",1,"taxRate",0))))));
        jdbc.update("update pur_order set status='APPROVED', approval_status='APPROVED' where id=?",po.path("id").asLong());
        JsonNode warned=data(post("/api/inventory/mrp/run"));
        assertThat(warned.path("calculationSnapshot").path("warnings")).anySatisfy(w -> assertThat(w.path("documentId").asLong()).isEqualTo(po.path("id").asLong()));
        data(put("/api/purchase/orders/"+po.path("id").asText()+"/planned-warehouse").contentType(MediaType.APPLICATION_JSON)
                .content("{\"plannedWarehouseId\":"+WAREHOUSE+"}"));
        mvc.perform(post(convertPath(warned,"purchaseLines",PRODUCT)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierId\":"+SUPPLIER+"}")).andExpect(status().isConflict());
        assertThat(line(data(post("/api/inventory/mrp/run")),"purchaseLines",PRODUCT).path("netQty").decimalValue()).isEqualByComparingTo("6");
        assertThat(jdbc.queryForObject("select status from pur_order where id=?",String.class,po.path("id").asLong())).isEqualTo("APPROVED");
    }

    @Test
    @WithErpUser(authorities={"inventory:mrp:view"})
    void viewPermissionCannotRunOrConvertPlans() throws Exception {
        mvc.perform(post("/api/inventory/mrp/run")).andExpect(status().isForbidden());
        mvc.perform(post("/api/inventory/mrp/runs/1/lines/2/convert")).andExpect(status().isForbidden());
    }

    @Test
    @WithErpUser(authorities={"inventory:mrp:run"})
    void runPermissionDoesNotGrantConversion() throws Exception {
        mvc.perform(post("/api/inventory/mrp/runs/1/lines/2/convert")).andExpect(status().isForbidden());
    }

    @Test
    @WithErpUser(authorities={"inventory:mrp:view","inventory:mrp:run","inventory:mrp:convert"})
    void conversionNeedsTheTargetDocumentCreatePermission() throws Exception {
        sales(PRODUCT,"10");
        JsonNode run=data(post("/api/inventory/mrp/run"));
        mvc.perform(post(convertPath(run,"purchaseLines",PRODUCT)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"supplierId\":"+SUPPLIER+"}")).andExpect(status().isForbidden());
        bom("1","2","0");
        run=data(post("/api/inventory/mrp/run"));
        mvc.perform(post(convertPath(run,"productionLines",PRODUCT))).andExpect(status().isForbidden());
    }

    @Test
    @WithErpUser(authorities={"inventory:mrp:view","inventory:mrp:run"}, allScope=false, selfScoped=true)
    void partialDataScopeCannotReadWholeBookPlanSnapshots() throws Exception {
        mvc.perform(post("/api/inventory/mrp/run")).andExpect(status().isForbidden());
        mvc.perform(get("/api/inventory/mrp/runs")).andExpect(status().isForbidden());
        mvc.perform(get("/api/inventory/mrp/runs/1")).andExpect(status().isForbidden());
    }

    private String convertPath(JsonNode run,String type,long product) {
        return "/api/inventory/mrp/runs/"+run.path("id").asText()+"/lines/"+line(run,type,product).path("id").asText()+"/convert";
    }

    @Test
    void productPolicyCanBeChangedPreservedByLegacyClientsAndCleared() throws Exception {
        var request = new LinkedHashMap<String,Object>();
        request.put("productName","MRP policy product"); request.put("categoryName","test"); request.put("unitName","pcs");
        request.put("purchasePrice",1); request.put("salePrice",1); request.put("taxRate",0);
        request.put("mrpPolicy",Map.of("supplyType","PURCHASE","materialWarehouseId",MATERIAL_WAREHOUSE));
        JsonNode saved=data(put("/api/masterdata/products/"+PRODUCT).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)));
        assertThat(saved.path("mrpPolicy").path("supplyType").asText()).isEqualTo("PURCHASE");
        assertThat(saved.path("mrpPolicy").path("materialWarehouseId").asLong()).isEqualTo(MATERIAL_WAREHOUSE);
        request.remove("mrpPolicy");
        data(put("/api/masterdata/products/"+PRODUCT).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)));
        assertThat(jdbc.queryForObject("select mrp_material_warehouse_id from md_product where id=?",Long.class,PRODUCT)).isEqualTo(MATERIAL_WAREHOUSE);
        request.put("mrpPolicy",Map.of("supplyType","AUTO"));
        data(put("/api/masterdata/products/"+PRODUCT).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)));
        assertThat(jdbc.queryForObject("select mrp_material_warehouse_id from md_product where id=?",Long.class,PRODUCT)).isNull();
        request.put("mrpPolicy",Map.of("supplyType","PRODUCTION","materialWarehouseId",999999));
        mvc.perform(put("/api/masterdata/products/"+PRODUCT).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select mrp_supply_type from md_product where id=?",String.class,PRODUCT)).isEqualTo("AUTO");
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

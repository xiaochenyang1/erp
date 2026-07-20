package com.tuowei.erp.purchase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.testsupport.WithErpUser;
import org.assertj.core.api.Assertions;
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
import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithErpUser(authorities = {"purchase:receipt:create", "purchase:receipt:post"})
class PurchaseReceiptControllerTest {

    private static final long COMPANY_ID = 1L;
    private static final long ACCOUNT_BOOK_ID = 1L;
    private static final long USER_ID = 1L;
    private static final long WAREHOUSE_ID = 895901L;
    private static final long PRODUCT_ID = 895201L;
    private static final long ORDER_ID = 895101L;
    private static final long ORDER_LINE_ID = 895111L;
    private static final long PERIOD_ID = 895001L;
    private static final LocalDate BIZ_DATE = LocalDate.of(2035, 5, 25);
    private static final LocalDateTime NOW = LocalDateTime.of(2035, 5, 25, 10, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        cleanup();
        seedWarehouse();
        seedOpenPeriod();
        seedProduct();
        seedPurchaseOrder();
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("""
                delete from fin_voucher_entry
                where voucher_id in (
                    select id from fin_voucher
                    where source_type = 'PURCHASE_RECEIPT'
                      and source_id in (
                          select id from pur_receipt
                          where order_id between 895000 and 895999
                      )
                )
                """);
        jdbcTemplate.update("""
                delete from fin_voucher
                where source_type = 'PURCHASE_RECEIPT'
                  and source_id in (
                      select id from pur_receipt
                      where order_id between 895000 and 895999
                  )
                """);
        jdbcTemplate.update("""
                delete from fin_payable
                where source_type = 'PURCHASE_RECEIPT'
                  and source_id in (
                      select id from pur_receipt
                      where order_id between 895000 and 895999
                  )
                """);
        jdbcTemplate.update("delete from inv_txn where product_id between 895200 and 895299 or biz_line_id between 895000 and 895999");
        jdbcTemplate.update("delete from inv_lot_balance where product_id between 895200 and 895299");
        jdbcTemplate.update("delete from inv_balance where product_id between 895200 and 895299");
        jdbcTemplate.update("delete from pur_receipt_line where order_line_id between 895000 and 895999 or product_id between 895200 and 895299");
        jdbcTemplate.update("delete from pur_receipt where order_id between 895000 and 895999");
        jdbcTemplate.update("delete from pur_order_line where order_id between 895000 and 895999 or product_id between 895200 and 895299");
        jdbcTemplate.update("delete from pur_order where id between 895000 and 895999");
        jdbcTemplate.update("delete from md_product where id between 895200 and 895299 or product_code like 'LOT-PR-%'");
        jdbcTemplate.update("delete from md_warehouse where id = ? or warehouse_code = 'LOT-PR-WH'", WAREHOUSE_ID);
        jdbcTemplate.update("delete from fin_account_period where id = ? or period_year = 2035", PERIOD_ID);
    }

    @Test
    void purchaseReceiptPersistsLotFieldsAndCreatesLotStock() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/purchase/receipts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": 895101,
                                  "warehouseId": 895901,
                                  "receiptDate": "2035-05-25",
                                  "remark": "lot receipt test",
                                  "lines": [
                                    {
                                      "orderLineId": 895111,
                                      "qty": 5.0000,
                                      "lotNo": "RCV-LOT-A",
                                      "productionDate": "2035-04-01",
                                      "expiryDate": "2035-12-31",
                                      "remark": "receipt lot line"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines[0].lotNo").value("RCV-LOT-A"))
                .andExpect(jsonPath("$.data.lines[0].productionDate").value("2035-04-01"))
                .andExpect(jsonPath("$.data.lines[0].expiryDate").value("2035-12-31"))
                .andReturn();
        long receiptId = responseId(created);

        mockMvc.perform(post("/api/purchase/receipts/{id}/post", receiptId))
                .andExpect(status().isOk());

        Map<String, Object> lotBalance = jdbcTemplate.queryForMap("""
                select lot_no, production_date, expiry_date, qty_on_hand, amount_on_hand
                from inv_lot_balance
                where company_id = ? and account_book_id = ? and warehouse_id = ? and product_id = ? and lot_no = 'RCV-LOT-A'
                """, COMPANY_ID, ACCOUNT_BOOK_ID, WAREHOUSE_ID, PRODUCT_ID);
        Assertions.assertThat(lotBalance.get("LOT_NO")).isEqualTo("RCV-LOT-A");
        Assertions.assertThat(dateValue(lotBalance.get("PRODUCTION_DATE"))).isEqualTo(LocalDate.of(2035, 4, 1));
        Assertions.assertThat(dateValue(lotBalance.get("EXPIRY_DATE"))).isEqualTo(LocalDate.of(2035, 12, 31));
        Assertions.assertThat((BigDecimal) lotBalance.get("QTY_ON_HAND")).isEqualByComparingTo("5.0000");
        Assertions.assertThat((BigDecimal) lotBalance.get("AMOUNT_ON_HAND")).isEqualByComparingTo("50.00");

        Map<String, Object> txn = jdbcTemplate.queryForMap("""
                select lot_no, production_date, expiry_date, lot_key, qty, amount
                from inv_txn
                where company_id = ? and product_id = ? and biz_type = 'PURCHASE_RECEIPT' and direction = 'IN'
                """, COMPANY_ID, PRODUCT_ID);
        Assertions.assertThat(txn.get("LOT_NO")).isEqualTo("RCV-LOT-A");
        Assertions.assertThat(dateValue(txn.get("PRODUCTION_DATE"))).isEqualTo(LocalDate.of(2035, 4, 1));
        Assertions.assertThat(dateValue(txn.get("EXPIRY_DATE"))).isEqualTo(LocalDate.of(2035, 12, 31));
        Assertions.assertThat(txn.get("LOT_KEY")).isEqualTo("RCV-LOT-A");
        Assertions.assertThat((BigDecimal) txn.get("QTY")).isEqualByComparingTo("5.0000");
        Assertions.assertThat((BigDecimal) txn.get("AMOUNT")).isEqualByComparingTo("50.00");
    }

    private void seedOpenPeriod() {
        jdbcTemplate.update("""
                insert into fin_account_period
                (id, company_id, account_book_id, period_year, period_month, start_date, end_date, status,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, 2035, '2035-05', '2035-05-01', '2035-05-31', 'OPEN',
                        ?, ?, ?, ?, 0)
                """, PERIOD_ID, COMPANY_ID, ACCOUNT_BOOK_ID, USER_ID, NOW, USER_ID, NOW);
    }

    private void seedWarehouse() {
        jdbcTemplate.update("""
                insert into md_warehouse
                (id, company_id, account_book_id, warehouse_code, warehouse_name, dept_id, manager_user_id,
                 address, status, deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, 'LOT-PR-WH', 'Purchase lot warehouse', 3501, 4001,
                        'lot receipt test', 'ACTIVE', 0, 'lot receipt test', ?, ?, ?, ?, 0)
                """, WAREHOUSE_ID, COMPANY_ID, ACCOUNT_BOOK_ID, USER_ID, NOW, USER_ID, NOW);
    }

    private void seedProduct() {
        jdbcTemplate.update("""
                insert into md_product
                (id, company_id, account_book_id, product_code, product_name, product_type, category_name,
                 specification, unit_name, purchase_price, sale_price, tax_rate, status, deleted_flag,
                 lot_controlled, shelf_life_controlled, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, 'LOT-PR-895201', 'Purchase lot product', 'FINISHED', 'LOT_TEST',
                        'spec', 'pcs', 10.00, 20.00, 0.0000, 'ACTIVE', 0,
                        1, 1, 'lot receipt test', ?, ?, ?, ?, 0)
                """, PRODUCT_ID, COMPANY_ID, ACCOUNT_BOOK_ID, USER_ID, NOW, USER_ID, NOW);
    }

    private void seedPurchaseOrder() {
        jdbcTemplate.update("""
                insert into pur_order
                (id, company_id, account_book_id, order_no, supplier_id, order_date, delivery_date, status,
                 approval_status, receipt_status, total_quantity, total_amount, total_tax_amount,
                 deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, 'PO-LOT-895101', 7001, ?, ?, 'APPROVED',
                        'APPROVED', 'NOT_RECEIVED', 5.0000, 50.00, 0.00,
                        0, 'lot receipt test', ?, ?, ?, ?, 0)
                """, ORDER_ID, COMPANY_ID, ACCOUNT_BOOK_ID, BIZ_DATE, BIZ_DATE, USER_ID, NOW, USER_ID, NOW);
        jdbcTemplate.update("""
                insert into pur_order_line
                (id, order_id, line_no, product_id, qty, price, tax_rate, tax_amount, amount,
                 received_qty, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, 1, ?, 5.0000, 10.00, 0.0000, 0.00, 50.00,
                        0.0000, 'lot receipt line', ?, ?, ?, ?, 0)
                """, ORDER_LINE_ID, ORDER_ID, PRODUCT_ID, USER_ID, NOW, USER_ID, NOW);
    }

    private long responseId(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return root.path("data").path("id").asLong();
    }

    private LocalDate dateValue(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        throw new IllegalArgumentException("Unsupported date value: " + value);
    }
}

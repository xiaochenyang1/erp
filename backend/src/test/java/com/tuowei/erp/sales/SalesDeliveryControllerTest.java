package com.tuowei.erp.sales;

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
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithErpUser(authorities = {"sales:delivery:create", "sales:delivery:post"})
class SalesDeliveryControllerTest {

    private static final long COMPANY_ID = 1L;
    private static final long ACCOUNT_BOOK_ID = 1L;
    private static final long USER_ID = 1L;
    private static final long WAREHOUSE_ID = 895902L;
    private static final long LOCATION_ID = WAREHOUSE_ID + 500000000000000000L;
    private static final long AUTO_PRODUCT_ID = 895301L;
    private static final long EXPLICIT_PRODUCT_ID = 895302L;
    private static final long PERIOD_ID = 895002L;
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
        seedProducts();
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("""
                delete from fin_voucher_entry
                where voucher_id in (
                    select id from fin_voucher
                    where source_type = 'SALES_DELIVERY'
                      and source_id in (
                          select id from sal_delivery
                          where order_id between 895000 and 895999
                      )
                )
                """);
        jdbcTemplate.update("""
                delete from fin_voucher
                where source_type = 'SALES_DELIVERY'
                  and source_id in (
                      select id from sal_delivery
                      where order_id between 895000 and 895999
                  )
                """);
        jdbcTemplate.update("""
                delete from fin_receivable
                where source_type = 'SALES_DELIVERY'
                  and source_id in (
                      select id from sal_delivery
                      where order_id between 895000 and 895999
                  )
                """);
        jdbcTemplate.update("delete from inv_reservation_event where source_id between 895000 and 895999 or product_id between 895300 and 895399");
        jdbcTemplate.update("delete from inv_reservation where source_id between 895000 and 895999 or product_id between 895300 and 895399");
        jdbcTemplate.update("delete from inv_txn where product_id between 895300 and 895399 or biz_line_id between 895000 and 895999");
        jdbcTemplate.update("delete from inv_lot_balance where product_id between 895300 and 895399");
        jdbcTemplate.update("delete from inv_balance where product_id between 895300 and 895399");
        jdbcTemplate.update("delete from sal_delivery_line where order_line_id between 895000 and 895999 or product_id between 895300 and 895399");
        jdbcTemplate.update("delete from sal_delivery where order_id between 895000 and 895999");
        jdbcTemplate.update("delete from sal_order_line where order_id between 895000 and 895999 or product_id between 895300 and 895399");
        jdbcTemplate.update("delete from sal_order where id between 895000 and 895999");
        jdbcTemplate.update("delete from md_product where id between 895300 and 895399 or product_code like 'LOT-SD-%'");
        jdbcTemplate.update("delete from md_location where id = ? or warehouse_id = ?", LOCATION_ID, WAREHOUSE_ID);
        jdbcTemplate.update("delete from md_warehouse where id = ? or warehouse_code = 'LOT-SD-WH'", WAREHOUSE_ID);
        jdbcTemplate.update("delete from fin_account_period where id = ? or period_year = 2035", PERIOD_ID);
    }

    @Test
    void salesDeliveryWithoutLotAutoPicksFefoAndCreatesSplitTransactions() throws Exception {
        long orderId = 895301L;
        long orderLineId = 895311L;
        seedSalesOrder(orderId, orderLineId, AUTO_PRODUCT_ID, "SO-LOT-AUTO", "4.0000", "80.00");
        seedAggregateStock(895321L, AUTO_PRODUCT_ID, "5.0000", "65.00", "4.0000");
        seedLotStock(895331L, AUTO_PRODUCT_ID, "AUTO-SOON", "2.0000", "20.00",
                LocalDate.of(2035, 1, 1), LocalDate.of(2035, 6, 30), NOW.minusHours(2));
        seedLotStock(895332L, AUTO_PRODUCT_ID, "AUTO-LATER", "3.0000", "45.00",
                LocalDate.of(2035, 1, 1), LocalDate.of(2035, 12, 31), NOW.minusHours(1));
        seedReservation(895341L, orderId, "SO-LOT-AUTO", orderLineId, AUTO_PRODUCT_ID, "4.0000");

        long deliveryId = createDelivery(orderId, orderLineId, "4.0000", null);

        mockMvc.perform(post("/api/sales/deliveries/{id}/post", deliveryId))
                .andExpect(status().isOk());

        List<Map<String, Object>> txns = jdbcTemplate.queryForList("""
                select lot_no, qty, amount, lot_key
                from inv_txn
                where company_id = ? and product_id = ? and biz_type = 'SALES_DELIVERY' and direction = 'OUT'
                order by id
                """, COMPANY_ID, AUTO_PRODUCT_ID);
        Assertions.assertThat(txns).hasSize(2);
        Assertions.assertThat(txns.get(0).get("LOT_NO")).isEqualTo("AUTO-SOON");
        Assertions.assertThat((BigDecimal) txns.get(0).get("QTY")).isEqualByComparingTo("2.0000");
        Assertions.assertThat((BigDecimal) txns.get(0).get("AMOUNT")).isEqualByComparingTo("20.00");
        Assertions.assertThat(txns.get(0).get("LOT_KEY")).isEqualTo("AUTO-SOON");
        Assertions.assertThat(txns.get(1).get("LOT_NO")).isEqualTo("AUTO-LATER");
        Assertions.assertThat((BigDecimal) txns.get(1).get("QTY")).isEqualByComparingTo("2.0000");
        Assertions.assertThat((BigDecimal) txns.get(1).get("AMOUNT")).isEqualByComparingTo("30.00");
        Assertions.assertThat(txns.get(1).get("LOT_KEY")).isEqualTo("AUTO-LATER");
    }

    @Test
    void salesDeliveryWithExplicitLotConsumesOnlyThatLot() throws Exception {
        long orderId = 895401L;
        long orderLineId = 895411L;
        seedSalesOrder(orderId, orderLineId, EXPLICIT_PRODUCT_ID, "SO-LOT-EXPLICIT", "3.0000", "60.00");
        seedAggregateStock(895421L, EXPLICIT_PRODUCT_ID, "10.0000", "100.00", "3.0000");
        seedLotStock(895431L, EXPLICIT_PRODUCT_ID, "EXPLICIT-SOON", "5.0000", "50.00",
                LocalDate.of(2035, 1, 1), LocalDate.of(2035, 6, 30), NOW.minusHours(2));
        seedLotStock(895432L, EXPLICIT_PRODUCT_ID, "EXPLICIT-TAKE", "5.0000", "50.00",
                LocalDate.of(2035, 1, 1), LocalDate.of(2035, 12, 31), NOW.minusHours(1));
        seedReservation(895441L, orderId, "SO-LOT-EXPLICIT", orderLineId, EXPLICIT_PRODUCT_ID, "3.0000");

        long deliveryId = createDelivery(orderId, orderLineId, "3.0000", "EXPLICIT-TAKE");

        mockMvc.perform(post("/api/sales/deliveries/{id}/post", deliveryId))
                .andExpect(status().isOk());

        List<String> consumedLots = jdbcTemplate.queryForList("""
                select lot_no
                from inv_txn
                where company_id = ? and product_id = ? and biz_type = 'SALES_DELIVERY' and direction = 'OUT'
                order by id
                """, String.class, COMPANY_ID, EXPLICIT_PRODUCT_ID);
        Assertions.assertThat(consumedLots).containsExactly("EXPLICIT-TAKE");
        Assertions.assertThat(lotQty(EXPLICIT_PRODUCT_ID, "EXPLICIT-SOON")).isEqualByComparingTo("5.0000");
        Assertions.assertThat(lotQty(EXPLICIT_PRODUCT_ID, "EXPLICIT-TAKE")).isEqualByComparingTo("2.0000");
    }

    @Test
    @WithErpUser(authorities = {"sales:delivery:create", "sales:delivery:update", "sales:delivery:post"})
    void createSecondDraftDeliveryFailsWhenExistingDraftConsumesRemainingReservation() throws Exception {
        long orderId = 895501L;
        long orderLineId = 895511L;
        seedSalesOrder(orderId, orderLineId, AUTO_PRODUCT_ID, "SO-RESERVE-DRAFT", "4.0000", "80.00");
        seedAggregateStock(895521L, AUTO_PRODUCT_ID, "10.0000", "100.00", "4.0000");
        seedReservation(895541L, orderId, "SO-RESERVE-DRAFT", orderLineId, AUTO_PRODUCT_ID, "4.0000");

        createDelivery(orderId, orderLineId, "3.0000", null);

        mockMvc.perform(post("/api/sales/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": %d,
                                  "warehouseId": %d,
                                  "deliveryDate": "2035-05-25",
                                  "remark": "reserve draft conflict",
                                  "lines": [
                                    {
                                      "orderLineId": %d,
                                      "qty": 2.0000,
                                      "remark": "second draft"
                                    }
                                  ]
                                }
                                """.formatted(orderId, WAREHOUSE_ID, orderLineId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("销售订单预占数量不足，不能创建销售出库单"));
    }

    @Test
    @WithErpUser(authorities = {"sales:delivery:create", "inventory:reservation:release"})
    void manualReleaseFailsWhenDraftDeliveryAlreadyOccupiesReservation() throws Exception {
        long orderId = 895601L;
        long orderLineId = 895611L;
        long reservationId = 895641L;
        seedSalesOrder(orderId, orderLineId, AUTO_PRODUCT_ID, "SO-RESERVE-RELEASE", "4.0000", "80.00");
        seedAggregateStock(895621L, AUTO_PRODUCT_ID, "10.0000", "100.00", "4.0000");
        seedReservation(reservationId, orderId, "SO-RESERVE-RELEASE", orderLineId, AUTO_PRODUCT_ID, "4.0000");

        createDelivery(orderId, orderLineId, "3.0000", null);

        mockMvc.perform(post("/api/inventory/reservations/{id}/manual-release", reservationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "qty": 2.0000,
                                  "reason": "manual release test"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("预占已被销售出库草稿占用，不能释放"));
    }

    @Test
    @WithErpUser(authorities = {"sales:delivery:create", "sales:delivery:post"})
    void postDeliveryFailsWhenReservationWasReleasedAfterDraftCreated() throws Exception {
        long orderId = 895701L;
        long orderLineId = 895711L;
        long reservationId = 895741L;
        seedSalesOrder(orderId, orderLineId, AUTO_PRODUCT_ID, "SO-RESERVE-POST", "4.0000", "80.00");
        seedAggregateStock(895721L, AUTO_PRODUCT_ID, "10.0000", "100.00", "1.0000");
        seedReservation(reservationId, orderId, "SO-RESERVE-POST", orderLineId, AUTO_PRODUCT_ID, "4.0000");

        long deliveryId = createDelivery(orderId, orderLineId, "4.0000", null);
        jdbcTemplate.update("""
                update inv_reservation
                set released_qty = 3.0000,
                    remaining_qty = 1.0000,
                    updated_time = ?
                where id = ?
                """, NOW.plusMinutes(1), reservationId);

        mockMvc.perform(post("/api/sales/deliveries/{id}/post", deliveryId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("销售订单预占数量不足，不能执行销售出库"));
    }

    private long createDelivery(long orderId, long orderLineId, String qty, String lotNo) throws Exception {
        String lotFields = lotNo == null ? "" : """
                                      "lotNo": "%s",
                                      """.formatted(lotNo);
        MvcResult created = mockMvc.perform(post("/api/sales/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderId": %d,
                                  "warehouseId": %d,
                                  "deliveryDate": "2035-05-25",
                                  "remark": "lot delivery test",
                                  "lines": [
                                    {
                                      "orderLineId": %d,
                                      "qty": %s,
                                %s      "remark": "delivery lot line"
                                    }
                                  ]
                }
                """.formatted(orderId, WAREHOUSE_ID, orderLineId, qty, lotFields)))
                .andExpect(status().isOk())
                .andExpect(lotNo == null
                        ? jsonPath("$.data.lines[0].orderLineId").value(orderLineId)
                        : jsonPath("$.data.lines[0].lotNo").value(lotNo))
                .andReturn();
        JsonNode root = objectMapper.readTree(created.getResponse().getContentAsString(StandardCharsets.UTF_8));
        return root.path("data").path("id").asLong();
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

    private void seedProducts() {
        seedProduct(AUTO_PRODUCT_ID, "LOT-SD-AUTO");
        seedProduct(EXPLICIT_PRODUCT_ID, "LOT-SD-EXPLICIT");
    }

    private void seedWarehouse() {
        jdbcTemplate.update("""
                insert into md_warehouse
                (id, company_id, account_book_id, warehouse_code, warehouse_name, dept_id, manager_user_id,
                 address, status, deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, 'LOT-SD-WH', 'Sales lot warehouse', 3501, 4001,
                        'lot delivery test', 'ACTIVE', 0, 'lot delivery test', ?, ?, ?, ?, 0)
                """, WAREHOUSE_ID, COMPANY_ID, ACCOUNT_BOOK_ID, USER_ID, NOW, USER_ID, NOW);
        jdbcTemplate.update("""
                insert into md_location
                (id, company_id, account_book_id, warehouse_id, location_code, location_name,
                 is_default, status, deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, ?, 'MAIN', 'Default Location', 1, 'ACTIVE', 0,
                        'sales delivery test default location', ?, ?, ?, ?, 0)
                """, LOCATION_ID, COMPANY_ID, ACCOUNT_BOOK_ID, WAREHOUSE_ID,
                USER_ID, NOW, USER_ID, NOW);
    }

    private void seedProduct(long productId, String productCode) {
        jdbcTemplate.update("""
                insert into md_product
                (id, company_id, account_book_id, product_code, product_name, product_type, category_name,
                 specification, unit_name, purchase_price, sale_price, tax_rate, status, deleted_flag,
                 lot_controlled, shelf_life_controlled, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, ?, 'Sales lot product', 'FINISHED', 'LOT_TEST',
                        'spec', 'pcs', 10.00, 20.00, 0.0000, 'ACTIVE', 0,
                        1, 1, 'lot delivery test', ?, ?, ?, ?, 0)
                """, productId, COMPANY_ID, ACCOUNT_BOOK_ID, productCode, USER_ID, NOW, USER_ID, NOW);
    }

    private void seedSalesOrder(long orderId, long orderLineId, long productId, String orderNo, String qty, String amount) {
        jdbcTemplate.update("""
                insert into sal_order
                (id, company_id, account_book_id, order_no, customer_id, warehouse_id, order_date, delivery_date,
                 status, approval_status, delivery_status, total_quantity, total_amount, total_tax_amount,
                 deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, ?, 8001, ?, ?, ?,
                        'APPROVED', 'APPROVED', 'NOT_DELIVERED', ?, ?, 0.00,
                        0, 'lot delivery test', ?, ?, ?, ?, 0)
                """, orderId, COMPANY_ID, ACCOUNT_BOOK_ID, orderNo, WAREHOUSE_ID, BIZ_DATE, BIZ_DATE,
                new BigDecimal(qty), new BigDecimal(amount), USER_ID, NOW, USER_ID, NOW);
        jdbcTemplate.update("""
                insert into sal_order_line
                (id, order_id, line_no, product_id, qty, price, tax_rate, amount, tax_amount,
                 delivered_qty, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, 1, ?, ?, 20.00, 0.0000, ?, 0.00,
                        0.0000, 'delivery lot line', ?, ?, ?, ?, 0)
                """, orderLineId, orderId, productId, new BigDecimal(qty), new BigDecimal(amount), USER_ID, NOW, USER_ID, NOW);
    }

    private void seedAggregateStock(long id, long productId, String qtyOnHand, String amountOnHand, String qtyReserved) {
        jdbcTemplate.update("""
                insert into inv_balance
                (id, company_id, account_book_id, warehouse_id, location_id, product_id, qty_on_hand, qty_reserved,
                 amount_on_hand, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, id, COMPANY_ID, ACCOUNT_BOOK_ID, WAREHOUSE_ID, LOCATION_ID, productId,
                new BigDecimal(qtyOnHand), new BigDecimal(qtyReserved), new BigDecimal(amountOnHand),
                USER_ID, NOW, USER_ID, NOW);
    }

    private void seedLotStock(
            long id,
            long productId,
            String lotNo,
            String qtyOnHand,
            String amountOnHand,
            LocalDate productionDate,
            LocalDate expiryDate,
            LocalDateTime firstInboundTime
    ) {
        jdbcTemplate.update("""
                insert into inv_lot_balance
                (id, company_id, account_book_id, warehouse_id, location_id, product_id, lot_no, production_date, expiry_date,
                 first_inbound_time, qty_on_hand, qty_reserved, amount_on_hand,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0.0000, ?, ?, ?, ?, ?, 0)
                """, id, COMPANY_ID, ACCOUNT_BOOK_ID, WAREHOUSE_ID, LOCATION_ID, productId, lotNo, productionDate, expiryDate,
                firstInboundTime, new BigDecimal(qtyOnHand), new BigDecimal(amountOnHand), USER_ID, NOW, USER_ID, NOW);
    }

    private void seedReservation(long id, long orderId, String orderNo, long orderLineId, long productId, String qty) {
        jdbcTemplate.update("""
                insert into inv_reservation
                (id, company_id, account_book_id, warehouse_id, product_id, source_type, source_id, source_no,
                 source_line_id, reserved_qty, released_qty, remaining_qty, status, remark,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, ?, ?, 'SALES_ORDER', ?, ?,
                        ?, ?, 0.0000, ?, 'ACTIVE', 'lot delivery test',
                        ?, ?, ?, ?, 0)
                """, id, COMPANY_ID, ACCOUNT_BOOK_ID, WAREHOUSE_ID, productId, orderId, orderNo, orderLineId,
                new BigDecimal(qty), new BigDecimal(qty), USER_ID, NOW, USER_ID, NOW);
    }

    private BigDecimal lotQty(long productId, String lotNo) {
        return jdbcTemplate.queryForObject("""
                select qty_on_hand
                from inv_lot_balance
                where company_id = ? and account_book_id = ? and warehouse_id = ? and product_id = ? and lot_no = ?
                """, BigDecimal.class, COMPANY_ID, ACCOUNT_BOOK_ID, WAREHOUSE_ID, productId, lotNo);
    }
}

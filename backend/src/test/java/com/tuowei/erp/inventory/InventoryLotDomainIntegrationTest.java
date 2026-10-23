package com.tuowei.erp.inventory;

import com.tuowei.erp.inventory.transfer.service.InventoryTransferService;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferCreateRequest;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferLineRequest;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferResponse;
import com.tuowei.erp.purchase.returnorder.service.PurchaseReturnService;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnCreateRequest;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnLineRequest;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnResponse;
import com.tuowei.erp.sales.returnorder.service.SalesReturnService;
import com.tuowei.erp.sales.returnorder.web.SalesReturnCreateRequest;
import com.tuowei.erp.sales.returnorder.web.SalesReturnLineRequest;
import com.tuowei.erp.sales.returnorder.web.SalesReturnResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@WithErpUser
class InventoryLotDomainIntegrationTest {

    private static final long COMPANY_ID = 1L;
    private static final long ACCOUNT_BOOK_ID = 1L;
    private static final long USER_ID = 1L;
    private static final long FROM_WAREHOUSE_ID = 896101L;
    private static final long TO_WAREHOUSE_ID = 896102L;
    private static final long LOCATION_ID_OFFSET = 500000000000000000L;
    private static final long PURCHASE_PRODUCT_ID = 896201L;
    private static final long SALES_PRODUCT_ID = 896202L;
    private static final long TRANSFER_PRODUCT_ID = 896203L;
    private static final long PURCHASE_ORDER_ID = 896301L;
    private static final long PURCHASE_ORDER_LINE_ID = 896311L;
    private static final long PURCHASE_RECEIPT_ID = 896321L;
    private static final long PURCHASE_RECEIPT_LINE_ID = 896331L;
    private static final long SALES_ORDER_ID = 896401L;
    private static final long SALES_ORDER_LINE_ID = 896411L;
    private static final long SALES_DELIVERY_ID = 896421L;
    private static final long SALES_DELIVERY_LINE_ID = 896431L;
    private static final LocalDate BIZ_DATE = LocalDate.of(2036, 5, 25);
    private static final LocalDateTime NOW = LocalDateTime.of(2036, 5, 25, 10, 0);

    @Autowired
    private PurchaseReturnService purchaseReturnService;

    @Autowired
    private SalesReturnService salesReturnService;

    @Autowired
    private InventoryTransferService inventoryTransferService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        cleanup();
        seedOpenPeriod();
        seedWarehouse(FROM_WAREHOUSE_ID, "LOT-WH-FROM");
        seedWarehouse(TO_WAREHOUSE_ID, "LOT-WH-TO");
        seedLotProduct(PURCHASE_PRODUCT_ID, "LOT-PR-RETURN");
        seedLotProduct(SALES_PRODUCT_ID, "LOT-SR-RETURN");
        seedLotProduct(TRANSFER_PRODUCT_ID, "LOT-TR-AUTO");
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("""
                delete from fin_voucher_entry
                where voucher_id in (
                    select id from fin_voucher
                    where source_type in ('PURCHASE_RETURN', 'SALES_RETURN')
                       or source_no like 'PO-LOT-896%'
                       or source_no like 'SO-LOT-896%'
                )
                """);
        jdbcTemplate.update("""
                delete from fin_voucher
                where source_type in ('PURCHASE_RETURN', 'SALES_RETURN')
                   or source_no like 'PO-LOT-896%'
                   or source_no like 'SO-LOT-896%'
                """);
        jdbcTemplate.update("delete from fin_payable where source_type = 'PURCHASE_RETURN' or source_no like 'PR-%'");
        jdbcTemplate.update("delete from fin_receivable where source_type = 'SALES_RETURN' or source_no like 'SR-%'");
        jdbcTemplate.update("delete from inv_txn where product_id between 896200 and 896299 or biz_line_id between 896000 and 896999");
        jdbcTemplate.update("delete from inv_lot_balance where product_id between 896200 and 896299");
        jdbcTemplate.update("delete from inv_balance where product_id between 896200 and 896299");
        jdbcTemplate.update("delete from pur_return_line where product_id between 896200 and 896299");
        jdbcTemplate.update("delete from pur_return where id between 896000 and 896999 or receipt_id between 896000 and 896999");
        jdbcTemplate.update("delete from pur_receipt_line where product_id between 896200 and 896299");
        jdbcTemplate.update("delete from pur_receipt where id between 896000 and 896999 or order_id between 896000 and 896999");
        jdbcTemplate.update("delete from pur_order_line where product_id between 896200 and 896299 or order_id between 896000 and 896999");
        jdbcTemplate.update("delete from pur_order where id between 896000 and 896999");
        jdbcTemplate.update("delete from sal_return_line where product_id between 896200 and 896299");
        jdbcTemplate.update("delete from sal_return where id between 896000 and 896999 or delivery_id between 896000 and 896999");
        jdbcTemplate.update("delete from sal_delivery_line where product_id between 896200 and 896299");
        jdbcTemplate.update("delete from sal_delivery where id between 896000 and 896999 or order_id between 896000 and 896999");
        jdbcTemplate.update("delete from sal_order_line where product_id between 896200 and 896299 or order_id between 896000 and 896999");
        jdbcTemplate.update("delete from sal_order where id between 896000 and 896999");
        jdbcTemplate.update("delete from inv_transfer_line where product_id between 896200 and 896299");
        jdbcTemplate.update("delete from inv_transfer where id between 896000 and 896999");
        jdbcTemplate.update("delete from md_product where id between 896200 and 896299 or product_code like 'LOT-%896%'");
        jdbcTemplate.update("delete from md_location where warehouse_id between 896100 and 896199");
        jdbcTemplate.update("delete from md_warehouse where id between 896100 and 896199 or warehouse_code like 'LOT-WH-%'");
        jdbcTemplate.update("delete from fin_account_period where id = 896001 or period_year = 2036");
    }

    @Test
    void purchaseReturnCanAutoPickLotStock() {
        seedPostedPurchaseReceipt();
        seedAggregateStock(896501L, FROM_WAREHOUSE_ID, PURCHASE_PRODUCT_ID, "5.0000", "65.00", "0.0000");
        seedLotStock(896511L, FROM_WAREHOUSE_ID, PURCHASE_PRODUCT_ID, "PR-LOT-SOON", "2.0000", "20.00",
                LocalDate.of(2036, 1, 1), LocalDate.of(2036, 6, 30), NOW.minusHours(2));
        seedLotStock(896512L, FROM_WAREHOUSE_ID, PURCHASE_PRODUCT_ID, "PR-LOT-LATER", "3.0000", "45.00",
                LocalDate.of(2036, 1, 1), LocalDate.of(2036, 12, 31), NOW.minusHours(1));

        PurchaseReturnResponse created = purchaseReturnService.create(new PurchaseReturnCreateRequest(
                PURCHASE_RECEIPT_ID,
                BIZ_DATE,
                "purchase return auto lot",
                List.of(new PurchaseReturnLineRequest(
                        PURCHASE_RECEIPT_LINE_ID,
                        new BigDecimal("4.0000"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        "return without explicit lot"
                ))
        ));

        purchaseReturnService.post(created.id());

        List<Map<String, Object>> txns = jdbcTemplate.queryForList("""
                select lot_no, qty, amount
                from inv_txn
                where company_id = ? and product_id = ? and biz_type = 'PURCHASE_RETURN' and direction = 'OUT'
                order by id
                """, COMPANY_ID, PURCHASE_PRODUCT_ID);
        Assertions.assertThat(txns).hasSize(2);
        Assertions.assertThat(txns.get(0).get("LOT_NO")).isEqualTo("PR-LOT-SOON");
        Assertions.assertThat((BigDecimal) txns.get(0).get("QTY")).isEqualByComparingTo("2.0000");
        Assertions.assertThat((BigDecimal) txns.get(0).get("AMOUNT")).isEqualByComparingTo("20.00");
        Assertions.assertThat(txns.get(1).get("LOT_NO")).isEqualTo("PR-LOT-LATER");
        Assertions.assertThat((BigDecimal) txns.get(1).get("QTY")).isEqualByComparingTo("2.0000");
        Assertions.assertThat((BigDecimal) txns.get(1).get("AMOUNT")).isEqualByComparingTo("30.00");
    }

    @Test
    void salesReturnRequiresLotWhenOriginalDeliverySplitAcrossLots() {
        seedPostedSalesDeliverySplitAcrossLots();
        SalesReturnResponse created = salesReturnService.create(new SalesReturnCreateRequest(
                SALES_DELIVERY_ID,
                BIZ_DATE,
                "sales return split delivery",
                List.of(new SalesReturnLineRequest(
                        SALES_DELIVERY_LINE_ID,
                        new BigDecimal("1.0000"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        "return without lot"
                ))
        ));

        Assertions.assertThatThrownBy(() -> salesReturnService.post(created.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("销售退货必须指定批次号");
    }

    @Test
    void inventoryTransferAutoPickedLotsArriveInTargetWarehouse() {
        seedAggregateStock(896601L, FROM_WAREHOUSE_ID, TRANSFER_PRODUCT_ID, "5.0000", "65.00", "0.0000");
        seedLotStock(896611L, FROM_WAREHOUSE_ID, TRANSFER_PRODUCT_ID, "TR-LOT-SOON", "2.0000", "20.00",
                LocalDate.of(2036, 1, 1), LocalDate.of(2036, 6, 30), NOW.minusHours(2));
        seedLotStock(896612L, FROM_WAREHOUSE_ID, TRANSFER_PRODUCT_ID, "TR-LOT-LATER", "3.0000", "45.00",
                LocalDate.of(2036, 1, 1), LocalDate.of(2036, 12, 31), NOW.minusHours(1));

        InventoryTransferResponse created = inventoryTransferService.create(new InventoryTransferCreateRequest(
                FROM_WAREHOUSE_ID,
                TO_WAREHOUSE_ID,
                BIZ_DATE,
                List.of(new InventoryTransferLineRequest(
                        TRANSFER_PRODUCT_ID,
                        new BigDecimal("4.0000"),
                        new BigDecimal("10.0000"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "auto transfer lot"
                )),
                "transfer lots"
        ));

        inventoryTransferService.post(created.id());

        List<Map<String, Object>> targetLots = jdbcTemplate.queryForList("""
                select lot_no, production_date, expiry_date, qty_on_hand, amount_on_hand
                from inv_lot_balance
                where company_id = ? and account_book_id = ? and warehouse_id = ? and product_id = ?
                order by lot_no
                """, COMPANY_ID, ACCOUNT_BOOK_ID, TO_WAREHOUSE_ID, TRANSFER_PRODUCT_ID);
        Assertions.assertThat(targetLots).hasSize(2);
        Assertions.assertThat(targetLots.get(0).get("LOT_NO")).isEqualTo("TR-LOT-LATER");
        Assertions.assertThat((BigDecimal) targetLots.get(0).get("QTY_ON_HAND")).isEqualByComparingTo("2.0000");
        Assertions.assertThat((BigDecimal) targetLots.get(0).get("AMOUNT_ON_HAND")).isEqualByComparingTo("30.00");
        Assertions.assertThat(targetLots.get(1).get("LOT_NO")).isEqualTo("TR-LOT-SOON");
        Assertions.assertThat((BigDecimal) targetLots.get(1).get("QTY_ON_HAND")).isEqualByComparingTo("2.0000");
        Assertions.assertThat((BigDecimal) targetLots.get(1).get("AMOUNT_ON_HAND")).isEqualByComparingTo("20.00");
    }

    private void seedOpenPeriod() {
        jdbcTemplate.update("""
                insert into fin_account_period
                (id, company_id, account_book_id, period_year, period_month, start_date, end_date, status,
                 created_by, created_time, updated_by, updated_time, version)
                values (896001, ?, ?, 2036, '2036-05', '2036-05-01', '2036-05-31', 'OPEN',
                        ?, ?, ?, ?, 0)
                """, COMPANY_ID, ACCOUNT_BOOK_ID, USER_ID, NOW, USER_ID, NOW);
    }

    private void seedWarehouse(long warehouseId, String warehouseCode) {
        jdbcTemplate.update("""
                insert into md_warehouse
                (id, company_id, account_book_id, warehouse_code, warehouse_name, dept_id, manager_user_id,
                 address, status, deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, ?, ?, 1, 1,
                        'lot test address', 'ACTIVE', 0, 'lot domain test', ?, ?, ?, ?, 0)
                """, warehouseId, COMPANY_ID, ACCOUNT_BOOK_ID, warehouseCode, warehouseCode, USER_ID, NOW, USER_ID, NOW);
        jdbcTemplate.update("""
                insert into md_location
                (id, company_id, account_book_id, warehouse_id, location_code, location_name,
                 is_default, status, deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, ?, 'MAIN', 'Default Location', 1, 'ACTIVE', 0,
                        'lot domain test default location', ?, ?, ?, ?, 0)
                """, warehouseId + LOCATION_ID_OFFSET, COMPANY_ID, ACCOUNT_BOOK_ID, warehouseId,
                USER_ID, NOW, USER_ID, NOW);
    }

    private void seedLotProduct(long productId, String productCode) {
        jdbcTemplate.update("""
                insert into md_product
                (id, company_id, account_book_id, product_code, product_name, product_type, category_name,
                 specification, unit_name, purchase_price, sale_price, tax_rate, status, deleted_flag,
                 lot_controlled, shelf_life_controlled, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, ?, ?, 'STANDARD', 'LOT_TEST',
                        'spec', 'pcs', 10.00, 20.00, 0.0000, 'ACTIVE', 0,
                        1, 1, 'lot domain test', ?, ?, ?, ?, 0)
                """, productId, COMPANY_ID, ACCOUNT_BOOK_ID, productCode, productCode, USER_ID, NOW, USER_ID, NOW);
    }

    private void seedPostedPurchaseReceipt() {
        jdbcTemplate.update("""
                insert into pur_order
                (id, company_id, account_book_id, order_no, supplier_id, order_date, delivery_date, status,
                 approval_status, receipt_status, total_quantity, total_amount, total_tax_amount,
                 deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, 'PO-LOT-896301', 7001, ?, ?, 'APPROVED',
                        'APPROVED', 'FULL_RECEIVED', 5.0000, 50.00, 0.00,
                        0, 'purchase return lot test', ?, ?, ?, ?, 0)
                """, PURCHASE_ORDER_ID, COMPANY_ID, ACCOUNT_BOOK_ID, BIZ_DATE, BIZ_DATE, USER_ID, NOW, USER_ID, NOW);
        jdbcTemplate.update("""
                insert into pur_order_line
                (id, order_id, line_no, product_id, qty, price, tax_rate, tax_amount, amount,
                 received_qty, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, 1, ?, 5.0000, 10.00, 0.0000, 0.00, 50.00,
                        5.0000, 'purchase return lot line', ?, ?, ?, ?, 0)
                """, PURCHASE_ORDER_LINE_ID, PURCHASE_ORDER_ID, PURCHASE_PRODUCT_ID, USER_ID, NOW, USER_ID, NOW);
        jdbcTemplate.update("""
                insert into pur_receipt
                (id, company_id, account_book_id, receipt_no, order_id, warehouse_id, receipt_date, status,
                 total_quantity, total_amount, total_tax_amount, deleted_flag, remark,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, 'RCV-LOT-896321', ?, ?, ?, 'POSTED',
                        5.0000, 50.00, 0.00, 0, 'posted lot receipt',
                        ?, ?, ?, ?, 0)
                """, PURCHASE_RECEIPT_ID, COMPANY_ID, ACCOUNT_BOOK_ID, PURCHASE_ORDER_ID, FROM_WAREHOUSE_ID, BIZ_DATE,
                USER_ID, NOW, USER_ID, NOW);
        jdbcTemplate.update("""
                insert into pur_receipt_line
                (id, receipt_id, line_no, order_line_id, product_id, qty, price, tax_rate, amount, tax_amount,
                 returned_qty, lot_no, production_date, expiry_date, remark,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, ?, 1, ?, ?, 5.0000, 10.00, 0.0000, 50.00, 0.00,
                        0.0000, null, null, null, 'posted lot receipt line',
                        ?, ?, ?, ?, 0)
                """, PURCHASE_RECEIPT_LINE_ID, PURCHASE_RECEIPT_ID, PURCHASE_ORDER_LINE_ID, PURCHASE_PRODUCT_ID,
                USER_ID, NOW, USER_ID, NOW);
    }

    private void seedPostedSalesDeliverySplitAcrossLots() {
        jdbcTemplate.update("""
                insert into sal_order
                (id, company_id, account_book_id, order_no, customer_id, warehouse_id, order_date, delivery_date,
                 status, approval_status, delivery_status, total_quantity, total_amount, total_tax_amount,
                 deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, 'SO-LOT-896401', 8001, ?, ?, ?,
                        'APPROVED', 'APPROVED', 'FULL_DELIVERED', 4.0000, 80.00, 0.00,
                        0, 'sales return lot test', ?, ?, ?, ?, 0)
                """, SALES_ORDER_ID, COMPANY_ID, ACCOUNT_BOOK_ID, FROM_WAREHOUSE_ID, BIZ_DATE, BIZ_DATE,
                USER_ID, NOW, USER_ID, NOW);
        jdbcTemplate.update("""
                insert into sal_order_line
                (id, order_id, line_no, product_id, qty, price, tax_rate, amount, tax_amount,
                 delivered_qty, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, 1, ?, 4.0000, 20.00, 0.0000, 80.00, 0.00,
                        4.0000, 'sales return lot line', ?, ?, ?, ?, 0)
                """, SALES_ORDER_LINE_ID, SALES_ORDER_ID, SALES_PRODUCT_ID, USER_ID, NOW, USER_ID, NOW);
        jdbcTemplate.update("""
                insert into sal_delivery
                (id, company_id, account_book_id, delivery_no, order_id, warehouse_id, delivery_date, status,
                 total_quantity, total_amount, total_tax_amount, deleted_flag, remark,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, 'SD-LOT-896421', ?, ?, ?, 'POSTED',
                        4.0000, 80.00, 0.00, 0, 'posted split delivery',
                        ?, ?, ?, ?, 0)
                """, SALES_DELIVERY_ID, COMPANY_ID, ACCOUNT_BOOK_ID, SALES_ORDER_ID, FROM_WAREHOUSE_ID, BIZ_DATE,
                USER_ID, NOW, USER_ID, NOW);
        jdbcTemplate.update("""
                insert into sal_delivery_line
                (id, delivery_id, line_no, order_line_id, product_id, qty, price, tax_rate, amount, tax_amount,
                 returned_qty, lot_no, production_date, expiry_date, remark,
                 created_by, created_time, updated_by, updated_time, version)
                values (?, ?, 1, ?, ?, 4.0000, 20.00, 0.0000, 80.00, 0.00,
                        0.0000, null, null, null, 'posted split delivery line',
                        ?, ?, ?, ?, 0)
                """, SALES_DELIVERY_LINE_ID, SALES_DELIVERY_ID, SALES_ORDER_LINE_ID, SALES_PRODUCT_ID,
                USER_ID, NOW, USER_ID, NOW);
        seedSalesDeliveryTxn(896441L, "SR-LOT-A", "2.0000", "20.00");
        seedSalesDeliveryTxn(896442L, "SR-LOT-B", "2.0000", "30.00");
    }

    private void seedSalesDeliveryTxn(long id, String lotNo, String qty, String amount) {
        jdbcTemplate.update("""
                insert into inv_txn
                (id, company_id, account_book_id, warehouse_id, product_id, biz_type, biz_no, biz_line_id,
                 direction, qty, amount, unit_cost, occurred_time, lot_no, production_date, expiry_date, lot_key,
                 remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, ?, ?, 'SALES_DELIVERY', 'SD-LOT-896421', ?,
                        'OUT', ?, ?, 10.0000, ?, ?, date '2036-01-01', date '2036-12-31', ?,
                        'split delivery txn', ?, ?, ?, ?, 0)
                """, id, COMPANY_ID, ACCOUNT_BOOK_ID, FROM_WAREHOUSE_ID, SALES_PRODUCT_ID, SALES_DELIVERY_LINE_ID,
                new BigDecimal(qty), new BigDecimal(amount), NOW, lotNo, lotNo, USER_ID, NOW, USER_ID, NOW);
    }

    private void seedAggregateStock(long id, long warehouseId, long productId, String qtyOnHand, String amountOnHand, String qtyReserved) {
        jdbcTemplate.update("""
                insert into inv_balance
                (id, company_id, account_book_id, warehouse_id, location_id, product_id, qty_on_hand, qty_reserved,
                 amount_on_hand, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, id, COMPANY_ID, ACCOUNT_BOOK_ID, warehouseId, warehouseId + LOCATION_ID_OFFSET, productId,
                new BigDecimal(qtyOnHand), new BigDecimal(qtyReserved), new BigDecimal(amountOnHand),
                USER_ID, NOW, USER_ID, NOW);
    }

    private void seedLotStock(
            long id,
            long warehouseId,
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
                """, id, COMPANY_ID, ACCOUNT_BOOK_ID, warehouseId, warehouseId + LOCATION_ID_OFFSET, productId, lotNo, productionDate, expiryDate,
                firstInboundTime, new BigDecimal(qtyOnHand), new BigDecimal(amountOnHand), USER_ID, NOW, USER_ID, NOW);
    }
}

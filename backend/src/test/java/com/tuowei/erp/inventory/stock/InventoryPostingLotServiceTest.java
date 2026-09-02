package com.tuowei.erp.inventory.stock;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
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

@SpringBootTest
@ActiveProfiles("test")
@WithErpUser(accountBookId = 1L)
class InventoryPostingLotServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long ACCOUNT_BOOK_ID = 1L;
    private static final long USER_ID = 1L;
    private static final long WAREHOUSE_ID = 894101L;
    private static final long LOCATION_ID = WAREHOUSE_ID + 500000000000000000L;
    private static final long LOT_SHELF_PRODUCT_ID = 894201L;
    private static final long LOT_ONLY_PRODUCT_ID = 894202L;
    private static final long NON_LOT_PRODUCT_ID = 894203L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 25, 10, 0);

    @Autowired
    InventoryPostingService inventoryPostingService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        cleanup();
        seedDefaultLocation();
        seedProducts();
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("""
                delete from inv_txn
                where biz_type in ('LOT_TEST_IN', 'LOT_TEST_OUT')
                   or product_id between 894200 and 894299
                """);
        jdbcTemplate.update("delete from inv_lot_balance where id between 894000 and 894999 or product_id between 894200 and 894299");
        jdbcTemplate.update("delete from inv_balance where product_id between 894200 and 894299");
        jdbcTemplate.update("delete from md_location where id = ? or warehouse_id = ?", LOCATION_ID, WAREHOUSE_ID);
        jdbcTemplate.update("delete from md_product where id between 894200 and 894299 or product_code like 'LOT-PROD-894%'");
    }

    private void seedDefaultLocation() {
        jdbcTemplate.update("""
                insert into md_location
                (id, company_id, account_book_id, warehouse_id, location_code, location_name,
                 is_default, status, deleted_flag, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, ?, 'MAIN', 'Default Location', 1, 'ACTIVE', 0,
                        'inventory lot posting test default location', ?, ?, ?, ?, 0)
                """, LOCATION_ID, COMPANY_ID, ACCOUNT_BOOK_ID, WAREHOUSE_ID,
                USER_ID, NOW, USER_ID, NOW);
    }

    @Test
    void inboundCreatesLotBalanceAndTransactionMetadata() {
        inventoryPostingService.postInbound(inbound(LOT_SHELF_PRODUCT_ID, 8949001L, "LOT-A", "10.0000", "100.00",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 12, 31)), audit());

        Map<String, Object> lotBalance = jdbcTemplate.queryForMap("""
                select lot_no, production_date, expiry_date, qty_on_hand, amount_on_hand
                from inv_lot_balance
                where company_id = ? and product_id = ? and lot_no = 'LOT-A'
                """, COMPANY_ID, LOT_SHELF_PRODUCT_ID);
        Assertions.assertThat(lotBalance.get("LOT_NO")).isEqualTo("LOT-A");
        Assertions.assertThat(dateValue(lotBalance.get("PRODUCTION_DATE"))).isEqualTo(LocalDate.of(2026, 5, 1));
        Assertions.assertThat(dateValue(lotBalance.get("EXPIRY_DATE"))).isEqualTo(LocalDate.of(2026, 12, 31));
        Assertions.assertThat((BigDecimal) lotBalance.get("QTY_ON_HAND")).isEqualByComparingTo("10.0000");
        Assertions.assertThat((BigDecimal) lotBalance.get("AMOUNT_ON_HAND")).isEqualByComparingTo("100.00");

        Map<String, Object> txn = jdbcTemplate.queryForMap("""
                select lot_no, production_date, expiry_date, lot_key, qty, amount
                from inv_txn
                where company_id = ? and biz_type = 'LOT_TEST_IN' and biz_line_id = 8949001 and direction = 'IN'
                """, COMPANY_ID);
        Assertions.assertThat(txn.get("LOT_NO")).isEqualTo("LOT-A");
        Assertions.assertThat(dateValue(txn.get("PRODUCTION_DATE"))).isEqualTo(LocalDate.of(2026, 5, 1));
        Assertions.assertThat(dateValue(txn.get("EXPIRY_DATE"))).isEqualTo(LocalDate.of(2026, 12, 31));
        Assertions.assertThat(txn.get("LOT_KEY")).isEqualTo("LOT-A");
        Assertions.assertThat((BigDecimal) txn.get("QTY")).isEqualByComparingTo("10.0000");
        Assertions.assertThat((BigDecimal) txn.get("AMOUNT")).isEqualByComparingTo("100.00");
    }

    @Test
    void inboundRejectsMissingLotForLotControlledProduct() {
        Assertions.assertThatThrownBy(() -> inventoryPostingService.postInbound(
                        inbound(LOT_ONLY_PRODUCT_ID, 8949002L, null, "1.0000", "10.00", null, null), audit()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("启用批次管理的商品必须填写批次号");
    }

    @Test
    void inboundRejectsConflictingExpiryForExistingLot() {
        inventoryPostingService.postInbound(inbound(LOT_SHELF_PRODUCT_ID, 8949003L, "LOT-CONFLICT", "5.0000", "50.00",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 12, 31)), audit());

        Assertions.assertThatThrownBy(() -> inventoryPostingService.postInbound(
                        inbound(LOT_SHELF_PRODUCT_ID, 8949004L, "LOT-CONFLICT", "1.0000", "10.00",
                                LocalDate.of(2026, 5, 1), LocalDate.of(2027, 1, 1)), audit()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("批次有效期与已有批次不一致");
        Assertions.assertThatThrownBy(() -> inventoryPostingService.postInbound(
                        inbound(LOT_SHELF_PRODUCT_ID, 8949005L, "LOT-CONFLICT", "1.0000", "10.00",
                                LocalDate.of(2026, 5, 1), null), audit()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("启用效期管理的商品必须填写有效期");

        BigDecimal qty = jdbcTemplate.queryForObject("""
                select qty_on_hand
                from inv_lot_balance
                where company_id = ? and product_id = ? and lot_no = 'LOT-CONFLICT'
                """, BigDecimal.class, COMPANY_ID, LOT_SHELF_PRODUCT_ID);
        Assertions.assertThat(qty).isEqualByComparingTo("5.0000");
    }

    @Test
    void inboundRejectsConflictingProductionDateForExistingLot() {
        inventoryPostingService.postInbound(inbound(LOT_ONLY_PRODUCT_ID, 8949006L, "LOT-PROD-CONFLICT", "5.0000", "50.00",
                null, null), audit());

        Assertions.assertThatThrownBy(() -> inventoryPostingService.postInbound(
                        inbound(LOT_ONLY_PRODUCT_ID, 8949007L, "LOT-PROD-CONFLICT", "1.0000", "10.00",
                                LocalDate.of(2026, 5, 1), null), audit()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("批次生产日期与已有批次不一致");

        BigDecimal qty = jdbcTemplate.queryForObject("""
                select qty_on_hand
                from inv_lot_balance
                where company_id = ? and product_id = ? and lot_no = 'LOT-PROD-CONFLICT'
                """, BigDecimal.class, COMPANY_ID, LOT_ONLY_PRODUCT_ID);
        Assertions.assertThat(qty).isEqualByComparingTo("5.0000");
    }

    @Test
    void explicitOutboundOnlyConsumesRequestedLot() {
        inventoryPostingService.postInbound(inbound(LOT_ONLY_PRODUCT_ID, 8949010L, "LOT-KEEP", "5.0000", "50.00", null, null), audit());
        inventoryPostingService.postInbound(inbound(LOT_ONLY_PRODUCT_ID, 8949011L, "LOT-TAKE", "7.0000", "70.00", null, null), audit());

        BigDecimal cost = inventoryPostingService.postOutbound(outbound(LOT_ONLY_PRODUCT_ID, 8949012L, "LOT-TAKE", "3.0000"), audit(), "批次库存不足");

        Assertions.assertThat(cost).isEqualByComparingTo("30.00");
        Assertions.assertThat(lotQty(LOT_ONLY_PRODUCT_ID, "LOT-KEEP")).isEqualByComparingTo("5.0000");
        Assertions.assertThat(lotQty(LOT_ONLY_PRODUCT_ID, "LOT-TAKE")).isEqualByComparingTo("4.0000");
        List<String> consumedLots = txnLots(8949012L);
        Assertions.assertThat(consumedLots).containsExactly("LOT-TAKE");
    }

    @Test
    void autoOutboundUsesFefoForShelfLifeProduct() {
        inventoryPostingService.postInbound(inbound(LOT_SHELF_PRODUCT_ID, 8949020L, "EXP-LATER", "5.0000", "50.00",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 12, 31)), audit());
        inventoryPostingService.postInbound(inbound(LOT_SHELF_PRODUCT_ID, 8949021L, "EXP-SOON", "5.0000", "50.00",
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 30)), audit());

        inventoryPostingService.postOutbound(outbound(LOT_SHELF_PRODUCT_ID, 8949001L, null, "6.0000"), audit(), "批次库存不足");

        List<String> consumedLots = jdbcTemplate.queryForList("""
                select lot_no
                from inv_txn
                where biz_type = 'LOT_TEST_OUT'
                  and biz_line_id = 8949001
                  and direction = 'OUT'
                order by id
                """, String.class);
        Assertions.assertThat(consumedLots).containsExactly("EXP-SOON", "EXP-LATER");
    }

    @Test
    void autoOutboundUsesFifoForLotControlledProduct() {
        inventoryPostingService.postInbound(inbound(LOT_ONLY_PRODUCT_ID, 8949030L, "FIFO-FIRST", "5.0000", "50.00", null, null), audit());
        inventoryPostingService.postInbound(inbound(LOT_ONLY_PRODUCT_ID, 8949031L, "FIFO-SECOND", "5.0000", "50.00", null, null), laterAudit());

        inventoryPostingService.postOutbound(outbound(LOT_ONLY_PRODUCT_ID, 8949032L, null, "6.0000"), laterAudit(), "批次库存不足");

        Assertions.assertThat(txnLots(8949032L)).containsExactly("FIFO-FIRST", "FIFO-SECOND");
    }

    @Test
    void autoOutboundAcrossLotsCreatesMultipleTransactions() {
        inventoryPostingService.postInbound(inbound(LOT_ONLY_PRODUCT_ID, 8949040L, "SPLIT-A", "2.0000", "20.00", null, null), audit());
        inventoryPostingService.postInbound(inbound(LOT_ONLY_PRODUCT_ID, 8949041L, "SPLIT-B", "3.0000", "45.00", null, null), laterAudit());

        BigDecimal cost = inventoryPostingService.postOutbound(outbound(LOT_ONLY_PRODUCT_ID, 8949042L, null, "4.0000"), audit(), "批次库存不足");

        Assertions.assertThat(cost).isEqualByComparingTo("50.00");
        List<Map<String, Object>> txns = jdbcTemplate.queryForList("""
                select lot_no, qty, amount, lot_key
                from inv_txn
                where company_id = ? and biz_type = 'LOT_TEST_OUT' and biz_line_id = 8949042 and direction = 'OUT'
                order by id
                """, COMPANY_ID);
        Assertions.assertThat(txns).hasSize(2);
        Assertions.assertThat(txns.get(0).get("LOT_NO")).isEqualTo("SPLIT-A");
        Assertions.assertThat((BigDecimal) txns.get(0).get("QTY")).isEqualByComparingTo("2.0000");
        Assertions.assertThat((BigDecimal) txns.get(0).get("AMOUNT")).isEqualByComparingTo("20.00");
        Assertions.assertThat(txns.get(0).get("LOT_KEY")).isEqualTo("SPLIT-A");
        Assertions.assertThat(txns.get(1).get("LOT_NO")).isEqualTo("SPLIT-B");
        Assertions.assertThat((BigDecimal) txns.get(1).get("QTY")).isEqualByComparingTo("2.0000");
        Assertions.assertThat((BigDecimal) txns.get(1).get("AMOUNT")).isEqualByComparingTo("30.00");
        Assertions.assertThat(txns.get(1).get("LOT_KEY")).isEqualTo("SPLIT-B");
    }

    @Test
    void autoOutboundRepeatedCallReturnsPostedAmountWithoutMutatingStock() {
        inventoryPostingService.postInbound(inbound(LOT_ONLY_PRODUCT_ID, 8949043L, "REPEAT-A", "2.0000", "20.00", null, null), audit());
        inventoryPostingService.postInbound(inbound(LOT_ONLY_PRODUCT_ID, 8949044L, "REPEAT-B", "3.0000", "45.00", null, null), laterAudit());

        InventoryPostingCommand command = outbound(LOT_ONLY_PRODUCT_ID, 8949045L, null, "4.0000");
        BigDecimal firstCost = inventoryPostingService.postOutbound(command, audit(), "批次库存不足");
        BigDecimal aggregateQtyAfterFirst = aggregateQty(LOT_ONLY_PRODUCT_ID);
        BigDecimal lotAQtyAfterFirst = lotQty(LOT_ONLY_PRODUCT_ID, "REPEAT-A");
        BigDecimal lotBQtyAfterFirst = lotQty(LOT_ONLY_PRODUCT_ID, "REPEAT-B");

        BigDecimal secondCost = inventoryPostingService.postOutbound(command, laterAudit(), "批次库存不足");

        Assertions.assertThat(secondCost).isEqualByComparingTo(firstCost);
        Assertions.assertThat(txnCount(8949045L)).isEqualTo(2);
        Assertions.assertThat(aggregateQty(LOT_ONLY_PRODUCT_ID)).isEqualByComparingTo(aggregateQtyAfterFirst);
        Assertions.assertThat(lotQty(LOT_ONLY_PRODUCT_ID, "REPEAT-A")).isEqualByComparingTo(lotAQtyAfterFirst);
        Assertions.assertThat(lotQty(LOT_ONLY_PRODUCT_ID, "REPEAT-B")).isEqualByComparingTo(lotBQtyAfterFirst);
    }

    @Test
    void explicitOutboundAfterAutoSplitReturnsPostedTotalWithoutMutatingStock() {
        inventoryPostingService.postInbound(inbound(LOT_ONLY_PRODUCT_ID, 8949046L, "AUTO-A", "2.0000", "20.00", null, null), audit());
        inventoryPostingService.postInbound(inbound(LOT_ONLY_PRODUCT_ID, 8949047L, "AUTO-B", "3.0000", "45.00", null, null), laterAudit());

        BigDecimal autoCost = inventoryPostingService.postOutbound(outbound(LOT_ONLY_PRODUCT_ID, 8949048L, null, "4.0000"), audit(), "批次库存不足");
        BigDecimal aggregateQtyAfterAuto = aggregateQty(LOT_ONLY_PRODUCT_ID);
        BigDecimal lotAQtyAfterAuto = lotQty(LOT_ONLY_PRODUCT_ID, "AUTO-A");
        BigDecimal lotBQtyAfterAuto = lotQty(LOT_ONLY_PRODUCT_ID, "AUTO-B");

        BigDecimal explicitCost = inventoryPostingService.postOutbound(outbound(LOT_ONLY_PRODUCT_ID, 8949048L, "AUTO-A", "4.0000"), laterAudit(), "批次库存不足");

        Assertions.assertThat(explicitCost).isEqualByComparingTo(autoCost);
        Assertions.assertThat(txnCount(8949048L)).isEqualTo(2);
        Assertions.assertThat(aggregateQty(LOT_ONLY_PRODUCT_ID)).isEqualByComparingTo(aggregateQtyAfterAuto);
        Assertions.assertThat(lotQty(LOT_ONLY_PRODUCT_ID, "AUTO-A")).isEqualByComparingTo(lotAQtyAfterAuto);
        Assertions.assertThat(lotQty(LOT_ONLY_PRODUCT_ID, "AUTO-B")).isEqualByComparingTo(lotBQtyAfterAuto);
    }

    @Test
    void autoOutboundInsufficientLotStockRollsBack() {
        inventoryPostingService.postInbound(inbound(LOT_ONLY_PRODUCT_ID, 8949050L, "ROLLBACK-A", "2.0000", "20.00", null, null), audit());
        inventoryPostingService.postInbound(inbound(LOT_ONLY_PRODUCT_ID, 8949051L, "ROLLBACK-B", "1.0000", "10.00", null, null), laterAudit());

        Assertions.assertThatThrownBy(() -> inventoryPostingService.postOutbound(
                        outbound(LOT_ONLY_PRODUCT_ID, 8949052L, null, "4.0000"), audit(), "批次库存不足"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("批次库存不足");

        Assertions.assertThat(lotQty(LOT_ONLY_PRODUCT_ID, "ROLLBACK-A")).isEqualByComparingTo("2.0000");
        Assertions.assertThat(lotQty(LOT_ONLY_PRODUCT_ID, "ROLLBACK-B")).isEqualByComparingTo("1.0000");
        Integer txnCount = jdbcTemplate.queryForObject("""
                select count(*)
                from inv_txn
                where company_id = ? and biz_type = 'LOT_TEST_OUT' and biz_line_id = 8949052
                """, Integer.class, COMPANY_ID);
        Assertions.assertThat(txnCount).isZero();
    }

    @Test
    void explicitOutboundRejectsExpiredLot() {
        inventoryPostingService.postInbound(inbound(LOT_SHELF_PRODUCT_ID, 8949070L, "EXPIRED-EXPLICIT", "5.0000", "50.00",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 24)), audit());

        Assertions.assertThatThrownBy(() -> inventoryPostingService.postOutbound(
                        outbound(LOT_SHELF_PRODUCT_ID, 8949071L, "EXPIRED-EXPLICIT", "1.0000"), audit(), "批次库存不足"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("批次已过期，不能出库");

        Assertions.assertThat(lotQty(LOT_SHELF_PRODUCT_ID, "EXPIRED-EXPLICIT")).isEqualByComparingTo("5.0000");
        Assertions.assertThat(txnCount(8949071L)).isZero();
    }

    @Test
    void autoOutboundSkipsExpiredLots() {
        inventoryPostingService.postInbound(inbound(LOT_SHELF_PRODUCT_ID, 8949072L, "EXPIRED-AUTO", "5.0000", "50.00",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 24)), audit());
        inventoryPostingService.postInbound(inbound(LOT_SHELF_PRODUCT_ID, 8949073L, "VALID-AUTO", "5.0000", "60.00",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 25)), laterAudit());

        BigDecimal cost = inventoryPostingService.postOutbound(
                outbound(LOT_SHELF_PRODUCT_ID, 8949074L, null, "3.0000"), audit(), "批次库存不足");

        Assertions.assertThat(cost).isEqualByComparingTo("36.00");
        Assertions.assertThat(txnLots(8949074L)).containsExactly("VALID-AUTO");
        Assertions.assertThat(lotQty(LOT_SHELF_PRODUCT_ID, "EXPIRED-AUTO")).isEqualByComparingTo("5.0000");
        Assertions.assertThat(lotQty(LOT_SHELF_PRODUCT_ID, "VALID-AUTO")).isEqualByComparingTo("2.0000");
    }

    @Test
    void autoOutboundFailsWhenOnlyExpiredLotsHaveStock() {
        inventoryPostingService.postInbound(inbound(LOT_SHELF_PRODUCT_ID, 8949075L, "EXPIRED-ONLY", "5.0000", "50.00",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 24)), audit());

        Assertions.assertThatThrownBy(() -> inventoryPostingService.postOutbound(
                        outbound(LOT_SHELF_PRODUCT_ID, 8949076L, null, "1.0000"), audit(), "批次库存不足"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("批次库存不足");

        Assertions.assertThat(lotQty(LOT_SHELF_PRODUCT_ID, "EXPIRED-ONLY")).isEqualByComparingTo("5.0000");
        Assertions.assertThat(txnCount(8949076L)).isZero();
    }

    @Test
    void nonLotProductRejectsLotMetadata() {
        Assertions.assertThatThrownBy(() -> inventoryPostingService.postInbound(
                        inbound(NON_LOT_PRODUCT_ID, 8949060L, "ILLEGAL-LOT", "1.0000", "10.00", null, null), audit()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("未启用批次管理的商品不能填写批次信息");

        inventoryPostingService.postInbound(new InventoryPostingCommand(
                WAREHOUSE_ID,
                NON_LOT_PRODUCT_ID,
                "LOT_TEST_IN",
                "LOT-IN-8949061",
                8949061L,
                new BigDecimal("2.0000"),
                new BigDecimal("20.00"),
                "non lot inbound",
                LocalDate.of(2026, 5, 25),
                null,
                null,
                null
        ), audit());

        Assertions.assertThatThrownBy(() -> inventoryPostingService.postOutbound(
                        outbound(NON_LOT_PRODUCT_ID, 8949062L, "ILLEGAL-LOT", "1.0000"), audit(), "库存不足"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("未启用批次管理的商品不能填写批次信息");
    }

    private void seedProducts() {
        seedProduct(LOT_SHELF_PRODUCT_ID, "LOT-PROD-894201", "批次效期商品", 1, 1);
        seedProduct(LOT_ONLY_PRODUCT_ID, "LOT-PROD-894202", "批次商品", 1, 0);
        seedProduct(NON_LOT_PRODUCT_ID, "LOT-PROD-894203", "非批次商品", 0, 0);
    }

    private void seedProduct(long id, String code, String name, int lotControlled, int shelfLifeControlled) {
        jdbcTemplate.update("""
                insert into md_product
                (id, company_id, account_book_id, product_code, product_name, product_type, category_name,
                 specification, unit_name, purchase_price, sale_price, tax_rate, status, deleted_flag,
                 lot_controlled, shelf_life_controlled, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, ?, ?, 'FINISHED', 'LOT_TEST', 'spec', 'pcs',
                        10.00, 20.00, 0.0000, 'ACTIVE', 0, ?, ?, 'lot posting test',
                        ?, ?, ?, ?, 0)
                """,
                id,
                COMPANY_ID,
                ACCOUNT_BOOK_ID,
                code,
                name,
                lotControlled,
                shelfLifeControlled,
                USER_ID,
                NOW,
                USER_ID,
                NOW);
    }

    private InventoryPostingCommand inbound(
            Long productId,
            Long bizLineId,
            String lotNo,
            String qty,
            String amount,
            LocalDate productionDate,
            LocalDate expiryDate
    ) {
        return new InventoryPostingCommand(
                WAREHOUSE_ID,
                productId,
                "LOT_TEST_IN",
                "LOT-IN-" + bizLineId,
                bizLineId,
                new BigDecimal(qty),
                new BigDecimal(amount),
                "lot inbound",
                LocalDate.of(2026, 5, 25),
                lotNo,
                productionDate,
                expiryDate
        );
    }

    private InventoryPostingCommand outbound(Long productId, Long bizLineId, String lotNo, String qty) {
        return new InventoryPostingCommand(
                WAREHOUSE_ID,
                productId,
                "LOT_TEST_OUT",
                "LOT-OUT-" + bizLineId,
                bizLineId,
                new BigDecimal(qty),
                BigDecimal.ZERO,
                "lot outbound",
                LocalDate.of(2026, 5, 25),
                lotNo,
                null,
                null
        );
    }

    private AuditMetadata audit() {
        return new AuditMetadata(COMPANY_ID, ACCOUNT_BOOK_ID, USER_ID, NOW);
    }

    private AuditMetadata laterAudit() {
        return new AuditMetadata(COMPANY_ID, ACCOUNT_BOOK_ID, USER_ID, NOW.plusHours(1));
    }

    private BigDecimal lotQty(long productId, String lotNo) {
        return jdbcTemplate.queryForObject("""
                select qty_on_hand
                from inv_lot_balance
                where company_id = ? and product_id = ? and lot_no = ?
                """, BigDecimal.class, COMPANY_ID, productId, lotNo);
    }

    private BigDecimal aggregateQty(long productId) {
        return jdbcTemplate.queryForObject("""
                select qty_on_hand
                from inv_balance
                where company_id = ? and product_id = ?
                """, BigDecimal.class, COMPANY_ID, productId);
    }

    private Integer txnCount(long bizLineId) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from inv_txn
                where company_id = ?
                  and biz_type = 'LOT_TEST_OUT'
                  and biz_line_id = ?
                  and direction = 'OUT'
                """, Integer.class, COMPANY_ID, bizLineId);
    }

    private List<String> txnLots(long bizLineId) {
        return jdbcTemplate.queryForList("""
                select lot_no
                from inv_txn
                where company_id = ?
                  and biz_type = 'LOT_TEST_OUT'
                  and biz_line_id = ?
                  and direction = 'OUT'
                order by id
                """, String.class, COMPANY_ID, bizLineId);
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

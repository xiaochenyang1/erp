package com.tuowei.erp.inventory.stock;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.testsupport.MysqlSpringBootIntegrationTest;
import com.tuowei.erp.testsupport.WithErpUser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfSystemProperty(named = "erp.testcontainers.enabled", matches = "true")
@Tag("testcontainers")
@WithErpUser(accountBookId = 1L)
class InventoryPostingMysqlIntegrationTest extends MysqlSpringBootIntegrationTest {

    private static final long COMPANY_ID = 1L;
    private static final long ACCOUNT_BOOK_ID = 1L;
    private static final long USER_ID = 1L;
    private static final long WAREHOUSE_ID = 897101L;
    private static final long LOT_PRODUCT_ID = 897201L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 25, 10, 0);

    @Autowired
    InventoryPostingService inventoryPostingService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        cleanup();
        seedLotProduct();
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("""
                delete from inv_txn
                where biz_type in ('MYSQL_LOT_IN', 'MYSQL_LOT_OUT')
                   or product_id = ?
                """, LOT_PRODUCT_ID);
        jdbcTemplate.update("delete from inv_lot_balance where product_id = ?", LOT_PRODUCT_ID);
        jdbcTemplate.update("delete from inv_balance where product_id = ?", LOT_PRODUCT_ID);
        jdbcTemplate.update("delete from md_product where id = ?", LOT_PRODUCT_ID);
    }

    @Test
    void repeatedInboundWithSameBusinessLineAndLotDoesNotMutateStockTwiceOnMysql() {
        InventoryPostingCommand command = inbound(8979001L, "MYSQL-LOT-A", "10.0000", "100.00");

        inventoryPostingService.postInbound(command, audit());
        inventoryPostingService.postInbound(command, audit());

        Assertions.assertThat(lotQty("MYSQL-LOT-A")).isEqualByComparingTo("10.0000");
        Assertions.assertThat(balanceQty()).isEqualByComparingTo("10.0000");
        Assertions.assertThat(txnCount("MYSQL_LOT_IN", 8979001L, "IN")).isEqualTo(1);
    }

    @Test
    void autoOutboundAcrossLotsPersistsSplitTransactionsOnMysql() {
        inventoryPostingService.postInbound(inbound(8979011L, "MYSQL-FIFO-A", "2.0000", "20.00"), audit());
        inventoryPostingService.postInbound(inbound(8979012L, "MYSQL-FIFO-B", "3.0000", "45.00"), laterAudit());

        BigDecimal cost = inventoryPostingService.postOutbound(outbound(8979013L, "4.0000"), audit(), "批次库存不足");

        Assertions.assertThat(cost).isEqualByComparingTo("50.00");
        Assertions.assertThat(lotQty("MYSQL-FIFO-A")).isEqualByComparingTo("0.0000");
        Assertions.assertThat(lotQty("MYSQL-FIFO-B")).isEqualByComparingTo("1.0000");
        Assertions.assertThat(txnLots(8979013L)).containsExactly("MYSQL-FIFO-A", "MYSQL-FIFO-B");
    }

    private void seedLotProduct() {
        jdbcTemplate.update("""
                insert into md_product
                (id, company_id, account_book_id, product_code, product_name, product_type, category_name,
                 specification, unit_name, purchase_price, sale_price, tax_rate, status, deleted_flag,
                 lot_controlled, shelf_life_controlled, remark, created_by, created_time, updated_by, updated_time, version)
                values (?, ?, ?, 'MYSQL-LOT-PROD-897201', 'MySQL批次商品', 'FINISHED', 'MYSQL_LOT_TEST', 'spec', 'pcs',
                        10.00, 20.00, 0.0000, 'ACTIVE', 0, 1, 0, 'mysql lot posting test',
                        ?, ?, ?, ?, 0)
                """, LOT_PRODUCT_ID, COMPANY_ID, ACCOUNT_BOOK_ID, USER_ID, NOW, USER_ID, NOW);
    }

    private InventoryPostingCommand inbound(Long bizLineId, String lotNo, String qty, String amount) {
        return new InventoryPostingCommand(
                WAREHOUSE_ID,
                LOT_PRODUCT_ID,
                "MYSQL_LOT_IN",
                "MYSQL-IN-" + bizLineId,
                bizLineId,
                new BigDecimal(qty),
                new BigDecimal(amount),
                "mysql lot inbound",
                LocalDate.of(2026, 5, 25),
                lotNo,
                null,
                null
        );
    }

    private InventoryPostingCommand outbound(Long bizLineId, String qty) {
        return new InventoryPostingCommand(
                WAREHOUSE_ID,
                LOT_PRODUCT_ID,
                "MYSQL_LOT_OUT",
                "MYSQL-OUT-" + bizLineId,
                bizLineId,
                new BigDecimal(qty),
                BigDecimal.ZERO,
                "mysql lot outbound",
                LocalDate.of(2026, 5, 25),
                null,
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

    private BigDecimal lotQty(String lotNo) {
        return jdbcTemplate.queryForObject("""
                select qty_on_hand
                from inv_lot_balance
                where company_id = ? and product_id = ? and lot_no = ?
                """, BigDecimal.class, COMPANY_ID, LOT_PRODUCT_ID, lotNo);
    }

    private BigDecimal balanceQty() {
        return jdbcTemplate.queryForObject("""
                select qty_on_hand
                from inv_balance
                where company_id = ? and product_id = ?
                """, BigDecimal.class, COMPANY_ID, LOT_PRODUCT_ID);
    }

    private Integer txnCount(String bizType, long bizLineId, String direction) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from inv_txn
                where company_id = ?
                  and biz_type = ?
                  and biz_line_id = ?
                  and direction = ?
                """, Integer.class, COMPANY_ID, bizType, bizLineId, direction);
    }

    private List<String> txnLots(long bizLineId) {
        return jdbcTemplate.queryForList("""
                select lot_no
                from inv_txn
                where company_id = ?
                  and biz_type = 'MYSQL_LOT_OUT'
                  and biz_line_id = ?
                  and direction = 'OUT'
                order by id
                """, String.class, COMPANY_ID, bizLineId);
    }
}

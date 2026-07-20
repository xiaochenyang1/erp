package com.tuowei.erp.inventory.stock;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.stock.service.InventoryStockQueryService;
import com.tuowei.erp.inventory.stock.web.InventoryLotBalancePageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotBalanceResponse;
import com.tuowei.erp.inventory.stock.web.InventoryLotExpiryAlertQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotExpiryAlertResponse;
import com.tuowei.erp.inventory.stock.web.InventoryLotTraceQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotTraceResponse;
import com.tuowei.erp.testsupport.WithErpUser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class InventoryLotBalanceQueryTest {

    @Autowired
    InventoryStockQueryService service;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    Clock clock;

    @BeforeEach
    void setup() {
        cleanup();
        when(clock.instant()).thenReturn(Instant.parse("2026-06-29T00:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        jdbcTemplate.update("""
                insert into inv_lot_balance
                (id, company_id, account_book_id, warehouse_id, product_id, lot_no,
                 production_date, expiry_date, first_inbound_time, qty_on_hand, qty_reserved,
                 amount_on_hand, created_by, updated_by, version)
                values (894001, 1, 1, 894101, 894201, 'LOT-A',
                        date '2026-01-01', date '2026-06-30', timestamp '2026-01-02 00:00:00',
                        10.0000, 0.0000, 100.00, 894001, 894001, 0)
                """);
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("delete from inv_txn where id between 894000 and 894999 or biz_type = 'TRACE_TEST'");
        jdbcTemplate.update("delete from inv_lot_balance where id between 894000 and 894999");
    }

    @Test
    @WithErpUser(authorities = {"inventory:stock:view"})
    void listsLotBalancesByExpiryWindow() {
        InventoryLotBalancePageQuery query = new InventoryLotBalancePageQuery();
        query.setWarehouseId(894101L);
        query.setProductId(894201L);
        query.setExpiryDateTo(LocalDate.of(2026, 12, 31));

        PageResponse<InventoryLotBalanceResponse> response = service.listLotBalances(query);

        Assertions.assertThat(response.records()).hasSize(1);
        Assertions.assertThat(response.records().get(0).lotNo()).isEqualTo("LOT-A");
        Assertions.assertThat(response.records().get(0).qtyAvailable()).isEqualByComparingTo("10.0000");
    }

    @Test
    @WithErpUser(accountBookId = 1, authorities = {"inventory:stock:view"})
    void hidesLotBalancesFromOtherAccountBook() {
        jdbcTemplate.update("""
                insert into inv_lot_balance
                (id, company_id, account_book_id, warehouse_id, product_id, lot_no,
                 production_date, expiry_date, first_inbound_time, qty_on_hand, qty_reserved,
                 amount_on_hand, created_by, updated_by, version)
                values (894002, 1, 2, 894101, 894201, 'LOT-BOOK-2',
                        date '2026-01-01', date '2026-06-30', timestamp '2026-01-02 00:00:00',
                        10.0000, 0.0000, 100.00, 894001, 894001, 0)
                """);
        InventoryLotBalancePageQuery query = new InventoryLotBalancePageQuery();
        query.setLotNo("LOT-BOOK-2");

        PageResponse<InventoryLotBalanceResponse> response = service.listLotBalances(query);

        Assertions.assertThat(response.records()).isEmpty();
        Assertions.assertThatThrownBy(() -> service.getLotBalanceById(894002L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("批次库存余额不存在");
    }

    @Test
    @WithErpUser(authorities = {"inventory:stock:view"})
    void filtersLotBalancesByExpiringWithinDaysUsingClock() {
        jdbcTemplate.update("""
                insert into inv_lot_balance
                (id, company_id, account_book_id, warehouse_id, product_id, lot_no,
                 production_date, expiry_date, first_inbound_time, qty_on_hand, qty_reserved,
                 amount_on_hand, created_by, updated_by, version)
                values (894002, 1, 1, 894101, 894201, 'LOT-WINDOW',
                        date '2026-01-01', date '2026-07-02', timestamp '2026-01-03 00:00:00',
                        10.0000, 0.0000, 100.00, 894001, 894001, 0)
                """);
        jdbcTemplate.update("""
                insert into inv_lot_balance
                (id, company_id, account_book_id, warehouse_id, product_id, lot_no,
                 production_date, expiry_date, first_inbound_time, qty_on_hand, qty_reserved,
                 amount_on_hand, created_by, updated_by, version)
                values (894003, 1, 1, 894101, 894201, 'LOT-OUT',
                        date '2026-01-01', date '2026-07-03', timestamp '2026-01-04 00:00:00',
                        10.0000, 0.0000, 100.00, 894001, 894001, 0)
                """);
        InventoryLotBalancePageQuery query = new InventoryLotBalancePageQuery();
        query.setExpiringWithinDays(3);

        PageResponse<InventoryLotBalanceResponse> response = service.listLotBalances(query);

        Assertions.assertThat(response.records())
                .extracting(InventoryLotBalanceResponse::lotNo)
                .containsExactly("LOT-A", "LOT-WINDOW");
    }

    @Test
    @WithErpUser(authorities = {"inventory:stock:view"})
    void clampsNegativeExpiringWithinDaysToToday() {
        jdbcTemplate.update("""
                insert into inv_lot_balance
                (id, company_id, account_book_id, warehouse_id, product_id, lot_no,
                 production_date, expiry_date, first_inbound_time, qty_on_hand, qty_reserved,
                 amount_on_hand, created_by, updated_by, version)
                values (894002, 1, 1, 894101, 894201, 'LOT-TODAY',
                        date '2026-01-01', date '2026-06-29', timestamp '2026-01-03 00:00:00',
                        10.0000, 0.0000, 100.00, 894001, 894001, 0)
                """);
        InventoryLotBalancePageQuery query = new InventoryLotBalancePageQuery();
        query.setExpiringWithinDays(-3);

        PageResponse<InventoryLotBalanceResponse> response = service.listLotBalances(query);

        Assertions.assertThat(response.records())
                .extracting(InventoryLotBalanceResponse::lotNo)
                .containsExactly("LOT-TODAY");
    }

    @Test
    @WithErpUser(authorities = {"inventory:stock:view"})
    void escapesLotNoLikeWildcards() {
        jdbcTemplate.update("""
                insert into inv_lot_balance
                (id, company_id, account_book_id, warehouse_id, product_id, lot_no,
                 production_date, expiry_date, first_inbound_time, qty_on_hand, qty_reserved,
                 amount_on_hand, created_by, updated_by, version)
                values (894002, 1, 1, 894101, 894201, 'LOT_%',
                        date '2026-01-01', date '2026-07-02', timestamp '2026-01-03 00:00:00',
                        10.0000, 0.0000, 100.00, 894001, 894001, 0)
                """);
        InventoryLotBalancePageQuery query = new InventoryLotBalancePageQuery();
        query.setLotNo("LOT_");

        PageResponse<InventoryLotBalanceResponse> response = service.listLotBalances(query);

        Assertions.assertThat(response.records())
                .extracting(InventoryLotBalanceResponse::lotNo)
                .containsExactly("LOT_%");
    }

    @Test
    @WithErpUser(authorities = {"inventory:stock:view"})
    void escapesLotNoLikeEscapeCharacter() {
        jdbcTemplate.update("""
                insert into inv_lot_balance
                (id, company_id, account_book_id, warehouse_id, product_id, lot_no,
                 production_date, expiry_date, first_inbound_time, qty_on_hand, qty_reserved,
                 amount_on_hand, created_by, updated_by, version)
                values (894002, 1, 1, 894101, 894201, 'LOT|A',
                        date '2026-01-01', date '2026-07-02', timestamp '2026-01-03 00:00:00',
                        10.0000, 0.0000, 100.00, 894001, 894001, 0)
                """);
        InventoryLotBalancePageQuery query = new InventoryLotBalancePageQuery();
        query.setLotNo("LOT|");

        PageResponse<InventoryLotBalanceResponse> response = service.listLotBalances(query);

        Assertions.assertThat(response.records())
                .extracting(InventoryLotBalanceResponse::lotNo)
                .containsExactly("LOT|A");
    }

    @Test
    @WithErpUser(authorities = {"inventory:stock:view"})
    void tracesLotTransactionsByProductAndLot() {
        seedTraceTxn(894101L, 894201L, "TRACE-A", "TRACE_TEST", "TRACE-1", 894701L,
                "IN", "5.0000", "50.00", "2026-06-01T09:00:00");
        seedTraceTxn(894101L, 894201L, "TRACE-A", "TRACE_TEST", "TRACE-2", 894702L,
                "OUT", "2.0000", "20.00", "2026-06-02T09:00:00");
        seedTraceTxn(894101L, 894201L, "TRACE-B", "TRACE_TEST", "TRACE-3", 894703L,
                "IN", "9.0000", "90.00", "2026-06-03T09:00:00");

        InventoryLotTraceQuery query = new InventoryLotTraceQuery();
        query.setProductId(894201L);
        query.setLotNo("TRACE-A");

        PageResponse<InventoryLotTraceResponse> response = service.traceLot(query);

        Assertions.assertThat(response.records())
                .extracting(InventoryLotTraceResponse::bizNo)
                .containsExactly("TRACE-2", "TRACE-1");
        Assertions.assertThat(response.records().get(0).lotNo()).isEqualTo("TRACE-A");
        Assertions.assertThat(response.records().get(0).direction()).isEqualTo("OUT");
    }

    @Test
    @WithErpUser(authorities = {"inventory:stock:view"})
    void traceLotRespectsDirectionAndWarehouseFilters() {
        seedTraceTxn(894101L, 894201L, "TRACE-FILTER", "TRACE_TEST", "TRACE-IN", 894711L,
                "IN", "5.0000", "50.00", "2026-06-01T09:00:00");
        seedTraceTxn(894101L, 894201L, "TRACE-FILTER", "TRACE_TEST", "TRACE-OUT", 894712L,
                "OUT", "2.0000", "20.00", "2026-06-02T09:00:00");
        seedTraceTxn(894102L, 894201L, "TRACE-FILTER", "TRACE_TEST", "TRACE-WH2", 894713L,
                "OUT", "1.0000", "10.00", "2026-06-03T09:00:00");

        InventoryLotTraceQuery query = new InventoryLotTraceQuery();
        query.setProductId(894201L);
        query.setLotNo("TRACE-FILTER");
        query.setWarehouseId(894101L);
        query.setDirection("out");

        PageResponse<InventoryLotTraceResponse> response = service.traceLot(query);

        Assertions.assertThat(response.records())
                .extracting(InventoryLotTraceResponse::bizNo)
                .containsExactly("TRACE-OUT");
    }

    @Test
    @WithErpUser(authorities = {"inventory:stock:view"})
    void listsExpiredAndExpiringAvailableLots() {
        seedAlertLot(894010L, "ALERT-EXPIRED", "2026-06-28", "5.0000", "0.0000");
        seedAlertLot(894011L, "ALERT-TODAY", "2026-06-29", "5.0000", "0.0000");
        seedAlertLot(894012L, "ALERT-SOON", "2026-07-29", "5.0000", "0.0000");
        seedAlertLot(894013L, "ALERT-LATE", "2026-07-30", "5.0000", "0.0000");

        InventoryLotExpiryAlertQuery query = new InventoryLotExpiryAlertQuery();

        PageResponse<InventoryLotExpiryAlertResponse> response = service.listLotExpiryAlerts(query);

        Assertions.assertThat(response.records())
                .extracting(InventoryLotExpiryAlertResponse::lotNo)
                .containsExactly("ALERT-EXPIRED", "ALERT-TODAY", "LOT-A", "ALERT-SOON");
        Assertions.assertThat(response.records())
                .extracting(InventoryLotExpiryAlertResponse::expiryStatus)
                .containsExactly("EXPIRED", "EXPIRING", "EXPIRING", "EXPIRING");
    }

    @Test
    @WithErpUser(authorities = {"inventory:stock:view"})
    void alertQueryCanFilterStatusAndExcludesZeroAvailableLots() {
        seedAlertLot(894010L, "ALERT-EXPIRED", "2026-06-28", "5.0000", "0.0000");
        seedAlertLot(894011L, "ALERT-ZERO", "2026-06-28", "5.0000", "5.0000");
        seedAlertLot(894012L, "ALERT-SOON", "2026-07-10", "5.0000", "0.0000");

        InventoryLotExpiryAlertQuery query = new InventoryLotExpiryAlertQuery();
        query.setStatus("expired");

        PageResponse<InventoryLotExpiryAlertResponse> response = service.listLotExpiryAlerts(query);

        Assertions.assertThat(response.records())
                .extracting(InventoryLotExpiryAlertResponse::lotNo)
                .containsExactly("ALERT-EXPIRED");
        Assertions.assertThat(response.records().get(0).daysToExpiry()).isEqualTo(-1L);
    }

    @Test
    @WithErpUser(authorities = {"inventory:stock:view"})
    void alertQueryRejectsInvalidStatus() {
        InventoryLotExpiryAlertQuery query = new InventoryLotExpiryAlertQuery();
        query.setStatus("BAD");

        Assertions.assertThatThrownBy(() -> service.listLotExpiryAlerts(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("预警状态不正确");
    }

    private void seedAlertLot(long id, String lotNo, String expiryDate, String qtyOnHand, String qtyReserved) {
        jdbcTemplate.update("""
                insert into inv_lot_balance
                (id, company_id, account_book_id, warehouse_id, product_id, lot_no,
                 production_date, expiry_date, first_inbound_time, qty_on_hand, qty_reserved,
                 amount_on_hand, created_by, updated_by, version)
                values (?, 1, 1, 894101, 894201, ?,
                        date '2026-01-01', ?, timestamp '2026-01-03 00:00:00',
                        ?, ?, 100.00, 894001, 894001, 0)
                """,
                id,
                lotNo,
                java.sql.Date.valueOf(expiryDate),
                new java.math.BigDecimal(qtyOnHand),
                new java.math.BigDecimal(qtyReserved));
    }

    private void seedTraceTxn(
            long warehouseId,
            long productId,
            String lotNo,
            String bizType,
            String bizNo,
            long bizLineId,
            String direction,
            String qty,
            String amount,
            String occurredTime
    ) {
        jdbcTemplate.update("""
                insert into inv_txn
                (id, company_id, account_book_id, warehouse_id, product_id, biz_type, biz_no, biz_line_id,
                 direction, qty, amount, unit_cost, occurred_time, lot_no, production_date, expiry_date, lot_key,
                 remark, created_by, updated_by, version)
                values (?, 1, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                        date '2026-01-01', date '2026-12-31', ?, 'trace test', 894001, 894001, 0)
                """,
                bizLineId,
                warehouseId,
                productId,
                bizType,
                bizNo,
                bizLineId,
                direction,
                new java.math.BigDecimal(qty),
                new java.math.BigDecimal(amount),
                new java.math.BigDecimal(amount).divide(new java.math.BigDecimal(qty), 6, java.math.RoundingMode.HALF_UP),
                java.time.LocalDateTime.parse(occurredTime),
                lotNo,
                lotNo);
    }
}

package com.tuowei.erp.inventory.mrp;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.mrp.service.MrpPlanCalculationService;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** Executes the real input SQL against relational fixtures, including duplicate plan links and partial fulfillment. */
class MrpPlanCalculationServiceTest {
    private static final AuditMetadata AUDIT = new AuditMetadata(9001L, 101L, 202L, LocalDateTime.of(2026, 11, 6, 11, 30));
    private JdbcTemplate jdbc;
    private MrpPlanCalculationService service;

    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:mrp_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds); service = new MrpPlanCalculationService(jdbc);
        String scope = "company_id bigint default 101, account_book_id bigint default 202, ";
        jdbc.execute("create table sal_order(id bigint primary key, " + scope + "order_no varchar, warehouse_id bigint, status varchar, delivery_status varchar, deleted_flag int default 0)");
        jdbc.execute("create table sal_order_line(id bigint primary key, " + scope + "order_id bigint, product_id bigint, qty decimal(18,4), delivered_qty decimal(18,4) default 0)");
        jdbc.execute("create table inv_alert_rule(id bigint primary key, " + scope + "warehouse_id bigint, product_id bigint, min_qty decimal(18,4), enabled int default 1, deleted_flag int default 0)");
        jdbc.execute("create table inv_balance(" + scope + "warehouse_id bigint, product_id bigint, qty_on_hand decimal(18,4))");
        jdbc.execute("create table pur_order(id bigint primary key, " + scope + "order_no varchar, planned_warehouse_id bigint, status varchar, deleted_flag int default 0)");
        jdbc.execute("create table pur_order_line(id bigint primary key, " + scope + "order_id bigint, product_id bigint, qty decimal(18,4), received_qty decimal(18,4) default 0)");
        jdbc.execute("create table prd_order(id bigint primary key, " + scope + "order_no varchar, product_id bigint, finished_warehouse_id bigint, material_warehouse_id bigint, planned_qty decimal(18,4), completed_qty decimal(18,4) default 0, status varchar, deleted_flag int default 0)");
        jdbc.execute("create table prd_order_material(id bigint primary key, " + scope + "order_id bigint, material_product_id bigint, required_qty decimal(18,4), issued_qty decimal(18,4) default 0)");
        jdbc.execute("create table inv_mrp_run_line(id bigint primary key, " + scope + "converted_biz_type varchar, converted_biz_id bigint, status varchar, deleted_flag int default 0)");
        jdbc.execute("create table md_product(id bigint primary key, " + scope + "product_code varchar, product_name varchar, mrp_supply_type varchar default 'AUTO', mrp_material_warehouse_id bigint, status varchar default 'ACTIVE', deleted_flag int default 0)");
        jdbc.execute("create table md_warehouse(id bigint primary key, " + scope + "warehouse_name varchar, status varchar default 'ACTIVE', deleted_flag int default 0)");
        jdbc.execute("create table prd_bom(id bigint primary key, " + scope + "bom_no varchar, product_id bigint, base_qty decimal(18,4) default 1, version int default 0, status varchar default 'ACTIVE', deleted_flag int default 0)");
        jdbc.execute("create table prd_bom_line(id bigint primary key, " + scope + "bom_id bigint, line_no int default 1, material_product_id bigint, qty_per decimal(18,4), loss_rate decimal(18,4) default 0)");
        jdbc.execute("create table sys_sequence_rule(id bigint primary key, " + scope + "biz_type varchar)");
        jdbc.update("insert into md_product(id, product_code, product_name) values (1,'A','成品'),(2,'B','半成品'),(3,'C','原料')");
        jdbc.update("insert into md_warehouse(id, warehouse_name) values (10,'成品仓'),(20,'材料仓')");
    }
    @AfterEach void cleanup() { jdbc.execute("shutdown"); }

    @Test void loadsThreeLevelSalesStockAndBomFactsWithTrace() {
        sales(1, 10, 10); chain(); stock(2, 10, 4); stock(3, 10, 10);
        var result = service.calculate(AUDIT);
        assertThat(result.productionLines()).extracting(MrpPlanCalculationService.ComputedLine::productId).containsExactly(1L, 2L);
        assertThat(result.productionLines().get(1).netQty()).isEqualByComparingTo("16");
        assertThat(result.purchaseLines()).singleElement().satisfies(line -> {
            assertThat(line.netQty()).isEqualByComparingTo("38");
            assertThat(line.trace().level()).isEqualTo(2);
        });
        assertThat(result.snapshot().nodes().get(0).sources().get(0).documentNo()).isEqualTo("SO-1");
    }

    @Test void safetyTargetsArePerWarehouseAndNotNetShortages() {
        stock(3, 10, 20); stock(3, 20, 100);
        jdbc.update("insert into inv_alert_rule(id,warehouse_id,product_id,min_qty) values (1,10,3,24),(2,10,3,22),(3,20,3,10)");
        var result = service.calculate(AUDIT);
        assertThat(result.purchaseLines()).singleElement().satisfies(line -> {
            assertThat(line.netQty()).isEqualByComparingTo("4");
            assertThat(line.trace().warehouseId()).isEqualTo(10L);
        });
        sales(3, 10, 5);
        assertThat(service.calculate(AUDIT).purchaseLines().get(0).netQty()).isEqualByComparingTo("9");
    }

    @Test void purchaseRemainingSupplyIsWarehouseSpecificAndCountedOnceAfterApproval() {
        sales(3, 10, 20); purchase(31, 10L, "APPROVED", 10, 3); purchase(32, 20L, "APPROVED", 100, 0);
        link(1, "PURCHASE_ORDER", 31); link(2, "PURCHASE_ORDER", 31);
        var line = service.calculate(AUDIT).purchaseLines().get(0);
        assertThat(line.netQty()).isEqualByComparingTo("13");
        assertThat(line.trace().purchaseSupplyQty()).isEqualByComparingTo("7");
        assertThat(line.trace().plannedSupplyQty()).isZero();
    }

    @Test void onlyMrpConvertedDraftsReserveSupplyAndCancelledOrdersReleaseIt() {
        sales(3, 10, 20); purchase(31, 10L, "DRAFT", 8, 0); purchase(32, 10L, "DRAFT", 100, 0);
        link(1, "PURCHASE_ORDER", 31); link(2, "PURCHASE_ORDER", 31);
        var line = service.calculate(AUDIT).purchaseLines().get(0);
        assertThat(line.netQty()).isEqualByComparingTo("12");
        assertThat(line.trace().plannedSupplyQty()).isEqualByComparingTo("8");
        jdbc.update("update pur_order set status='CANCELLED' where id=31");
        assertThat(service.calculate(AUDIT).purchaseLines().get(0).netQty()).isEqualByComparingTo("20");
    }

    @Test void unallocatedHistoricalPurchasesProduceWarningsInsteadOfCrossWarehouseSupply() {
        sales(3, 10, 20); purchase(31, null, "APPROVED", 10, 3);
        var result = service.calculate(AUDIT);
        assertThat(result.purchaseLines().get(0).netQty()).isEqualByComparingTo("20");
        assertThat(result.snapshot().warnings()).singleElement().satisfies(w -> {
            assertThat(w.documentNo()).isEqualTo("PO-31"); assertThat(w.qty()).isEqualByComparingTo("7");
        });
        jdbc.update("update pur_order set planned_warehouse_id=10 where id=31");
        assertThat(service.calculate(AUDIT).purchaseLines().get(0).netQty()).isEqualByComparingTo("13");
        assertThat(service.calculate(AUDIT).snapshot().warnings()).isEmpty();
    }

    @Test void productionRemainderAndUnissuedMaterialsAreCountedOnceInTheirOwnWarehouses() {
        sales(1, 10, 10); chain();
        jdbc.update("update md_product set mrp_material_warehouse_id=20 where id=1");
        production("MATERIAL_ISSUED"); link(1, "PRODUCTION_ORDER", 40); link(2, "PRODUCTION_ORDER", 40);
        var result = service.calculate(AUDIT);
        assertThat(result.productionLines().get(0).netQty()).isEqualByComparingTo("6");
        var b = result.productionLines().stream().filter(l -> l.productId()==2).findFirst().orElseThrow();
        assertThat(b.netQty()).isEqualByComparingTo("17"); // 6*2 new + (12-7) unissued.
        assertThat(b.trace().warehouseId()).isEqualTo(20L);
        assertThat(b.trace().sources()).anySatisfy(s -> assertThat(s.type()).isEqualTo("PRODUCTION_MATERIAL"));
        assertThat(result.snapshot().nodes().get(0).productionSupplyQty()).isEqualByComparingTo("4");
    }

    @Test void plannedProductionIncludesMaterialDemandWhileOrdinaryDraftDoesNot() {
        sales(1, 10, 10); chain(); production("DRAFT");
        assertThat(service.calculate(AUDIT).productionLines().get(0).netQty()).isEqualByComparingTo("10");
        link(1, "PRODUCTION_ORDER", 40);
        var result = service.calculate(AUDIT);
        assertThat(result.productionLines().get(0).netQty()).isEqualByComparingTo("6");
        assertThat(result.snapshot().nodes()).anySatisfy(n -> {
            assertThat(n.productId()).isEqualTo(2L); assertThat(n.warehouseId()).isEqualTo(20L);
            assertThat(n.demandQty()).isEqualByComparingTo("5");
        });
    }

    @Test void fulfilledAndInactiveDocumentsDoNotCreateSupplyOrDemand() {
        sales(3, 10, 10); purchase(31, 10L, "CLOSED", 100, 0); purchase(32, 10L, "APPROVED", 9, 9);
        jdbc.update("update sal_order_line set delivered_qty=4");
        assertThat(service.calculate(AUDIT).purchaseLines().get(0).netQty()).isEqualByComparingTo("6");
        jdbc.update("update sal_order set status='DRAFT'");
        assertThat(service.calculate(AUDIT).purchaseLines()).isEmpty();
    }

    @Test void allFactsAndLinksAreScopedToCompanyAndBook() {
        sales(3, 10, 10); purchase(31, 10L, "APPROVED", 100, 0); stock(3, 10, 100);
        jdbc.update("update pur_order set company_id=999");
        jdbc.update("update inv_balance set account_book_id=999");
        purchase(32, 10L, "DRAFT", 100, 0); link(1, "PURCHASE_ORDER", 32);
        jdbc.update("update inv_mrp_run_line set account_book_id=999");
        assertThat(service.calculate(AUDIT).purchaseLines().get(0).netQty()).isEqualByComparingTo("10");
        jdbc.update("update sal_order set account_book_id=999");
        assertThat(service.calculate(AUDIT).snapshot().nodes()).isEmpty();
    }

    @Test void refusesMissingInputTablesAndMissingPlanningRule() {
        assertThatThrownBy(() -> service.lockPlanning(AUDIT)).hasMessageContaining("编号规则不存在");
        jdbc.update("insert into sys_sequence_rule(id,biz_type) values(1,'MRP_RUN')");
        service.lockPlanning(AUDIT);
        jdbc.execute("drop table pur_order");
        assertThatThrownBy(() -> service.calculate(AUDIT)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    private void sales(long product, long warehouse, int qty) {
        jdbc.update("insert into sal_order(id,order_no,warehouse_id,status,delivery_status) values(?, ?, ?, 'APPROVED','NOT_DELIVERED')", product, "SO-"+product, warehouse);
        jdbc.update("insert into sal_order_line(id,order_id,product_id,qty) values(?,?,?,?)", product, product, product, qty);
    }
    private void stock(long product, long warehouse, int qty) { jdbc.update("insert into inv_balance(product_id,warehouse_id,qty_on_hand) values(?,?,?)",product,warehouse,qty); }
    private void chain() {
        jdbc.update("insert into prd_bom(id,bom_no,product_id) values(101,'BOM-A',1),(102,'BOM-B',2)");
        jdbc.update("insert into prd_bom_line(id,bom_id,material_product_id,qty_per) values(111,101,2,2),(112,102,3,3)");
    }
    private void purchase(long id, Long warehouse, String status, int qty, int received) {
        jdbc.update("insert into pur_order(id,order_no,planned_warehouse_id,status) values(?,?,?,?)",id,"PO-"+id,warehouse,status);
        jdbc.update("insert into pur_order_line(id,order_id,product_id,qty,received_qty) values(?,?,3,?,?)",id,id,qty,received);
    }
    private void link(long id, String type, long document) { jdbc.update("insert into inv_mrp_run_line(id,converted_biz_type,converted_biz_id,status) values(?,?,?,'CONVERTED')",id,type,document); }
    private void production(String status) {
        jdbc.update("insert into prd_order(id,order_no,product_id,finished_warehouse_id,material_warehouse_id,planned_qty,completed_qty,status) values(40,'MO-40',1,10,20,6,2,?)",status);
        jdbc.update("insert into prd_order_material(id,order_id,material_product_id,required_qty,issued_qty) values(41,40,2,12,7)");
    }
}

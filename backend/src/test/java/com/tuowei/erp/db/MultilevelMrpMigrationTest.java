package com.tuowei.erp.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class MultilevelMrpMigrationTest {
    @Test void upgradingPreservesLegacyPlansWithoutInventingWarehousesOrCalculationSnapshots() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:mrp_upgrade;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var directory = H2MigrationTestSupport.copyCompatibleMigrations(getClass(), "mrp-upgrade-migrations");
        String location = "filesystem:" + directory.toAbsolutePath().toString().replace('\\','/');
        Flyway.configure().dataSource(source).locations(location).target("169").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("insert into inv_mrp_run(id,company_id,account_book_id,run_no,as_of_date,status) values(999001,1,1,'MRP-LEGACY','2026-11-05','OPEN')");
        jdbc.update("insert into inv_mrp_run_line(id,company_id,account_book_id,run_id,line_no,product_id,suggestion_type,demand_qty,net_qty,reason) values(999002,1,1,999001,1,123,'PURCHASE',10,7,'legacy explanation')");
        int tables = jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public'",Integer.class);
        Flyway.configure().dataSource(source).locations(location).load().migrate();
        var run = jdbc.queryForMap("select algorithm_version,calculation_snapshot,run_no from inv_mrp_run where id=999001");
        assertThat(run.get("algorithm_version")).isEqualTo(1); assertThat(run.get("calculation_snapshot")).isNull();
        assertThat(run.get("run_no")).isEqualTo("MRP-LEGACY");
        var line = jdbc.queryForMap("select warehouse_id,material_warehouse_id,calculation_trace,reason,net_qty from inv_mrp_run_line where id=999002");
        assertThat(line.get("warehouse_id")).isNull(); assertThat(line.get("material_warehouse_id")).isNull();
        assertThat(line.get("calculation_trace")).isNull(); assertThat(line.get("reason")).isEqualTo("legacy explanation");
        assertThat((java.math.BigDecimal) line.get("net_qty")).isEqualByComparingTo("7");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public'",Integer.class)).isEqualTo(tables);
        assertThat(jdbc.queryForList("select column_name from information_schema.columns where table_name='md_product'",String.class)).contains("mrp_supply_type","mrp_material_warehouse_id");
        assertThat(jdbc.queryForList("select column_name from information_schema.columns where table_name='pur_order'",String.class)).contains("planned_warehouse_id");
        assertThat(Flyway.configure().dataSource(source).locations(location).load().migrate().migrationsExecuted).isZero();
        jdbc.execute("shutdown");
    }
}

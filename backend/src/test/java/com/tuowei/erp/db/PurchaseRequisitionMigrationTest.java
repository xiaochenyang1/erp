package com.tuowei.erp.db;
import org.flywaydb.core.Flyway; import org.junit.jupiter.api.*; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Path; import static org.assertj.core.api.Assertions.assertThat;
class PurchaseRequisitionMigrationTest {
  private static JdbcTemplate jdbc;
  @BeforeAll static void migrate() throws Exception {
    DriverManagerDataSource ds=new DriverManagerDataSource(); ds.setDriverClassName("org.h2.Driver");
    ds.setUrl("jdbc:h2:mem:purchase_requisition;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"); ds.setUsername("sa"); ds.setPassword("");
    Path dir=H2MigrationTestSupport.copyCompatibleMigrations(PurchaseRequisitionMigrationTest.class,"purchase-requisition-migrations");
    Flyway.configure().dataSource(ds).locations("filesystem:"+dir.toAbsolutePath().toString().replace('\\','/')).load().migrate();
    jdbc=new JdbcTemplate(ds);
  }
  @Test void v133CreatesRequisitionTablesAndMenu(){
    assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_name='pur_requisition'", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_name='pur_requisition_line'", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from sys_menu where menu_code in ('PURCHASE_REQUISITION','PURCHASE_REQUISITION_MANAGE')", Integer.class)).isEqualTo(2);
  }
}

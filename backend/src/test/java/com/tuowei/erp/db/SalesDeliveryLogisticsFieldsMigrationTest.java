package com.tuowei.erp.db;
import org.flywaydb.core.Flyway; import org.junit.jupiter.api.*; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Path; import static org.assertj.core.api.Assertions.assertThat;
class SalesDeliveryLogisticsFieldsMigrationTest {
  private static JdbcTemplate jdbc;
  @BeforeAll static void migrate() throws Exception {
    DriverManagerDataSource ds=new DriverManagerDataSource(); ds.setDriverClassName("org.h2.Driver");
    ds.setUrl("jdbc:h2:mem:sales_delivery_logistics;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"); ds.setUsername("sa"); ds.setPassword("");
    Path dir=H2MigrationTestSupport.copyCompatibleMigrations(SalesDeliveryLogisticsFieldsMigrationTest.class,"sales-delivery-logistics-migrations");
    Flyway.configure().dataSource(ds).locations("filesystem:"+dir.toAbsolutePath().toString().replace('\\','/')).load().migrate();
    jdbc=new JdbcTemplate(ds);
  }
  @Test void v134AddsCarrierAndTrackingColumns(){
    assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name='sal_delivery' and column_name='carrier_name'", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name='sal_delivery' and column_name='tracking_no'", Integer.class)).isEqualTo(1);
  }
}

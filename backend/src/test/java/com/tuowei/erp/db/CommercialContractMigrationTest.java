package com.tuowei.erp.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CommercialContractMigrationTest {
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:commercial_contract;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                CommercialContractMigrationTest.class, "commercial-contract-migrations");
        Flyway.configure().dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load().migrate();
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void v148CreatesContractTablesIndexesAndConstraints() {
        assertThat(tableCount("biz_contract")).isEqualTo(1);
        assertThat(tableCount("biz_contract_line")).isEqualTo(1);
        assertThat(indexCount("uk_biz_contract_company_book_no")).isEqualTo(1);
        assertThat(indexCount("uk_biz_contract_line_scope")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from information_schema.columns where table_name='biz_contract' and column_name in ('company_id','account_book_id','contract_type','status','total_amount')", Integer.class)).isEqualTo(5);
    }

    @Test
    void v148SeedsSequenceMenuPermissionsAndAdminBindings() {
        assertThat(jdbcTemplate.queryForObject("select count(*) from sys_sequence_rule where biz_type='COMMERCIAL_CONTRACT'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select prefix from sys_sequence_rule where biz_type='COMMERCIAL_CONTRACT'", String.class)).isEqualTo("CT");
        assertThat(jdbcTemplate.queryForObject("select count(*) from sys_menu where menu_code in ('CONTRACT','CONTRACT_CENTER','CONTRACT_MANAGE','CONTRACT_APPROVE')", Integer.class)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("select count(*) from sys_role_menu where menu_id in (5500,5501,5502,5503) and role_id=3002", Integer.class)).isEqualTo(4);
    }

    @Test
    void v149LinksSalesAndPurchaseOrdersToContractExecution() {
        assertThat(columnCount("sal_order", "contract_id")).isEqualTo(1);
        assertThat(columnCount("sal_order_line", "contract_line_id")).isEqualTo(1);
        assertThat(columnCount("pur_order", "contract_id")).isEqualTo(1);
        assertThat(columnCount("pur_order_line", "contract_line_id")).isEqualTo(1);
        assertThat(indexCount("idx_sal_order_line_contract")).isEqualTo(1);
        assertThat(indexCount("idx_pur_order_line_contract")).isEqualTo(1);
    }

    @Test
    void v150CreatesContractVersionHistoryTable() {
        assertThat(tableCount("biz_contract_version")).isEqualTo(1);
        assertThat(indexCount("uk_biz_contract_version_scope")).isEqualTo(1);
        assertThat(indexCount("idx_biz_contract_version_contract")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from information_schema.columns where table_name='biz_contract_version' and column_name in ('contract_id','version_no','event_type','contract_snapshot_json','line_snapshot_json')", Integer.class)).isEqualTo(5);
    }

    private int tableCount(String table) {
        return jdbcTemplate.queryForObject("select count(*) from information_schema.tables where table_name=?", Integer.class, table);
    }

    private int indexCount(String index) {
        return jdbcTemplate.queryForObject("select count(*) from information_schema.indexes where index_name=?", Integer.class, index);
    }

    private int columnCount(String table, String column) {
        return jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns where table_name=? and column_name=?",
                Integer.class, table, column);
    }
}

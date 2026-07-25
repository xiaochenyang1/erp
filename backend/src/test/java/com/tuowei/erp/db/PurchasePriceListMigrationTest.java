package com.tuowei.erp.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PurchasePriceListMigrationTest {

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:purchase_price_list;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                PurchasePriceListMigrationTest.class,
                "purchase-price-list-migrations");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void v128CreatesPurchasePriceTableAndMenuSeeds() {
        List<String> columns = jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_name = 'md_purchase_price'
                order by ordinal_position
                """, String.class);

        assertThat(columns).contains(
                "id",
                "company_id",
                "account_book_id",
                "supplier_id",
                "product_id",
                "list_price",
                "max_price",
                "effective_from",
                "effective_to",
                "status"
        );

        Integer menuCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_menu
                where menu_code in ('PURCHASE_PRICE', 'PURCHASE_PRICE_MANAGE')
                """, Integer.class);
        assertThat(menuCount).isEqualTo(2);

        Integer roleMenuCount = jdbcTemplate.queryForObject("""
                select count(*) from sys_role_menu
                where menu_id in (5430, 5431) and role_id = 3002
                """, Integer.class);
        assertThat(roleMenuCount).isEqualTo(2);
    }
}

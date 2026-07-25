package com.tuowei.erp.db;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseInquiryQuoteLineMigrationTest {

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:purchase_inquiry_quote_lines;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                PurchaseInquiryQuoteLineMigrationTest.class,
                "purchase-inquiry-quote-line-migrations");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .target(MigrationVersion.fromVersion("126"))
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
        seedLegacyHeaderQuote();

        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load()
                .migrate();
    }

    @Test
    void v127CreatesTenantScopedQuoteLineContract() {
        List<String> columns = jdbcTemplate.queryForList("""
                select column_name
                from information_schema.columns
                where table_name = 'pur_inquiry_quote_line'
                order by ordinal_position
                """, String.class);

        assertThat(columns).containsExactly(
                "id",
                "company_id",
                "account_book_id",
                "inquiry_id",
                "quote_id",
                "inquiry_line_id",
                "unit_price",
                "tax_rate",
                "deleted_flag",
                "created_by",
                "created_time",
                "updated_by",
                "updated_time",
                "version"
        );
    }

    @Test
    void v127BackfillsHeaderPriceAcrossEveryActiveInquiryLine() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select company_id, account_book_id, inquiry_id, quote_id, inquiry_line_id,
                       unit_price, tax_rate, created_by, updated_by, version
                from pur_inquiry_quote_line
                where quote_id = 127002
                order by inquiry_line_id
                """);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(row -> ((Number) row.get("inquiry_line_id")).longValue())
                .containsExactly(127003L, 127004L);
        assertThat(rows).allSatisfy(row -> {
            assertThat(((Number) row.get("company_id")).longValue()).isEqualTo(100L);
            assertThat(((Number) row.get("account_book_id")).longValue()).isEqualTo(200L);
            assertThat(((Number) row.get("inquiry_id")).longValue()).isEqualTo(127001L);
            assertThat((BigDecimal) row.get("unit_price")).isEqualByComparingTo("18.75");
            assertThat((BigDecimal) row.get("tax_rate")).isEqualByComparingTo("9.0000");
            assertThat(((Number) row.get("created_by")).longValue()).isEqualTo(7001L);
            assertThat(((Number) row.get("updated_by")).longValue()).isEqualTo(7002L);
            assertThat(((Number) row.get("version")).intValue()).isZero();
        });
    }

    @Test
    void duplicateQuoteAndInquiryLineIsRejectedWithinTenantAndBook() {
        seedInquiryGraph(127101L, 127201L, 127301L, 101L, 201L, "RFQ-V127-DUP", 501L);
        insertQuoteLine(127401L, 101L, 201L, 127101L, 127201L, 127301L);

        assertThatThrownBy(() -> insertQuoteLine(
                127402L,
                101L,
                201L,
                127101L,
                127201L,
                127301L
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void equivalentBusinessKeysRemainIsolatedAcrossAccountBooks() {
        seedInquiryGraph(127111L, 127211L, 127311L, 102L, 211L, "RFQ-V127-BOOK", 511L);
        seedInquiryGraph(127112L, 127212L, 127312L, 102L, 212L, "RFQ-V127-BOOK", 511L);

        insertQuoteLine(127411L, 102L, 211L, 127111L, 127211L, 127311L);
        insertQuoteLine(127412L, 102L, 212L, 127112L, 127212L, 127312L);

        Long count = jdbcTemplate.queryForObject("""
                select count(*)
                from pur_inquiry_quote_line
                where company_id = 102
                  and account_book_id in (211, 212)
                """, Long.class);
        assertThat(count).isEqualTo(2L);
    }

    @Test
    void compositeForeignKeysRejectCrossBookAndCrossInquiryReferences() {
        seedInquiryGraph(127121L, 127221L, 127321L, 103L, 221L, "RFQ-V127-A", 521L);
        seedInquiryGraph(127122L, 127222L, 127322L, 103L, 221L, "RFQ-V127-B", 522L);

        assertThatThrownBy(() -> insertQuoteLine(
                127421L,
                103L,
                222L,
                127121L,
                127221L,
                127321L
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertQuoteLine(
                127422L,
                103L,
                221L,
                127121L,
                127221L,
                127322L
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertQuoteLine(
                127423L,
                103L,
                221L,
                127121L,
                127222L,
                127321L
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private static void seedInquiryGraph(
            Long inquiryId,
            Long quoteId,
            Long inquiryLineId,
            Long companyId,
            Long accountBookId,
            String inquiryNo,
            Long supplierId
    ) {
        jdbcTemplate.update("""
                insert into pur_inquiry (
                    id, company_id, account_book_id, inquiry_no, inquiry_date, status
                ) values (?, ?, ?, ?, date '2026-07-23', 'SUBMITTED')
                """, inquiryId, companyId, accountBookId, inquiryNo);
        jdbcTemplate.update("""
                insert into pur_inquiry_quote (
                    id, company_id, account_book_id, inquiry_id, supplier_id, status
                ) values (?, ?, ?, ?, ?, 'PENDING')
                """, quoteId, companyId, accountBookId, inquiryId, supplierId);
        jdbcTemplate.update("""
                insert into pur_inquiry_line (
                    id, company_id, account_book_id, inquiry_id, line_no, product_id, qty
                ) values (?, ?, ?, ?, 1, ?, 1)
                """, inquiryLineId, companyId, accountBookId, inquiryId, inquiryLineId + 1000);
    }

    private static void seedLegacyHeaderQuote() {
        jdbcTemplate.update("""
                insert into pur_inquiry (
                    id, company_id, account_book_id, inquiry_no, inquiry_date, status,
                    created_by, updated_by
                ) values (127001, 100, 200, 'RFQ-V127-LEGACY', date '2026-07-23', 'SUBMITTED', 7001, 7002)
                """);
        jdbcTemplate.update("""
                insert into pur_inquiry_quote (
                    id, company_id, account_book_id, inquiry_id, supplier_id,
                    unit_price, tax_rate, status, created_by, updated_by
                ) values (127002, 100, 200, 127001, 500, 18.75, 9.0000, 'PENDING', 7001, 7002)
                """);
        jdbcTemplate.update("""
                insert into pur_inquiry_line (
                    id, company_id, account_book_id, inquiry_id, line_no, product_id, qty,
                    deleted_flag, created_by, updated_by
                ) values
                    (127003, 100, 200, 127001, 1, 10001, 2, 0, 7001, 7002),
                    (127004, 100, 200, 127001, 2, 10002, 3, 0, 7001, 7002),
                    (127005, 100, 200, 127001, 3, 10003, 4, 1, 7001, 7002)
                """);
    }

    private static void insertQuoteLine(
            Long id,
            Long companyId,
            Long accountBookId,
            Long inquiryId,
            Long quoteId,
            Long inquiryLineId
    ) {
        jdbcTemplate.update("""
                insert into pur_inquiry_quote_line (
                    id, company_id, account_book_id, inquiry_id, quote_id, inquiry_line_id,
                    unit_price, tax_rate
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                companyId,
                accountBookId,
                inquiryId,
                quoteId,
                inquiryLineId,
                new BigDecimal("12.50"),
                new BigDecimal("13.0000"));
    }
}

package com.tuowei.erp.db;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseInquiryAtomicConversionMigrationTest {

    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void migrateSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:purchase_inquiry_atomic_conversion;MODE=MySQL;"
                + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        Path migrationDir = H2MigrationTestSupport.copyCompatibleMigrations(
                PurchaseInquiryAtomicConversionMigrationTest.class,
                "purchase-inquiry-atomic-conversion-migrations");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("filesystem:" + migrationDir.toAbsolutePath().toString().replace('\\', '/'))
                .load()
                .migrate();

        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void v125AddsStructuredBidirectionalHeaderAndLineColumns() {
        Long inquiryColumns = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_name = 'pur_inquiry'
                  and column_name in (
                    'converted_order_id', 'converted_order_no', 'converted_by', 'converted_time'
                  )
                """, Long.class);
        Long orderColumns = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_name = 'pur_order'
                  and column_name in ('source_inquiry_id', 'source_inquiry_no', 'source_quote_id')
                """, Long.class);
        Long lineColumns = jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_name = 'pur_order_line'
                  and column_name in ('source_inquiry_id', 'source_inquiry_line_id')
                """, Long.class);

        assertThat(inquiryColumns).isEqualTo(4L);
        assertThat(orderColumns).isEqualTo(3L);
        assertThat(lineColumns).isEqualTo(2L);
    }

    @Test
    void sourceInquiryCanCreateOnlyOnePurchaseOrderWithinTenantAndBook() {
        insertOrder(9101L, 101L, 201L, "PO-V125-1", 5001L);

        assertThatThrownBy(() -> insertOrder(9102L, 101L, 201L, "PO-V125-2", 5001L))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertOrder(9103L, 101L, 202L, "PO-V125-3", 5001L);
        assertThat(countOrdersForSource(101L, 201L, 5001L)).isEqualTo(1L);
        assertThat(countOrdersForSource(101L, 202L, 5001L)).isEqualTo(1L);
    }

    @Test
    void convertedPurchaseOrderCanLinkBackToOnlyOneInquiryWithinTenantAndBook() {
        insertInquiry(9201L, 102L, 201L, "RFQ-V125-1", 9901L);

        assertThatThrownBy(() -> insertInquiry(9202L, 102L, 201L, "RFQ-V125-2", 9901L))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertInquiry(9203L, 102L, 202L, "RFQ-V125-3", 9901L);
    }

    @Test
    void inquiryLineCanMapToOnlyOnePurchaseOrderLineWithinTenantAndBook() {
        insertOrder(9301L, 103L, 201L, "PO-V125-LINE-1", 6001L);
        insertOrder(9302L, 103L, 201L, "PO-V125-LINE-2", 6002L);
        insertOrder(9303L, 103L, 202L, "PO-V125-LINE-3", 6001L);
        insertOrderLine(9401L, 103L, 201L, 9301L, 1, 7001L);

        assertThatThrownBy(() -> insertOrderLine(9402L, 103L, 201L, 9302L, 1, 7001L))
                .isInstanceOf(DataIntegrityViolationException.class);

        insertOrderLine(9403L, 103L, 202L, 9303L, 1, 7001L);
    }

    private static void insertOrder(
            Long id,
            Long companyId,
            Long accountBookId,
            String orderNo,
            Long sourceInquiryId
    ) {
        jdbcTemplate.update("""
                insert into pur_order (
                    id, company_id, account_book_id, order_no, supplier_id, order_date,
                    source_inquiry_id, source_inquiry_no, source_quote_id
                ) values (?, ?, ?, ?, ?, date '2026-07-23', ?, ?, ?)
                """, id, companyId, accountBookId, orderNo, 8001L,
                sourceInquiryId, "RFQ-" + sourceInquiryId, 8101L);
    }

    private static void insertInquiry(
            Long id,
            Long companyId,
            Long accountBookId,
            String inquiryNo,
            Long convertedOrderId
    ) {
        jdbcTemplate.update("""
                insert into pur_inquiry (
                    id, company_id, account_book_id, inquiry_no, inquiry_date, status,
                    converted_order_id, converted_order_no
                ) values (?, ?, ?, ?, date '2026-07-23', 'CONVERTED', ?, ?)
                """, id, companyId, accountBookId, inquiryNo, convertedOrderId, "PO-" + convertedOrderId);
    }

    private static void insertOrderLine(
            Long id,
            Long companyId,
            Long accountBookId,
            Long orderId,
            Integer lineNo,
            Long sourceInquiryLineId
    ) {
        jdbcTemplate.update("""
                insert into pur_order_line (
                    id, company_id, account_book_id, order_id, line_no, product_id,
                    qty, price, tax_rate, tax_amount, amount, source_inquiry_id,
                    source_inquiry_line_id
                ) values (?, ?, ?, ?, ?, ?, 1, 1, 0, 0, 1, ?, ?)
                """, id, companyId, accountBookId, orderId, lineNo, 8201L,
                6001L, sourceInquiryLineId);
    }

    private static Long countOrdersForSource(Long companyId, Long accountBookId, Long sourceInquiryId) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from pur_order
                where company_id = ?
                  and account_book_id = ?
                  and source_inquiry_id = ?
                """, Long.class, companyId, accountBookId, sourceInquiryId);
    }
}

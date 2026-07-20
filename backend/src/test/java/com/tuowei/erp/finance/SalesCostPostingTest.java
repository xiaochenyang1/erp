package com.tuowei.erp.finance;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import com.tuowei.erp.testsupport.WithErpUser;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class SalesCostPostingTest {

    @Autowired
    private FinancePostingService financePostingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("""
                delete from fin_voucher_entry
                where voucher_id in (
                    select id from fin_voucher
                    where source_type in ('SALES_DELIVERY', 'SALES_RETURN')
                      and source_id between 864000 and 864999
                )
                """);
        jdbcTemplate.update("delete from fin_voucher where source_type in ('SALES_DELIVERY', 'SALES_RETURN') and source_id between 864000 and 864999");
        jdbcTemplate.update("delete from fin_receivable where source_type in ('SALES_DELIVERY', 'SALES_RETURN') and source_id between 864000 and 864999");
    }

    @Test
    @WithErpUser
    void salesDeliveryPostsInventoryCostEntriesIdempotently() {
        AuditMetadata audit = audit();
        SalesOrderEntity order = salesOrder();
        SalesDeliveryEntity delivery = salesDelivery(864101L, "SD-864101");

        financePostingService.recordSalesDelivery(delivery, order, new BigDecimal("45.00"), audit);
        financePostingService.recordSalesDelivery(delivery, order, new BigDecimal("45.00"), audit);

        Assertions.assertThat(entryCount("SALES_DELIVERY", 864101L, "6402", "debit_amount", "45.00")).isEqualTo(1);
        Assertions.assertThat(entryCount("SALES_DELIVERY", 864101L, "1001", "credit_amount", "45.00")).isEqualTo(1);
        Assertions.assertThat(voucherEntryCount("SALES_DELIVERY", 864101L)).isEqualTo(4);
    }

    @Test
    @WithErpUser
    void salesReturnPostsReverseInventoryCostEntriesIdempotently() {
        AuditMetadata audit = audit();
        SalesOrderEntity order = salesOrder();
        SalesReturnEntity salesReturn = salesReturn(864201L, "SR-864201");

        financePostingService.recordSalesReturn(salesReturn, order, new BigDecimal("45.00"), audit);
        financePostingService.recordSalesReturn(salesReturn, order, new BigDecimal("45.00"), audit);

        Assertions.assertThat(entryCount("SALES_RETURN", 864201L, "1001", "debit_amount", "45.00")).isEqualTo(1);
        Assertions.assertThat(entryCount("SALES_RETURN", 864201L, "6402", "credit_amount", "45.00")).isEqualTo(1);
        Assertions.assertThat(voucherEntryCount("SALES_RETURN", 864201L)).isEqualTo(4);
    }

    private Integer entryCount(String sourceType, long sourceId, String subjectCode, String amountColumn, String amount) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from fin_voucher_entry e
                join fin_voucher v on v.id = e.voucher_id
                where v.source_type = ?
                  and v.source_id = ?
                  and e.subject_code = ?
                  and %s = ?
                """.formatted(amountColumn), Integer.class, sourceType, sourceId, subjectCode, new BigDecimal(amount));
    }

    private Integer voucherEntryCount(String sourceType, long sourceId) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from fin_voucher_entry e
                join fin_voucher v on v.id = e.voucher_id
                where v.source_type = ?
                  and v.source_id = ?
                """, Integer.class, sourceType, sourceId);
    }

    private AuditMetadata audit() {
        return new AuditMetadata(1L, 1L, 1L, LocalDateTime.of(2026, 5, 22, 10, 0));
    }

    private SalesOrderEntity salesOrder() {
        SalesOrderEntity order = new SalesOrderEntity();
        order.setId(864001L);
        order.setCustomerId(8001L);
        return order;
    }

    private SalesDeliveryEntity salesDelivery(long id, String deliveryNo) {
        SalesDeliveryEntity delivery = new SalesDeliveryEntity();
        delivery.setId(id);
        delivery.setDeliveryNo(deliveryNo);
        delivery.setDeliveryDate(LocalDate.of(2026, 5, 22));
        delivery.setTotalAmount(new BigDecimal("100.00"));
        delivery.setTotalTaxAmount(BigDecimal.ZERO);
        return delivery;
    }

    private SalesReturnEntity salesReturn(long id, String returnNo) {
        SalesReturnEntity salesReturn = new SalesReturnEntity();
        salesReturn.setId(id);
        salesReturn.setReturnNo(returnNo);
        salesReturn.setReturnDate(LocalDate.of(2026, 5, 22));
        salesReturn.setTotalAmount(new BigDecimal("100.00"));
        salesReturn.setTotalTaxAmount(BigDecimal.ZERO);
        return salesReturn;
    }
}

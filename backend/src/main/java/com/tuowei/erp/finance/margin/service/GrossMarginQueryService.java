package com.tuowei.erp.finance.margin.service;

import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Tenant-scoped native query for posted sales and their actual inventory cost. */
@Service
@NativeSqlTenantScoped("margin report scopes by current company/account book")
public class GrossMarginQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditMetadataFactory auditMetadataFactory;

    public GrossMarginQueryService(JdbcTemplate jdbcTemplate, AuditMetadataFactory auditMetadataFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public GrossMarginData load(LocalDate dateFrom, LocalDate dateTo) {
        validateDates(dateFrom, dateTo);
        AuditMetadata audit = auditMetadataFactory.current();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select l.product_id as productId,
                       max(p.product_code) as productCode,
                       max(p.product_name) as productName,
                       coalesce(sum(l.qty), 0) as salesQty,
                       coalesce(sum(l.amount), 0) as salesAmount,
                       coalesce(sum(coalesce(c.cost_amount, 0)), 0) as costAmount
                from sal_delivery d
                join sal_delivery_line l
                  on l.company_id = d.company_id
                 and l.account_book_id = d.account_book_id
                 and l.delivery_id = d.id
                left join md_product p
                  on p.id = l.product_id
                 and p.company_id = d.company_id
                 and p.account_book_id = d.account_book_id
                left join (
                    select t.company_id,
                           t.account_book_id,
                           t.biz_line_id,
                           t.product_id,
                           sum(t.amount) as cost_amount
                    from inv_txn t
                    where t.biz_type = 'SALES_DELIVERY'
                      and t.direction = 'OUT'
                    group by t.company_id, t.account_book_id, t.biz_line_id, t.product_id
                ) c
                  on c.company_id = d.company_id
                 and c.account_book_id = d.account_book_id
                 and c.biz_line_id = l.id
                 and c.product_id = l.product_id
                where d.company_id = ?
                  and d.account_book_id = ?
                  and d.deleted_flag = 0
                  and d.status = 'POSTED'
                  and d.delivery_date >= ?
                  and d.delivery_date <= ?
                group by l.product_id
                order by salesAmount desc
                """, audit.companyId(), audit.accountBookId(), dateFrom, dateTo);
        return new GrossMarginData(dateFrom, dateTo, rows);
    }

    private void validateDates(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null || dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("日期区间不合法");
        }
    }

    public record GrossMarginData(LocalDate dateFrom, LocalDate dateTo, List<Map<String, Object>> rows) {
    }
}

package com.tuowei.erp.finance.margin.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.margin.web.GrossMarginLineResponse;
import com.tuowei.erp.finance.margin.web.GrossMarginSummaryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 毛利简报：按已过账销售发货汇总销售额；成本取商品采购价×数量（近似，非标准成本核算）。
 */
@Service
@NativeSqlTenantScoped("margin report scopes by current company/account book")
public class GrossMarginService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditMetadataFactory auditMetadataFactory;

    public GrossMarginService(JdbcTemplate jdbcTemplate, AuditMetadataFactory auditMetadataFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public GrossMarginSummaryResponse summary(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null || dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("日期区间不合法");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select l.product_id as productId,
                       max(p.product_code) as productCode,
                       max(p.product_name) as productName,
                       coalesce(sum(l.qty), 0) as salesQty,
                       coalesce(sum(l.amount), 0) as salesAmount,
                       coalesce(sum(l.qty * coalesce(p.purchase_price, 0)), 0) as costAmount
                from sal_delivery d
                join sal_delivery_line l
                  on l.company_id = d.company_id
                 and l.account_book_id = d.account_book_id
                 and l.delivery_id = d.id
                left join md_product p
                  on p.id = l.product_id
                where d.company_id = ?
                  and d.account_book_id = ?
                  and d.deleted_flag = 0
                  and d.status = 'POSTED'
                  and d.delivery_date >= ?
                  and d.delivery_date <= ?
                group by l.product_id
                order by salesAmount desc
                """, audit.companyId(), audit.accountBookId(), dateFrom, dateTo);

        List<GrossMarginLineResponse> lines = new ArrayList<>();
        BigDecimal salesTotal = BigDecimal.ZERO;
        BigDecimal costTotal = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            BigDecimal salesQty = toBd(row.get("salesQty"));
            BigDecimal salesAmount = ScalePrecision.amount(toBd(row.get("salesAmount")));
            BigDecimal costAmount = ScalePrecision.amount(toBd(row.get("costAmount")));
            BigDecimal margin = ScalePrecision.amount(salesAmount.subtract(costAmount));
            BigDecimal rate = salesAmount.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : margin.multiply(new BigDecimal("100")).divide(salesAmount, 2, RoundingMode.HALF_UP);
            salesTotal = salesTotal.add(salesAmount);
            costTotal = costTotal.add(costAmount);
            Long productId = row.get("productId") == null ? null : ((Number) row.get("productId")).longValue();
            lines.add(new GrossMarginLineResponse(
                    productId,
                    str(row.get("productCode")),
                    str(row.get("productName")),
                    ScalePrecision.quantity(salesQty),
                    salesAmount,
                    costAmount,
                    margin,
                    rate
            ));
        }
        salesTotal = ScalePrecision.amount(salesTotal);
        costTotal = ScalePrecision.amount(costTotal);
        BigDecimal gross = ScalePrecision.amount(salesTotal.subtract(costTotal));
        BigDecimal marginRate = salesTotal.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : gross.multiply(new BigDecimal("100")).divide(salesTotal, 2, RoundingMode.HALF_UP);
        return new GrossMarginSummaryResponse(dateFrom, dateTo, salesTotal, costTotal, gross, marginRate, lines);
    }

    private BigDecimal toBd(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }

    private String str(Object value) {
        return value == null ? null : value.toString();
    }
}

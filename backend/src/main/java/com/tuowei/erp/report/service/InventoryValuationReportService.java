package com.tuowei.erp.report.service;

import com.tuowei.erp.common.config.ReportProperties;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.report.web.InventoryValuationReportQuery;
import com.tuowei.erp.report.web.InventoryValuationReportResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

@Service
@NativeSqlTenantScoped("Inventory valuation SQL always binds the current company/account book and restricts warehouse rows from the principal data-scope snapshot.")
public class InventoryValuationReportService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CurrentUserContext currentUserContext;
    private final Clock clock;
    private final ReportProperties reportProperties;

    public InventoryValuationReportService(
            NamedParameterJdbcTemplate jdbcTemplate,
            CurrentUserContext currentUserContext,
            Clock clock,
            ReportProperties reportProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.currentUserContext = currentUserContext;
        this.clock = clock;
        this.reportProperties = reportProperties;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryValuationReportResponse> list(InventoryValuationReportQuery query) {
        InventoryValuationReportQuery safeQuery = query == null ? new InventoryValuationReportQuery() : query;
        NormalizedQuery normalized = normalize(safeQuery);
        if (!normalized.visible()) {
            return new PageResponse<>(normalized.pageNo(), normalized.pageSize(), 0, List.of());
        }
        long total = count(normalized);
        List<InventoryValuationReportResponse> rows = rows(normalized, normalized.pageSize(), (normalized.pageNo() - 1) * normalized.pageSize());
        return new PageResponse<>(normalized.pageNo(), normalized.pageSize(), total, rows);
    }

    @Transactional(readOnly = true)
    public void assertExportWithinLimit(InventoryValuationReportQuery query) {
        NormalizedQuery normalized = normalize(query);
        long total = normalized.visible() ? count(normalized) : 0;
        if (total > reportProperties.maxExportRows()) {
            throw new IllegalArgumentException("导出结果超过" + reportProperties.maxExportRows() + "行，请缩小筛选范围后重试");
        }
    }

    @Transactional(readOnly = true)
    public void stream(InventoryValuationReportQuery query, Consumer<InventoryValuationReportResponse> consumer) {
        NormalizedQuery normalized = normalize(query);
        if (!normalized.visible()) {
            return;
        }
        long offset = 0;
        int batchSize = Math.min(reportProperties.exportBatchSize(), reportProperties.maxExportRows());
        while (true) {
            List<InventoryValuationReportResponse> rows = rows(normalized, batchSize, offset);
            rows.forEach(consumer);
            if (rows.size() < batchSize) {
                return;
            }
            offset += batchSize;
        }
    }

    private long count(NormalizedQuery query) {
        Long total = jdbcTemplate.queryForObject("select count(*) from (" + aggregateSql(query) + ") valuation_count", params(query), Long.class);
        return total == null ? 0 : total;
    }

    private List<InventoryValuationReportResponse> rows(NormalizedQuery query, long limit, long offset) {
        MapSqlParameterSource params = params(query).addValue("limit", limit).addValue("offset", offset);
        return jdbcTemplate.query(aggregateSql(query) + " order by warehouse_code, product_code limit :limit offset :offset", params, (rs, rowNum) -> {
            BigDecimal closingQty = ScalePrecision.quantity(rs.getBigDecimal("closing_qty"));
            BigDecimal closingAmount = ScalePrecision.amount(rs.getBigDecimal("closing_amount"));
            BigDecimal averageUnitCost = closingQty.signum() == 0
                    ? BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
                    : closingAmount.divide(closingQty, 4, RoundingMode.HALF_UP);
            long warehouseId = rs.getLong("warehouse_id");
            long productId = rs.getLong("product_id");
            return new InventoryValuationReportResponse(
                    warehouseId + "-" + productId,
                    query.periodStart(),
                    query.asOfDate(),
                    warehouseId,
                    rs.getString("warehouse_code"),
                    rs.getString("warehouse_name"),
                    productId,
                    rs.getString("product_code"),
                    rs.getString("product_name"),
                    ScalePrecision.quantity(rs.getBigDecimal("opening_qty")),
                    ScalePrecision.amount(rs.getBigDecimal("opening_amount")),
                    ScalePrecision.quantity(rs.getBigDecimal("inbound_qty")),
                    ScalePrecision.amount(rs.getBigDecimal("inbound_amount")),
                    ScalePrecision.quantity(rs.getBigDecimal("outbound_qty")),
                    ScalePrecision.amount(rs.getBigDecimal("outbound_amount")),
                    closingQty,
                    closingAmount,
                    averageUnitCost
            );
        });
    }

    private String aggregateSql(NormalizedQuery query) {
        StringBuilder filters = new StringBuilder();
        if (query.warehouseId() != null) filters.append(" and t.warehouse_id = :warehouseId");
        if (query.productId() != null) filters.append(" and t.product_id = :productId");
        if (!query.allWarehouses()) filters.append(" and t.warehouse_id in (:warehouseIds)");
        if (StringUtils.hasText(query.keyword())) {
            filters.append(" and (p.product_code like :keywordLike or p.product_name like :keywordLike or w.warehouse_code like :keywordLike or w.warehouse_name like :keywordLike)");
        }
        return """
                select
                    t.warehouse_id,
                    w.warehouse_code,
                    w.warehouse_name,
                    t.product_id,
                    p.product_code,
                    p.product_name,
                    coalesce(sum(case when t.occurred_time < :periodStartTime then case when t.direction = 'IN' then t.qty else -t.qty end else 0 end), 0) opening_qty,
                    coalesce(sum(case when t.occurred_time < :periodStartTime then case when t.direction = 'IN' then t.amount else -t.amount end else 0 end), 0) opening_amount,
                    coalesce(sum(case when t.occurred_time >= :periodStartTime and t.direction = 'IN' then t.qty else 0 end), 0) inbound_qty,
                    coalesce(sum(case when t.occurred_time >= :periodStartTime and t.direction = 'IN' then t.amount else 0 end), 0) inbound_amount,
                    coalesce(sum(case when t.occurred_time >= :periodStartTime and t.direction = 'OUT' then t.qty else 0 end), 0) outbound_qty,
                    coalesce(sum(case when t.occurred_time >= :periodStartTime and t.direction = 'OUT' then t.amount else 0 end), 0) outbound_amount,
                    coalesce(sum(case when t.direction = 'IN' then t.qty else -t.qty end), 0) closing_qty,
                    coalesce(sum(case when t.direction = 'IN' then t.amount else -t.amount end), 0) closing_amount
                from inv_txn t
                join md_warehouse w on w.id = t.warehouse_id
                    and w.company_id = t.company_id and w.account_book_id = t.account_book_id
                join md_product p on p.id = t.product_id
                    and p.company_id = t.company_id and p.account_book_id = t.account_book_id
                where t.company_id = :companyId
                  and t.account_book_id = :accountBookId
                  and t.occurred_time < :endExclusive
                """ + filters + """
                group by t.warehouse_id, w.warehouse_code, w.warehouse_name, t.product_id, p.product_code, p.product_name
                having abs(coalesce(sum(case when t.direction = 'IN' then t.qty else -t.qty end), 0)) > 0.00005
                    or abs(coalesce(sum(case when t.direction = 'IN' then t.amount else -t.amount end), 0)) > 0.005
                    or coalesce(sum(case when t.occurred_time >= :periodStartTime then t.qty else 0 end), 0) > 0
                """;
    }

    private MapSqlParameterSource params(NormalizedQuery query) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("companyId", query.companyId())
                .addValue("accountBookId", query.accountBookId())
                .addValue("periodStartTime", query.periodStart().atStartOfDay())
                .addValue("endExclusive", query.asOfDate().plusDays(1).atStartOfDay())
                .addValue("warehouseId", query.warehouseId())
                .addValue("productId", query.productId());
        if (!query.allWarehouses()) params.addValue("warehouseIds", query.warehouseIds());
        if (StringUtils.hasText(query.keyword())) params.addValue("keywordLike", "%" + query.keyword() + "%");
        return params;
    }

    private NormalizedQuery normalize(InventoryValuationReportQuery query) {
        InventoryValuationReportQuery safe = query == null ? new InventoryValuationReportQuery() : query;
        CurrentUser user = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        LocalDate asOfDate = safe.getAsOfDate() == null ? LocalDate.now(clock) : safe.getAsOfDate();
        LocalDate periodStart = safe.getPeriodStart() == null ? asOfDate.withDayOfMonth(1) : safe.getPeriodStart();
        if (periodStart.isAfter(asOfDate)) throw new IllegalArgumentException("估值期间开始日期不能晚于截止日期");
        String keyword = StringUtils.hasText(safe.getKeyword()) ? safe.getKeyword().trim() : null;
        boolean visible = snapshot.hasAllScope() || !snapshot.warehouseIds().isEmpty();
        return new NormalizedQuery(
                Math.max(safe.getPageNo() == null ? 1 : safe.getPageNo(), 1),
                Math.min(Math.max(safe.getPageSize() == null ? 20 : safe.getPageSize(), 1), 200),
                periodStart, asOfDate, safe.getWarehouseId(), safe.getProductId(), keyword,
                user.companyId(), user.accountBookId(), snapshot.hasAllScope(), snapshot.warehouseIds(), visible
        );
    }

    private record NormalizedQuery(long pageNo, long pageSize, LocalDate periodStart, LocalDate asOfDate,
                                   Long warehouseId, Long productId, String keyword, Long companyId, Long accountBookId,
                                   boolean allWarehouses, java.util.Set<Long> warehouseIds, boolean visible) {
    }
}

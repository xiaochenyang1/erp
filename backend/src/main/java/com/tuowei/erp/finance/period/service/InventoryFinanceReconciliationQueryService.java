package com.tuowei.erp.finance.period.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceDetailResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

/** Tenant-scoped native reads for inventory and finance reconciliation. */
@Service
@NativeSqlTenantScoped("reconciliation queries use a period verified against the current company/account book")
public class InventoryFinanceReconciliationQueryService {

    private final AccountPeriodMapper accountPeriodMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final JdbcTemplate jdbcTemplate;

    public InventoryFinanceReconciliationQueryService(
            AccountPeriodMapper accountPeriodMapper,
            AuditMetadataFactory auditMetadataFactory,
            JdbcTemplate jdbcTemplate
    ) {
        this.accountPeriodMapper = accountPeriodMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public SummaryData loadSummary(Long periodId) {
        AccountPeriodEntity period = requirePeriod(periodId);
        return new SummaryData(period, inventoryNetAmount(period), financeInventoryNetAmount(period));
    }

    @Transactional(readOnly = true)
    public DifferenceData loadDifferences(Long periodId) {
        AccountPeriodEntity period = requirePeriod(periodId);
        return new DifferenceData(period, inventorySourceAmounts(period), financeSourceAmounts(period));
    }

    @Transactional(readOnly = true)
    public DifferenceDetailData loadDifferenceDetail(Long periodId, String sourceType, String sourceNo) {
        AccountPeriodEntity period = requirePeriod(periodId);
        String normalizedSourceType = requireText(sourceType, "来源类型不能为空");
        String normalizedSourceNo = requireText(sourceNo, "来源单号不能为空");
        return new DifferenceDetailData(
                period,
                normalizedSourceType,
                normalizedSourceNo,
                inventoryTransactions(period, normalizedSourceType, normalizedSourceNo),
                voucherEntries(period, normalizedSourceType, normalizedSourceNo)
        );
    }

    private BigDecimal inventoryNetAmount(AccountPeriodEntity period) {
        BigDecimal amount = jdbcTemplate.queryForObject("""
                select coalesce(sum(case when direction = 'IN' then amount else -amount end), 0)
                from inv_txn
                where company_id = ?
                  and account_book_id = ?
                  and occurred_time >= ?
                  and occurred_time < ?
                """,
                BigDecimal.class,
                period.getCompanyId(),
                period.getAccountBookId(),
                Timestamp.valueOf(period.getStartDate().atStartOfDay()),
                Timestamp.valueOf(period.getEndDate().plusDays(1).atStartOfDay()));
        return ScalePrecision.amount(amount);
    }

    private BigDecimal financeInventoryNetAmount(AccountPeriodEntity period) {
        BigDecimal amount = jdbcTemplate.queryForObject("""
                select coalesce(sum(debit_amount - credit_amount), 0)
                from fin_voucher_entry
                where company_id = ?
                  and account_book_id = ?
                  and subject_code = '1001'
                  and biz_date >= ?
                  and biz_date <= ?
                """,
                BigDecimal.class,
                period.getCompanyId(),
                period.getAccountBookId(),
                Date.valueOf(period.getStartDate()),
                Date.valueOf(period.getEndDate()));
        return ScalePrecision.amount(amount);
    }

    private List<InventorySourceAmount> inventorySourceAmounts(AccountPeriodEntity period) {
        return jdbcTemplate.query("""
                select biz_type, biz_no, coalesce(sum(case when direction = 'IN' then amount else -amount end), 0) as amount
                from inv_txn
                where company_id = ?
                  and account_book_id = ?
                  and occurred_time >= ?
                  and occurred_time < ?
                group by biz_type, biz_no
                """,
                (rs, rowNum) -> new InventorySourceAmount(
                        rs.getString("biz_type"),
                        rs.getString("biz_no"),
                        ScalePrecision.amount(rs.getBigDecimal("amount"))
                ),
                period.getCompanyId(),
                period.getAccountBookId(),
                Timestamp.valueOf(period.getStartDate().atStartOfDay()),
                Timestamp.valueOf(period.getEndDate().plusDays(1).atStartOfDay()));
    }

    private List<FinanceSourceAmount> financeSourceAmounts(AccountPeriodEntity period) {
        return jdbcTemplate.query("""
                select v.source_type, v.source_no, coalesce(sum(e.debit_amount - e.credit_amount), 0) as amount
                from fin_voucher_entry e
                join fin_voucher v on v.id = e.voucher_id
                where e.company_id = ?
                  and e.account_book_id = ?
                  and e.subject_code = '1001'
                  and e.biz_date >= ?
                  and e.biz_date <= ?
                group by v.source_type, v.source_no
                """,
                (rs, rowNum) -> new FinanceSourceAmount(
                        rs.getString("source_type"),
                        rs.getString("source_no"),
                        ScalePrecision.amount(rs.getBigDecimal("amount"))
                ),
                period.getCompanyId(),
                period.getAccountBookId(),
                Date.valueOf(period.getStartDate()),
                Date.valueOf(period.getEndDate()));
    }

    private List<InventoryFinanceDifferenceDetailResponse.InventoryTransactionResponse> inventoryTransactions(
            AccountPeriodEntity period,
            String sourceType,
            String sourceNo
    ) {
        return jdbcTemplate.query("""
                select id, biz_type, biz_no, direction, qty, amount, occurred_time, remark
                from inv_txn
                where company_id = ?
                  and account_book_id = ?
                  and occurred_time >= ?
                  and occurred_time < ?
                  and biz_type = ?
                  and biz_no = ?
                order by occurred_time, id
                """,
                (rs, rowNum) -> new InventoryFinanceDifferenceDetailResponse.InventoryTransactionResponse(
                        rs.getLong("id"),
                        rs.getString("biz_type"),
                        rs.getString("biz_no"),
                        rs.getString("direction"),
                        ScalePrecision.quantity(rs.getBigDecimal("qty")),
                        ScalePrecision.amount(rs.getBigDecimal("amount")),
                        rs.getTimestamp("occurred_time").toLocalDateTime(),
                        rs.getString("remark")
                ),
                period.getCompanyId(),
                period.getAccountBookId(),
                Timestamp.valueOf(period.getStartDate().atStartOfDay()),
                Timestamp.valueOf(period.getEndDate().plusDays(1).atStartOfDay()),
                sourceType,
                sourceNo);
    }

    private List<InventoryFinanceDifferenceDetailResponse.VoucherEntryResponse> voucherEntries(
            AccountPeriodEntity period,
            String sourceType,
            String sourceNo
    ) {
        return jdbcTemplate.query("""
                select e.voucher_id, v.voucher_no, v.source_type, v.source_no, e.biz_date, e.line_no,
                       e.subject_code, e.subject_name, e.debit_amount, e.credit_amount, e.summary
                from fin_voucher_entry e
                join fin_voucher v
                  on v.id = e.voucher_id
                 and v.company_id = e.company_id
                 and v.account_book_id = e.account_book_id
                where e.company_id = ?
                  and e.account_book_id = ?
                  and e.subject_code = '1001'
                  and e.biz_date >= ?
                  and e.biz_date <= ?
                  and v.source_type = ?
                  and v.source_no = ?
                order by e.biz_date, v.voucher_no, e.line_no, e.id
                """,
                (rs, rowNum) -> new InventoryFinanceDifferenceDetailResponse.VoucherEntryResponse(
                        rs.getLong("voucher_id"),
                        rs.getString("voucher_no"),
                        rs.getString("source_type"),
                        rs.getString("source_no"),
                        rs.getDate("biz_date").toLocalDate(),
                        rs.getInt("line_no"),
                        rs.getString("subject_code"),
                        rs.getString("subject_name"),
                        ScalePrecision.amount(rs.getBigDecimal("debit_amount")),
                        ScalePrecision.amount(rs.getBigDecimal("credit_amount")),
                        rs.getString("summary")
                ),
                period.getCompanyId(),
                period.getAccountBookId(),
                Date.valueOf(period.getStartDate()),
                Date.valueOf(period.getEndDate()),
                sourceType,
                sourceNo);
    }

    private AccountPeriodEntity requirePeriod(Long periodId) {
        AuditMetadata audit = auditMetadataFactory.current();
        AccountPeriodEntity period = accountPeriodMapper.selectById(periodId);
        if (period == null
                || !Objects.equals(period.getCompanyId(), audit.companyId())
                || !Objects.equals(period.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("会计期间不存在");
        }
        return period;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    public record SummaryData(
            AccountPeriodEntity period,
            BigDecimal inventoryAmount,
            BigDecimal financeAmount
    ) {
    }

    public record DifferenceData(
            AccountPeriodEntity period,
            List<InventorySourceAmount> inventorySources,
            List<FinanceSourceAmount> financeSources
    ) {
    }

    public record DifferenceDetailData(
            AccountPeriodEntity period,
            String sourceType,
            String sourceNo,
            List<InventoryFinanceDifferenceDetailResponse.InventoryTransactionResponse> inventoryTransactions,
            List<InventoryFinanceDifferenceDetailResponse.VoucherEntryResponse> voucherEntries
    ) {
    }

    public record InventorySourceAmount(String sourceType, String sourceNo, BigDecimal amount) {
        public String sourceKey() {
            return sourceType + ":" + sourceNo;
        }
    }

    public record FinanceSourceAmount(String sourceType, String sourceNo, BigDecimal amount) {
        public String sourceKey() {
            return sourceType + ":" + sourceNo;
        }
    }
}

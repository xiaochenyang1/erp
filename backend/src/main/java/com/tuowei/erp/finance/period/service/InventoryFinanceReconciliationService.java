package com.tuowei.erp.finance.period.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceDetailResponse;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceQuery;
import com.tuowei.erp.finance.period.web.InventoryFinanceDifferenceResponse;
import com.tuowei.erp.finance.period.web.InventoryFinanceReconciliationResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@NativeSqlTenantScoped("JdbcTemplate reconciliation queries derive company_id and account_book_id from a period that is first verified against the current audit scope.")
public class InventoryFinanceReconciliationService {

    private static final BigDecimal ZERO_AMOUNT = ScalePrecision.amount(BigDecimal.ZERO);

    private final AccountPeriodMapper accountPeriodMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final JdbcTemplate jdbcTemplate;

    public InventoryFinanceReconciliationService(
            AccountPeriodMapper accountPeriodMapper,
            AuditMetadataFactory auditMetadataFactory,
            JdbcTemplate jdbcTemplate
    ) {
        this.accountPeriodMapper = accountPeriodMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public InventoryFinanceReconciliationResponse summary(Long periodId) {
        AccountPeriodEntity period = requirePeriod(periodId);
        BigDecimal inventoryAmount = inventoryNetAmount(period);
        BigDecimal financeAmount = financeInventoryNetAmount(period);
        BigDecimal differenceAmount = ScalePrecision.amount(inventoryAmount.subtract(financeAmount));
        return new InventoryFinanceReconciliationResponse(
                period.getId(),
                period.getPeriodMonth(),
                inventoryAmount,
                financeAmount,
                differenceAmount,
                differenceAmount.compareTo(BigDecimal.ZERO) == 0
        );
    }

    @Transactional(readOnly = true)
    public List<InventoryFinanceDifferenceResponse> differences(Long periodId, InventoryFinanceDifferenceQuery query) {
        AccountPeriodEntity period = requirePeriod(periodId);
        Map<String, DifferenceAccumulator> rows = new LinkedHashMap<>();
        for (InventorySourceAmount amount : inventorySourceAmounts(period)) {
            rows.computeIfAbsent(amount.sourceKey(), ignored -> new DifferenceAccumulator(amount.sourceType(), amount.sourceNo()))
                    .addInventory(amount.amount());
        }
        for (FinanceSourceAmount amount : financeSourceAmounts(period)) {
            rows.computeIfAbsent(amount.sourceKey(), ignored -> new DifferenceAccumulator(amount.sourceType(), amount.sourceNo()))
                    .addFinance(amount.amount());
        }
        String filterType = normalizeType(query == null ? null : query.getDifferenceType());
        return rows.entrySet().stream()
                .map(entry -> toDifferenceResponse(entry.getKey(), entry.getValue()))
                .filter(response -> response.differenceAmount().compareTo(BigDecimal.ZERO) != 0)
                .filter(response -> filterType == null || filterType.equals(response.differenceType()))
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryFinanceDifferenceDetailResponse differenceDetail(Long periodId, String sourceType, String sourceNo) {
        AccountPeriodEntity period = requirePeriod(periodId);
        String normalizedSourceType = requireText(sourceType, "来源类型不能为空");
        String normalizedSourceNo = requireText(sourceNo, "来源单号不能为空");
        List<InventoryFinanceDifferenceDetailResponse.InventoryTransactionResponse> inventoryTransactions =
                inventoryTransactions(period, normalizedSourceType, normalizedSourceNo);
        List<InventoryFinanceDifferenceDetailResponse.VoucherEntryResponse> voucherEntries =
                voucherEntries(period, normalizedSourceType, normalizedSourceNo);
        BigDecimal inventoryAmount = inventoryTransactions.stream()
                .map(this::signedInventoryAmount)
                .reduce(ZERO_AMOUNT, BigDecimal::add);
        BigDecimal financeAmount = voucherEntries.stream()
                .map(entry -> ScalePrecision.zeroDefault(entry.debitAmount()).subtract(ScalePrecision.zeroDefault(entry.creditAmount())))
                .reduce(ZERO_AMOUNT, BigDecimal::add);
        inventoryAmount = ScalePrecision.amount(inventoryAmount);
        financeAmount = ScalePrecision.amount(financeAmount);
        BigDecimal differenceAmount = ScalePrecision.amount(inventoryAmount.subtract(financeAmount));
        return new InventoryFinanceDifferenceDetailResponse(
                period.getId(),
                period.getPeriodMonth(),
                sourceKey(normalizedSourceType, normalizedSourceNo),
                normalizedSourceType,
                normalizedSourceNo,
                inventoryAmount,
                financeAmount,
                differenceAmount,
                resolveDifferenceType(inventoryAmount, financeAmount),
                inventoryTransactions,
                voucherEntries
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

    private BigDecimal signedInventoryAmount(InventoryFinanceDifferenceDetailResponse.InventoryTransactionResponse transaction) {
        BigDecimal amount = ScalePrecision.zeroDefault(transaction.amount());
        return "IN".equals(transaction.direction()) ? amount : amount.negate();
    }

    private InventoryFinanceDifferenceResponse toDifferenceResponse(String sourceKey, DifferenceAccumulator accumulator) {
        BigDecimal inventoryAmount = ScalePrecision.amount(accumulator.inventoryAmount());
        BigDecimal financeAmount = ScalePrecision.amount(accumulator.financeAmount());
        BigDecimal differenceAmount = ScalePrecision.amount(inventoryAmount.subtract(financeAmount));
        return new InventoryFinanceDifferenceResponse(
                sourceKey,
                accumulator.sourceType(),
                accumulator.sourceNo(),
                inventoryAmount,
                financeAmount,
                differenceAmount,
                resolveDifferenceType(inventoryAmount, financeAmount)
        );
    }

    private String resolveDifferenceType(BigDecimal inventoryAmount, BigDecimal financeAmount) {
        if (inventoryAmount.compareTo(BigDecimal.ZERO) == 0) {
            return "FINANCE_ONLY";
        }
        if (financeAmount.compareTo(BigDecimal.ZERO) == 0) {
            return "INVENTORY_ONLY";
        }
        return "AMOUNT_MISMATCH";
    }

    private String normalizeType(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String sourceKey(String sourceType, String sourceNo) {
        return sourceType + ":" + sourceNo;
    }

    private record InventorySourceAmount(String sourceType, String sourceNo, BigDecimal amount) {
        private String sourceKey() {
            return InventoryFinanceReconciliationService.sourceKey(sourceType, sourceNo);
        }
    }

    private record FinanceSourceAmount(String sourceType, String sourceNo, BigDecimal amount) {
        private String sourceKey() {
            return InventoryFinanceReconciliationService.sourceKey(sourceType, sourceNo);
        }
    }

    private static final class DifferenceAccumulator {
        private final String sourceType;
        private final String sourceNo;
        private BigDecimal inventoryAmount = ZERO_AMOUNT;
        private BigDecimal financeAmount = ZERO_AMOUNT;

        private DifferenceAccumulator(String sourceType, String sourceNo) {
            this.sourceType = sourceType;
            this.sourceNo = sourceNo;
        }

        private void addInventory(BigDecimal amount) {
            inventoryAmount = ScalePrecision.amount(inventoryAmount.add(ScalePrecision.zeroDefault(amount)));
        }

        private void addFinance(BigDecimal amount) {
            financeAmount = ScalePrecision.amount(financeAmount.add(ScalePrecision.zeroDefault(amount)));
        }

        private String sourceType() {
            return sourceType;
        }

        private String sourceNo() {
            return sourceNo;
        }

        private BigDecimal inventoryAmount() {
            return inventoryAmount;
        }

        private BigDecimal financeAmount() {
            return financeAmount;
        }
    }
}

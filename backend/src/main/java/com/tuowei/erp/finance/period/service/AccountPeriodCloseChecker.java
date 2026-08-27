package com.tuowei.erp.finance.period.service;

import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.persistence.NativeSqlTenantScoped;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import com.tuowei.erp.finance.period.web.AccountPeriodCloseCheckItemResponse;
import com.tuowei.erp.finance.period.web.AccountPeriodCloseCheckResponse;
import com.tuowei.erp.finance.period.web.AccountPeriodCloseIssueResponse;
import com.tuowei.erp.finance.period.web.InventoryFinanceReconciliationResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@NativeSqlTenantScoped("JdbcTemplate close checks use the selected account period as the scope source and pass its company_id/account_book_id into every tenant-owned balance, voucher, and settlement query.")
public class AccountPeriodCloseChecker {

    private final AccountPeriodMapper accountPeriodMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final InventoryFinanceReconciliationService reconciliationService;
    private final JdbcTemplate jdbcTemplate;

    @org.springframework.beans.factory.annotation.Autowired
    public AccountPeriodCloseChecker(
            AccountPeriodMapper accountPeriodMapper,
            AuditMetadataFactory auditMetadataFactory,
            InventoryFinanceReconciliationService reconciliationService,
            JdbcTemplate jdbcTemplate
    ) {
        this.accountPeriodMapper = accountPeriodMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.reconciliationService = reconciliationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Keeps direct construction in legacy tests compatible; Spring uses the tenant-aware constructor above. */
    public AccountPeriodCloseChecker(
            AccountPeriodMapper accountPeriodMapper,
            InventoryFinanceReconciliationService reconciliationService,
            JdbcTemplate jdbcTemplate
    ) {
        this(accountPeriodMapper, null, reconciliationService, jdbcTemplate);
    }

    @Transactional(readOnly = true)
    public AccountPeriodCloseCheckResponse check(Long periodId) {
        AuditMetadata audit = auditMetadataFactory == null ? null : auditMetadataFactory.current();
        AccountPeriodEntity period = accountPeriodMapper.selectById(periodId);
        if (period == null
                || (audit != null && (!Objects.equals(period.getCompanyId(), audit.companyId())
                || !Objects.equals(period.getAccountBookId(), audit.accountBookId())))) {
            throw new IllegalArgumentException("会计期间不存在");
        }
        List<AccountPeriodCloseIssueResponse> issues = new ArrayList<>();
        List<AccountPeriodCloseCheckItemResponse> checks = new ArrayList<>();

        addOpenDocumentsCheck(period, issues, checks);
        addInventoryFinanceCheck(period, issues, checks);
        addVoucherChecks(period, issues, checks);
        addSettlementChecks(period, issues, checks);
        addBankStatementCheck(period, issues, checks);
        addInventoryBalanceCheck(period, issues, checks);

        return new AccountPeriodCloseCheckResponse(
                period.getId(),
                period.getPeriodMonth(),
                issues.isEmpty(),
                issues,
                checks
        );
    }

    private void addOpenDocumentsCheck(
            AccountPeriodEntity period,
            List<AccountPeriodCloseIssueResponse> issues,
            List<AccountPeriodCloseCheckItemResponse> checks
    ) {
        long salesOrders = count("""
                select count(*) from sal_order
                where company_id = ? and account_book_id = ? and deleted_flag = 0
                  and status in ('DRAFT', 'SUBMITTED', 'REJECTED')
                  and order_date >= ? and order_date <= ?
                """, period);
        long purchaseOrders = count("""
                select count(*) from pur_order
                where company_id = ? and account_book_id = ? and deleted_flag = 0
                  and status in ('DRAFT', 'SUBMITTED', 'REJECTED')
                  and order_date >= ? and order_date <= ?
                """, period);
        long deliveries = count("""
                select count(*) from sal_delivery
                where company_id = ? and account_book_id = ? and deleted_flag = 0
                  and status = 'DRAFT'
                  and delivery_date >= ? and delivery_date <= ?
                """, period);
        long receipts = count("""
                select count(*) from pur_receipt
                where company_id = ? and account_book_id = ? and deleted_flag = 0
                  and status = 'DRAFT'
                  and receipt_date >= ? and receipt_date <= ?
                """, period);
        long expenses = count("""
                select count(*) from fin_expense
                where company_id = ? and account_book_id = ? and deleted_flag = 0
                  and status in ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')
                  and expense_date >= ? and expense_date <= ?
                """, period);
        long manualVouchers = count("""
                select count(*) from fin_manual_voucher
                where company_id = ? and account_book_id = ? and deleted_flag = 0
                  and status in ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED')
                  and biz_date >= ? and biz_date <= ?
                """, period);

        long total = salesOrders + purchaseOrders + deliveries + receipts + expenses + manualVouchers;
        boolean passed = total == 0;
        String message = passed
                ? "期间内无未完结业务单据"
                : String.format(
                "未完结：销售订单 %d、采购订单 %d、发货草稿 %d、收货草稿 %d、费用单 %d、手工凭证 %d",
                salesOrders, purchaseOrders, deliveries, receipts, expenses, manualVouchers
        );
        checks.add(new AccountPeriodCloseCheckItemResponse(
                "OPEN_DOCUMENTS",
                "未完结业务单据",
                "业务",
                passed,
                message,
                BigDecimal.valueOf(total)
        ));
        if (!passed) {
            issues.add(new AccountPeriodCloseIssueResponse("OPEN_DOCUMENTS", message, BigDecimal.valueOf(total)));
        }
    }

    private void addInventoryFinanceCheck(
            AccountPeriodEntity period,
            List<AccountPeriodCloseIssueResponse> issues,
            List<AccountPeriodCloseCheckItemResponse> checks
    ) {
        InventoryFinanceReconciliationResponse response = reconciliationService.summary(period.getId());
        boolean passed = response.balanced();
        String message = passed
                ? "库存流水与库存科目金额一致"
                : "库存流水与库存科目金额不一致，差异 " + response.differenceAmount().toPlainString();
        checks.add(new AccountPeriodCloseCheckItemResponse(
                "INVENTORY_FINANCE_RECONCILIATION",
                "库存财务对账",
                "对账",
                passed,
                message,
                response.differenceAmount()
        ));
        if (!passed) {
            issues.add(new AccountPeriodCloseIssueResponse(
                    "INVENTORY_FINANCE_RECONCILIATION",
                    "库存流水与库存科目金额不一致",
                    response.differenceAmount()
            ));
        }
    }

    private void addVoucherChecks(
            AccountPeriodEntity period,
            List<AccountPeriodCloseIssueResponse> issues,
            List<AccountPeriodCloseCheckItemResponse> checks
    ) {
        Long missingEntryCount = jdbcTemplate.queryForObject("""
                select count(*)
                from fin_voucher v
                where v.company_id = ?
                  and v.account_book_id = ?
                  and v.status = 'POSTED'
                  and v.deleted_flag = 0
                  and v.biz_date >= ?
                  and v.biz_date <= ?
                  and not exists (
                      select 1 from fin_voucher_entry e
                      where e.company_id = v.company_id
                        and e.account_book_id = v.account_book_id
                        and e.voucher_id = v.id
                  )
                """, Long.class, period.getCompanyId(), period.getAccountBookId(), period.getStartDate(), period.getEndDate());
        long missing = missingEntryCount == null ? 0L : missingEntryCount;
        if (missing > 0) {
            issues.add(new AccountPeriodCloseIssueResponse("VOUCHER_ENTRY_MISSING", "存在没有分录的已过账凭证", BigDecimal.valueOf(missing)));
        }

        Long unbalancedCount = jdbcTemplate.queryForObject("""
                select count(*)
                from (
                    select v.id, coalesce(sum(e.debit_amount), 0) as debit_total, coalesce(sum(e.credit_amount), 0) as credit_total
                    from fin_voucher v
                    join fin_voucher_entry e on e.voucher_id = v.id
                    where v.company_id = ?
                      and v.account_book_id = ?
                      and v.status = 'POSTED'
                      and v.deleted_flag = 0
                      and v.biz_date >= ?
                      and v.biz_date <= ?
                    group by v.id
                    having coalesce(sum(e.debit_amount), 0) <> coalesce(sum(e.credit_amount), 0)
                ) t
                """, Long.class, period.getCompanyId(), period.getAccountBookId(), period.getStartDate(), period.getEndDate());
        long unbalanced = unbalancedCount == null ? 0L : unbalancedCount;
        if (unbalanced > 0) {
            issues.add(new AccountPeriodCloseIssueResponse("VOUCHER_UNBALANCED", "存在借贷不平的已过账凭证", BigDecimal.valueOf(unbalanced)));
        }

        boolean passed = missing == 0 && unbalanced == 0;
        String message = passed
                ? "已过账凭证分录完整且借贷平衡"
                : String.format("缺分录凭证 %d 张，借贷不平凭证 %d 张", missing, unbalanced);
        checks.add(new AccountPeriodCloseCheckItemResponse(
                "VOUCHER_BALANCE",
                "凭证完整性",
                "财务",
                passed,
                message,
                BigDecimal.valueOf(missing + unbalanced)
        ));
    }

    private void addSettlementChecks(
            AccountPeriodEntity period,
            List<AccountPeriodCloseIssueResponse> issues,
            List<AccountPeriodCloseCheckItemResponse> checks
    ) {
        Long paymentMismatchCount = jdbcTemplate.queryForObject("""
                select count(*)
                from fin_payment p
                where p.company_id = ?
                  and p.account_book_id = ?
                  and p.status <> 'CANCELLED'
                  and coalesce(p.allocated_amount, 0) <> (
                      select coalesce(sum(a.amount), 0)
                      from fin_payment_allocation a
                      where a.company_id = p.company_id
                        and a.account_book_id = p.account_book_id
                        and a.payment_id = p.id
                  )
                """, Long.class, period.getCompanyId(), period.getAccountBookId());
        long paymentMismatch = paymentMismatchCount == null ? 0L : paymentMismatchCount;
        if (paymentMismatch > 0) {
            issues.add(new AccountPeriodCloseIssueResponse("PAYMENT_ALLOCATION_MISMATCH", "付款单核销金额与明细不一致", BigDecimal.valueOf(paymentMismatch)));
        }

        Long receiptMismatchCount = jdbcTemplate.queryForObject("""
                select count(*)
                from fin_receipt r
                where r.company_id = ?
                  and r.account_book_id = ?
                  and r.status <> 'CANCELLED'
                  and coalesce(r.allocated_amount, 0) <> (
                      select coalesce(sum(a.amount), 0)
                      from fin_receipt_allocation a
                      where a.company_id = r.company_id
                        and a.account_book_id = r.account_book_id
                        and a.receipt_id = r.id
                  )
                """, Long.class, period.getCompanyId(), period.getAccountBookId());
        long receiptMismatch = receiptMismatchCount == null ? 0L : receiptMismatchCount;
        if (receiptMismatch > 0) {
            issues.add(new AccountPeriodCloseIssueResponse("RECEIPT_ALLOCATION_MISMATCH", "收款单核销金额与明细不一致", BigDecimal.valueOf(receiptMismatch)));
        }

        Long invalidCount = jdbcTemplate.queryForObject("""
                select (
                    select count(*) from fin_payable
                    where company_id = ?
                      and account_book_id = ?
                      and (settled_amount < 0 or settled_amount > original_amount)
                ) + (
                    select count(*) from fin_receivable
                    where company_id = ?
                      and account_book_id = ?
                      and (settled_amount < 0 or settled_amount > original_amount)
                )
                """, Long.class, period.getCompanyId(), period.getAccountBookId(), period.getCompanyId(), period.getAccountBookId());
        long invalid = invalidCount == null ? 0L : invalidCount;
        if (invalid > 0) {
            issues.add(new AccountPeriodCloseIssueResponse("SETTLEMENT_AMOUNT_INVALID", "应收应付已核销金额不合法", BigDecimal.valueOf(invalid)));
        }

        long total = paymentMismatch + receiptMismatch + invalid;
        boolean passed = total == 0;
        checks.add(new AccountPeriodCloseCheckItemResponse(
                "SETTLEMENT_CONSISTENCY",
                "收付核销一致性",
                "财务",
                passed,
                passed ? "收付核销金额与明细一致" : "收付/应收应付核销存在不一致",
                BigDecimal.valueOf(total)
        ));
    }

    private void addBankStatementCheck(
            AccountPeriodEntity period,
            List<AccountPeriodCloseIssueResponse> issues,
            List<AccountPeriodCloseCheckItemResponse> checks
    ) {
        Long unmatchedCount = jdbcTemplate.queryForObject("""
                select count(*)
                from fin_bank_statement
                where company_id = ?
                  and account_book_id = ?
                  and status = 'UNMATCHED'
                  and deleted_flag = 0
                  and transaction_date >= ?
                  and transaction_date <= ?
                """, Long.class, period.getCompanyId(), period.getAccountBookId(), period.getStartDate(), period.getEndDate());
        long unmatched = unmatchedCount == null ? 0L : unmatchedCount;
        boolean passed = unmatched == 0;
        String message = passed ? "银行流水均已匹配" : "存在未匹配银行流水 " + unmatched + " 笔";
        checks.add(new AccountPeriodCloseCheckItemResponse(
                "BANK_STATEMENT_UNMATCHED",
                "银行流水匹配",
                "资金",
                passed,
                message,
                BigDecimal.valueOf(unmatched)
        ));
        if (!passed) {
            issues.add(new AccountPeriodCloseIssueResponse("BANK_STATEMENT_UNMATCHED", "存在未匹配银行流水", BigDecimal.valueOf(unmatched)));
        }
    }

    private void addInventoryBalanceCheck(
            AccountPeriodEntity period,
            List<AccountPeriodCloseIssueResponse> issues,
            List<AccountPeriodCloseCheckItemResponse> checks
    ) {
        Long negativeCount = jdbcTemplate.queryForObject("""
                select count(*)
                from inv_balance
                where company_id = ?
                  and account_book_id = ?
                  and (qty_on_hand < 0 or amount_on_hand < 0)
                """, Long.class, period.getCompanyId(), period.getAccountBookId());
        long negative = negativeCount == null ? 0L : negativeCount;
        boolean passed = negative == 0;
        String message = passed ? "无负库存数量/金额" : "存在负库存数量或金额 " + negative + " 条";
        checks.add(new AccountPeriodCloseCheckItemResponse(
                "INVENTORY_BALANCE_NEGATIVE",
                "库存余额健康",
                "库存",
                passed,
                message,
                ScalePrecision.amount(BigDecimal.valueOf(negative))
        ));
        if (!passed) {
            issues.add(new AccountPeriodCloseIssueResponse(
                    "INVENTORY_BALANCE_NEGATIVE",
                    "存在负库存数量或金额",
                    ScalePrecision.amount(BigDecimal.valueOf(negative))
            ));
        }
    }

    private long count(String sql, AccountPeriodEntity period) {
        Long value = jdbcTemplate.queryForObject(
                sql,
                Long.class,
                period.getCompanyId(),
                period.getAccountBookId(),
                period.getStartDate(),
                period.getEndDate()
        );
        return value == null ? 0L : value;
    }
}

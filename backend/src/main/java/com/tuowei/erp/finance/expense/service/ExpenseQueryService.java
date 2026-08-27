package com.tuowei.erp.finance.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageQueryNormalizer;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.expense.mapper.ExpenseMapper;
import com.tuowei.erp.finance.expense.model.ExpenseEntity;
import com.tuowei.erp.finance.expense.web.ExpensePageQuery;
import com.tuowei.erp.finance.expense.web.ExpenseReconciliationResponse;
import com.tuowei.erp.finance.expense.web.ExpenseResponse;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import com.tuowei.erp.finance.voucher.service.VoucherQueryService;
import com.tuowei.erp.finance.voucher.web.VoucherEntryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Read-side filtering, tenant guards, voucher hydration and reconciliation mapping for expenses. */
@Service
public class ExpenseQueryService {

    private final ExpenseMapper expenseMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final VoucherQueryService voucherQueryService;

    public ExpenseQueryService(
            ExpenseMapper expenseMapper,
            VoucherMapper voucherMapper,
            VoucherEntryMapper voucherEntryMapper,
            AuditMetadataFactory auditMetadataFactory,
            VoucherQueryService voucherQueryService
    ) {
        this.expenseMapper = expenseMapper;
        this.voucherMapper = voucherMapper;
        this.voucherEntryMapper = voucherEntryMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.voucherQueryService = voucherQueryService;
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> list(ExpensePageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExpensePageQuery safeQuery = query == null ? new ExpensePageQuery() : query;
        Page<ExpenseEntity> page = new Page<>(
                PageQueryNormalizer.normalizePageNo(safeQuery.getPageNo()),
                PageQueryNormalizer.normalizePageSize(safeQuery.getPageSize())
        );
        LambdaQueryWrapper<ExpenseEntity> wrapper = new LambdaQueryWrapper<ExpenseEntity>()
                .eq(ExpenseEntity::getCompanyId, audit.companyId())
                .eq(ExpenseEntity::getAccountBookId, audit.accountBookId())
                .eq(ExpenseEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(safeQuery.getStatus())) {
            wrapper.eq(ExpenseEntity::getStatus, safeQuery.getStatus().trim().toUpperCase(Locale.ROOT));
        }
        if (safeQuery.getDateFrom() != null) {
            wrapper.ge(ExpenseEntity::getExpenseDate, safeQuery.getDateFrom());
        }
        if (safeQuery.getDateTo() != null) {
            wrapper.le(ExpenseEntity::getExpenseDate, safeQuery.getDateTo());
        }
        wrapper.orderByDesc(ExpenseEntity::getExpenseDate).orderByDesc(ExpenseEntity::getId);

        Page<ExpenseEntity> result = expenseMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public ExpenseResponse detail(Long id) {
        return toResponse(requireExpense(id));
    }

    @Transactional(readOnly = true)
    public ExpenseReconciliationResponse reconciliation(Long id) {
        ExpenseEntity expense = requireExpense(id);
        VoucherEntity voucher = findExpenseVoucher(expense);
        List<VoucherEntryEntity> entries = voucher == null
                ? List.of()
                : voucherEntries(voucher.getId(), expense.getCompanyId(), expense.getAccountBookId());
        VoucherEntity reversalVoucher = findReversalVoucher(expense);
        List<VoucherEntryEntity> reversalEntries = reversalVoucher == null
                ? List.of()
                : voucherEntries(reversalVoucher.getId(), expense.getCompanyId(), expense.getAccountBookId());
        BigDecimal debitTotal = sumDebit(entries);
        BigDecimal creditTotal = sumCredit(entries);
        BigDecimal reversalDebitTotal = sumDebit(reversalEntries);
        BigDecimal reversalCreditTotal = sumCredit(reversalEntries);
        boolean entriesMissing = entries.isEmpty();
        boolean voucherBalanced = !entriesMissing && amountsEqual(debitTotal, creditTotal);
        boolean amountMatched = voucher != null && amountsEqual(expense.getAmount(), voucher.getAmount())
                && amountsEqual(expense.getAmount(), debitTotal)
                && amountsEqual(expense.getAmount(), creditTotal);
        boolean voucherLinkedToExpense = voucher != null
                && Objects.equals(voucher.getSourceType(), "EXPENSE")
                && Objects.equals(voucher.getSourceId(), expense.getId())
                && Objects.equals(expense.getVoucherId(), voucher.getId());
        List<VoucherEntryResponse> entryResponses = entries.stream()
                .map(voucherQueryService::toEntryResponse)
                .toList();
        List<VoucherEntryResponse> reversalEntryResponses = reversalEntries.stream()
                .map(voucherQueryService::toEntryResponse)
                .toList();
        return new ExpenseReconciliationResponse(
                toResponse(expense, voucher, entries, reversalVoucher, reversalEntries),
                voucher == null ? null : voucherQueryService.toResponse(voucher),
                entryResponses,
                reversalVoucher == null ? null : voucherQueryService.toResponse(reversalVoucher),
                reversalEntryResponses,
                debitTotal,
                creditTotal,
                reversalDebitTotal,
                reversalCreditTotal,
                voucher == null,
                entriesMissing,
                voucherBalanced,
                amountMatched,
                voucherLinkedToExpense,
                !reversalEntries.isEmpty() && amountsEqual(reversalDebitTotal, reversalCreditTotal),
                reversalVoucher != null
                        && amountsEqual(expense.getAmount(), reversalVoucher.getAmount())
                        && amountsEqual(expense.getAmount(), reversalDebitTotal)
                        && amountsEqual(expense.getAmount(), reversalCreditTotal),
                reversalVoucher != null
        );
    }

    @Transactional(readOnly = true)
    public ExpenseEntity requireExpense(Long id) {
        ExpenseEntity expense = expenseMapper.selectById(id);
        AuditMetadata audit = auditMetadataFactory.current();
        if (expense == null
                || expense.getDeletedFlag() == null
                || expense.getDeletedFlag() != 0
                || !Objects.equals(expense.getCompanyId(), audit.companyId())
                || !Objects.equals(expense.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("费用单不存在");
        }
        return expense;
    }

    @Transactional(readOnly = true)
    public ExpenseResponse toResponse(ExpenseEntity expense) {
        VoucherEntity voucher = findExpenseVoucher(expense);
        List<VoucherEntryEntity> entries = voucher == null
                ? List.of()
                : voucherEntries(voucher.getId(), expense.getCompanyId(), expense.getAccountBookId());
        VoucherEntity reversalVoucher = findReversalVoucher(expense);
        List<VoucherEntryEntity> reversalEntries = reversalVoucher == null
                ? List.of()
                : voucherEntries(reversalVoucher.getId(), expense.getCompanyId(), expense.getAccountBookId());
        return toResponse(expense, voucher, entries, reversalVoucher, reversalEntries);
    }

    @Transactional(readOnly = true)
    public VoucherEntity findExpenseVoucher(ExpenseEntity expense) {
        if (expense.getVoucherId() != null) {
            VoucherEntity linkedVoucher = voucherMapper.selectById(expense.getVoucherId());
            if (linkedVoucher != null
                    && Objects.equals(linkedVoucher.getCompanyId(), expense.getCompanyId())
                    && Objects.equals(linkedVoucher.getAccountBookId(), expense.getAccountBookId())
                    && linkedVoucher.getDeletedFlag() != null
                    && linkedVoucher.getDeletedFlag() == 0) {
                return linkedVoucher;
            }
        }
        return voucherMapper.selectOne(new LambdaQueryWrapper<VoucherEntity>()
                .eq(VoucherEntity::getCompanyId, expense.getCompanyId())
                .eq(VoucherEntity::getAccountBookId, expense.getAccountBookId())
                .eq(VoucherEntity::getSourceType, "EXPENSE")
                .eq(VoucherEntity::getSourceId, expense.getId())
                .eq(VoucherEntity::getDeletedFlag, 0));
    }

    @Transactional(readOnly = true)
    public VoucherEntity findReversalVoucher(ExpenseEntity expense) {
        return voucherMapper.selectOne(new LambdaQueryWrapper<VoucherEntity>()
                .eq(VoucherEntity::getCompanyId, expense.getCompanyId())
                .eq(VoucherEntity::getAccountBookId, expense.getAccountBookId())
                .eq(VoucherEntity::getSourceType, "EXPENSE_REVERSAL")
                .eq(VoucherEntity::getSourceId, expense.getId())
                .eq(VoucherEntity::getDeletedFlag, 0));
    }

    @Transactional(readOnly = true)
    public List<VoucherEntryEntity> voucherEntries(Long voucherId, Long companyId, Long accountBookId) {
        return voucherEntryMapper.selectList(new LambdaQueryWrapper<VoucherEntryEntity>()
                .eq(VoucherEntryEntity::getCompanyId, companyId)
                .eq(VoucherEntryEntity::getAccountBookId, accountBookId)
                .eq(VoucherEntryEntity::getVoucherId, voucherId)
                .orderByAsc(VoucherEntryEntity::getLineNo)
                .orderByAsc(VoucherEntryEntity::getId));
    }

    private ExpenseResponse toResponse(
            ExpenseEntity expense,
            VoucherEntity voucher,
            List<VoucherEntryEntity> entries,
            VoucherEntity reversalVoucher,
            List<VoucherEntryEntity> reversalEntries
    ) {
        BigDecimal debitTotal = sumDebit(entries);
        BigDecimal creditTotal = sumCredit(entries);
        BigDecimal reversalDebitTotal = sumDebit(reversalEntries);
        BigDecimal reversalCreditTotal = sumCredit(reversalEntries);
        boolean hasEntries = !entries.isEmpty();
        boolean hasReversalEntries = !reversalEntries.isEmpty();
        return new ExpenseResponse(
                expense.getId(),
                expense.getExpenseNo(),
                expense.getExpenseDate(),
                expense.getSubjectId(),
                expense.getPaymentSubjectId(),
                expense.getAmount(),
                expense.getStatus(),
                voucher == null ? null : voucher.getId(),
                voucher == null ? null : voucher.getVoucherNo(),
                voucher == null ? null : voucher.getStatus(),
                voucher == null ? null : voucher.getAmount(),
                (long) entries.size(),
                hasEntries && amountsEqual(debitTotal, creditTotal),
                voucher != null && hasEntries
                        && amountsEqual(expense.getAmount(), voucher.getAmount())
                        && amountsEqual(expense.getAmount(), debitTotal)
                        && amountsEqual(expense.getAmount(), creditTotal),
                reversalVoucher == null ? null : reversalVoucher.getId(),
                reversalVoucher == null ? null : reversalVoucher.getVoucherNo(),
                reversalVoucher == null ? null : reversalVoucher.getStatus(),
                reversalVoucher == null ? null : reversalVoucher.getAmount(),
                (long) reversalEntries.size(),
                hasReversalEntries && amountsEqual(reversalDebitTotal, reversalCreditTotal),
                reversalVoucher != null && hasReversalEntries
                        && amountsEqual(expense.getAmount(), reversalVoucher.getAmount())
                        && amountsEqual(expense.getAmount(), reversalDebitTotal)
                        && amountsEqual(expense.getAmount(), reversalCreditTotal),
                reversalVoucher != null,
                expense.getRemark(),
                expense.getDeptId(),
                expense.getBudgetLineId(),
                expense.getBudgetState(),
                expense.getBudgetOverrunFlag()
        );
    }

    private BigDecimal sumDebit(List<VoucherEntryEntity> entries) {
        return ScalePrecision.amount(entries.stream()
                .map(VoucherEntryEntity::getDebitAmount)
                .map(ScalePrecision::zeroDefault)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal sumCredit(List<VoucherEntryEntity> entries) {
        return ScalePrecision.amount(entries.stream()
                .map(VoucherEntryEntity::getCreditAmount)
                .map(ScalePrecision::zeroDefault)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private boolean amountsEqual(BigDecimal left, BigDecimal right) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(left))
                .compareTo(ScalePrecision.amount(ScalePrecision.zeroDefault(right))) == 0;
    }
}

package com.tuowei.erp.finance.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.expense.mapper.ExpenseMapper;
import com.tuowei.erp.finance.expense.model.ExpenseEntity;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.subject.service.AccountSubjectService;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Owns the write-side accounting effects of an expense document.
 *
 * The facade keeps the public API and response mapping while this service
 * keeps posting and reversal transactions together and idempotent.
 */
@Service
public class ExpensePostingService {

    private static final String SOURCE_TYPE = "EXPENSE";
    private static final String REVERSAL_SOURCE_TYPE = "EXPENSE_REVERSAL";

    private final ExpenseMapper expenseMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final AccountSubjectService accountSubjectService;
    private final AccountPeriodGuard accountPeriodGuard;
    private final AttachmentService attachmentService;
    private final AuditMetadataFactory auditMetadataFactory;

    public ExpensePostingService(
            ExpenseMapper expenseMapper,
            VoucherMapper voucherMapper,
            VoucherEntryMapper voucherEntryMapper,
            AccountSubjectService accountSubjectService,
            AccountPeriodGuard accountPeriodGuard,
            AttachmentService attachmentService,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.expenseMapper = expenseMapper;
        this.voucherMapper = voucherMapper;
        this.voucherEntryMapper = voucherEntryMapper;
        this.accountSubjectService = accountSubjectService;
        this.accountPeriodGuard = accountPeriodGuard;
        this.attachmentService = attachmentService;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional
    public void post(Long id) {
        ExpenseEntity expense = requireExpense(id);
        if ("POSTED".equals(expense.getStatus())) {
            return;
        }
        if (!"APPROVED".equals(expense.getStatus())) {
            throw new IllegalArgumentException("只有审批通过的费用单可以过账");
        }
        attachmentService.requireIfConfigured(SOURCE_TYPE, expense.getId());
        accountPeriodGuard.requireOpen(expense.getExpenseDate(), "费用单过账");

        AuditMetadata audit = auditMetadataFactory.current();
        AccountSubjectEntity expenseSubject = accountSubjectService.requireActiveSubject(
                expense.getSubjectId(), "费用科目不存在或已停用");
        AccountSubjectEntity paymentSubject = accountSubjectService.requireActiveSubject(
                expense.getPaymentSubjectId(), "支付科目不存在或已停用");
        requireSubjectsInTenant(expenseSubject, paymentSubject, audit);

        VoucherEntity voucher = insertVoucherIfAbsent(expense, audit);
        insertEntriesIfAbsent(voucher, expense, expenseSubject, paymentSubject, audit);

        expense.setStatus("POSTED");
        expense.setVoucherId(voucher.getId());
        expense.setUpdatedBy(audit.userId());
        expense.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(expenseMapper.updateById(expense),
                "费用单已被其他操作修改，请刷新后重试");
    }

    @Transactional
    public void reverse(Long id) {
        ExpenseEntity expense = requireExpense(id);
        if (!"POSTED".equals(expense.getStatus())) {
            throw new IllegalArgumentException("只有已过账费用单可以红冲");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        accountPeriodGuard.requireOpen(audit.now().toLocalDate(), "费用单红冲");
        VoucherEntity voucher = findExpenseVoucher(expense);
        if (voucher == null) {
            throw new IllegalStateException("费用单缺少原始凭证，无法红冲");
        }
        List<VoucherEntryEntity> entries = voucherEntries(voucher.getId(), expense.getCompanyId(), expense.getAccountBookId());
        if (entries.isEmpty()) {
            throw new IllegalStateException("费用单原始凭证缺少分录，无法红冲");
        }
        VoucherEntity reversalVoucher = insertReversalVoucherIfAbsent(expense, voucher, audit);
        insertReversalEntriesIfAbsent(reversalVoucher, entries, audit);
    }

    private VoucherEntity insertVoucherIfAbsent(ExpenseEntity expense, AuditMetadata audit) {
        VoucherEntity existing = voucherMapper.selectOne(new LambdaQueryWrapper<VoucherEntity>()
                .eq(VoucherEntity::getCompanyId, audit.companyId())
                .eq(VoucherEntity::getAccountBookId, audit.accountBookId())
                .eq(VoucherEntity::getSourceType, SOURCE_TYPE)
                .eq(VoucherEntity::getSourceId, expense.getId()));
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = audit.now();
        VoucherEntity voucher = new VoucherEntity();
        voucher.setCompanyId(audit.companyId());
        voucher.setAccountBookId(audit.accountBookId());
        voucher.setVoucherNo("VO-EXPENSE-" + expense.getId());
        voucher.setSourceType(SOURCE_TYPE);
        voucher.setSourceId(expense.getId());
        voucher.setSourceNo(expense.getExpenseNo());
        voucher.setBizDate(expense.getExpenseDate());
        voucher.setAmount(expense.getAmount());
        voucher.setStatus("POSTED");
        voucher.setDeletedFlag(0);
        voucher.setRemark("费用登记凭证");
        setAudit(voucher, audit, now);
        voucherMapper.insert(voucher);
        return voucher;
    }

    private VoucherEntity insertReversalVoucherIfAbsent(
            ExpenseEntity expense,
            VoucherEntity originalVoucher,
            AuditMetadata audit
    ) {
        VoucherEntity existing = findReversalVoucher(expense);
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = audit.now();
        VoucherEntity voucher = new VoucherEntity();
        voucher.setCompanyId(audit.companyId());
        voucher.setAccountBookId(audit.accountBookId());
        voucher.setVoucherNo("VO-EXPENSE-REV-" + expense.getId());
        voucher.setSourceType(REVERSAL_SOURCE_TYPE);
        voucher.setSourceId(expense.getId());
        voucher.setSourceNo(expense.getExpenseNo());
        voucher.setBizDate(audit.now().toLocalDate());
        voucher.setAmount(originalVoucher.getAmount());
        voucher.setStatus("POSTED");
        voucher.setDeletedFlag(0);
        voucher.setRemark("费用登记红冲凭证: " + originalVoucher.getVoucherNo());
        setAudit(voucher, audit, now);
        voucherMapper.insert(voucher);
        return voucher;
    }

    private void insertEntriesIfAbsent(
            VoucherEntity voucher,
            ExpenseEntity expense,
            AccountSubjectEntity expenseSubject,
            AccountSubjectEntity paymentSubject,
            AuditMetadata audit
    ) {
        if (voucherEntryMapper.selectCount(new LambdaQueryWrapper<VoucherEntryEntity>()
                .eq(VoucherEntryEntity::getCompanyId, audit.companyId())
                .eq(VoucherEntryEntity::getAccountBookId, audit.accountBookId())
                .eq(VoucherEntryEntity::getVoucherId, voucher.getId())) > 0) {
            return;
        }
        insertEntry(voucher, expense, expenseSubject, 1, expense.getAmount(), BigDecimalZero.VALUE, audit);
        insertEntry(voucher, expense, paymentSubject, 2, BigDecimalZero.VALUE, expense.getAmount(), audit);
    }

    private void insertEntry(
            VoucherEntity voucher,
            ExpenseEntity expense,
            AccountSubjectEntity subject,
            int lineNo,
            java.math.BigDecimal debitAmount,
            java.math.BigDecimal creditAmount,
            AuditMetadata audit
    ) {
        LocalDateTime now = audit.now();
        VoucherEntryEntity entry = new VoucherEntryEntity();
        entry.setCompanyId(audit.companyId());
        entry.setAccountBookId(audit.accountBookId());
        entry.setVoucherId(voucher.getId());
        entry.setBizDate(expense.getExpenseDate());
        entry.setLineNo(lineNo);
        entry.setSubjectId(subject.getId());
        entry.setSubjectCode(subject.getSubjectCode());
        entry.setSubjectName(subject.getSubjectName());
        entry.setDebitAmount(ScalePrecision.amount(debitAmount));
        entry.setCreditAmount(ScalePrecision.amount(creditAmount));
        entry.setSummary(expense.getRemark());
        setAudit(entry, audit, now);
        voucherEntryMapper.insert(entry);
    }

    private void insertReversalEntriesIfAbsent(
            VoucherEntity reversalVoucher,
            List<VoucherEntryEntity> originalEntries,
            AuditMetadata audit
    ) {
        if (voucherEntryMapper.selectCount(new LambdaQueryWrapper<VoucherEntryEntity>()
                .eq(VoucherEntryEntity::getCompanyId, audit.companyId())
                .eq(VoucherEntryEntity::getAccountBookId, audit.accountBookId())
                .eq(VoucherEntryEntity::getVoucherId, reversalVoucher.getId())) > 0) {
            return;
        }
        LocalDateTime now = audit.now();
        for (VoucherEntryEntity originalEntry : originalEntries) {
            VoucherEntryEntity entry = new VoucherEntryEntity();
            entry.setCompanyId(audit.companyId());
            entry.setAccountBookId(audit.accountBookId());
            entry.setVoucherId(reversalVoucher.getId());
            entry.setBizDate(reversalVoucher.getBizDate());
            entry.setLineNo(originalEntry.getLineNo());
            entry.setSubjectId(originalEntry.getSubjectId());
            entry.setSubjectCode(originalEntry.getSubjectCode());
            entry.setSubjectName(originalEntry.getSubjectName());
            entry.setDebitAmount(ScalePrecision.amount(ScalePrecision.zeroDefault(originalEntry.getCreditAmount())));
            entry.setCreditAmount(ScalePrecision.amount(ScalePrecision.zeroDefault(originalEntry.getDebitAmount())));
            entry.setSummary("红冲: " + originalEntry.getSummary());
            setAudit(entry, audit, now);
            voucherEntryMapper.insert(entry);
        }
    }

    private ExpenseEntity requireExpense(Long id) {
        ExpenseEntity expense = expenseMapper.selectById(id);
        AuditMetadata audit = auditMetadataFactory.current();
        if (expense == null || expense.getDeletedFlag() == null || expense.getDeletedFlag() != 0
                || !Objects.equals(expense.getCompanyId(), audit.companyId())
                || !Objects.equals(expense.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("费用单不存在");
        }
        return expense;
    }

    private VoucherEntity findExpenseVoucher(ExpenseEntity expense) {
        if (expense.getVoucherId() != null) {
            VoucherEntity linkedVoucher = voucherMapper.selectById(expense.getVoucherId());
            if (linkedVoucher != null
                    && Objects.equals(linkedVoucher.getCompanyId(), expense.getCompanyId())
                    && Objects.equals(linkedVoucher.getAccountBookId(), expense.getAccountBookId())
                    && Objects.equals(linkedVoucher.getSourceType(), SOURCE_TYPE)
                    && Objects.equals(linkedVoucher.getSourceId(), expense.getId())
                    && Objects.equals(linkedVoucher.getDeletedFlag(), 0)) {
                return linkedVoucher;
            }
        }
        return voucherMapper.selectOne(new LambdaQueryWrapper<VoucherEntity>()
                .eq(VoucherEntity::getCompanyId, expense.getCompanyId())
                .eq(VoucherEntity::getAccountBookId, expense.getAccountBookId())
                .eq(VoucherEntity::getSourceType, SOURCE_TYPE)
                .eq(VoucherEntity::getSourceId, expense.getId())
                .eq(VoucherEntity::getDeletedFlag, 0));
    }

    private VoucherEntity findReversalVoucher(ExpenseEntity expense) {
        return voucherMapper.selectOne(new LambdaQueryWrapper<VoucherEntity>()
                .eq(VoucherEntity::getCompanyId, expense.getCompanyId())
                .eq(VoucherEntity::getAccountBookId, expense.getAccountBookId())
                .eq(VoucherEntity::getSourceType, REVERSAL_SOURCE_TYPE)
                .eq(VoucherEntity::getSourceId, expense.getId())
                .eq(VoucherEntity::getDeletedFlag, 0));
    }

    private List<VoucherEntryEntity> voucherEntries(Long voucherId, Long companyId, Long accountBookId) {
        return voucherEntryMapper.selectList(new LambdaQueryWrapper<VoucherEntryEntity>()
                .eq(VoucherEntryEntity::getCompanyId, companyId)
                .eq(VoucherEntryEntity::getAccountBookId, accountBookId)
                .eq(VoucherEntryEntity::getVoucherId, voucherId)
                .orderByAsc(VoucherEntryEntity::getLineNo)
                .orderByAsc(VoucherEntryEntity::getId));
    }

    private void requireSubjectsInTenant(
            AccountSubjectEntity expenseSubject,
            AccountSubjectEntity paymentSubject,
            AuditMetadata audit
    ) {
        if (!Objects.equals(expenseSubject.getCompanyId(), audit.companyId())
                || !Objects.equals(expenseSubject.getAccountBookId(), audit.accountBookId())
                || !Objects.equals(paymentSubject.getCompanyId(), audit.companyId())
                || !Objects.equals(paymentSubject.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("会计科目不存在");
        }
    }

    private void setAudit(Object entity, AuditMetadata audit, LocalDateTime now) {
        if (entity instanceof VoucherEntity voucher) {
            voucher.setCreatedBy(audit.userId());
            voucher.setCreatedTime(now);
            voucher.setUpdatedBy(audit.userId());
            voucher.setUpdatedTime(now);
            voucher.setVersion(0);
        } else if (entity instanceof VoucherEntryEntity entry) {
            entry.setCreatedBy(audit.userId());
            entry.setCreatedTime(now);
            entry.setUpdatedBy(audit.userId());
            entry.setUpdatedTime(now);
            entry.setVersion(0);
        }
    }

    private static final class BigDecimalZero {
        private static final java.math.BigDecimal VALUE = ScalePrecision.amount(java.math.BigDecimal.ZERO);
    }
}

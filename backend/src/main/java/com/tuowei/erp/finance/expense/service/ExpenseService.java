package com.tuowei.erp.finance.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.expense.mapper.ExpenseMapper;
import com.tuowei.erp.finance.expense.model.ExpenseEntity;
import com.tuowei.erp.finance.expense.web.ExpenseCreateRequest;
import com.tuowei.erp.finance.expense.web.ExpensePageQuery;
import com.tuowei.erp.finance.expense.web.ExpenseReconciliationResponse;
import com.tuowei.erp.finance.expense.web.ExpenseResponse;
import com.tuowei.erp.finance.expense.web.ExpenseUpdateRequest;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.subject.service.AccountSubjectService;
import com.tuowei.erp.finance.voucher.mapper.VoucherEntryMapper;
import com.tuowei.erp.finance.voucher.mapper.VoucherMapper;
import com.tuowei.erp.finance.voucher.model.VoucherEntity;
import com.tuowei.erp.finance.voucher.model.VoucherEntryEntity;
import com.tuowei.erp.finance.voucher.service.VoucherQueryService;
import com.tuowei.erp.finance.voucher.web.VoucherEntryResponse;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.List;

@Service
public class ExpenseService {

    private static final BigDecimal ZERO_AMOUNT = ScalePrecision.amount(BigDecimal.ZERO);

    private final ExpenseMapper expenseMapper;
    private final VoucherMapper voucherMapper;
    private final VoucherEntryMapper voucherEntryMapper;
    private final ExpenseNumberService expenseNumberService;
    private final AccountSubjectService accountSubjectService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final VoucherQueryService voucherQueryService;
    private final AccountPeriodGuard accountPeriodGuard;
    private final AttachmentService attachmentService;
    private final WorkflowService workflowService;

    public ExpenseService(
            ExpenseMapper expenseMapper,
            VoucherMapper voucherMapper,
            VoucherEntryMapper voucherEntryMapper,
            ExpenseNumberService expenseNumberService,
            AccountSubjectService accountSubjectService,
            AuditMetadataFactory auditMetadataFactory,
            VoucherQueryService voucherQueryService,
            AccountPeriodGuard accountPeriodGuard,
            AttachmentService attachmentService,
            WorkflowService workflowService
    ) {
        this.expenseMapper = expenseMapper;
        this.voucherMapper = voucherMapper;
        this.voucherEntryMapper = voucherEntryMapper;
        this.expenseNumberService = expenseNumberService;
        this.accountSubjectService = accountSubjectService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.voucherQueryService = voucherQueryService;
        this.accountPeriodGuard = accountPeriodGuard;
        this.attachmentService = attachmentService;
        this.workflowService = workflowService;
    }

    @Transactional
    public ExpenseResponse create(ExpenseCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        AccountSubjectEntity expenseSubject = accountSubjectService.requireActiveSubject(request.subjectId(), "费用科目不存在或已停用");
        AccountSubjectEntity paymentSubject = accountSubjectService.requireActiveSubject(request.paymentSubjectId(), "支付科目不存在或已停用");
        requireSubjectsInTenant(expenseSubject, paymentSubject, audit);

        ExpenseEntity entity = new ExpenseEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setExpenseNo(expenseNumberService.nextExpenseNo(request.expenseDate()));
        entity.setExpenseDate(request.expenseDate());
        entity.setSubjectId(expenseSubject.getId());
        entity.setPaymentSubjectId(paymentSubject.getId());
        entity.setAmount(ScalePrecision.amount(request.amount()));
        entity.setStatus("DRAFT");
        entity.setDeletedFlag(0);
        entity.setRemark(request.remark());
        setAudit(entity, audit, now);
        expenseMapper.insert(entity);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> list(ExpensePageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        ExpensePageQuery safeQuery = query == null ? new ExpensePageQuery() : query;
        Page<ExpenseEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
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
        List<VoucherEntryEntity> entries = voucher == null ? List.of() : voucherEntries(voucher.getId(), expense.getCompanyId(), expense.getAccountBookId());
        VoucherEntity reversalVoucher = findReversalVoucher(expense);
        List<VoucherEntryEntity> reversalEntries = reversalVoucher == null ? List.of() : voucherEntries(reversalVoucher.getId(), expense.getCompanyId(), expense.getAccountBookId());
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

    @Transactional
    public ExpenseResponse update(Long id, ExpenseUpdateRequest request) {
        ExpenseEntity expense = requireExpense(id);
        if (!"DRAFT".equals(expense.getStatus()) && !"REJECTED".equals(expense.getStatus())) {
            throw new IllegalArgumentException("只有草稿或已驳回的费用单可以编辑");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        AccountSubjectEntity expenseSubject = accountSubjectService.requireActiveSubject(request.subjectId(), "费用科目不存在或已停用");
        AccountSubjectEntity paymentSubject = accountSubjectService.requireActiveSubject(request.paymentSubjectId(), "支付科目不存在或已停用");
        requireSubjectsInTenant(expenseSubject, paymentSubject, audit);

        expense.setExpenseDate(request.expenseDate());
        expense.setSubjectId(expenseSubject.getId());
        expense.setPaymentSubjectId(paymentSubject.getId());
        expense.setAmount(ScalePrecision.amount(request.amount()));
        expense.setRemark(request.remark());
        expense.setUpdatedBy(audit.userId());
        expense.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(expenseMapper.updateById(expense), "费用单已被其他操作修改，请刷新后重试");
        return detail(id);
    }

    @Transactional
    public ExpenseResponse submit(Long id, String remark) {
        ExpenseEntity expense = requireExpense(id);
        if (!"DRAFT".equals(expense.getStatus()) && !"REJECTED".equals(expense.getStatus())) {
            throw new IllegalArgumentException("只有草稿或已驳回的费用单可以提交审批");
        }
        ExpenseResponse response = transitionStatus(expense, "PENDING");
        attachmentService.requireIfConfigured("EXPENSE", expense.getId());
        workflowService.submit("EXPENSE", expense.getId(), expense.getExpenseNo(), "费用单 " + expense.getExpenseNo(), remark);
        return response;
    }

    @Transactional
    public ExpenseResponse approve(Long id, String remark) {
        return approve(id, remark, null);
    }

    @Transactional
    public ExpenseResponse approveWorkflowTask(Long taskId, Long id, String remark) {
        return approve(id, remark, taskId);
    }

    private ExpenseResponse approve(Long id, String remark, Long workflowTaskId) {
        ExpenseEntity expense = requireExpense(id);
        if (!"PENDING".equals(expense.getStatus())) {
            throw new IllegalArgumentException("只有待审批的费用单可以审批通过");
        }
        ExpenseResponse response = transitionStatus(expense, "APPROVED");
        if (workflowTaskId == null) {
            workflowService.approve("EXPENSE", expense.getId(), remark);
        } else {
            workflowService.approveTaskForBusiness(workflowTaskId, "EXPENSE", expense.getId(), remark);
        }
        return response;
    }

    @Transactional
    public ExpenseResponse reject(Long id, String reason) {
        return reject(id, reason, null);
    }

    @Transactional
    public ExpenseResponse rejectWorkflowTask(Long taskId, Long id, String reason) {
        return reject(id, reason, taskId);
    }

    private ExpenseResponse reject(Long id, String reason, Long workflowTaskId) {
        ExpenseEntity expense = requireExpense(id);
        if (!"PENDING".equals(expense.getStatus())) {
            throw new IllegalArgumentException("只有待审批的费用单可以驳回");
        }
        ExpenseResponse response = transitionStatus(expense, "REJECTED");
        if (workflowTaskId == null) {
            workflowService.reject("EXPENSE", expense.getId(), reason);
        } else {
            workflowService.rejectTaskForBusiness(workflowTaskId, "EXPENSE", expense.getId(), reason);
        }
        return response;
    }

    @Transactional
    public ExpenseResponse post(Long id) {
        ExpenseEntity expense = requireExpense(id);
        if ("POSTED".equals(expense.getStatus())) {
            return toResponse(expense);
        }
        if (!"APPROVED".equals(expense.getStatus())) {
            throw new IllegalArgumentException("只有审批通过的费用单可以过账");
        }
        attachmentService.requireIfConfigured("EXPENSE", expense.getId());
        accountPeriodGuard.requireOpen(expense.getExpenseDate(), "费用单过账");
        AuditMetadata audit = auditMetadataFactory.current();
        AccountSubjectEntity expenseSubject = accountSubjectService.requireActiveSubject(expense.getSubjectId(), "费用科目不存在或已停用");
        AccountSubjectEntity paymentSubject = accountSubjectService.requireActiveSubject(expense.getPaymentSubjectId(), "支付科目不存在或已停用");
        VoucherEntity voucher = insertVoucherIfAbsent(expense, audit);
        insertEntriesIfAbsent(voucher, expense, expenseSubject, paymentSubject, audit);

        expense.setStatus("POSTED");
        expense.setVoucherId(voucher.getId());
        expense.setUpdatedBy(audit.userId());
        expense.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(expenseMapper.updateById(expense), "费用单已被其他操作修改，请刷新后重试");
        return detail(id);
    }

    @Transactional
    public ExpenseResponse reverse(Long id) {
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
        return detail(id);
    }

    @Transactional
    public ExpenseResponse cancel(Long id) {
        ExpenseEntity expense = requireExpense(id);
        if (!"DRAFT".equals(expense.getStatus()) && !"REJECTED".equals(expense.getStatus())) {
            throw new IllegalArgumentException("只有草稿或已驳回的费用单可以作废");
        }
        accountPeriodGuard.requireOpen(expense.getExpenseDate(), "费用单作废");
        AuditMetadata audit = auditMetadataFactory.current();
        expense.setStatus("CANCELLED");
        expense.setUpdatedBy(audit.userId());
        expense.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(expenseMapper.updateById(expense), "费用单已被其他操作修改，请刷新后重试");
        return detail(id);
    }

    private ExpenseResponse transitionStatus(ExpenseEntity expense, String status) {
        AuditMetadata audit = auditMetadataFactory.current();
        expense.setStatus(status);
        expense.setUpdatedBy(audit.userId());
        expense.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(expenseMapper.updateById(expense), "费用单已被其他操作修改，请刷新后重试");
        return detail(expense.getId());
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

    private VoucherEntity insertVoucherIfAbsent(ExpenseEntity expense, AuditMetadata audit) {
        VoucherEntity existing = voucherMapper.selectOne(new LambdaQueryWrapper<VoucherEntity>()
                .eq(VoucherEntity::getCompanyId, audit.companyId())
                .eq(VoucherEntity::getAccountBookId, audit.accountBookId())
                .eq(VoucherEntity::getSourceType, "EXPENSE")
                .eq(VoucherEntity::getSourceId, expense.getId()));
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = audit.now();
        VoucherEntity voucher = new VoucherEntity();
        voucher.setCompanyId(audit.companyId());
        voucher.setAccountBookId(audit.accountBookId());
        voucher.setVoucherNo("VO-EXPENSE-" + expense.getId());
        voucher.setSourceType("EXPENSE");
        voucher.setSourceId(expense.getId());
        voucher.setSourceNo(expense.getExpenseNo());
        voucher.setBizDate(expense.getExpenseDate());
        voucher.setAmount(expense.getAmount());
        voucher.setStatus("POSTED");
        voucher.setDeletedFlag(0);
        voucher.setRemark("费用登记凭证");
        voucher.setCreatedBy(audit.userId());
        voucher.setCreatedTime(now);
        voucher.setUpdatedBy(audit.userId());
        voucher.setUpdatedTime(now);
        voucher.setVersion(0);
        voucherMapper.insert(voucher);
        return voucher;
    }

    private VoucherEntity insertReversalVoucherIfAbsent(ExpenseEntity expense, VoucherEntity originalVoucher, AuditMetadata audit) {
        VoucherEntity existing = findReversalVoucher(expense);
        if (existing != null) {
            return existing;
        }
        LocalDateTime now = audit.now();
        VoucherEntity voucher = new VoucherEntity();
        voucher.setCompanyId(audit.companyId());
        voucher.setAccountBookId(audit.accountBookId());
        voucher.setVoucherNo("VO-EXPENSE-REV-" + expense.getId());
        voucher.setSourceType("EXPENSE_REVERSAL");
        voucher.setSourceId(expense.getId());
        voucher.setSourceNo(expense.getExpenseNo());
        voucher.setBizDate(audit.now().toLocalDate());
        voucher.setAmount(originalVoucher.getAmount());
        voucher.setStatus("POSTED");
        voucher.setDeletedFlag(0);
        voucher.setRemark("费用登记红冲凭证: " + originalVoucher.getVoucherNo());
        voucher.setCreatedBy(audit.userId());
        voucher.setCreatedTime(now);
        voucher.setUpdatedBy(audit.userId());
        voucher.setUpdatedTime(now);
        voucher.setVersion(0);
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
        insertEntry(voucher, expense, expenseSubject, 1, expense.getAmount(), ZERO_AMOUNT, audit);
        insertEntry(voucher, expense, paymentSubject, 2, ZERO_AMOUNT, expense.getAmount(), audit);
    }

    private void insertEntry(
            VoucherEntity voucher,
            ExpenseEntity expense,
            AccountSubjectEntity subject,
            int lineNo,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
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
        entry.setCreatedBy(audit.userId());
        entry.setCreatedTime(now);
        entry.setUpdatedBy(audit.userId());
        entry.setUpdatedTime(now);
        entry.setVersion(0);
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
            entry.setCreatedBy(audit.userId());
            entry.setCreatedTime(now);
            entry.setUpdatedBy(audit.userId());
            entry.setUpdatedTime(now);
            entry.setVersion(0);
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

    private ExpenseResponse toResponse(ExpenseEntity expense) {
        VoucherEntity voucher = findExpenseVoucher(expense);
        List<VoucherEntryEntity> entries = voucher == null ? List.of() : voucherEntries(voucher.getId(), expense.getCompanyId(), expense.getAccountBookId());
        VoucherEntity reversalVoucher = findReversalVoucher(expense);
        List<VoucherEntryEntity> reversalEntries = reversalVoucher == null ? List.of() : voucherEntries(reversalVoucher.getId(), expense.getCompanyId(), expense.getAccountBookId());
        return toResponse(expense, voucher, entries, reversalVoucher, reversalEntries);
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
                expense.getRemark()
        );
    }

    private VoucherEntity findExpenseVoucher(ExpenseEntity expense) {
        VoucherEntity linkedVoucher = null;
        if (expense.getVoucherId() != null) {
            linkedVoucher = voucherMapper.selectById(expense.getVoucherId());
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

    private VoucherEntity findReversalVoucher(ExpenseEntity expense) {
        return voucherMapper.selectOne(new LambdaQueryWrapper<VoucherEntity>()
                .eq(VoucherEntity::getCompanyId, expense.getCompanyId())
                .eq(VoucherEntity::getAccountBookId, expense.getAccountBookId())
                .eq(VoucherEntity::getSourceType, "EXPENSE_REVERSAL")
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

    private BigDecimal sumDebit(List<VoucherEntryEntity> entries) {
        return ScalePrecision.amount(entries.stream()
                .map(VoucherEntryEntity::getDebitAmount)
                .map(ScalePrecision::zeroDefault)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    private BigDecimal sumCredit(List<VoucherEntryEntity> entries) {
        return ScalePrecision.amount(entries.stream()
                .map(VoucherEntryEntity::getCreditAmount)
                .map(ScalePrecision::zeroDefault)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
        );
    }

    private boolean amountsEqual(BigDecimal left, BigDecimal right) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(left))
                .compareTo(ScalePrecision.amount(ScalePrecision.zeroDefault(right))) == 0;
    }

    private void setAudit(ExpenseEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}

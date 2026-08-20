package com.tuowei.erp.finance.expense.service;

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
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.subject.service.AccountSubjectService;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class ExpenseService {

    private final ExpenseMapper expenseMapper;
    private final ExpenseNumberService expenseNumberService;
    private final AccountSubjectService accountSubjectService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final ExpenseQueryService expenseQueryService;
    private final ExpensePostingService expensePostingService;
    private final AccountPeriodGuard accountPeriodGuard;
    private final AttachmentService attachmentService;
    private final WorkflowService workflowService;

    public ExpenseService(
            ExpenseMapper expenseMapper,
            ExpenseNumberService expenseNumberService,
            AccountSubjectService accountSubjectService,
            AuditMetadataFactory auditMetadataFactory,
            ExpenseQueryService expenseQueryService,
            ExpensePostingService expensePostingService,
            AccountPeriodGuard accountPeriodGuard,
            AttachmentService attachmentService,
            WorkflowService workflowService
    ) {
        this.expenseMapper = expenseMapper;
        this.expenseNumberService = expenseNumberService;
        this.accountSubjectService = accountSubjectService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.expenseQueryService = expenseQueryService;
        this.expensePostingService = expensePostingService;
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
        return expenseQueryService.toResponse(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponse> list(ExpensePageQuery query) {
        ExpensePageQuery safeQuery = query == null ? new ExpensePageQuery() : query;
        return expenseQueryService.list(safeQuery);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse detail(Long id) {
        return expenseQueryService.detail(id);
    }

    @Transactional(readOnly = true)
    public ExpenseReconciliationResponse reconciliation(Long id) {
        return expenseQueryService.reconciliation(id);
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
        attachmentService.requireIfConfigured(AttachmentBusinessType.EXPENSE, expense.getId());
        ExpenseResponse response = transitionStatus(expense, "PENDING");
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
        boolean completed;
        if (workflowTaskId == null) {
            completed = workflowService.approve("EXPENSE", expense.getId(), remark);
        } else {
            completed = workflowService.approveTaskForBusiness(
                    workflowTaskId,
                    "EXPENSE",
                    expense.getId(),
                    remark
            );
        }
        if (!completed) {
            return detail(expense.getId());
        }
        return transitionStatus(expense, "APPROVED");
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
        return expensePostingService.post(id);
    }

    @Transactional
    public ExpenseResponse reverse(Long id) {
        return expensePostingService.reverse(id);
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

    private ExpenseEntity requireExpense(Long id) {
        return expenseQueryService.requireExpense(id);
    }

    private void setAudit(ExpenseEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

}

package com.tuowei.erp.finance.expense.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.expense.mapper.ExpenseMapper;
import com.tuowei.erp.finance.expense.model.ExpenseEntity;
import com.tuowei.erp.finance.expense.web.ExpenseCreateRequest;
import com.tuowei.erp.finance.expense.web.ExpenseResponse;
import com.tuowei.erp.finance.expense.web.ExpenseUpdateRequest;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.subject.service.AccountSubjectService;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/** Draft entry and approval lifecycle commands for expenses. */
@Service
public class ExpenseCommandService {

    private static final String BUSINESS_TYPE = "EXPENSE";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String OPTIMISTIC_LOCK_MESSAGE = "费用单已被其他操作修改，请刷新后重试";

    private final ExpenseMapper expenseMapper;
    private final ExpenseNumberService expenseNumberService;
    private final AccountSubjectService accountSubjectService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final ExpenseQueryService expenseQueryService;
    private final AccountPeriodGuard accountPeriodGuard;
    private final AttachmentService attachmentService;
    private final WorkflowService workflowService;
    private final com.tuowei.erp.finance.budget.service.BudgetExecutionService budgetExecutionService;

    @Autowired
    public ExpenseCommandService(
            ExpenseMapper expenseMapper,
            ExpenseNumberService expenseNumberService,
            AccountSubjectService accountSubjectService,
            AuditMetadataFactory auditMetadataFactory,
            ExpenseQueryService expenseQueryService,
            AccountPeriodGuard accountPeriodGuard,
            AttachmentService attachmentService,
            WorkflowService workflowService,
            com.tuowei.erp.finance.budget.service.BudgetExecutionService budgetExecutionService
    ) {
        this.expenseMapper = expenseMapper;
        this.expenseNumberService = expenseNumberService;
        this.accountSubjectService = accountSubjectService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.expenseQueryService = expenseQueryService;
        this.accountPeriodGuard = accountPeriodGuard;
        this.attachmentService = attachmentService;
        this.workflowService = workflowService;
        this.budgetExecutionService = budgetExecutionService;
    }

    /** Compatibility constructor retained for focused unit tests and embedders. */
    public ExpenseCommandService(
            ExpenseMapper expenseMapper,
            ExpenseNumberService expenseNumberService,
            AccountSubjectService accountSubjectService,
            AuditMetadataFactory auditMetadataFactory,
            ExpenseQueryService expenseQueryService,
            AccountPeriodGuard accountPeriodGuard,
            AttachmentService attachmentService,
            WorkflowService workflowService
    ) {
        this(expenseMapper, expenseNumberService, accountSubjectService, auditMetadataFactory,
                expenseQueryService, accountPeriodGuard, attachmentService, workflowService, null);
    }

    @Transactional
    public ExpenseResponse create(ExpenseCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        AccountSubjectEntity expenseSubject = accountSubjectService.requireActiveSubject(
                request.subjectId(),
                "费用科目不存在或已停用"
        );
        AccountSubjectEntity paymentSubject = accountSubjectService.requireActiveSubject(
                request.paymentSubjectId(),
                "支付科目不存在或已停用"
        );
        requireSubjectsInTenant(expenseSubject, paymentSubject, audit);

        ExpenseEntity entity = new ExpenseEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setExpenseNo(expenseNumberService.nextExpenseNo(request.expenseDate()));
        entity.setExpenseDate(request.expenseDate());
        entity.setDeptId(request.deptId());
        entity.setSubjectId(expenseSubject.getId());
        entity.setPaymentSubjectId(paymentSubject.getId());
        entity.setAmount(ScalePrecision.amount(request.amount()));
        entity.setStatus(STATUS_DRAFT);
        entity.setDeletedFlag(0);
        entity.setRemark(request.remark());
        setAudit(entity, audit, now);
        expenseMapper.insert(entity);
        return expenseQueryService.toResponse(entity);
    }

    @Transactional
    public ExpenseResponse update(Long id, ExpenseUpdateRequest request) {
        ExpenseEntity expense = requireExpense(id);
        if (!STATUS_DRAFT.equals(expense.getStatus()) && !STATUS_REJECTED.equals(expense.getStatus())) {
            throw new IllegalArgumentException("只有草稿或已驳回的费用单可以编辑");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        AccountSubjectEntity expenseSubject = accountSubjectService.requireActiveSubject(
                request.subjectId(),
                "费用科目不存在或已停用"
        );
        AccountSubjectEntity paymentSubject = accountSubjectService.requireActiveSubject(
                request.paymentSubjectId(),
                "支付科目不存在或已停用"
        );
        requireSubjectsInTenant(expenseSubject, paymentSubject, audit);

        expense.setExpenseDate(request.expenseDate());
        expense.setDeptId(request.deptId());
        expense.setSubjectId(expenseSubject.getId());
        expense.setPaymentSubjectId(paymentSubject.getId());
        expense.setAmount(ScalePrecision.amount(request.amount()));
        expense.setRemark(request.remark());
        expense.setUpdatedBy(audit.userId());
        expense.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(expenseMapper.updateById(expense), OPTIMISTIC_LOCK_MESSAGE);
        return expenseQueryService.detail(id);
    }

    @Transactional
    public ExpenseResponse submit(Long id, String remark) {
        ExpenseEntity expense = requireExpense(id);
        if (!STATUS_DRAFT.equals(expense.getStatus()) && !STATUS_REJECTED.equals(expense.getStatus())) {
            throw new IllegalArgumentException("只有草稿或已驳回的费用单可以提交审批");
        }
        attachmentService.requireIfConfigured(AttachmentBusinessType.EXPENSE, expense.getId());
        if (budgetExecutionService != null) budgetExecutionService.commitExpense(expense);
        ExpenseResponse response = transitionStatus(expense, STATUS_PENDING);
        workflowService.submit(
                BUSINESS_TYPE,
                expense.getId(),
                expense.getExpenseNo(),
                "费用单 " + expense.getExpenseNo(),
                remark
        );
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

    @Transactional
    public ExpenseResponse reject(Long id, String reason) {
        return reject(id, reason, null);
    }

    @Transactional
    public ExpenseResponse rejectWorkflowTask(Long taskId, Long id, String reason) {
        return reject(id, reason, taskId);
    }

    @Transactional
    public ExpenseResponse cancel(Long id) {
        ExpenseEntity expense = requireExpense(id);
        if (!STATUS_DRAFT.equals(expense.getStatus()) && !STATUS_REJECTED.equals(expense.getStatus())) {
            throw new IllegalArgumentException("只有草稿或已驳回的费用单可以作废");
        }
        accountPeriodGuard.requireOpen(expense.getExpenseDate(), "费用单作废");
        if (budgetExecutionService != null) budgetExecutionService.releaseExpense(expense);
        AuditMetadata audit = auditMetadataFactory.current();
        expense.setStatus(STATUS_CANCELLED);
        expense.setUpdatedBy(audit.userId());
        expense.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(expenseMapper.updateById(expense), OPTIMISTIC_LOCK_MESSAGE);
        return expenseQueryService.detail(id);
    }

    private ExpenseResponse approve(Long id, String remark, Long workflowTaskId) {
        ExpenseEntity expense = requireExpense(id);
        if (!STATUS_PENDING.equals(expense.getStatus())) {
            throw new IllegalArgumentException("只有待审批的费用单可以审批通过");
        }
        boolean completed;
        if (workflowTaskId == null) {
            completed = workflowService.approve(BUSINESS_TYPE, expense.getId(), remark);
        } else {
            completed = workflowService.approveTaskForBusiness(
                    workflowTaskId,
                    BUSINESS_TYPE,
                    expense.getId(),
                    remark
            );
        }
        if (!completed) {
            return expenseQueryService.detail(expense.getId());
        }
        return transitionStatus(expense, STATUS_APPROVED);
    }

    private ExpenseResponse reject(Long id, String reason, Long workflowTaskId) {
        ExpenseEntity expense = requireExpense(id);
        if (!STATUS_PENDING.equals(expense.getStatus())) {
            throw new IllegalArgumentException("只有待审批的费用单可以驳回");
        }
        if (budgetExecutionService != null) budgetExecutionService.releaseExpense(expense);
        ExpenseResponse response = transitionStatus(expense, STATUS_REJECTED);
        if (workflowTaskId == null) {
            workflowService.reject(BUSINESS_TYPE, expense.getId(), reason);
        } else {
            workflowService.rejectTaskForBusiness(workflowTaskId, BUSINESS_TYPE, expense.getId(), reason);
        }
        return response;
    }

    private ExpenseResponse transitionStatus(ExpenseEntity expense, String status) {
        AuditMetadata audit = auditMetadataFactory.current();
        expense.setStatus(status);
        expense.setUpdatedBy(audit.userId());
        expense.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(expenseMapper.updateById(expense), OPTIMISTIC_LOCK_MESSAGE);
        return expenseQueryService.detail(expense.getId());
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

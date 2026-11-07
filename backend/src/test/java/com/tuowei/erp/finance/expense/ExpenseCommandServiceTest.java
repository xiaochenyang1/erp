package com.tuowei.erp.finance.expense;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.expense.mapper.ExpenseMapper;
import com.tuowei.erp.finance.expense.model.ExpenseEntity;
import com.tuowei.erp.finance.expense.service.ExpenseCommandService;
import com.tuowei.erp.finance.expense.service.ExpenseNumberService;
import com.tuowei.erp.finance.expense.service.ExpenseQueryService;
import com.tuowei.erp.finance.expense.web.ExpenseCreateRequest;
import com.tuowei.erp.finance.expense.web.ExpenseResponse;
import com.tuowei.erp.finance.expense.web.ExpenseUpdateRequest;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.subject.service.AccountSubjectService;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            7001L,
            8001L,
            9001L,
            LocalDateTime.of(2026, 8, 20, 10, 30)
    );
    private static final Long EXPENSE_ID = 1001L;
    private static final Long EXPENSE_SUBJECT_ID = 2001L;
    private static final Long PAYMENT_SUBJECT_ID = 2002L;
    private static final Long WORKFLOW_TASK_ID = 3001L;
    private static final LocalDate EXPENSE_DATE = LocalDate.of(2026, 8, 20);

    @Mock
    private ExpenseMapper expenseMapper;
    @Mock
    private ExpenseNumberService expenseNumberService;
    @Mock
    private AccountSubjectService accountSubjectService;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;
    @Mock
    private ExpenseQueryService expenseQueryService;
    @Mock
    private AccountPeriodGuard accountPeriodGuard;
    @Mock
    private AttachmentService attachmentService;
    @Mock
    private WorkflowService workflowService;

    @Test
    void createBuildsTenantScopedDraftWithRoundedAmountAndAuditMetadata() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(accountSubjectService.requireActiveSubject(EXPENSE_SUBJECT_ID, "费用科目不存在或已停用"))
                .thenReturn(subject(EXPENSE_SUBJECT_ID, AUDIT.companyId(), AUDIT.accountBookId(), "6601"));
        when(accountSubjectService.requireActiveSubject(PAYMENT_SUBJECT_ID, "支付科目不存在或已停用"))
                .thenReturn(subject(PAYMENT_SUBJECT_ID, AUDIT.companyId(), AUDIT.accountBookId(), "1002"));
        when(expenseNumberService.nextExpenseNo(EXPENSE_DATE)).thenReturn("EXP202608200001");
        when(expenseMapper.insert(any(ExpenseEntity.class))).thenAnswer(invocation -> {
            ExpenseEntity entity = invocation.getArgument(0);
            entity.setId(EXPENSE_ID);
            return 1;
        });
        ExpenseResponse expected = response("DRAFT");
        when(expenseQueryService.toResponse(any(ExpenseEntity.class))).thenReturn(expected);

        ExpenseResponse actual = service().create(new ExpenseCreateRequest(
                EXPENSE_DATE,
                EXPENSE_SUBJECT_ID,
                PAYMENT_SUBJECT_ID,
                new BigDecimal("123.456"),
                "差旅费"
        ));

        assertThat(actual).isSameAs(expected);
        ArgumentCaptor<ExpenseEntity> captor = ArgumentCaptor.forClass(ExpenseEntity.class);
        verify(expenseMapper).insert(captor.capture());
        ExpenseEntity inserted = captor.getValue();
        assertThat(inserted.getId()).isEqualTo(EXPENSE_ID);
        assertThat(inserted.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(inserted.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(inserted.getExpenseNo()).isEqualTo("EXP202608200001");
        assertThat(inserted.getExpenseDate()).isEqualTo(EXPENSE_DATE);
        assertThat(inserted.getSubjectId()).isEqualTo(EXPENSE_SUBJECT_ID);
        assertThat(inserted.getPaymentSubjectId()).isEqualTo(PAYMENT_SUBJECT_ID);
        assertThat(inserted.getAmount()).isEqualByComparingTo("123.46");
        assertThat(inserted.getStatus()).isEqualTo("DRAFT");
        assertThat(inserted.getDeletedFlag()).isZero();
        assertThat(inserted.getRemark()).isEqualTo("差旅费");
        assertThat(inserted.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getCreatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(inserted.getUpdatedTime()).isEqualTo(AUDIT.now());
        assertThat(inserted.getVersion()).isZero();
        verify(expenseNumberService).nextExpenseNo(EXPENSE_DATE);
    }

    @Test
    void createRejectsSubjectFromAnotherTenantBeforeNumberAndInsert() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(accountSubjectService.requireActiveSubject(EXPENSE_SUBJECT_ID, "费用科目不存在或已停用"))
                .thenReturn(subject(EXPENSE_SUBJECT_ID, AUDIT.companyId(), 9999L, "6601"));
        when(accountSubjectService.requireActiveSubject(PAYMENT_SUBJECT_ID, "支付科目不存在或已停用"))
                .thenReturn(subject(PAYMENT_SUBJECT_ID, AUDIT.companyId(), AUDIT.accountBookId(), "1002"));

        assertThatThrownBy(() -> service().create(createRequest(new BigDecimal("12.34"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("会计科目不存在");

        verify(expenseNumberService, never()).nextExpenseNo(any());
        verify(expenseMapper, never()).insert(any(ExpenseEntity.class));
        verify(expenseQueryService, never()).toResponse(any(ExpenseEntity.class));
    }

    @Test
    void updateRejectsNonEditableStatusBeforeLoadingSubjectsOrWriting() {
        ExpenseEntity pending = expense("PENDING");
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(pending);

        assertThatThrownBy(() -> service().update(EXPENSE_ID, updateRequest(new BigDecimal("8.88"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("只有草稿或已驳回的费用单可以编辑");

        verify(accountSubjectService, never()).requireActiveSubject(any(), anyString());
        verify(expenseMapper, never()).updateById(any(ExpenseEntity.class));
        verify(expenseQueryService, never()).detail(any());
    }

    @Test
    void updateChangesEditableFieldsWithoutGeneratingANewNumber() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ExpenseEntity draft = expense("REJECTED");
        draft.setExpenseNo("EXP202608190007");
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(draft);
        when(accountSubjectService.requireActiveSubject(EXPENSE_SUBJECT_ID, "费用科目不存在或已停用"))
                .thenReturn(subject(EXPENSE_SUBJECT_ID, AUDIT.companyId(), AUDIT.accountBookId(), "6601"));
        when(accountSubjectService.requireActiveSubject(PAYMENT_SUBJECT_ID, "支付科目不存在或已停用"))
                .thenReturn(subject(PAYMENT_SUBJECT_ID, AUDIT.companyId(), AUDIT.accountBookId(), "1002"));
        when(expenseMapper.updateById(draft)).thenReturn(1);
        ExpenseResponse expected = response("REJECTED");
        when(expenseQueryService.detail(EXPENSE_ID)).thenReturn(expected);

        ExpenseResponse actual = service().update(EXPENSE_ID, new ExpenseUpdateRequest(
                LocalDate.of(2026, 8, 22),
                EXPENSE_SUBJECT_ID,
                PAYMENT_SUBJECT_ID,
                new BigDecimal("8.876"),
                "  调整后  "
        ));

        assertThat(actual).isSameAs(expected);
        assertThat(draft.getExpenseNo()).isEqualTo("EXP202608190007");
        assertThat(draft.getExpenseDate()).isEqualTo(LocalDate.of(2026, 8, 22));
        assertThat(draft.getAmount()).isEqualByComparingTo("8.88");
        assertThat(draft.getRemark()).isEqualTo("  调整后  ");
        assertThat(draft.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(draft.getUpdatedTime()).isEqualTo(AUDIT.now());
        verify(expenseNumberService, never()).nextExpenseNo(any());
        verify(expenseMapper).updateById(draft);
    }

    @Test
    void updateRaisesConflictWhenOptimisticLockDoesNotUpdate() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ExpenseEntity draft = expense("DRAFT");
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(draft);
        stubSubjects();
        when(expenseMapper.updateById(draft)).thenReturn(0);

        assertThatThrownBy(() -> service().update(EXPENSE_ID, updateRequest(new BigDecimal("8.88"))))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("费用单已被其他操作修改，请刷新后重试");

        verify(expenseQueryService, never()).detail(any());
    }

    @Test
    void submitRejectsNonEditableStatusBeforeAttachmentGate() {
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(expense("APPROVED"));

        assertThatThrownBy(() -> service().submit(EXPENSE_ID, "请审批"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("只有草稿或已驳回的费用单可以提交审批");

        verify(attachmentService, never()).requireIfConfigured(anyString(), any());
        verify(expenseMapper, never()).updateById(any(ExpenseEntity.class));
        verify(workflowService, never()).submit(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    void submitRunsAttachmentStatusDetailAndWorkflowInOrder() {
        ExpenseEntity draft = expense("DRAFT");
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(draft);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(expenseMapper.updateById(draft)).thenReturn(1);
        ExpenseResponse expected = response("PENDING");
        when(expenseQueryService.detail(EXPENSE_ID)).thenReturn(expected);

        ExpenseResponse actual = service().submit(EXPENSE_ID, "  提交说明  ");

        assertThat(actual).isSameAs(expected);
        assertThat(draft.getStatus()).isEqualTo("PENDING");
        assertThat(draft.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(draft.getUpdatedTime()).isEqualTo(AUDIT.now());
        InOrder order = inOrder(expenseQueryService, attachmentService, auditMetadataFactory,
                expenseMapper, workflowService);
        order.verify(expenseQueryService).requireExpense(EXPENSE_ID);
        order.verify(attachmentService).requireIfConfigured(AttachmentBusinessType.EXPENSE, EXPENSE_ID);
        order.verify(auditMetadataFactory).current();
        order.verify(expenseMapper).updateById(draft);
        order.verify(expenseQueryService).detail(EXPENSE_ID);
        order.verify(workflowService).submit(
                "EXPENSE",
                EXPENSE_ID,
                draft.getExpenseNo(),
                "费用单 " + draft.getExpenseNo(),
                "  提交说明  "
        );
    }

    @Test
    void submitStopsBeforeStatusWriteWhenAttachmentGateRejects() {
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(expense("DRAFT"));
        doThrow(new IllegalArgumentException("附件缺失"))
                .when(attachmentService)
                .requireIfConfigured(AttachmentBusinessType.EXPENSE, EXPENSE_ID);

        assertThatThrownBy(() -> service().submit(EXPENSE_ID, "请审批"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("附件缺失");

        verify(expenseMapper, never()).updateById(any(ExpenseEntity.class));
        verify(expenseQueryService, never()).detail(any());
        verify(workflowService, never()).submit(anyString(), any(), anyString(), anyString(), any());
    }

    @Test
    void directApprovalLeavesPendingWhenWorkflowIsNotComplete() {
        ExpenseEntity pending = expense("PENDING");
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(pending);
        ExpenseResponse expected = response("PENDING");
        when(expenseQueryService.detail(EXPENSE_ID)).thenReturn(expected);
        when(workflowService.approve("EXPENSE", EXPENSE_ID, "逐级审批")).thenReturn(false);

        assertThat(service().approve(EXPENSE_ID, "逐级审批")).isSameAs(expected);

        assertThat(pending.getStatus()).isEqualTo("PENDING");
        verify(expenseMapper, never()).updateById(any(ExpenseEntity.class));
    }

    @Test
    void taskApprovalLeavesPendingWhenWorkflowIsNotComplete() {
        ExpenseEntity pending = expense("PENDING");
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(pending);
        ExpenseResponse expected = response("PENDING");
        when(expenseQueryService.detail(EXPENSE_ID)).thenReturn(expected);
        when(workflowService.approveTaskForBusiness(
                WORKFLOW_TASK_ID, "EXPENSE", EXPENSE_ID, "任务审批"
        )).thenReturn(false);

        assertThat(service().approveWorkflowTask(WORKFLOW_TASK_ID, EXPENSE_ID, "任务审批"))
                .isSameAs(expected);

        assertThat(pending.getStatus()).isEqualTo("PENDING");
        verify(expenseMapper, never()).updateById(any(ExpenseEntity.class));
    }

    @Test
    void directApprovalUpdatesStatusOnlyAfterWorkflowCompletes() {
        ExpenseEntity pending = expense("PENDING");
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(pending);
        when(workflowService.approve("EXPENSE", EXPENSE_ID, "最终通过")).thenReturn(true);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(expenseMapper.updateById(pending)).thenReturn(1);
        ExpenseResponse expected = response("APPROVED");
        when(expenseQueryService.detail(EXPENSE_ID)).thenReturn(expected);

        assertThat(service().approve(EXPENSE_ID, "最终通过")).isSameAs(expected);

        assertThat(pending.getStatus()).isEqualTo("APPROVED");
        assertThat(pending.getUpdatedBy()).isEqualTo(AUDIT.userId());
        InOrder order = inOrder(workflowService, auditMetadataFactory, expenseMapper, expenseQueryService);
        order.verify(workflowService).approve("EXPENSE", EXPENSE_ID, "最终通过");
        order.verify(auditMetadataFactory).current();
        order.verify(expenseMapper).updateById(pending);
        order.verify(expenseQueryService).detail(EXPENSE_ID);
    }

    @Test
    void taskApprovalUpdatesStatusOnlyAfterWorkflowCompletes() {
        ExpenseEntity pending = expense("PENDING");
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(pending);
        when(workflowService.approveTaskForBusiness(
                WORKFLOW_TASK_ID, "EXPENSE", EXPENSE_ID, "任务最终通过"
        )).thenReturn(true);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(expenseMapper.updateById(pending)).thenReturn(1);
        ExpenseResponse expected = response("APPROVED");
        when(expenseQueryService.detail(EXPENSE_ID)).thenReturn(expected);

        assertThat(service().approveWorkflowTask(WORKFLOW_TASK_ID, EXPENSE_ID, "任务最终通过"))
                .isSameAs(expected);

        assertThat(pending.getStatus()).isEqualTo("APPROVED");
        verify(workflowService).approveTaskForBusiness(
                WORKFLOW_TASK_ID, "EXPENSE", EXPENSE_ID, "任务最终通过"
        );
        verify(expenseMapper).updateById(pending);
    }

    @Test
    void rejectUpdatesStatusAndDetailBeforeCallingDirectWorkflow() {
        ExpenseEntity pending = expense("PENDING");
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(pending);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(expenseMapper.updateById(pending)).thenReturn(1);
        ExpenseResponse expected = response("REJECTED");
        when(expenseQueryService.detail(EXPENSE_ID)).thenReturn(expected);

        assertThat(service().reject(EXPENSE_ID, "科目错误")).isSameAs(expected);

        assertThat(pending.getStatus()).isEqualTo("REJECTED");
        InOrder order = inOrder(expenseQueryService, auditMetadataFactory, expenseMapper, workflowService);
        order.verify(expenseQueryService).requireExpense(EXPENSE_ID);
        order.verify(auditMetadataFactory).current();
        order.verify(expenseMapper).updateById(pending);
        order.verify(expenseQueryService).detail(EXPENSE_ID);
        order.verify(workflowService).reject("EXPENSE", EXPENSE_ID, "科目错误");
    }

    @Test
    void rejectWorkflowTaskUsesTaskRouteAfterStatusUpdate() {
        ExpenseEntity pending = expense("PENDING");
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(pending);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(expenseMapper.updateById(pending)).thenReturn(1);
        when(expenseQueryService.detail(EXPENSE_ID)).thenReturn(response("REJECTED"));

        service().rejectWorkflowTask(WORKFLOW_TASK_ID, EXPENSE_ID, "任务驳回");

        verify(workflowService).rejectTaskForBusiness(
                WORKFLOW_TASK_ID, "EXPENSE", EXPENSE_ID, "任务驳回"
        );
    }

    @Test
    void cancelChecksOpenPeriodBeforeMutatingExpense() {
        ExpenseEntity draft = expense("DRAFT");
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(draft);
        doThrow(new BusinessConflictException("期间已结账"))
                .when(accountPeriodGuard).requireOpen(EXPENSE_DATE, "费用单作废");

        assertThatThrownBy(() -> service().cancel(EXPENSE_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("期间已结账");

        verify(expenseMapper, never()).updateById(any(ExpenseEntity.class));
        verify(auditMetadataFactory, never()).current();
    }

    @Test
    void cancelRejectsNonEditableStatusBeforePeriodGate() {
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(expense("PENDING"));

        assertThatThrownBy(() -> service().cancel(EXPENSE_ID))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("只有草稿或已驳回的费用单可以作废");

        verify(accountPeriodGuard, never()).requireOpen(any(), anyString());
        verify(expenseMapper, never()).updateById(any(ExpenseEntity.class));
    }

    @Test
    void cancelPersistsCancelledStatusAndAuditMetadata() {
        ExpenseEntity rejected = expense("REJECTED");
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(rejected);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(expenseMapper.updateById(rejected)).thenReturn(1);
        ExpenseResponse expected = response("CANCELLED");
        when(expenseQueryService.detail(EXPENSE_ID)).thenReturn(expected);

        assertThat(service().cancel(EXPENSE_ID)).isSameAs(expected);

        assertThat(rejected.getStatus()).isEqualTo("CANCELLED");
        assertThat(rejected.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(rejected.getUpdatedTime()).isEqualTo(AUDIT.now());
        verify(accountPeriodGuard).requireOpen(EXPENSE_DATE, "费用单作废");
        verify(expenseMapper).updateById(rejected);
    }

    @Test
    void cancelRaisesConflictWhenOptimisticLockDoesNotUpdate() {
        ExpenseEntity draft = expense("DRAFT");
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(draft);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(expenseMapper.updateById(draft)).thenReturn(0);

        assertThatThrownBy(() -> service().cancel(EXPENSE_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("费用单已被其他操作修改，请刷新后重试");

        assertThat(draft.getStatus()).isEqualTo("CANCELLED");
        verify(expenseQueryService, never()).detail(any());
    }

    private ExpenseCommandService service() {
        return new ExpenseCommandService(
                expenseMapper,
                expenseNumberService,
                accountSubjectService,
                auditMetadataFactory,
                expenseQueryService,
                accountPeriodGuard,
                attachmentService,
                workflowService
        );
    }

    private ExpenseCreateRequest createRequest(BigDecimal amount) {
        return new ExpenseCreateRequest(
                EXPENSE_DATE,
                EXPENSE_SUBJECT_ID,
                PAYMENT_SUBJECT_ID,
                amount,
                "费用"
        );
    }

    private ExpenseUpdateRequest updateRequest(BigDecimal amount) {
        return new ExpenseUpdateRequest(
                EXPENSE_DATE,
                EXPENSE_SUBJECT_ID,
                PAYMENT_SUBJECT_ID,
                amount,
                "调整"
        );
    }

    private void stubSubjects() {
        when(accountSubjectService.requireActiveSubject(EXPENSE_SUBJECT_ID, "费用科目不存在或已停用"))
                .thenReturn(subject(EXPENSE_SUBJECT_ID, AUDIT.companyId(), AUDIT.accountBookId(), "6601"));
        when(accountSubjectService.requireActiveSubject(PAYMENT_SUBJECT_ID, "支付科目不存在或已停用"))
                .thenReturn(subject(PAYMENT_SUBJECT_ID, AUDIT.companyId(), AUDIT.accountBookId(), "1002"));
    }

    private ExpenseEntity expense(String status) {
        ExpenseEntity entity = new ExpenseEntity();
        entity.setId(EXPENSE_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setExpenseNo("EXP-1001");
        entity.setExpenseDate(EXPENSE_DATE);
        entity.setSubjectId(EXPENSE_SUBJECT_ID);
        entity.setPaymentSubjectId(PAYMENT_SUBJECT_ID);
        entity.setAmount(new BigDecimal("12.34"));
        entity.setStatus(status);
        entity.setDeletedFlag(0);
        entity.setVersion(0);
        return entity;
    }

    private AccountSubjectEntity subject(Long id, Long companyId, Long accountBookId, String code) {
        AccountSubjectEntity entity = new AccountSubjectEntity();
        entity.setId(id);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setSubjectCode(code);
        entity.setSubjectName(code + "科目");
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private ExpenseResponse response(String status) {
        return new ExpenseResponse(
                EXPENSE_ID,
                "EXP-1001",
                EXPENSE_DATE,
                EXPENSE_SUBJECT_ID,
                PAYMENT_SUBJECT_ID,
                new BigDecimal("12.34"),
                status,
                null,
                null,
                null,
                null,
                0L,
                false,
                false,
                null,
                null,
                null,
                null,
                0L,
                false,
                false,
                false,
                "费用"
        );
    }
}

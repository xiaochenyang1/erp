package com.tuowei.erp.finance.expense;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.expense.mapper.ExpenseMapper;
import com.tuowei.erp.finance.expense.model.ExpenseEntity;
import com.tuowei.erp.finance.expense.service.ExpenseNumberService;
import com.tuowei.erp.finance.expense.service.ExpensePostingService;
import com.tuowei.erp.finance.expense.service.ExpenseQueryService;
import com.tuowei.erp.finance.expense.service.ExpenseService;
import com.tuowei.erp.finance.expense.web.ExpenseResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.subject.service.AccountSubjectService;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceApprovalCompletionTest {

    private static final Long EXPENSE_ID = 9101L;
    private static final Long WORKFLOW_TASK_ID = 9201L;
    private static final AuditMetadata AUDIT = new AuditMetadata(
            701L,
            801L,
            901L,
            LocalDateTime.parse("2026-08-20T14:30:00")
    );

    @Mock
    private ExpenseMapper expenseMapper;
    @Mock
    private ExpenseQueryService expenseQueryService;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;
    @Mock
    private WorkflowService workflowService;

    private ExpenseService service;
    private ExpenseEntity expense;

    @BeforeEach
    void setUp() {
        expense = pendingExpense();
        when(expenseQueryService.requireExpense(EXPENSE_ID)).thenReturn(expense);
        when(expenseQueryService.detail(EXPENSE_ID))
                .thenAnswer(invocation -> response(expense.getStatus()));
        service = new ExpenseService(
                expenseMapper,
                org.mockito.Mockito.mock(ExpenseNumberService.class),
                org.mockito.Mockito.mock(AccountSubjectService.class),
                auditMetadataFactory,
                expenseQueryService,
                org.mockito.Mockito.mock(ExpensePostingService.class),
                org.mockito.Mockito.mock(AccountPeriodGuard.class),
                org.mockito.Mockito.mock(AttachmentService.class),
                workflowService
        );
    }

    @Test
    void directApprovalKeepsExpensePendingWhenWorkflowIsNotComplete() {
        when(workflowService.approve("EXPENSE", EXPENSE_ID, "first approval")).thenReturn(false);

        ExpenseResponse actual = service.approve(EXPENSE_ID, "first approval");

        assertThat(actual.status()).isEqualTo("PENDING");
        assertThat(expense.getStatus()).isEqualTo("PENDING");
        verify(expenseMapper, never()).updateById(expense);
    }

    @Test
    void taskApprovalKeepsExpensePendingWhenWorkflowIsNotComplete() {
        when(workflowService.approveTaskForBusiness(
                WORKFLOW_TASK_ID,
                "EXPENSE",
                EXPENSE_ID,
                "first task approval"
        )).thenReturn(false);

        ExpenseResponse actual = service.approveWorkflowTask(
                WORKFLOW_TASK_ID,
                EXPENSE_ID,
                "first task approval"
        );

        assertThat(actual.status()).isEqualTo("PENDING");
        assertThat(expense.getStatus()).isEqualTo("PENDING");
        verify(expenseMapper, never()).updateById(expense);
    }

    @Test
    void directApprovalUpdatesExpenseAfterWorkflowCompletes() {
        when(workflowService.approve("EXPENSE", EXPENSE_ID, "final approval")).thenReturn(true);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(expenseMapper.updateById(expense)).thenReturn(1);

        ExpenseResponse actual = service.approve(EXPENSE_ID, "final approval");

        assertThat(actual.status()).isEqualTo("APPROVED");
        assertThat(expense.getStatus()).isEqualTo("APPROVED");
        assertThat(expense.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(expense.getUpdatedTime()).isEqualTo(AUDIT.now());
        InOrder order = inOrder(workflowService, expenseMapper);
        order.verify(workflowService).approve("EXPENSE", EXPENSE_ID, "final approval");
        order.verify(expenseMapper).updateById(expense);
    }

    @Test
    void taskApprovalUpdatesExpenseAfterWorkflowCompletes() {
        when(workflowService.approveTaskForBusiness(
                WORKFLOW_TASK_ID,
                "EXPENSE",
                EXPENSE_ID,
                "final task approval"
        )).thenReturn(true);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(expenseMapper.updateById(expense)).thenReturn(1);

        ExpenseResponse actual = service.approveWorkflowTask(
                WORKFLOW_TASK_ID,
                EXPENSE_ID,
                "final task approval"
        );

        assertThat(actual.status()).isEqualTo("APPROVED");
        assertThat(expense.getStatus()).isEqualTo("APPROVED");
        assertThat(expense.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(expense.getUpdatedTime()).isEqualTo(AUDIT.now());
        InOrder order = inOrder(workflowService, expenseMapper);
        order.verify(workflowService).approveTaskForBusiness(
                WORKFLOW_TASK_ID,
                "EXPENSE",
                EXPENSE_ID,
                "final task approval"
        );
        order.verify(expenseMapper).updateById(expense);
    }

    private ExpenseEntity pendingExpense() {
        ExpenseEntity entity = new ExpenseEntity();
        entity.setId(EXPENSE_ID);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setExpenseNo("EXP-9101");
        entity.setExpenseDate(LocalDate.of(2026, 8, 20));
        entity.setSubjectId(9301L);
        entity.setPaymentSubjectId(9302L);
        entity.setAmount(new BigDecimal("125.50"));
        entity.setStatus("PENDING");
        entity.setDeletedFlag(0);
        entity.setVersion(0);
        return entity;
    }

    private ExpenseResponse response(String status) {
        return new ExpenseResponse(
                EXPENSE_ID,
                expense.getExpenseNo(),
                expense.getExpenseDate(),
                expense.getSubjectId(),
                expense.getPaymentSubjectId(),
                expense.getAmount(),
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
                null
        );
    }
}

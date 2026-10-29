package com.tuowei.erp.finance.budget;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.budget.mapper.BudgetLineMapper;
import com.tuowei.erp.finance.budget.mapper.BudgetMapper;
import com.tuowei.erp.finance.budget.model.BudgetEntity;
import com.tuowei.erp.finance.budget.model.BudgetLineEntity;
import com.tuowei.erp.finance.budget.service.BudgetCommandService;
import com.tuowei.erp.finance.budget.service.BudgetQueryService;
import com.tuowei.erp.finance.budget.web.BudgetCreateRequest;
import com.tuowei.erp.finance.budget.web.BudgetLineRequest;
import com.tuowei.erp.finance.budget.web.BudgetResponse;
import com.tuowei.erp.finance.subject.model.AccountSubjectEntity;
import com.tuowei.erp.finance.subject.service.AccountSubjectService;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.dept.model.DeptEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(9L, 1L, 2L,
            LocalDateTime.of(2026, 8, 25, 10, 0));

    @Mock private BudgetMapper budgetMapper;
    @Mock private BudgetLineMapper budgetLineMapper;
    @Mock private BudgetQueryService budgetQueryService;
    @Mock private AccountSubjectService accountSubjectService;
    @Mock private DeptMapper deptMapper;
    @Mock private AuditMetadataFactory auditMetadataFactory;
    private BudgetCommandService service;

    @BeforeEach
    void setUp() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        service = new BudgetCommandService(budgetMapper, budgetLineMapper, budgetQueryService,
                accountSubjectService, deptMapper, auditMetadataFactory);
    }

    @Test
    void createPersistsTenantScopedHeaderAndLines() {
        when(accountSubjectService.requireActiveSubject(any(), any())).thenReturn(subject());
        when(deptMapper.selectById(31L)).thenReturn(dept(AUDIT.companyId(), AUDIT.accountBookId()));
        when(budgetMapper.insert(any(BudgetEntity.class))).thenAnswer(invocation -> {
            ((BudgetEntity) invocation.getArgument(0)).setId(100L);
            return 1;
        });
        when(budgetQueryService.detail(100L)).thenReturn(response("DRAFT"));

        BudgetResponse result = service.create(new BudgetCreateRequest(
                2026, "年度费用预算", "APPROVAL", "测试",
                List.of(new BudgetLineRequest(8, 31L, 6602L, new BigDecimal("100.00"), null))));

        ArgumentCaptor<BudgetEntity> header = ArgumentCaptor.forClass(BudgetEntity.class);
        verify(budgetMapper).insert(header.capture());
        assertThat(header.getValue().getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(header.getValue().getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(header.getValue().getStatus()).isEqualTo("DRAFT");
        ArgumentCaptor<BudgetLineEntity> line = ArgumentCaptor.forClass(BudgetLineEntity.class);
        verify(budgetLineMapper).insert(line.capture());
        assertThat(line.getValue().getBudgetId()).isEqualTo(100L);
        assertThat(line.getValue().getCommittedAmount()).isEqualByComparingTo("0");
        assertThat(result.status()).isEqualTo("DRAFT");
    }

    @Test
    void duplicateDimensionIsRejectedBeforeAnyWrite() {
        when(accountSubjectService.requireActiveSubject(any(), any())).thenReturn(subject());
        BudgetLineRequest line = new BudgetLineRequest(0, null, 6602L, new BigDecimal("100.00"), null);

        assertThatThrownBy(() -> service.create(new BudgetCreateRequest(
                2026, "重复预算", "REJECT", null, List.of(line, line))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");

        verify(budgetMapper, never()).insert(any(BudgetEntity.class));
        verify(budgetLineMapper, never()).insert(any(BudgetLineEntity.class));
    }

    @Test
    void departmentFromAnotherTenantIsRejected() {
        when(accountSubjectService.requireActiveSubject(any(), any())).thenReturn(subject());
        when(deptMapper.selectById(31L)).thenReturn(dept(99L, AUDIT.accountBookId()));

        assertThatThrownBy(() -> service.create(new BudgetCreateRequest(
                2026, "越权预算", "REJECT", null,
                List.of(new BudgetLineRequest(8, 31L, 6602L, new BigDecimal("100.00"), null)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("部门不存在");

        verify(budgetMapper, never()).insert(any(BudgetEntity.class));
    }

    @Test
    void onlyOneBudgetCanBeApprovedForTheSameTenantAndYear() {
        BudgetEntity submitted = budgetEntity("SUBMITTED");
        when(budgetQueryService.requireBudget(100L)).thenReturn(submitted);
        when(budgetMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.approve(100L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("同一年度只能有一个");

        verify(budgetMapper, never()).updateById(any(BudgetEntity.class));
    }

    @Test
    void submittedBudgetCanBeApprovedWhenYearHasNoActiveBudget() {
        BudgetEntity submitted = budgetEntity("SUBMITTED");
        when(budgetQueryService.requireBudget(100L)).thenReturn(submitted);
        when(budgetMapper.selectCount(any())).thenReturn(0L);
        when(budgetMapper.updateById(any(BudgetEntity.class))).thenReturn(1);
        when(budgetQueryService.detail(100L)).thenReturn(response("APPROVED"));

        BudgetResponse result = service.approve(100L);

        assertThat(submitted.getStatus()).isEqualTo("APPROVED");
        assertThat(result.status()).isEqualTo("APPROVED");
    }

    private BudgetEntity budgetEntity(String status) {
        BudgetEntity entity = new BudgetEntity();
        entity.setId(100L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setBudgetYear(2026);
        entity.setStatus(status);
        entity.setDeletedFlag(0);
        entity.setVersion(0);
        return entity;
    }

    private AccountSubjectEntity subject() {
        AccountSubjectEntity entity = new AccountSubjectEntity();
        entity.setId(6602L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private DeptEntity dept(Long companyId, Long accountBookId) {
        DeptEntity entity = new DeptEntity();
        entity.setId(31L);
        entity.setCompanyId(companyId);
        entity.setAccountBookId(accountBookId);
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        return entity;
    }

    private BudgetResponse response(String status) {
        return new BudgetResponse(100L, 2026, "预算", "REJECT", status,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, List.of());
    }
}

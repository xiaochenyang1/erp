package com.tuowei.erp.finance.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.budget.mapper.BudgetLineMapper;
import com.tuowei.erp.finance.budget.mapper.BudgetMapper;
import com.tuowei.erp.finance.budget.model.BudgetEntity;
import com.tuowei.erp.finance.budget.model.BudgetLineEntity;
import com.tuowei.erp.finance.budget.web.BudgetExecutionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Read side of budget execution: preview available balance and resolve the active budget/line reused by the write side. */
@Service
public class BudgetExecutionQueryService {

    private final BudgetMapper budgetMapper;
    private final BudgetLineMapper budgetLineMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public BudgetExecutionQueryService(BudgetMapper budgetMapper, BudgetLineMapper budgetLineMapper, AuditMetadataFactory auditMetadataFactory) {
        this.budgetMapper = budgetMapper;
        this.budgetLineMapper = budgetLineMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public BudgetExecutionResponse check(LocalDate expenseDate, Long deptId, Long subjectId, BigDecimal amount) {
        AuditMetadata audit = auditMetadataFactory.current();
        int year = expenseDate.getYear(); int month = expenseDate.getMonthValue();
        BudgetEntity budgetEntity = activeBudget(audit, year);
        BudgetLineEntity line = resolvedLine(audit, budgetEntity, expenseDate, deptId, subjectId);
        BigDecimal budget = line == null ? BigDecimal.ZERO : zero(line.getBudgetAmount());
        BigDecimal committed = line == null ? BigDecimal.ZERO : zero(line.getCommittedAmount());
        BigDecimal actual = line == null ? BigDecimal.ZERO : zero(line.getActualAmount());
        BigDecimal available = budget.subtract(committed).subtract(actual);
        BigDecimal requested = zero(amount);
        BigDecimal projected = available.subtract(requested);
        boolean overrun = budgetEntity != null && (line == null || projected.signum() < 0);
        String source = line == null ? "NONE" : line.getPeriodMonth() == 0 ? "ANNUAL" : "MONTHLY";
        return new BudgetExecutionResponse(year, month, deptId, subjectId, budget, committed, actual, available,
                budgetEntity == null ? null : budgetEntity.getId(), line == null ? null : line.getId(),
                budgetEntity == null ? null : budgetEntity.getControlPolicy(), source, requested, projected, overrun);
    }

    BudgetEntity activeBudget(AuditMetadata audit, int year) {
        return budgetMapper.selectOne(new LambdaQueryWrapper<BudgetEntity>()
                .eq(BudgetEntity::getCompanyId, audit.companyId()).eq(BudgetEntity::getAccountBookId, audit.accountBookId())
                .eq(BudgetEntity::getBudgetYear, year).eq(BudgetEntity::getStatus, "APPROVED").eq(BudgetEntity::getDeletedFlag, 0)
                .orderByDesc(BudgetEntity::getId).last("limit 1"));
    }

    BudgetLineEntity resolvedLine(AuditMetadata audit, BudgetEntity budget, LocalDate date, Long deptId, Long subjectId) {
        if (budget == null) return null;
        BudgetLineEntity line = findLine(audit, budget.getId(), date.getMonthValue(), deptId, subjectId);
        return line == null ? findLine(audit, budget.getId(), 0, deptId, subjectId) : line;
    }

    BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : ScalePrecision.amount(value); }

    private BudgetLineEntity findLine(AuditMetadata audit, Long budgetId, int month, Long deptId, Long subjectId) {
        return budgetLineMapper.selectOne(new LambdaQueryWrapper<BudgetLineEntity>()
                .eq(BudgetLineEntity::getCompanyId, audit.companyId()).eq(BudgetLineEntity::getAccountBookId, audit.accountBookId())
                .eq(BudgetLineEntity::getBudgetId, budgetId).eq(BudgetLineEntity::getPeriodMonth, month)
                .eq(BudgetLineEntity::getSubjectId, subjectId)
                .eq(deptId != null, BudgetLineEntity::getDeptId, deptId)
                .isNull(deptId == null, BudgetLineEntity::getDeptId));
    }
}

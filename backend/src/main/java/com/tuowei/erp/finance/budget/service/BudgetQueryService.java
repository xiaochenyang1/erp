package com.tuowei.erp.finance.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.budget.mapper.BudgetLineMapper;
import com.tuowei.erp.finance.budget.mapper.BudgetMapper;
import com.tuowei.erp.finance.budget.model.BudgetEntity;
import com.tuowei.erp.finance.budget.model.BudgetLineEntity;
import com.tuowei.erp.finance.budget.web.BudgetLineResponse;
import com.tuowei.erp.finance.budget.web.BudgetPageQuery;
import com.tuowei.erp.finance.budget.web.BudgetResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class BudgetQueryService {
    private final BudgetMapper budgetMapper;
    private final BudgetLineMapper budgetLineMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public BudgetQueryService(BudgetMapper budgetMapper, BudgetLineMapper budgetLineMapper, AuditMetadataFactory auditMetadataFactory) {
        this.budgetMapper = budgetMapper;
        this.budgetLineMapper = budgetLineMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<BudgetResponse> list(BudgetPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        BudgetPageQuery safe = query == null ? new BudgetPageQuery() : query;
        Page<BudgetEntity> page = budgetMapper.selectPage(new Page<>(pageNo(safe.getPageNo()), pageSize(safe.getPageSize())),
                wrapper(audit, safe));
        return new PageResponse<>(page.getCurrent(), page.getSize(), page.getTotal(), page.getRecords().stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public BudgetResponse detail(Long id) {
        return toResponse(requireBudget(id));
    }

    public BudgetEntity requireBudget(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        BudgetEntity entity = budgetMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !audit.companyId().equals(entity.getCompanyId()) || !audit.accountBookId().equals(entity.getAccountBookId())) {
            throw new IllegalArgumentException("预算不存在");
        }
        return entity;
    }

    public List<BudgetLineEntity> lines(Long budgetId, Long companyId, Long accountBookId) {
        return budgetLineMapper.selectList(new LambdaQueryWrapper<BudgetLineEntity>()
                .eq(BudgetLineEntity::getBudgetId, budgetId)
                .eq(BudgetLineEntity::getCompanyId, companyId)
                .eq(BudgetLineEntity::getAccountBookId, accountBookId)
                .orderByAsc(BudgetLineEntity::getPeriodMonth)
                .orderByAsc(BudgetLineEntity::getDeptId)
                .orderByAsc(BudgetLineEntity::getSubjectId)
                .orderByAsc(BudgetLineEntity::getId));
    }

    BudgetResponse toResponse(BudgetEntity entity) {
        List<BudgetLineResponse> lines = lines(entity.getId(), entity.getCompanyId(), entity.getAccountBookId()).stream()
                .map(line -> new BudgetLineResponse(
                        line.getId(), line.getPeriodMonth(), line.getDeptId(), line.getSubjectId(),
                        line.getBudgetAmount(), line.getCommittedAmount(), line.getActualAmount(),
                        available(line), line.getRemark()))
                .toList();
        BigDecimal budget = lines.stream().map(BudgetLineResponse::budgetAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal committed = lines.stream().map(BudgetLineResponse::committedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actual = lines.stream().map(BudgetLineResponse::actualAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BudgetResponse(entity.getId(), entity.getBudgetYear(), entity.getBudgetName(), entity.getControlPolicy(), entity.getStatus(),
                budget, committed, actual, budget.subtract(committed).subtract(actual), entity.getRemark(), lines);
    }

    private LambdaQueryWrapper<BudgetEntity> wrapper(AuditMetadata audit, BudgetPageQuery query) {
        LambdaQueryWrapper<BudgetEntity> wrapper = new LambdaQueryWrapper<BudgetEntity>()
                .eq(BudgetEntity::getCompanyId, audit.companyId())
                .eq(BudgetEntity::getAccountBookId, audit.accountBookId())
                .eq(BudgetEntity::getDeletedFlag, 0)
                .orderByDesc(BudgetEntity::getBudgetYear)
                .orderByDesc(BudgetEntity::getId);
        if (query.getBudgetYear() != null) wrapper.eq(BudgetEntity::getBudgetYear, query.getBudgetYear());
        if (StringUtils.hasText(query.getStatus())) wrapper.eq(BudgetEntity::getStatus, query.getStatus().trim().toUpperCase(Locale.ROOT));
        if (StringUtils.hasText(query.getKeyword())) wrapper.like(BudgetEntity::getBudgetName, query.getKeyword().trim());
        return wrapper;
    }

    private BigDecimal available(BudgetLineEntity line) {
        return zero(line.getBudgetAmount()).subtract(zero(line.getCommittedAmount())).subtract(zero(line.getActualAmount()));
    }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private long pageNo(Integer value) { return value == null || value < 1 ? 1 : value; }
    private long pageSize(Integer value) { return value == null || value < 1 ? 20 : Math.min(value, 200); }
}

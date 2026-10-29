package com.tuowei.erp.finance.budget.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.budget.mapper.BudgetLineMapper;
import com.tuowei.erp.finance.budget.mapper.BudgetMapper;
import com.tuowei.erp.finance.budget.model.BudgetEntity;
import com.tuowei.erp.finance.budget.model.BudgetLineEntity;
import com.tuowei.erp.finance.budget.web.BudgetCreateRequest;
import com.tuowei.erp.finance.budget.web.BudgetLineRequest;
import com.tuowei.erp.finance.budget.web.BudgetResponse;
import com.tuowei.erp.finance.budget.web.BudgetUpdateRequest;
import com.tuowei.erp.finance.subject.service.AccountSubjectService;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class BudgetCommandService {
    private final BudgetMapper budgetMapper;
    private final BudgetLineMapper budgetLineMapper;
    private final BudgetQueryService budgetQueryService;
    private final AccountSubjectService accountSubjectService;
    private final DeptMapper deptMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public BudgetCommandService(BudgetMapper budgetMapper, BudgetLineMapper budgetLineMapper, BudgetQueryService budgetQueryService,
                                AccountSubjectService accountSubjectService, DeptMapper deptMapper, AuditMetadataFactory auditMetadataFactory) {
        this.budgetMapper = budgetMapper;
        this.budgetLineMapper = budgetLineMapper;
        this.budgetQueryService = budgetQueryService;
        this.accountSubjectService = accountSubjectService;
        this.deptMapper = deptMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional
    public BudgetResponse create(BudgetCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        validateLines(request.lines(), audit);
        BudgetEntity entity = new BudgetEntity();
        entity.setCompanyId(audit.companyId()); entity.setAccountBookId(audit.accountBookId());
        entity.setBudgetYear(request.budgetYear()); entity.setBudgetName(request.budgetName().trim());
        entity.setControlPolicy(policy(request.controlPolicy())); entity.setStatus("DRAFT"); entity.setRemark(request.remark());
        setAudit(entity, audit, audit.now()); budgetMapper.insert(entity);
        saveLines(entity, request.lines(), audit, audit.now());
        return budgetQueryService.detail(entity.getId());
    }

    @Transactional
    public BudgetResponse update(Long id, BudgetUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        BudgetEntity entity = budgetQueryService.requireBudget(id);
        if (!Set.of("DRAFT", "SUBMITTED").contains(entity.getStatus())) throw new IllegalArgumentException("当前预算状态不允许编辑");
        validateLines(request.lines(), audit);
        entity.setBudgetName(request.budgetName().trim()); entity.setControlPolicy(policy(request.controlPolicy())); entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(budgetMapper.updateById(entity), "预算已被其他操作修改，请刷新后重试");
        budgetLineMapper.delete(new LambdaQueryWrapper<BudgetLineEntity>().eq(BudgetLineEntity::getBudgetId, id)
                .eq(BudgetLineEntity::getCompanyId, audit.companyId()).eq(BudgetLineEntity::getAccountBookId, audit.accountBookId()));
        saveLines(entity, request.lines(), audit, audit.now());
        return budgetQueryService.detail(id);
    }

    @Transactional
    public BudgetResponse submit(Long id) { return transition(id, "SUBMITTED", Set.of("DRAFT")); }
    @Transactional
    public BudgetResponse approve(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        BudgetEntity entity = budgetQueryService.requireBudget(id);
        if (!"SUBMITTED".equals(entity.getStatus())) throw new IllegalArgumentException("当前预算状态不允许该操作");
        long approvedCount = budgetMapper.selectCount(new LambdaQueryWrapper<BudgetEntity>()
                .eq(BudgetEntity::getCompanyId, audit.companyId())
                .eq(BudgetEntity::getAccountBookId, audit.accountBookId())
                .eq(BudgetEntity::getBudgetYear, entity.getBudgetYear())
                .eq(BudgetEntity::getStatus, "APPROVED")
                .eq(BudgetEntity::getDeletedFlag, 0)
                .ne(BudgetEntity::getId, entity.getId()));
        if (approvedCount > 0) throw new IllegalArgumentException("同一年度只能有一个已审批预算，请先关闭现有预算");
        return transition(entity, audit, "APPROVED");
    }
    @Transactional
    public BudgetResponse close(Long id) { return transition(id, "CLOSED", Set.of("APPROVED")); }
    @Transactional
    public BudgetResponse cancel(Long id) { return transition(id, "CANCELLED", Set.of("DRAFT", "SUBMITTED")); }

    private BudgetResponse transition(Long id, String target, Set<String> allowed) {
        AuditMetadata audit = auditMetadataFactory.current(); BudgetEntity entity = budgetQueryService.requireBudget(id);
        if (!allowed.contains(entity.getStatus())) throw new IllegalArgumentException("当前预算状态不允许该操作");
        return transition(entity, audit, target);
    }

    private BudgetResponse transition(BudgetEntity entity, AuditMetadata audit, String target) {
        entity.setStatus(target); entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(budgetMapper.updateById(entity), "预算已被其他操作修改，请刷新后重试");
        return budgetQueryService.detail(entity.getId());
    }

    private void saveLines(BudgetEntity budget, List<BudgetLineRequest> requests, AuditMetadata audit, LocalDateTime now) {
        for (BudgetLineRequest request : requests) {
            BudgetLineEntity line = new BudgetLineEntity();
            line.setCompanyId(audit.companyId()); line.setAccountBookId(audit.accountBookId()); line.setBudgetId(budget.getId());
            line.setPeriodMonth(request.periodMonth()); line.setDeptId(request.deptId()); line.setSubjectId(request.subjectId());
            line.setBudgetAmount(ScalePrecision.amount(request.budgetAmount())); line.setCommittedAmount(BigDecimal.ZERO); line.setActualAmount(BigDecimal.ZERO);
            line.setRemark(request.remark()); line.setCreatedBy(audit.userId()); line.setCreatedTime(now); line.setUpdatedBy(audit.userId()); line.setUpdatedTime(now); line.setVersion(0);
            budgetLineMapper.insert(line);
        }
    }

    private void validateLines(List<BudgetLineRequest> lines, AuditMetadata audit) {
        Set<String> keys = new HashSet<>();
        for (BudgetLineRequest line : lines) {
            accountSubjectService.requireActiveSubject(line.subjectId(), "预算科目不存在或已停用");
            if (line.deptId() != null) {
                var dept = deptMapper.selectById(line.deptId());
                if (dept == null || dept.getDeletedFlag() == null || dept.getDeletedFlag() != 0
                        || !audit.companyId().equals(dept.getCompanyId()) || !audit.accountBookId().equals(dept.getAccountBookId())) {
                    throw new IllegalArgumentException("预算部门不存在");
                }
            }
            String key = line.periodMonth() + ":" + line.deptId() + ":" + line.subjectId();
            if (!keys.add(key)) throw new IllegalArgumentException("预算明细存在重复的期间、部门、科目组合");
        }
    }
    private String policy(String value) { return "APPROVAL".equalsIgnoreCase(value) ? "APPROVAL" : "REJECT"; }
    private void setAudit(BudgetEntity entity, AuditMetadata audit, LocalDateTime now) { entity.setCreatedBy(audit.userId()); entity.setCreatedTime(now); entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(now); entity.setVersion(0); entity.setDeletedFlag(0); }
}

package com.tuowei.erp.finance.budget.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.budget.web.BudgetCreateRequest;
import com.tuowei.erp.finance.budget.web.BudgetExecutionQuery;
import com.tuowei.erp.finance.budget.web.BudgetExecutionResponse;
import com.tuowei.erp.finance.budget.web.BudgetPageQuery;
import com.tuowei.erp.finance.budget.web.BudgetResponse;
import com.tuowei.erp.finance.budget.web.BudgetUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {
    private final BudgetQueryService queryService;
    private final BudgetCommandService commandService;
    private final BudgetExecutionService executionService;

    public BudgetService(BudgetQueryService queryService, BudgetCommandService commandService, BudgetExecutionService executionService) {
        this.queryService = queryService; this.commandService = commandService; this.executionService = executionService;
    }
    @Transactional(readOnly = true) public PageResponse<BudgetResponse> list(BudgetPageQuery query) {
        BudgetPageQuery effectiveQuery = query == null ? new BudgetPageQuery() : query;
        return queryService.list(effectiveQuery);
    }
    @Transactional(readOnly = true) public BudgetResponse detail(Long id) { return queryService.detail(id); }
    @Transactional public BudgetResponse create(BudgetCreateRequest request) { return commandService.create(request); }
    @Transactional public BudgetResponse update(Long id, BudgetUpdateRequest request) { return commandService.update(id, request); }
    @Transactional public BudgetResponse submit(Long id) { return commandService.submit(id); }
    @Transactional public BudgetResponse approve(Long id) { return commandService.approve(id); }
    @Transactional public BudgetResponse close(Long id) { return commandService.close(id); }
    @Transactional public BudgetResponse cancel(Long id) { return commandService.cancel(id); }
    @Transactional(readOnly = true) public BudgetExecutionResponse execution(BudgetExecutionQuery query) {
        if (query == null || query.getBudgetYear() == null || query.getPeriodMonth() == null || query.getSubjectId() == null) {
            throw new IllegalArgumentException("预算执行查询必须提供年度、月份和科目");
        }
        if (query.getBudgetYear() < 2000 || query.getBudgetYear() > 2100
                || query.getPeriodMonth() < 1 || query.getPeriodMonth() > 12) {
            throw new IllegalArgumentException("预算执行查询的年度或月份不合法");
        }
        if (query.getAmount() != null && query.getAmount().signum() < 0) {
            throw new IllegalArgumentException("预算执行查询金额不能小于0");
        }
        java.time.LocalDate date = java.time.LocalDate.of(query.getBudgetYear(), query.getPeriodMonth(), 1);
        return executionService.check(date, query.getDeptId(), query.getSubjectId(), query.getAmount());
    }
}

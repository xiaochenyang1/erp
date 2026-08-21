package com.tuowei.erp.system.dept.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.dept.web.DeptCreateRequest;
import com.tuowei.erp.system.dept.web.DeptPageQuery;
import com.tuowei.erp.system.dept.web.DeptResponse;
import com.tuowei.erp.system.dept.web.DeptUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Compatibility facade for department queries and commands. */
@Service
public class DeptService {

    private final DeptQueryService deptQueryService;
    private final DeptCommandService deptCommandService;

    public DeptService(DeptQueryService deptQueryService, DeptCommandService deptCommandService) {
        this.deptQueryService = deptQueryService;
        this.deptCommandService = deptCommandService;
    }

    @Transactional
    public DeptResponse create(DeptCreateRequest request) {
        return deptCommandService.create(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<DeptResponse> list(DeptPageQuery query) {
        return deptQueryService.list(query == null ? new DeptPageQuery() : query);
    }

    @Transactional(readOnly = true)
    public List<DeptResponse> tree() {
        return deptQueryService.tree();
    }

    @Transactional(readOnly = true)
    public DeptResponse getById(Long id) {
        return deptQueryService.getById(id);
    }

    @Transactional
    public DeptResponse update(Long id, DeptUpdateRequest request) {
        return deptCommandService.update(id, request);
    }

    @Transactional
    public DeptResponse enable(Long id) {
        return deptCommandService.enable(id);
    }

    @Transactional
    public DeptResponse disable(Long id) {
        return deptCommandService.disable(id);
    }
}

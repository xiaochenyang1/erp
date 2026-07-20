package com.tuowei.erp.system.dept.controller;

import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.system.dept.service.DeptService;
import com.tuowei.erp.system.dept.web.DeptCreateRequest;
import com.tuowei.erp.system.dept.web.DeptPageQuery;
import com.tuowei.erp.system.dept.web.DeptResponse;
import com.tuowei.erp.system.dept.web.DeptUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/depts")
public class DeptController {

    private final DeptService deptService;

    public DeptController(DeptService deptService) {
        this.deptService = deptService;
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_DEPT_CREATE)
    @PostMapping
    public ApiResponse<DeptResponse> create(@Valid @RequestBody DeptCreateRequest request) {
        return ApiResponse.success(deptService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_DEPT_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<DeptResponse>> list(DeptPageQuery query) {
        return ApiResponse.success(deptService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_DEPT_VIEW)
    @GetMapping("/tree")
    public ApiResponse<List<DeptResponse>> tree() {
        return ApiResponse.success(deptService.tree());
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_DEPT_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<DeptResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(deptService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_DEPT_UPDATE)
    @PutMapping("/{id}")
    public ApiResponse<DeptResponse> update(@PathVariable Long id, @Valid @RequestBody DeptUpdateRequest request) {
        return ApiResponse.success(deptService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_DEPT_ENABLE)
    @PostMapping("/{id}/enable")
    public ApiResponse<DeptResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(deptService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_DEPT_DISABLE)
    @PostMapping("/{id}/disable")
    public ApiResponse<DeptResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(deptService.disable(id));
    }
}

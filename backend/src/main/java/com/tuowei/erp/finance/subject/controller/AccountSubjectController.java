package com.tuowei.erp.finance.subject.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.subject.service.AccountSubjectService;
import com.tuowei.erp.finance.subject.web.AccountSubjectCreateRequest;
import com.tuowei.erp.finance.subject.web.AccountSubjectPageQuery;
import com.tuowei.erp.finance.subject.web.AccountSubjectResponse;
import com.tuowei.erp.finance.subject.web.AccountSubjectTreeNode;
import com.tuowei.erp.finance.subject.web.AccountSubjectUpdateRequest;
import com.tuowei.erp.system.log.annotation.OperationLog;
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
@RequestMapping("/api/finance/account-subjects")
public class AccountSubjectController {

    private final AccountSubjectService accountSubjectService;

    public AccountSubjectController(AccountSubjectService accountSubjectService) {
        this.accountSubjectService = accountSubjectService;
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_SUBJECT_MANAGE)
    @PostMapping
    @OperationLog(module = "finance", operation = "create-account-subject", message = "创建会计科目", bizNo = "#result.data.subjectCode")
    public ApiResponse<AccountSubjectResponse> create(@Valid @RequestBody AccountSubjectCreateRequest request) {
        return ApiResponse.success(accountSubjectService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_SUBJECT_MANAGE)
    @GetMapping
    public ApiResponse<PageResponse<AccountSubjectResponse>> list(AccountSubjectPageQuery query) {
        return ApiResponse.success(accountSubjectService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_SUBJECT_MANAGE)
    @GetMapping("/tree")
    public ApiResponse<List<AccountSubjectTreeNode>> tree() {
        return ApiResponse.success(accountSubjectService.tree());
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_SUBJECT_MANAGE)
    @GetMapping("/{id}")
    public ApiResponse<AccountSubjectResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(accountSubjectService.detail(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_SUBJECT_MANAGE)
    @PutMapping("/{id}")
    @OperationLog(module = "finance", operation = "update-account-subject", message = "更新会计科目", bizNo = "#id")
    public ApiResponse<AccountSubjectResponse> update(@PathVariable Long id, @Valid @RequestBody AccountSubjectUpdateRequest request) {
        return ApiResponse.success(accountSubjectService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_SUBJECT_MANAGE)
    @PostMapping("/{id}/enable")
    @OperationLog(module = "finance", operation = "enable-account-subject", message = "启用会计科目", bizNo = "#id")
    public ApiResponse<AccountSubjectResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(accountSubjectService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_FINANCE_SUBJECT_MANAGE)
    @PostMapping("/{id}/disable")
    @OperationLog(module = "finance", operation = "disable-account-subject", message = "停用会计科目", bizNo = "#id")
    public ApiResponse<AccountSubjectResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(accountSubjectService.disable(id));
    }
}

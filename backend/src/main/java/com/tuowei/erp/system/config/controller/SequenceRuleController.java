package com.tuowei.erp.system.config.controller;

import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.system.config.service.SequenceRuleService;
import com.tuowei.erp.system.config.web.SequenceRuleCreateRequest;
import com.tuowei.erp.system.config.web.SequenceRulePageQuery;
import com.tuowei.erp.system.config.web.SequenceRuleResponse;
import com.tuowei.erp.system.config.web.SequenceRuleUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/sequence-rules")
public class SequenceRuleController {

    private final SequenceRuleService sequenceRuleService;

    public SequenceRuleController(SequenceRuleService sequenceRuleService) {
        this.sequenceRuleService = sequenceRuleService;
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_SEQUENCE_RULE_CREATE)
    @PostMapping
    public ApiResponse<SequenceRuleResponse> create(@Valid @RequestBody SequenceRuleCreateRequest request) {
        return ApiResponse.success(sequenceRuleService.create(request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_SEQUENCE_RULE_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<SequenceRuleResponse>> list(SequenceRulePageQuery query) {
        return ApiResponse.success(sequenceRuleService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_SEQUENCE_RULE_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<SequenceRuleResponse> detail(@PathVariable Long id) {
        return ApiResponse.success(sequenceRuleService.getById(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_SEQUENCE_RULE_UPDATE)
    @PutMapping("/{id}")
    public ApiResponse<SequenceRuleResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody SequenceRuleUpdateRequest request
    ) {
        return ApiResponse.success(sequenceRuleService.update(id, request));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_SEQUENCE_RULE_ENABLE)
    @PostMapping("/{id}/enable")
    public ApiResponse<SequenceRuleResponse> enable(@PathVariable Long id) {
        return ApiResponse.success(sequenceRuleService.enable(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_SEQUENCE_RULE_DISABLE)
    @PostMapping("/{id}/disable")
    public ApiResponse<SequenceRuleResponse> disable(@PathVariable Long id) {
        return ApiResponse.success(sequenceRuleService.disable(id));
    }
}

package com.tuowei.erp.common.document;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/document-state-rules")
public class DocumentStateRuleController {

    private final DocumentStateRuleService documentStateRuleService;

    public DocumentStateRuleController(DocumentStateRuleService documentStateRuleService) {
        this.documentStateRuleService = documentStateRuleService;
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_CONFIG_VIEW)
    @GetMapping
    public ApiResponse<List<DocumentStateRuleResponse>> list() {
        return ApiResponse.success(documentStateRuleService.list());
    }
}

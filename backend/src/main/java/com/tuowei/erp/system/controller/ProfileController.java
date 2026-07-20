package com.tuowei.erp.system.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class ProfileController {

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_PROFILE_VIEW)
    @GetMapping("/profile")
    public ApiResponse<Map<String, String>> profile() {
        return ApiResponse.success(Map.of("scope", "protected"));
    }
}

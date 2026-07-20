package com.tuowei.erp.system.log.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.system.log.web.AuditLogPageQuery;
import com.tuowei.erp.system.log.web.AuditLogResponse;
import com.tuowei.erp.system.log.web.LoginLogPageQuery;
import com.tuowei.erp.system.log.web.LoginLogResponse;
import com.tuowei.erp.system.log.web.OperationLogPageQuery;
import com.tuowei.erp.system.log.web.OperationLogResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@RestController
public class SystemLogController {

    private final SystemLogService systemLogService;

    public SystemLogController(SystemLogService systemLogService) {
        this.systemLogService = systemLogService;
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_LOG_VIEW)
    @GetMapping("/api/system/login-logs")
    public ApiResponse<PageResponse<LoginLogResponse>> listLoginLogs(LoginLogPageQuery query) {
        return ApiResponse.success(systemLogService.listLoginLogs(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_LOG_VIEW)
    @GetMapping("/api/system/operation-logs")
    public ApiResponse<PageResponse<OperationLogResponse>> listOperationLogs(OperationLogPageQuery query) {
        return ApiResponse.success(systemLogService.listOperationLogs(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_LOG_VIEW)
    @GetMapping("/api/system/operation-logs/{id}")
    public ApiResponse<OperationLogResponse> getOperationLog(@PathVariable Long id) {
        return ApiResponse.success(systemLogService.getOperationLog(id));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_LOG_VIEW)
    @GetMapping("/api/system/operation-logs/export")
    public ResponseEntity<StreamingResponseBody> exportOperationLogs(OperationLogPageQuery query) {
        return csv("operation-logs.csv", systemLogService.exportOperationLogs(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_LOG_VIEW)
    @GetMapping("/api/system/audit-logs")
    public ApiResponse<PageResponse<AuditLogResponse>> listAuditLogs(AuditLogPageQuery query) {
        return ApiResponse.success(systemLogService.listAuditLogs(query));
    }

    private ResponseEntity<StreamingResponseBody> csv(String filename, StreamingResponseBody body) {
        String safeFilename = SafeFilename.normalize(filename, "operation-logs.csv", 255);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(safeFilename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }
}

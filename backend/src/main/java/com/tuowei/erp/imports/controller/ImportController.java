package com.tuowei.erp.imports.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.imports.service.ImportJobService;
import com.tuowei.erp.imports.web.ImportJobPageQuery;
import com.tuowei.erp.imports.web.ImportJobResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final ImportJobService importJobService;

    public ImportController(ImportJobService importJobService) {
        this.importJobService = importJobService;
    }

    @PreAuthorize(PermissionCodes.HAS_IMPORT_INIT_MANAGE)
    @GetMapping(value = "/templates/{type}", produces = "text/csv")
    public ResponseEntity<ByteArrayResource> template(@PathVariable String type) {
        return importJobService.template(type);
    }

    @PreAuthorize(PermissionCodes.HAS_IMPORT_INIT_MANAGE)
    @PostMapping(value = "/jobs/{type}/preview", consumes = "multipart/form-data")
    public ApiResponse<ImportJobResponse> preview(@PathVariable String type, @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(importJobService.preview(type, file));
    }

    @PreAuthorize(PermissionCodes.HAS_IMPORT_INIT_MANAGE)
    @GetMapping("/jobs")
    public ApiResponse<PageResponse<ImportJobResponse>> list(ImportJobPageQuery query) {
        return ApiResponse.success(importJobService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_IMPORT_INIT_MANAGE)
    @GetMapping("/jobs/{jobId}")
    public ApiResponse<ImportJobResponse> detail(@PathVariable Long jobId) {
        return ApiResponse.success(importJobService.detail(jobId));
    }

    @PreAuthorize(PermissionCodes.HAS_IMPORT_INIT_MANAGE)
    @GetMapping(value = "/jobs/{jobId}/error-rows/export", produces = "text/csv")
    public ResponseEntity<ByteArrayResource> exportErrorRows(@PathVariable Long jobId) {
        return importJobService.exportErrorRows(jobId);
    }

    @PreAuthorize(PermissionCodes.HAS_IMPORT_INIT_MANAGE)
    @PostMapping("/jobs/{jobId}/commit")
    public ApiResponse<ImportJobResponse> commit(@PathVariable Long jobId) {
        return ApiResponse.success(importJobService.commit(jobId));
    }
}

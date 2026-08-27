package com.tuowei.erp.commercial.contract.controller;

import com.tuowei.erp.commercial.contract.service.ContractExportService;
import com.tuowei.erp.commercial.contract.service.ContractService;
import com.tuowei.erp.commercial.contract.service.ContractAlertQueryService;
import com.tuowei.erp.commercial.contract.service.ContractAttachmentService;
import com.tuowei.erp.commercial.contract.web.ContractAlertResponse;
import com.tuowei.erp.commercial.contract.web.ContractPageQuery;
import com.tuowei.erp.commercial.contract.web.ContractResponse;
import com.tuowei.erp.commercial.contract.web.ContractSaveRequest;
import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.system.log.annotation.OperationLog;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;
import com.tuowei.erp.system.attachment.web.AttachmentResponse;
import com.tuowei.erp.commercial.contract.web.ContractVersionResponse;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {
    private final ContractService contractService;
    private final ContractExportService exportService;
    private final ContractAlertQueryService alertQueryService;
    private final ContractAttachmentService attachmentService;

    public ContractController(ContractService contractService, ContractExportService exportService,
                              ContractAlertQueryService alertQueryService, ContractAttachmentService attachmentService) {
        this.contractService = contractService;
        this.exportService = exportService;
        this.alertQueryService = alertQueryService;
        this.attachmentService = attachmentService;
    }

    @PreAuthorize(PermissionCodes.HAS_CONTRACT_VIEW)
    @GetMapping("/alerts")
    public ApiResponse<List<ContractAlertResponse>> alerts(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "30") int expirationWarningDays,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0.5") BigDecimal lowExecutionRate) {
        return ApiResponse.success(alertQueryService.list(Math.max(0, expirationWarningDays), lowExecutionRate.max(BigDecimal.ZERO).min(BigDecimal.ONE)));
    }

    @PreAuthorize(PermissionCodes.HAS_CONTRACT_VIEW)
    @GetMapping("/{id}/versions")
    public ApiResponse<List<ContractVersionResponse>> versions(@PathVariable Long id) {
        return ApiResponse.success(contractService.versions(id));
    }

    @PreAuthorize(PermissionCodes.HAS_CONTRACT_VIEW)
    @GetMapping("/{id}/versions/{versionId}")
    public ApiResponse<ContractVersionResponse> version(@PathVariable Long id, @PathVariable Long versionId) {
        return ApiResponse.success(contractService.version(id, versionId));
    }

    @PreAuthorize(PermissionCodes.HAS_CONTRACT_MANAGE)
    @PostMapping("/{id}/versions/{versionId}/restore")
    public ApiResponse<ContractResponse> restoreVersion(@PathVariable Long id, @PathVariable Long versionId) {
        return ApiResponse.success(contractService.restoreVersion(id, versionId));
    }

    @PreAuthorize(PermissionCodes.HAS_CONTRACT_VIEW)
    @GetMapping("/{id}/attachments")
    public ApiResponse<PageResponse<AttachmentResponse>> attachments(@PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer pageNo, @RequestParam(defaultValue = "50") Integer pageSize) {
        return ApiResponse.success(attachmentService.list(id, pageNo, pageSize));
    }

    @PreAuthorize(PermissionCodes.HAS_CONTRACT_MANAGE)
    @PostMapping(value = "/{id}/attachments", consumes = "multipart/form-data")
    public ApiResponse<AttachmentResponse> uploadAttachment(@PathVariable Long id, @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(attachmentService.upload(id, file));
    }

    @PreAuthorize(PermissionCodes.HAS_CONTRACT_VIEW)
    @GetMapping("/{id}/attachments/{attachmentId}/download")
    public ResponseEntity<Resource> downloadAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        return attachmentService.download(id, attachmentId);
    }

    @PreAuthorize(PermissionCodes.HAS_CONTRACT_MANAGE)
    @DeleteMapping("/{id}/attachments/{attachmentId}")
    public ApiResponse<Void> deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        attachmentService.delete(id, attachmentId);
        return ApiResponse.success(null);
    }

    @PreAuthorize(PermissionCodes.HAS_CONTRACT_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<ContractResponse>> list(ContractPageQuery query) { return ApiResponse.success(contractService.list(query)); }

    @PreAuthorize(PermissionCodes.HAS_CONTRACT_VIEW)
    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(ContractPageQuery query) {
        String filename = SafeFilename.normalize("contracts.csv", "contracts.csv", 255);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8")).body(exportService.export(query));
    }

    @PreAuthorize(PermissionCodes.HAS_CONTRACT_VIEW)
    @GetMapping("/{id}")
    public ApiResponse<ContractResponse> detail(@PathVariable Long id) { return ApiResponse.success(contractService.detail(id)); }

    @PreAuthorize(PermissionCodes.HAS_CONTRACT_MANAGE)
    @PostMapping
    @OperationLog(module = "contract", operation = "create", bizNo = "#result.data.contractNo")
    public ApiResponse<ContractResponse> create(@Valid @RequestBody ContractSaveRequest request) { return ApiResponse.success(contractService.create(request)); }

    @PreAuthorize(PermissionCodes.HAS_CONTRACT_MANAGE)
    @PutMapping("/{id}")
    public ApiResponse<ContractResponse> update(@PathVariable Long id, @Valid @RequestBody ContractSaveRequest request) { return ApiResponse.success(contractService.update(id, request)); }

    @PreAuthorize(PermissionCodes.HAS_CONTRACT_MANAGE)
    @PostMapping("/{id}/submit")
    public ApiResponse<ContractResponse> submit(@PathVariable Long id) { return ApiResponse.success(contractService.submit(id)); }

    @PreAuthorize(PermissionCodes.HAS_CONTRACT_APPROVE)
    @PostMapping("/{id}/approve")
    public ApiResponse<ContractResponse> approve(@PathVariable Long id) { return ApiResponse.success(contractService.approve(id)); }

    @PreAuthorize(PermissionCodes.HAS_CONTRACT_APPROVE)
    @PostMapping("/{id}/reject")
    public ApiResponse<ContractResponse> reject(@PathVariable Long id) { return ApiResponse.success(contractService.reject(id)); }

    @PreAuthorize(PermissionCodes.HAS_CONTRACT_APPROVE)
    @PostMapping("/{id}/close")
    public ApiResponse<ContractResponse> close(@PathVariable Long id) { return ApiResponse.success(contractService.close(id)); }

    @PreAuthorize(PermissionCodes.HAS_CONTRACT_MANAGE)
    @PostMapping("/{id}/cancel")
    public ApiResponse<ContractResponse> cancel(@PathVariable Long id) { return ApiResponse.success(contractService.cancel(id)); }
}

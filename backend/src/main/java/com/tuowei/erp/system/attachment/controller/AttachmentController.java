package com.tuowei.erp.system.attachment.controller;

import com.tuowei.erp.common.security.PermissionCodes;
import com.tuowei.erp.common.web.ApiResponse;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.attachment.web.AttachmentPageQuery;
import com.tuowei.erp.system.attachment.web.AttachmentResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/system/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_ATTACHMENT_MANAGE)
    @PostMapping(consumes = "multipart/form-data")
    public ApiResponse<AttachmentResponse> upload(
            @RequestParam String businessType,
            @RequestParam Long businessId,
            @RequestParam(required = false) String businessNo,
            @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(attachmentService.upload(businessType, businessId, businessNo, file));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_ATTACHMENT_VIEW)
    @GetMapping
    public ApiResponse<PageResponse<AttachmentResponse>> list(AttachmentPageQuery query) {
        return ApiResponse.success(attachmentService.list(query));
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_ATTACHMENT_VIEW)
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        return attachmentService.download(id);
    }

    @PreAuthorize(PermissionCodes.HAS_SYSTEM_ATTACHMENT_DELETE)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        attachmentService.delete(id);
        return ApiResponse.success(null);
    }
}

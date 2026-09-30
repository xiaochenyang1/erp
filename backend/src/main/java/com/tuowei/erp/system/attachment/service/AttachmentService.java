package com.tuowei.erp.system.attachment.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.attachment.web.AttachmentPageQuery;
import com.tuowei.erp.system.attachment.web.AttachmentResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Compatibility facade for attachment operations.
 *
 * <p>Delegates the read side to {@link AttachmentQueryService} and the write side to
 * {@link AttachmentCommandService}, preserving the original public API and transaction boundaries
 * for existing callers.
 */
@Service
public class AttachmentService {

    private final AttachmentQueryService queryService;
    private final AttachmentCommandService commandService;

    public AttachmentService(AttachmentQueryService queryService, AttachmentCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
    }

    @Transactional
    public AttachmentResponse upload(String businessType, Long businessId, String businessNo, MultipartFile file) {
        return commandService.upload(businessType, businessId, businessNo, file);
    }

    @Transactional(readOnly = true)
    public PageResponse<AttachmentResponse> list(AttachmentPageQuery query) {
        AttachmentPageQuery safeQuery = query == null ? new AttachmentPageQuery() : query;
        return queryService.list(safeQuery);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> download(Long id) {
        return queryService.download(id);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadForBusiness(Long id, String businessType, Long businessId) {
        return queryService.downloadForBusiness(id, businessType, businessId);
    }

    @Transactional(readOnly = true)
    public void requireForBusiness(Long id, String businessType, Long businessId) {
        queryService.requireForBusiness(id, businessType, businessId);
    }

    @Transactional
    public void delete(Long id) {
        commandService.delete(id);
    }

    @Transactional
    public void deleteForBusiness(Long id, String businessType, Long businessId) {
        commandService.deleteForBusiness(id, businessType, businessId);
    }

    @Transactional(readOnly = true)
    public long countActive(String businessType, Long businessId) {
        return queryService.countActive(businessType, businessId);
    }

    @Transactional(readOnly = true)
    public void requireIfConfigured(String businessType, Long businessId) {
        queryService.requireIfConfigured(businessType, businessId);
    }
}

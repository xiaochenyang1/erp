package com.tuowei.erp.system.attachment.service;

import com.tuowei.erp.common.config.AttachmentProperties;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.attachment.mapper.AttachmentMapper;
import com.tuowei.erp.system.attachment.model.AttachmentEntity;
import com.tuowei.erp.system.attachment.web.AttachmentResponse;
import com.tuowei.erp.system.timeline.service.BusinessTimelineService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

/** Write-side upload/delete for attachments, coordinating storage IO, persistence and timeline events. */
@Service
public class AttachmentCommandService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final AttachmentMapper attachmentMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AttachmentProperties attachmentProperties;
    private final BusinessTimelineService timelineService;
    private final AttachmentStorageService storageService;
    private final AttachmentQueryService queryService;
    private final Clock clock;

    public AttachmentCommandService(
            AttachmentMapper attachmentMapper,
            AuditMetadataFactory auditMetadataFactory,
            AttachmentProperties attachmentProperties,
            BusinessTimelineService timelineService,
            AttachmentStorageService storageService,
            AttachmentQueryService queryService,
            Clock clock
    ) {
        this.attachmentMapper = attachmentMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.attachmentProperties = attachmentProperties;
        this.timelineService = timelineService;
        this.storageService = storageService;
        this.queryService = queryService;
        this.clock = clock;
    }

    @Transactional
    public AttachmentResponse upload(String businessType, Long businessId, String businessNo, MultipartFile file) {
        AuditMetadata audit = auditMetadataFactory.current();
        String normalizedBusinessType = queryService.normalizeRequired(businessType, "业务类型不能为空").toUpperCase(Locale.ROOT);
        if (businessId == null) {
            throw new IllegalArgumentException("业务ID不能为空");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("附件不能为空");
        }
        if (file.getSize() > attachmentProperties.maxFileSizeBytes()) {
            throw new IllegalArgumentException("附件大小超过限制");
        }

        String originalFilename = storageService.normalizeFilename(file.getOriginalFilename());
        String relativePath = storageService.buildRelativePath(audit, originalFilename);
        Path target = storageService.resolveStoragePath(relativePath);

        try {
            AttachmentStorageService.StoredFile storedFile = storageService.writeFile(target, file);
            LocalDateTime now = LocalDateTime.now(clock);
            AttachmentEntity entity = new AttachmentEntity();
            entity.setCompanyId(audit.companyId());
            entity.setAccountBookId(audit.accountBookId());
            entity.setBusinessType(normalizedBusinessType);
            entity.setBusinessId(businessId);
            entity.setBusinessNo(queryService.normalizeNullable(businessNo));
            entity.setOriginalFilename(originalFilename);
            entity.setStoragePath(relativePath);
            entity.setContentType(storageService.resolveContentType(file));
            entity.setFileSize(storedFile.fileSize());
            entity.setChecksumSha256(storedFile.checksumSha256());
            entity.setStatus(STATUS_ACTIVE);
            entity.setDeletedFlag(0);
            entity.setCreatedBy(audit.userId());
            entity.setCreatedTime(now);
            entity.setUpdatedBy(audit.userId());
            entity.setUpdatedTime(now);
            entity.setVersion(0);
            if (attachmentMapper.insert(entity) != 1) {
                throw new IllegalStateException("保存附件记录失败");
            }
            timelineService.recordAttachmentUploaded(entity, audit);
            return queryService.toResponse(entity);
        } catch (RuntimeException ex) {
            storageService.deleteFileQuietly(target);
            throw ex;
        }
    }

    @Transactional
    public void delete(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        AttachmentEntity entity = queryService.requireActive(id);
        delete(entity, audit);
    }

    @Transactional
    public void deleteForBusiness(Long id, String businessType, Long businessId) {
        AuditMetadata audit = auditMetadataFactory.current();
        AttachmentEntity entity = queryService.requireActive(id);
        queryService.requireBusiness(entity, businessType, businessId);
        delete(entity, audit);
    }

    private void delete(AttachmentEntity entity, AuditMetadata audit) {
        entity.setDeletedFlag(1);
        entity.setStatus("DELETED");
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(attachmentMapper.updateById(entity), "附件已被其他操作修改，请刷新后重试");
        timelineService.recordAttachmentDeleted(entity, audit);
    }
}

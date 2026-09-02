package com.tuowei.erp.system.attachment.service;

import com.tuowei.erp.common.config.AttachmentProperties;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.system.attachment.mapper.AttachmentMapper;
import com.tuowei.erp.system.attachment.model.AttachmentEntity;
import com.tuowei.erp.system.attachment.web.AttachmentPageQuery;
import com.tuowei.erp.system.attachment.web.AttachmentPolicyResponse;
import com.tuowei.erp.system.attachment.web.AttachmentResponse;
import com.tuowei.erp.system.timeline.service.BusinessTimelineService;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class AttachmentService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final AttachmentMapper attachmentMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AttachmentProperties attachmentProperties;
    private final BusinessTimelineService timelineService;
    private final Clock clock;
    private final AttachmentQueryService queryService;

    public AttachmentService(
            AttachmentMapper attachmentMapper,
            AuditMetadataFactory auditMetadataFactory,
            AttachmentProperties attachmentProperties,
            BusinessTimelineService timelineService,
            Clock clock,
            AttachmentQueryService queryService
    ) {
        this.attachmentMapper = attachmentMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.attachmentProperties = attachmentProperties;
        this.timelineService = timelineService;
        this.clock = clock;
        this.queryService = queryService;
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

        String originalFilename = queryService.normalizeFilename(file.getOriginalFilename());
        String relativePath = buildRelativePath(audit, originalFilename);
        Path target = queryService.resolveStoragePath(relativePath);

        try {
            StoredFile storedFile = writeFile(target, file);
            LocalDateTime now = LocalDateTime.now(clock);
            AttachmentEntity entity = new AttachmentEntity();
            entity.setCompanyId(audit.companyId());
            entity.setAccountBookId(audit.accountBookId());
            entity.setBusinessType(normalizedBusinessType);
            entity.setBusinessId(businessId);
            entity.setBusinessNo(queryService.normalizeNullable(businessNo));
            entity.setOriginalFilename(originalFilename);
            entity.setStoragePath(relativePath);
            entity.setContentType(queryService.resolveContentType(file.getContentType()));
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
            deleteFileQuietly(target);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AttachmentResponse> list(AttachmentPageQuery query) {
        AttachmentPageQuery safeQuery = query == null ? new AttachmentPageQuery() : query;
        return queryService.list(safeQuery);
    }

    /**
     * 附件闸门策略，纯配置读取，不落库也不需要事务。
     *
     * requiredBusinessTypes 只回未被 {@link AttachmentBusinessType#GATED} 排除的类型：
     * 配置里出现未挂闸门的类型时 {@code AttachmentRequiredTypeValidator} 已在启动期拒绝，
     * 这里再过一次是为了保证响应绝不承诺一个并不存在的闸门。
     */
    public AttachmentPolicyResponse policy() {
        return new AttachmentPolicyResponse(
                attachmentProperties.maxFileSizeBytes(),
                attachmentProperties.requiredMinCount(),
                attachmentProperties.requiredBusinessTypeSet().stream()
                        .filter(AttachmentBusinessType::isGated)
                        .sorted()
                        .toList(),
                AttachmentBusinessType.GATED.stream().sorted().toList()
        );
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

    @Transactional(readOnly = true)
    public long countActive(String businessType, Long businessId) {
        return queryService.countActive(businessType, businessId);
    }

    /**
     * 若业务类型配置为强制附件，则当前业务至少需要 minRequiredCount 个有效附件。
     */
    @Transactional(readOnly = true)
    public void requireIfConfigured(String businessType, Long businessId) {
        queryService.requireIfConfigured(businessType, businessId);
    }

    private StoredFile writeFile(Path target, MultipartFile file) {
        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long totalBytes = 0;
            byte[] buffer = new byte[8192];
            try (DigestInputStream inputStream = new DigestInputStream(file.getInputStream(), digest);
                 OutputStream outputStream = Files.newOutputStream(target)) {
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    totalBytes += read;
                    if (totalBytes > attachmentProperties.maxFileSizeBytes()) {
                        throw new IllegalArgumentException("附件大小超过限制");
                    }
                    outputStream.write(buffer, 0, read);
                }
            }
            return new StoredFile(totalBytes, URL_ENCODER.encodeToString(digest.digest()));
        } catch (IOException ex) {
            throw new UncheckedIOException("保存附件失败", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("计算附件摘要失败", ex);
        }
    }

    private void deleteFileQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private String buildRelativePath(AuditMetadata audit, String originalFilename) {
        String suffix = SafeFilename.extensionOf(originalFilename, ".bin", 32);
        LocalDate today = LocalDate.now(clock);
        return Path.of(
                audit.companyId().toString(),
                audit.accountBookId().toString(),
                today.toString(),
                UUID.randomUUID() + suffix
        ).toString().replace('\\', '/');
    }

    private record StoredFile(long fileSize, String checksumSha256) {
    }
}

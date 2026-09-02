package com.tuowei.erp.system.attachment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.config.AttachmentProperties;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.web.SafeFilename;
import com.tuowei.erp.system.attachment.mapper.AttachmentMapper;
import com.tuowei.erp.system.attachment.model.AttachmentEntity;
import com.tuowei.erp.system.attachment.web.AttachmentPageQuery;
import com.tuowei.erp.system.attachment.web.AttachmentResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Service
public class AttachmentQueryService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final AttachmentMapper attachmentMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AttachmentProperties attachmentProperties;

    public AttachmentQueryService(
            AttachmentMapper attachmentMapper,
            AuditMetadataFactory auditMetadataFactory,
            AttachmentProperties attachmentProperties
    ) {
        this.attachmentMapper = attachmentMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.attachmentProperties = attachmentProperties;
    }

    @Transactional(readOnly = true)
    public PageResponse<AttachmentResponse> list(AttachmentPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        AttachmentPageQuery safeQuery = query == null ? new AttachmentPageQuery() : query;
        Page<AttachmentEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        Page<AttachmentEntity> result = attachmentMapper.selectPage(page, buildQuery(audit, safeQuery));
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> download(Long id) {
        return download(requireActive(id));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadForBusiness(Long id, String businessType, Long businessId) {
        AttachmentEntity entity = requireActive(id);
        requireBusiness(entity, businessType, businessId);
        return download(entity);
    }

    @Transactional(readOnly = true)
    public void requireForBusiness(Long id, String businessType, Long businessId) {
        AttachmentEntity entity = requireActive(id);
        requireBusiness(entity, businessType, businessId);
    }

    @Transactional(readOnly = true)
    public long countActive(String businessType, Long businessId) {
        AuditMetadata audit = auditMetadataFactory.current();
        String type = normalizeRequired(businessType, "业务类型不能为空").toUpperCase(Locale.ROOT);
        if (businessId == null) {
            throw new IllegalArgumentException("业务ID不能为空");
        }
        Long count = attachmentMapper.selectCount(new LambdaQueryWrapper<AttachmentEntity>()
                .eq(AttachmentEntity::getCompanyId, audit.companyId())
                .eq(AttachmentEntity::getAccountBookId, audit.accountBookId())
                .eq(AttachmentEntity::getBusinessType, type)
                .eq(AttachmentEntity::getBusinessId, businessId)
                .eq(AttachmentEntity::getDeletedFlag, 0)
                .eq(AttachmentEntity::getStatus, STATUS_ACTIVE));
        return count == null ? 0L : count;
    }

    @Transactional(readOnly = true)
    public void requireIfConfigured(String businessType, Long businessId) {
        String type = normalizeRequired(businessType, "业务类型不能为空").toUpperCase(Locale.ROOT);
        if (!attachmentProperties.requiredBusinessTypeSet().contains(type)) {
            return;
        }
        long count = countActive(type, businessId);
        int min = attachmentProperties.requiredMinCount();
        if (count < min) {
            throw new IllegalArgumentException(
                    "业务类型 " + type + " 要求至少上传 " + min + " 个附件，当前 " + count + " 个"
            );
        }
    }

    AttachmentEntity requireActive(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        AttachmentEntity entity = attachmentMapper.selectById(id);
        if (entity == null
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0
                || !STATUS_ACTIVE.equals(entity.getStatus())
                || !audit.companyId().equals(entity.getCompanyId())
                || !audit.accountBookId().equals(entity.getAccountBookId())) {
            throw new IllegalArgumentException("附件不存在");
        }
        return entity;
    }

    void requireBusiness(AttachmentEntity entity, String businessType, Long businessId) {
        String type = normalizeRequired(businessType, "业务类型不能为空").toUpperCase(Locale.ROOT);
        if (!type.equals(entity.getBusinessType()) || businessId == null || !businessId.equals(entity.getBusinessId())) {
            throw new IllegalArgumentException("附件不存在");
        }
    }

    AttachmentResponse toResponse(AttachmentEntity entity) {
        return new AttachmentResponse(
                entity.getId(),
                entity.getBusinessType(),
                entity.getBusinessId(),
                entity.getBusinessNo(),
                entity.getOriginalFilename(),
                entity.getContentType(),
                entity.getFileSize(),
                entity.getChecksumSha256(),
                entity.getCreatedTime(),
                entity.getCreatedBy()
        );
    }

    Path resolveStoragePath(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new IllegalArgumentException("附件路径无效");
        }
        Path root = Path.of(attachmentProperties.storageRoot()).toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("附件路径无效");
        }
        return target;
    }

    String normalizeFilename(String filename) {
        return SafeFilename.normalize(filename, "attachment", 255);
    }

    String resolveContentType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        try {
            return MediaType.parseMediaType(contentType.trim()).toString();
        } catch (InvalidMediaTypeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }

    String normalizeRequired(String value, String message) {
        String normalized = normalizeNullable(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private ResponseEntity<Resource> download(AttachmentEntity entity) {
        Path path = resolveStoragePath(entity.getStoragePath());
        long fileSize = requireFileSize(path);
        String downloadFilename = normalizeFilename(entity.getOriginalFilename());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resolveContentType(entity.getContentType())))
                .contentLength(fileSize)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(downloadFilename, java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(path));
    }

    private LambdaQueryWrapper<AttachmentEntity> buildQuery(AuditMetadata audit, AttachmentPageQuery query) {
        LambdaQueryWrapper<AttachmentEntity> wrapper = new LambdaQueryWrapper<AttachmentEntity>()
                .eq(AttachmentEntity::getCompanyId, audit.companyId())
                .eq(AttachmentEntity::getAccountBookId, audit.accountBookId())
                .eq(AttachmentEntity::getDeletedFlag, 0);

        String businessType = normalizeNullable(query.getBusinessType());
        if (StringUtils.hasText(businessType)) {
            wrapper.eq(AttachmentEntity::getBusinessType, businessType.toUpperCase(Locale.ROOT));
        }
        if (query.getBusinessId() != null) {
            wrapper.eq(AttachmentEntity::getBusinessId, query.getBusinessId());
        }
        String businessNo = normalizeNullable(query.getBusinessNo());
        if (StringUtils.hasText(businessNo)) {
            wrapper.eq(AttachmentEntity::getBusinessNo, businessNo);
        }
        return wrapper.orderByDesc(AttachmentEntity::getCreatedTime).orderByDesc(AttachmentEntity::getId);
    }

    private long requireFileSize(Path path) {
        try {
            if (!Files.exists(path) || !Files.isRegularFile(path)) {
                throw new IllegalArgumentException("附件文件不存在");
            }
            return Files.size(path);
        } catch (IOException ex) {
            throw new UncheckedIOException("读取附件失败", ex);
        }
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}

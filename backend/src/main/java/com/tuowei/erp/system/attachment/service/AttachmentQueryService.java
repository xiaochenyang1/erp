package com.tuowei.erp.system.attachment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.config.AttachmentProperties;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.attachment.mapper.AttachmentMapper;
import com.tuowei.erp.system.attachment.model.AttachmentEntity;
import com.tuowei.erp.system.attachment.web.AttachmentPageQuery;
import com.tuowei.erp.system.attachment.web.AttachmentResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;

/** Read-side listing, tenant/business guards and download resolution for attachments. */
@Service
public class AttachmentQueryService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final AttachmentMapper attachmentMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AttachmentProperties attachmentProperties;
    private final AttachmentStorageService storageService;

    public AttachmentQueryService(
            AttachmentMapper attachmentMapper,
            AuditMetadataFactory auditMetadataFactory,
            AttachmentProperties attachmentProperties,
            AttachmentStorageService storageService
    ) {
        this.attachmentMapper = attachmentMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.attachmentProperties = attachmentProperties;
        this.storageService = storageService;
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
        AttachmentEntity entity = requireActive(id);
        return storageService.buildDownloadResponse(entity);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<Resource> downloadForBusiness(Long id, String businessType, Long businessId) {
        AttachmentEntity entity = requireActive(id);
        requireBusiness(entity, businessType, businessId);
        return storageService.buildDownloadResponse(entity);
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

    /**
     * 若业务类型配置为强制附件，则当前业务至少需要 minRequiredCount 个有效附件。
     */
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

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}

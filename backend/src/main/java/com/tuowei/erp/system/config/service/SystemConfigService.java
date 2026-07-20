package com.tuowei.erp.system.config.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.system.config.mapper.SystemConfigMapper;
import com.tuowei.erp.system.config.model.SystemConfigEntity;
import com.tuowei.erp.system.config.web.SystemConfigCreateRequest;
import com.tuowei.erp.system.config.web.SystemConfigPageQuery;
import com.tuowei.erp.system.config.web.SystemConfigResponse;
import com.tuowei.erp.system.config.web.SystemConfigUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class SystemConfigService {

    private final SystemConfigMapper systemConfigMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public SystemConfigService(SystemConfigMapper systemConfigMapper, AuditMetadataFactory auditMetadataFactory) {
        this.systemConfigMapper = systemConfigMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional
    public SystemConfigResponse create(SystemConfigCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();

        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setConfigCode(request.configCode());
        entity.setConfigName(request.configName());
        entity.setConfigValue(request.configValue());
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark(request.remark());
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);

        systemConfigMapper.insert(entity);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<SystemConfigResponse> list(SystemConfigPageQuery query) {
        SystemConfigPageQuery safeQuery = query == null ? new SystemConfigPageQuery() : query;
        long pageNo = normalizePageNo(safeQuery.getPageNo());
        long pageSize = normalizePageSize(safeQuery.getPageSize());
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String status = normalizeStatus(safeQuery.getStatus());

        Page<SystemConfigEntity> page = new Page<>(pageNo, pageSize);
        Page<SystemConfigEntity> result = systemConfigMapper.selectPage(page, buildListQuery(keyword, status));

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public SystemConfigResponse getById(Long id) {
        return toResponse(requireConfig(id));
    }

    @Transactional
    public SystemConfigResponse update(Long id, SystemConfigUpdateRequest request) {
        SystemConfigEntity entity = requireConfig(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setConfigName(request.configName());
        entity.setConfigValue(request.configValue());
        entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(systemConfigMapper.updateById(entity), "系统参数已被其他操作修改，请刷新后重试");
        return toResponse(entity);
    }

    @Transactional
    public SystemConfigResponse enable(Long id) {
        return updateStatus(id, "ACTIVE");
    }

    @Transactional
    public SystemConfigResponse disable(Long id) {
        return updateStatus(id, "DISABLED");
    }

    private SystemConfigResponse toResponse(SystemConfigEntity entity) {
        return new SystemConfigResponse(
                entity.getId(),
                entity.getConfigCode(),
                entity.getConfigName(),
                entity.getConfigValue(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private SystemConfigEntity requireConfig(Long id) {
        SystemConfigEntity entity = systemConfigMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("系统参数不存在");
        }
        return entity;
    }

    private SystemConfigResponse updateStatus(Long id, String status) {
        SystemConfigEntity entity = requireConfig(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(systemConfigMapper.updateById(entity), "系统参数已被其他操作修改，请刷新后重试");
        return toResponse(entity);
    }

    private LambdaQueryWrapper<SystemConfigEntity> buildListQuery(String keyword, String status) {
        LambdaQueryWrapper<SystemConfigEntity> wrapper = new LambdaQueryWrapper<SystemConfigEntity>()
                .eq(SystemConfigEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(SystemConfigEntity::getConfigCode, keyword)
                    .or()
                    .like(SystemConfigEntity::getConfigName, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SystemConfigEntity::getStatus, status);
        }
        return wrapper.orderByAsc(SystemConfigEntity::getConfigCode);
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeNullableText(status);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return 1L;
        }
        return pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200);
    }

}

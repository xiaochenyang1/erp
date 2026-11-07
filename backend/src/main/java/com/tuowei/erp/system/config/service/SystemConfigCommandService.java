package com.tuowei.erp.system.config.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.config.mapper.SystemConfigMapper;
import com.tuowei.erp.system.config.model.SystemConfigEntity;
import com.tuowei.erp.system.config.web.SystemConfigCreateRequest;
import com.tuowei.erp.system.config.web.SystemConfigResponse;
import com.tuowei.erp.system.config.web.SystemConfigUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Write-side global system configuration commands. */
@Service
public class SystemConfigCommandService {

    private final SystemConfigMapper systemConfigMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SystemConfigQueryService systemConfigQueryService;

    public SystemConfigCommandService(
            SystemConfigMapper systemConfigMapper,
            AuditMetadataFactory auditMetadataFactory,
            SystemConfigQueryService systemConfigQueryService
    ) {
        this.systemConfigMapper = systemConfigMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.systemConfigQueryService = systemConfigQueryService;
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
        return systemConfigQueryService.toResponse(entity);
    }

    @Transactional
    public SystemConfigResponse update(Long id, SystemConfigUpdateRequest request) {
        SystemConfigEntity entity = systemConfigQueryService.requireConfig(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setConfigName(request.configName());
        entity.setConfigValue(request.configValue());
        entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                systemConfigMapper.updateById(entity), "系统参数已被其他操作修改，请刷新后重试"
        );
        return systemConfigQueryService.toResponse(entity);
    }

    @Transactional
    public SystemConfigResponse enable(Long id) {
        return updateStatus(id, "ACTIVE");
    }

    @Transactional
    public SystemConfigResponse disable(Long id) {
        return updateStatus(id, "DISABLED");
    }

    private SystemConfigResponse updateStatus(Long id, String status) {
        SystemConfigEntity entity = systemConfigQueryService.requireConfig(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                systemConfigMapper.updateById(entity), "系统参数已被其他操作修改，请刷新后重试"
        );
        return systemConfigQueryService.toResponse(entity);
    }
}

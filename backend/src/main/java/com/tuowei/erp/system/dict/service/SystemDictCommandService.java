package com.tuowei.erp.system.dict.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.dict.mapper.DictItemMapper;
import com.tuowei.erp.system.dict.mapper.DictTypeMapper;
import com.tuowei.erp.system.dict.model.DictItemEntity;
import com.tuowei.erp.system.dict.model.DictTypeEntity;
import com.tuowei.erp.system.dict.web.DictItemCreateRequest;
import com.tuowei.erp.system.dict.web.DictItemResponse;
import com.tuowei.erp.system.dict.web.DictItemUpdateRequest;
import com.tuowei.erp.system.dict.web.DictTypeCreateRequest;
import com.tuowei.erp.system.dict.web.DictTypeResponse;
import com.tuowei.erp.system.dict.web.DictTypeUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** Write-side dictionary type/item commands and cache invalidation. */
@Service
public class SystemDictCommandService {

    private final DictTypeMapper dictTypeMapper;
    private final DictItemMapper dictItemMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SystemDictQueryService systemDictQueryService;

    public SystemDictCommandService(
            DictTypeMapper dictTypeMapper,
            DictItemMapper dictItemMapper,
            AuditMetadataFactory auditMetadataFactory,
            SystemDictQueryService systemDictQueryService
    ) {
        this.dictTypeMapper = dictTypeMapper;
        this.dictItemMapper = dictItemMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.systemDictQueryService = systemDictQueryService;
    }

    @Transactional
    public DictTypeResponse createType(DictTypeCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        DictTypeEntity entity = new DictTypeEntity();
        entity.setDictType(normalizeRequired(request.dictType()));
        entity.setDictName(request.dictName().trim());
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark(request.remark());
        setCreateAudit(entity, audit, now);
        dictTypeMapper.insert(entity);
        return systemDictQueryService.toTypeResponse(entity);
    }

    @Transactional
    public DictTypeResponse updateType(Long id, DictTypeUpdateRequest request) {
        DictTypeEntity entity = systemDictQueryService.requireType(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setDictName(request.dictName().trim());
        entity.setRemark(request.remark());
        touch(entity, audit);
        OptimisticLockGuard.requireUpdated(
                dictTypeMapper.updateById(entity),
                "字典类型已被其他操作修改，请刷新后重试"
        );
        return systemDictQueryService.toTypeResponse(entity);
    }

    @Transactional
    public DictTypeResponse enableType(Long id) {
        return updateTypeStatus(id, "ACTIVE");
    }

    @Transactional
    public DictTypeResponse disableType(Long id) {
        return updateTypeStatus(id, "DISABLED");
    }

    @Transactional
    public DictItemResponse createItem(DictItemCreateRequest request) {
        String dictType = normalizeRequired(request.dictType());
        DictTypeEntity type = systemDictQueryService.requireTypeByCode(dictType);
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        DictItemEntity entity = new DictItemEntity();
        entity.setTypeId(type.getId());
        entity.setDictType(type.getDictType());
        entity.setItemLabel(request.itemLabel().trim());
        entity.setItemValue(request.itemValue().trim());
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark(request.remark());
        setCreateAudit(entity, audit, now);
        dictItemMapper.insert(entity);
        systemDictQueryService.evictDictItemsCache(dictType);
        return systemDictQueryService.toItemResponse(entity);
    }

    @Transactional
    public DictItemResponse updateItem(Long id, DictItemUpdateRequest request) {
        DictItemEntity entity = systemDictQueryService.requireItem(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setItemLabel(request.itemLabel().trim());
        entity.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        entity.setRemark(request.remark());
        touch(entity, audit);
        OptimisticLockGuard.requireUpdated(
                dictItemMapper.updateById(entity),
                "字典项已被其他操作修改，请刷新后重试"
        );
        systemDictQueryService.evictDictItemsCache(entity.getDictType());
        return systemDictQueryService.toItemResponse(entity);
    }

    @Transactional
    public DictItemResponse enableItem(Long id) {
        return updateItemStatus(id, "ACTIVE");
    }

    @Transactional
    public DictItemResponse disableItem(Long id) {
        return updateItemStatus(id, "DISABLED");
    }

    private DictTypeResponse updateTypeStatus(Long id, String status) {
        DictTypeEntity entity = systemDictQueryService.requireType(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        touch(entity, audit);
        OptimisticLockGuard.requireUpdated(
                dictTypeMapper.updateById(entity),
                "字典类型已被其他操作修改，请刷新后重试"
        );
        return systemDictQueryService.toTypeResponse(entity);
    }

    private DictItemResponse updateItemStatus(Long id, String status) {
        DictItemEntity entity = systemDictQueryService.requireItem(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        touch(entity, audit);
        OptimisticLockGuard.requireUpdated(
                dictItemMapper.updateById(entity),
                "字典项已被其他操作修改，请刷新后重试"
        );
        systemDictQueryService.evictDictItemsCache(entity.getDictType());
        return systemDictQueryService.toItemResponse(entity);
    }

    private String normalizeRequired(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("字典编码不能为空");
        }
        return value.trim();
    }

    private void setCreateAudit(DictTypeEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private void setCreateAudit(DictItemEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private void touch(DictTypeEntity entity, AuditMetadata audit) {
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
    }

    private void touch(DictItemEntity entity, AuditMetadata audit) {
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
    }
}

package com.tuowei.erp.masterdata.location.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.location.mapper.LocationMapper;
import com.tuowei.erp.masterdata.location.model.LocationEntity;
import com.tuowei.erp.masterdata.location.web.LocationCreateRequest;
import com.tuowei.erp.masterdata.location.web.LocationResponse;
import com.tuowei.erp.masterdata.location.web.LocationUpdateRequest;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Objects;

/** Write-side creation, default maintenance, updates and status commands. */
@Service
public class LocationCommandService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String DEFAULT_CODE = "MAIN";
    private static final String DEFAULT_NAME = "默认库位";

    private final LocationMapper locationMapper;
    private final WarehouseMapper warehouseMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final LocationQueryService locationQueryService;

    public LocationCommandService(
            LocationMapper locationMapper,
            WarehouseMapper warehouseMapper,
            AuditMetadataFactory auditMetadataFactory,
            LocationQueryService locationQueryService
    ) {
        this.locationMapper = locationMapper;
        this.warehouseMapper = warehouseMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.locationQueryService = locationQueryService;
    }

    @Transactional
    public LocationResponse create(LocationCreateRequest request) {
        return create(request, auditMetadataFactory.current());
    }

    private LocationResponse create(LocationCreateRequest request, AuditMetadata audit) {
        WarehouseEntity warehouse = requireWarehouse(request.warehouseId(), audit);
        String code = normalizeCode(request.locationCode());
        String name = normalizeName(request.locationName());
        ensureCodeUnique(audit, warehouse.getId(), code, null);
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault()) || !hasDefaultLocation(audit, warehouse.getId());
        if (makeDefault) {
            clearDefaultFlag(audit, warehouse.getId());
        }
        LocalDateTime now = audit.now();
        LocationEntity entity = new LocationEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setWarehouseId(warehouse.getId());
        entity.setLocationCode(code);
        entity.setLocationName(name);
        entity.setIsDefault(makeDefault ? 1 : 0);
        entity.setStatus(STATUS_ACTIVE);
        entity.setDeletedFlag(0);
        entity.setRemark(trimToNull(request.remark()));
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
        locationMapper.insert(entity);
        return locationQueryService.toResponse(entity, warehouse.getWarehouseName());
    }

    @Transactional
    public LocationResponse ensureDefaultLocation(WarehouseEntity warehouse, AuditMetadata audit) {
        LocationEntity existing = locationMapper.selectOne(new LambdaQueryWrapper<LocationEntity>()
                .eq(LocationEntity::getCompanyId, audit.companyId())
                .eq(LocationEntity::getAccountBookId, audit.accountBookId())
                .eq(LocationEntity::getWarehouseId, warehouse.getId())
                .eq(LocationEntity::getDeletedFlag, 0)
                .eq(LocationEntity::getIsDefault, 1)
                .last("limit 1"));
        if (existing != null) {
            return locationQueryService.toResponse(existing, warehouse.getWarehouseName());
        }
        return create(
                new LocationCreateRequest(warehouse.getId(), DEFAULT_CODE, DEFAULT_NAME, true, "系统默认库位"),
                audit
        );
    }

    @Transactional
    public LocationResponse update(Long id, LocationUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocationEntity entity = locationQueryService.requireLocation(id, audit);
        String code = normalizeCode(request.locationCode());
        String name = normalizeName(request.locationName());
        ensureCodeUnique(audit, entity.getWarehouseId(), code, entity.getId());
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault());
        if (makeDefault) {
            clearDefaultFlag(audit, entity.getWarehouseId());
            entity.setIsDefault(1);
        } else if (Integer.valueOf(1).equals(entity.getIsDefault()) && Boolean.FALSE.equals(request.isDefault())) {
            throw new IllegalArgumentException("至少保留一个默认库位");
        }
        if (StringUtils.hasText(request.status())) {
            entity.setStatus(normalizeStatus(request.status()));
        }
        entity.setLocationCode(code);
        entity.setLocationName(name);
        entity.setRemark(trimToNull(request.remark()));
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(locationMapper.updateById(entity), "库位已被其他操作修改，请刷新后重试");
        return locationQueryService.toResponse(entity);
    }

    @Transactional
    public LocationResponse enable(Long id) {
        return toggleStatus(id, STATUS_ACTIVE);
    }

    @Transactional
    public LocationResponse disable(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocationEntity entity = locationQueryService.requireLocation(id, audit);
        if (Integer.valueOf(1).equals(entity.getIsDefault())) {
            throw new IllegalArgumentException("默认库位不能停用");
        }
        return toggleStatus(id, STATUS_INACTIVE);
    }

    private LocationResponse toggleStatus(Long id, String status) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocationEntity entity = locationQueryService.requireLocation(id, audit);
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(locationMapper.updateById(entity), "库位已被其他操作修改，请刷新后重试");
        return locationQueryService.toResponse(entity);
    }

    private void clearDefaultFlag(AuditMetadata audit, Long warehouseId) {
        locationMapper.update(null, new LambdaUpdateWrapper<LocationEntity>()
                .eq(LocationEntity::getCompanyId, audit.companyId())
                .eq(LocationEntity::getAccountBookId, audit.accountBookId())
                .eq(LocationEntity::getWarehouseId, warehouseId)
                .eq(LocationEntity::getDeletedFlag, 0)
                .eq(LocationEntity::getIsDefault, 1)
                .set(LocationEntity::getIsDefault, 0)
                .set(LocationEntity::getUpdatedBy, audit.userId())
                .set(LocationEntity::getUpdatedTime, audit.now()));
    }

    private boolean hasDefaultLocation(AuditMetadata audit, Long warehouseId) {
        Long count = locationMapper.selectCount(new LambdaQueryWrapper<LocationEntity>()
                .eq(LocationEntity::getCompanyId, audit.companyId())
                .eq(LocationEntity::getAccountBookId, audit.accountBookId())
                .eq(LocationEntity::getWarehouseId, warehouseId)
                .eq(LocationEntity::getDeletedFlag, 0)
                .eq(LocationEntity::getIsDefault, 1));
        return count != null && count > 0;
    }

    private void ensureCodeUnique(AuditMetadata audit, Long warehouseId, String code, Long excludeId) {
        LambdaQueryWrapper<LocationEntity> wrapper = new LambdaQueryWrapper<LocationEntity>()
                .eq(LocationEntity::getCompanyId, audit.companyId())
                .eq(LocationEntity::getAccountBookId, audit.accountBookId())
                .eq(LocationEntity::getWarehouseId, warehouseId)
                .eq(LocationEntity::getLocationCode, code)
                .eq(LocationEntity::getDeletedFlag, 0);
        if (excludeId != null) {
            wrapper.ne(LocationEntity::getId, excludeId);
        }
        Long count = locationMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new IllegalArgumentException("同一仓库下库位编码已存在");
        }
    }

    private WarehouseEntity requireWarehouse(Long warehouseId, AuditMetadata audit) {
        WarehouseEntity warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null
                || warehouse.getDeletedFlag() == null
                || warehouse.getDeletedFlag() != 0
                || !Objects.equals(warehouse.getCompanyId(), audit.companyId())
                || !Objects.equals(warehouse.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("仓库不存在");
        }
        return warehouse;
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("库位编码不能为空");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("库位名称不能为空");
        }
        return name.trim();
    }

    private String normalizeStatus(String status) {
        String upper = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUS_ACTIVE.equals(upper) && !STATUS_INACTIVE.equals(upper)) {
            throw new IllegalArgumentException("状态仅支持 ACTIVE/INACTIVE");
        }
        return upper;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}

package com.tuowei.erp.masterdata.location.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageQueryNormalizer;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.location.mapper.LocationMapper;
import com.tuowei.erp.masterdata.location.model.LocationEntity;
import com.tuowei.erp.masterdata.location.web.LocationPageQuery;
import com.tuowei.erp.masterdata.location.web.LocationResponse;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Read-side filtering, tenant guards, warehouse name hydration and response mapping. */
@Service
public class LocationQueryService {

    private final LocationMapper locationMapper;
    private final WarehouseMapper warehouseMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public LocationQueryService(
            LocationMapper locationMapper,
            WarehouseMapper warehouseMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.locationMapper = locationMapper;
        this.warehouseMapper = warehouseMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public LocationResponse getById(Long id) {
        return toResponse(requireLocation(id, auditMetadataFactory.current()));
    }

    @Transactional(readOnly = true)
    public PageResponse<LocationResponse> list(LocationPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocationPageQuery safe = query == null ? new LocationPageQuery() : query;
        long pageNo = PageQueryNormalizer.normalizePageNo(safe.getPageNo() == null ? null : safe.getPageNo().intValue());
        long pageSize = PageQueryNormalizer.normalizePageSize(safe.getPageSize() == null ? null : safe.getPageSize().intValue());
        LambdaQueryWrapper<LocationEntity> wrapper = new LambdaQueryWrapper<LocationEntity>()
                .eq(LocationEntity::getCompanyId, audit.companyId())
                .eq(LocationEntity::getAccountBookId, audit.accountBookId())
                .eq(LocationEntity::getDeletedFlag, 0)
                .orderByDesc(LocationEntity::getIsDefault)
                .orderByAsc(LocationEntity::getLocationCode)
                .orderByDesc(LocationEntity::getId);
        if (safe.getWarehouseId() != null) {
            wrapper.eq(LocationEntity::getWarehouseId, safe.getWarehouseId());
        }
        if (StringUtils.hasText(safe.getStatus())) {
            wrapper.eq(LocationEntity::getStatus, normalizeStatus(safe.getStatus()));
        }
        if (StringUtils.hasText(safe.getKeyword())) {
            String keyword = safe.getKeyword().trim();
            wrapper.and(w -> w.like(LocationEntity::getLocationCode, keyword)
                    .or()
                    .like(LocationEntity::getLocationName, keyword));
        }
        Page<LocationEntity> page = locationMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        Map<Long, String> warehouseNames = loadWarehouseNames(page.getRecords(), audit);
        return new PageResponse<>(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getRecords().stream()
                        .map(item -> toResponse(item, warehouseNames.get(item.getWarehouseId())))
                        .toList()
        );
    }

    LocationEntity requireLocation(Long id, AuditMetadata audit) {
        LocationEntity entity = locationMapper.selectById(id);
        if (entity == null
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("库位不存在");
        }
        return entity;
    }

    LocationResponse toResponse(LocationEntity entity) {
        AuditMetadata audit = auditMetadataFactory.current();
        WarehouseEntity warehouse = warehouseMapper.selectById(entity.getWarehouseId());
        String warehouseName = warehouse == null ? null : warehouse.getWarehouseName();
        return toResponse(entity, warehouseName);
    }

    LocationResponse toResponse(LocationEntity entity, String warehouseName) {
        return new LocationResponse(
                entity.getId(),
                entity.getWarehouseId(),
                warehouseName,
                entity.getLocationCode(),
                entity.getLocationName(),
                Integer.valueOf(1).equals(entity.getIsDefault()),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private Map<Long, String> loadWarehouseNames(List<LocationEntity> records, AuditMetadata audit) {
        Set<Long> ids = records.stream().map(LocationEntity::getWarehouseId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return warehouseMapper.selectBatchIds(ids).stream()
                .filter(w -> Objects.equals(w.getCompanyId(), audit.companyId()))
                .collect(Collectors.toMap(WarehouseEntity::getId, WarehouseEntity::getWarehouseName, (a, b) -> a, HashMap::new));
    }

    private String normalizeStatus(String status) {
        String upper = status.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(upper) && !"INACTIVE".equals(upper)) {
            throw new IllegalArgumentException("状态仅支持 ACTIVE/INACTIVE");
        }
        return upper;
    }
}

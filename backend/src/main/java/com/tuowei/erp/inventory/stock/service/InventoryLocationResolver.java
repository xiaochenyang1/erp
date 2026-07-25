package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.masterdata.location.mapper.LocationMapper;
import com.tuowei.erp.masterdata.location.model.LocationEntity;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InventoryLocationResolver {

    private final LocationMapper locationMapper;
    private final ConcurrentHashMap<String, Long> defaultLocationCache = new ConcurrentHashMap<>();

    public InventoryLocationResolver(LocationMapper locationMapper) {
        this.locationMapper = locationMapper;
    }

    public Long resolveLocationId(InventoryPostingCommand command, AuditMetadata audit) {
        if (command.locationId() != null) {
            LocationEntity location = requireLocation(command.locationId(), audit);
            if (!Objects.equals(location.getWarehouseId(), command.warehouseId())) {
                throw new IllegalArgumentException("库位不属于指定仓库");
            }
            if (!"ACTIVE".equalsIgnoreCase(String.valueOf(location.getStatus()))) {
                throw new IllegalArgumentException("库位未启用");
            }
            return location.getId();
        }
        return requireDefaultLocationId(command.warehouseId(), audit);
    }

    public Long requireDefaultLocationId(Long warehouseId, AuditMetadata audit) {
        String cacheKey = audit.companyId() + ":" + audit.accountBookId() + ":" + warehouseId;
        Long cached = defaultLocationCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        LocationEntity location = locationMapper.selectOne(new LambdaQueryWrapper<LocationEntity>()
                .eq(LocationEntity::getCompanyId, audit.companyId())
                .eq(LocationEntity::getAccountBookId, audit.accountBookId())
                .eq(LocationEntity::getWarehouseId, warehouseId)
                .eq(LocationEntity::getDeletedFlag, 0)
                .eq(LocationEntity::getIsDefault, 1)
                .eq(LocationEntity::getStatus, "ACTIVE")
                .last("limit 1"));
        if (location == null) {
            location = locationMapper.selectOne(new LambdaQueryWrapper<LocationEntity>()
                    .eq(LocationEntity::getCompanyId, audit.companyId())
                    .eq(LocationEntity::getAccountBookId, audit.accountBookId())
                    .eq(LocationEntity::getWarehouseId, warehouseId)
                    .eq(LocationEntity::getDeletedFlag, 0)
                    .eq(LocationEntity::getLocationCode, "MAIN")
                    .last("limit 1"));
        }
        if (location == null) {
            throw new IllegalArgumentException("仓库缺少默认库位，请先维护库位主数据");
        }
        defaultLocationCache.put(cacheKey, location.getId());
        return location.getId();
    }

    private LocationEntity requireLocation(Long locationId, AuditMetadata audit) {
        LocationEntity location = locationMapper.selectById(locationId);
        if (location == null
                || location.getDeletedFlag() == null
                || location.getDeletedFlag() != 0
                || !Objects.equals(location.getCompanyId(), audit.companyId())
                || !Objects.equals(location.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("库位不存在");
        }
        return location;
    }
}

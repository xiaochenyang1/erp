package com.tuowei.erp.imports.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.imports.model.ImportJobEntity;
import com.tuowei.erp.imports.model.ImportJobRowEntity;
import com.tuowei.erp.imports.web.ImportRowErrorResponse;
import com.tuowei.erp.masterdata.location.service.LocationService;
import com.tuowei.erp.masterdata.location.web.LocationCreateRequest;
import com.tuowei.erp.masterdata.location.mapper.LocationMapper;
import com.tuowei.erp.masterdata.location.model.LocationEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class LocationImportHandler extends AbstractImportHandler {

    private final WarehouseMapper warehouseMapper;
    private final LocationMapper locationMapper;
    private final LocationService locationService;

    public LocationImportHandler(
            ImportValidationSupport support,
            WarehouseMapper warehouseMapper,
            LocationMapper locationMapper,
            LocationService locationService
    ) {
        super(support);
        this.warehouseMapper = warehouseMapper;
        this.locationMapper = locationMapper;
        this.locationService = locationService;
    }

    @Override
    public String importType() {
        return ImportConstants.LOCATION;
    }

    @Override
    public ImportRowPlan validate(int rowNo, Map<String, String> raw, ImportValidationContext context) {
        List<ImportRowErrorResponse> errors = support.errorList();
        Map<String, Object> normalized = support.linkedMap();
        String warehouseCode = support.required(raw, "warehouse_code", errors);
        String locationCode = support.required(raw, "location_code", errors);
        String locationName = support.required(raw, "location_name", errors);
        if (locationCode != null) {
            locationCode = locationCode.trim().toUpperCase(Locale.ROOT);
        }
        WarehouseEntity warehouse = null;
        if (warehouseCode != null) {
            warehouse = warehouseMapper.selectOne(new LambdaQueryWrapper<WarehouseEntity>()
                    .eq(WarehouseEntity::getCompanyId, context.companyId())
                    .eq(WarehouseEntity::getAccountBookId, context.accountBookId())
                    .eq(WarehouseEntity::getWarehouseCode, warehouseCode)
                    .eq(WarehouseEntity::getDeletedFlag, 0)
                    .last("limit 1"));
            if (warehouse == null) {
                errors.add(new ImportRowErrorResponse("warehouse_code", "仓库不存在"));
            } else if (!"ACTIVE".equalsIgnoreCase(warehouse.getStatus())) {
                errors.add(new ImportRowErrorResponse("warehouse_code", "仓库已停用，不能导入库位"));
            }
        }
        if (warehouse != null && locationCode != null) {
            String duplicateKey = warehouse.getId() + "|" + locationCode;
            support.duplicateInFile(seen(context, "locationCode"), duplicateKey, "location_code", errors);
            Long count = locationMapper.selectCount(new LambdaQueryWrapper<LocationEntity>()
                    .eq(LocationEntity::getCompanyId, context.companyId())
                    .eq(LocationEntity::getAccountBookId, context.accountBookId())
                    .eq(LocationEntity::getWarehouseId, warehouse.getId())
                    .eq(LocationEntity::getLocationCode, locationCode)
                    .eq(LocationEntity::getDeletedFlag, 0));
            if (exists(count)) {
                errors.add(new ImportRowErrorResponse("location_code", "同一仓库下库位编码已存在"));
            }
        }
        boolean isDefault = support.optionalFlag(raw, "is_default", false, errors);
        String status = support.optionalText(raw, "status", "ACTIVE");
        if (StringUtils.hasText(status)) {
            status = status.trim().toUpperCase(Locale.ROOT);
            if (!"ACTIVE".equals(status)) {
                // LocationService.create always inserts ACTIVE; importing inactive locations is not supported.
                errors.add(new ImportRowErrorResponse("status", "库位导入仅支持ACTIVE，停用请在库位管理中操作"));
            }
        }
        normalized.put("warehouseId", warehouse == null ? null : warehouse.getId());
        normalized.put("locationCode", locationCode);
        normalized.put("locationName", locationName == null ? null : locationName.trim());
        normalized.put("isDefault", isDefault);
        normalized.put("status", "ACTIVE");
        normalized.put("remark", support.optionalText(raw, "remark"));
        return new ImportRowPlan(normalized, errors);
    }

    @Override
    public int commit(ImportJobEntity job, List<ImportJobRowEntity> rows, AuditMetadata audit) {
        for (ImportJobRowEntity row : rows) {
            Map<String, Object> normalized = normalized(row);
            boolean isDefault = Boolean.TRUE.equals(normalized.get("isDefault"))
                    || "true".equalsIgnoreCase(String.valueOf(normalized.get("isDefault")))
                    || "1".equals(String.valueOf(normalized.get("isDefault")));
            locationService.create(new LocationCreateRequest(
                    longValue(normalized, "warehouseId"),
                    text(normalized, "locationCode"),
                    text(normalized, "locationName"),
                    isDefault,
                    text(normalized, "remark")
            ));
        }
        return rows.size();
    }
}

package com.tuowei.erp.masterdata.warehouse.service;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class WarehouseReferenceValidator {
    private final WarehouseMapper warehouseMapper;
    public WarehouseReferenceValidator(WarehouseMapper warehouseMapper) { this.warehouseMapper = warehouseMapper; }

    public void requireActive(Long id, AuditMetadata audit) {
        if (id == null) return;
        WarehouseEntity warehouse = warehouseMapper.selectById(id);
        if (warehouse == null || !Objects.equals(warehouse.getDeletedFlag(), 0)
                || !Objects.equals(warehouse.getCompanyId(), audit.companyId())
                || !Objects.equals(warehouse.getAccountBookId(), audit.accountBookId())
                || !"ACTIVE".equals(warehouse.getStatus())) {
            throw new IllegalArgumentException("仓库不存在或未启用");
        }
    }
}

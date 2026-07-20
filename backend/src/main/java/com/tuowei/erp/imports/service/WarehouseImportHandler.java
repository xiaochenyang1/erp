package com.tuowei.erp.imports.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.imports.model.ImportJobEntity;
import com.tuowei.erp.imports.model.ImportJobRowEntity;
import com.tuowei.erp.imports.web.ImportRowErrorResponse;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.dept.model.DeptEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class WarehouseImportHandler extends AbstractImportHandler {

    private final WarehouseMapper warehouseMapper;
    private final DeptMapper deptMapper;
    private final UserMapper userMapper;

    public WarehouseImportHandler(
            ImportValidationSupport support,
            WarehouseMapper warehouseMapper,
            DeptMapper deptMapper,
            UserMapper userMapper
    ) {
        super(support);
        this.warehouseMapper = warehouseMapper;
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
    }

    @Override
    public String importType() {
        return ImportConstants.WAREHOUSE;
    }

    @Override
    public ImportRowPlan validate(int rowNo, Map<String, String> raw, ImportValidationContext context) {
        List<ImportRowErrorResponse> errors = support.errorList();
        Map<String, Object> normalized = support.linkedMap();
        String warehouseCode = support.required(raw, "warehouse_code", errors);
        String warehouseName = support.required(raw, "warehouse_name", errors);
        if (warehouseCode != null) {
            support.duplicateInFile(seen(context, "warehouseCode"), warehouseCode, "warehouse_code", errors);
            Long count = warehouseMapper.selectCount(new LambdaQueryWrapper<WarehouseEntity>()
                    .eq(WarehouseEntity::getCompanyId, context.companyId())
                    .eq(WarehouseEntity::getAccountBookId, context.accountBookId())
                    .eq(WarehouseEntity::getWarehouseCode, warehouseCode)
                    .eq(WarehouseEntity::getDeletedFlag, 0));
            if (exists(count)) {
                errors.add(new ImportRowErrorResponse("warehouse_code", "仓库编码已存在"));
            }
        }
        Long deptId = support.optionalLong(raw, "dept_id", errors);
        if (deptId != null) {
            Long count = deptMapper.selectCount(new LambdaQueryWrapper<DeptEntity>()
                    .eq(DeptEntity::getCompanyId, context.companyId())
                    .eq(DeptEntity::getAccountBookId, context.accountBookId())
                    .eq(DeptEntity::getId, deptId)
                    .eq(DeptEntity::getDeletedFlag, 0));
            if (!exists(count)) {
                errors.add(new ImportRowErrorResponse("dept_id", "部门不存在或不属于当前公司"));
            }
        }
        Long managerUserId = support.optionalLong(raw, "manager_user_id", errors);
        if (managerUserId != null) {
            Long count = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                    .eq(UserEntity::getCompanyId, context.companyId())
                    .eq(UserEntity::getAccountBookId, context.accountBookId())
                    .eq(UserEntity::getId, managerUserId)
                    .eq(UserEntity::getDeletedFlag, 0));
            if (!exists(count)) {
                errors.add(new ImportRowErrorResponse("manager_user_id", "负责人不存在或不属于当前公司"));
            }
        }
        normalized.put("warehouseCode", warehouseCode);
        normalized.put("warehouseName", warehouseName);
        normalized.put("deptId", deptId);
        normalized.put("managerUserId", managerUserId);
        normalized.put("address", support.optionalText(raw, "address"));
        normalized.put("status", support.optionalText(raw, "status", "ACTIVE"));
        normalized.put("remark", support.optionalText(raw, "remark"));
        return new ImportRowPlan(normalized, errors);
    }

    @Override
    public int commit(ImportJobEntity job, List<ImportJobRowEntity> rows, AuditMetadata audit) {
        LocalDateTime now = audit.now();
        for (ImportJobRowEntity row : rows) {
            Map<String, Object> normalized = normalized(row);
            WarehouseEntity entity = new WarehouseEntity();
            entity.setCompanyId(audit.companyId());
            entity.setAccountBookId(audit.accountBookId());
            entity.setWarehouseCode(text(normalized, "warehouseCode"));
            entity.setWarehouseName(text(normalized, "warehouseName"));
            entity.setDeptId(longValue(normalized, "deptId"));
            entity.setManagerUserId(longValue(normalized, "managerUserId"));
            entity.setAddress(text(normalized, "address"));
            entity.setStatus(text(normalized, "status"));
            entity.setDeletedFlag(0);
            entity.setRemark(text(normalized, "remark"));
            entity.setCreatedBy(audit.userId());
            entity.setCreatedTime(now);
            entity.setUpdatedBy(audit.userId());
            entity.setUpdatedTime(now);
            entity.setVersion(0);
            warehouseMapper.insert(entity);
        }
        return rows.size();
    }
}

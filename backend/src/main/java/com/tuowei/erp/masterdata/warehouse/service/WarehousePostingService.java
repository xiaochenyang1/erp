package com.tuowei.erp.masterdata.warehouse.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.location.service.LocationService;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseCreateRequest;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseResponse;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseUpdateRequest;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.dept.model.DeptEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

/** Write-side create/update/state-transition for warehouses. */
@Service
public class WarehousePostingService {

    private final WarehouseMapper warehouseMapper;
    private final DeptMapper deptMapper;
    private final UserMapper userMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final LocationService locationService;
    private final WarehouseQueryService warehouseQueryService;

    public WarehousePostingService(
            WarehouseMapper warehouseMapper,
            DeptMapper deptMapper,
            UserMapper userMapper,
            AuditMetadataFactory auditMetadataFactory,
            LocationService locationService,
            WarehouseQueryService warehouseQueryService
    ) {
        this.warehouseMapper = warehouseMapper;
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.locationService = locationService;
        this.warehouseQueryService = warehouseQueryService;
    }

    @Transactional
    public WarehouseResponse create(WarehouseCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        requireDept(request.deptId(), audit);
        requireManager(request.managerUserId(), audit);
        LocalDateTime now = audit.now();

        WarehouseEntity entity = new WarehouseEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setWarehouseCode(request.warehouseCode());
        entity.setWarehouseName(request.warehouseName());
        entity.setDeptId(request.deptId());
        entity.setManagerUserId(request.managerUserId());
        entity.setAddress(request.address());
        entity.setStatus("ACTIVE");
        entity.setDeletedFlag(0);
        entity.setRemark(request.remark());
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);

        warehouseMapper.insert(entity);
        locationService.ensureDefaultLocation(entity, audit);
        return warehouseQueryService.toResponse(entity);
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseUpdateRequest request) {
        WarehouseEntity entity = warehouseQueryService.requireWarehouse(id);
        AuditMetadata audit = auditMetadataFactory.current();
        requireDept(request.deptId(), audit);
        requireManager(request.managerUserId(), audit);

        entity.setWarehouseName(request.warehouseName());
        entity.setDeptId(request.deptId());
        entity.setManagerUserId(request.managerUserId());
        entity.setAddress(request.address());
        entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(warehouseMapper.updateById(entity), "仓库已被其他操作修改，请刷新后重试");
        return warehouseQueryService.toResponse(entity);
    }

    @Transactional
    public WarehouseResponse enable(Long id) {
        return updateStatus(id, "ACTIVE");
    }

    @Transactional
    public WarehouseResponse disable(Long id) {
        return updateStatus(id, "INACTIVE");
    }

    private WarehouseResponse updateStatus(Long id, String status) {
        WarehouseEntity entity = warehouseQueryService.requireWarehouse(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(warehouseMapper.updateById(entity), "仓库已被其他操作修改，请刷新后重试");
        return warehouseQueryService.toResponse(entity);
    }

    private void requireDept(Long deptId, AuditMetadata audit) {
        DeptEntity entity = deptMapper.selectById(deptId);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("部门不存在");
        }
    }

    private void requireManager(Long managerUserId, AuditMetadata audit) {
        UserEntity entity = userMapper.selectById(managerUserId);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("负责人不存在");
        }
    }
}

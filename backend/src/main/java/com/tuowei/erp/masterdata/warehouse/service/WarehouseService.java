package com.tuowei.erp.masterdata.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.location.service.LocationService;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseCreateRequest;
import com.tuowei.erp.masterdata.warehouse.web.WarehousePageQuery;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseResponse;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseUpdateRequest;
import com.tuowei.erp.system.dept.mapper.DeptMapper;
import com.tuowei.erp.system.dept.model.DeptEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class WarehouseService {

    private static final List<String> WAREHOUSE_EXPORT_HEADERS = List.of(
            "warehouseCode",
            "warehouseName",
            "deptId",
            "managerUserId",
            "address",
            "status",
            "remark"
    );

    private final WarehouseMapper warehouseMapper;
    private final DeptMapper deptMapper;
    private final UserMapper userMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final LocationService locationService;

    public WarehouseService(WarehouseMapper warehouseMapper,
                            DeptMapper deptMapper,
                            UserMapper userMapper,
                            AuditMetadataFactory auditMetadataFactory,
                            LocationService locationService) {
        this.warehouseMapper = warehouseMapper;
        this.deptMapper = deptMapper;
        this.userMapper = userMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.locationService = locationService;
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
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public WarehouseResponse getById(Long id) {
        return toResponse(requireWarehouse(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<WarehouseResponse> list(WarehousePageQuery query) {
        WarehousePageQuery safeQuery = query == null ? new WarehousePageQuery() : query;
        long pageNo = normalizePageNo(safeQuery.getPageNo());
        long pageSize = normalizePageSize(safeQuery.getPageSize());
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String status = normalizeStatus(safeQuery.getStatus());
        AuditMetadata audit = auditMetadataFactory.current();

        Page<WarehouseEntity> page = new Page<>(pageNo, pageSize);
        Page<WarehouseEntity> result = warehouseMapper.selectPage(
                page,
                buildListQuery(audit.companyId(), audit.accountBookId(), keyword, status, safeQuery.getDeptId(), safeQuery.getManagerUserId())
        );

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    public StreamingResponseBody exportWarehouses(WarehousePageQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        WarehousePageQuery safeQuery = query == null ? new WarehousePageQuery() : query;
        return outputStream -> withAuthentication(authentication, () -> CsvExport.write(outputStream, WAREHOUSE_EXPORT_HEADERS, rowWriter -> {
            String keyword = normalizeNullableText(safeQuery.getKeyword());
            String status = normalizeStatus(safeQuery.getStatus());
            AuditMetadata audit = auditMetadataFactory.current();
            List<WarehouseEntity> warehouses = warehouseMapper.selectList(
                    buildListQuery(
                            audit.companyId(),
                            audit.accountBookId(),
                            keyword,
                            status,
                            safeQuery.getDeptId(),
                            safeQuery.getManagerUserId()
                    )
            );
            for (WarehouseEntity entity : warehouses) {
                rowWriter.write(warehouseExportRow(toResponse(entity)));
            }
        }));
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseUpdateRequest request) {
        WarehouseEntity entity = requireWarehouse(id);
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
        return toResponse(entity);
    }

    @Transactional
    public WarehouseResponse enable(Long id) {
        return updateStatus(id, "ACTIVE");
    }

    @Transactional
    public WarehouseResponse disable(Long id) {
        return updateStatus(id, "INACTIVE");
    }

    private WarehouseEntity requireWarehouse(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        WarehouseEntity entity = warehouseMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("仓库不存在");
        }
        return entity;
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

    private WarehouseResponse updateStatus(Long id, String status) {
        WarehouseEntity entity = requireWarehouse(id);
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus(status);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(warehouseMapper.updateById(entity), "仓库已被其他操作修改，请刷新后重试");
        return toResponse(entity);
    }

    private LambdaQueryWrapper<WarehouseEntity> buildListQuery(
            Long companyId,
            Long accountBookId,
            String keyword,
            String status,
            Long deptId,
            Long managerUserId
    ) {
        LambdaQueryWrapper<WarehouseEntity> wrapper = new LambdaQueryWrapper<WarehouseEntity>()
                .eq(WarehouseEntity::getCompanyId, companyId)
                .eq(WarehouseEntity::getAccountBookId, accountBookId)
                .eq(WarehouseEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(query -> query.like(WarehouseEntity::getWarehouseCode, keyword)
                    .or()
                    .like(WarehouseEntity::getWarehouseName, keyword));
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(WarehouseEntity::getStatus, status);
        }
        if (deptId != null) {
            wrapper.eq(WarehouseEntity::getDeptId, deptId);
        }
        if (managerUserId != null) {
            wrapper.eq(WarehouseEntity::getManagerUserId, managerUserId);
        }
        return wrapper.orderByAsc(WarehouseEntity::getWarehouseCode);
    }

    private List<?> warehouseExportRow(WarehouseResponse record) {
        return Arrays.asList(
                record.warehouseCode(),
                record.warehouseName(),
                record.deptId(),
                record.managerUserId(),
                record.address(),
                record.status(),
                record.remark()
        );
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

    private WarehouseResponse toResponse(WarehouseEntity entity) {
        return new WarehouseResponse(
                entity.getId(),
                entity.getWarehouseCode(),
                entity.getWarehouseName(),
                entity.getDeptId(),
                entity.getManagerUserId(),
                entity.getAddress(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private void withAuthentication(Authentication authentication, ThrowingRunnable action) throws IOException {
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            action.run();
        } finally {
            if (previousAuthentication == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws IOException;
    }

}

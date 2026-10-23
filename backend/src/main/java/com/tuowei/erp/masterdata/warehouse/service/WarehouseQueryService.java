package com.tuowei.erp.masterdata.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.masterdata.warehouse.web.WarehousePageQuery;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Read-side filtering, tenant guards, response mapping and warehouse export. */
@Service
public class WarehouseQueryService {

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
    private final AuditMetadataFactory auditMetadataFactory;

    public WarehouseQueryService(WarehouseMapper warehouseMapper, AuditMetadataFactory auditMetadataFactory) {
        this.warehouseMapper = warehouseMapper;
        this.auditMetadataFactory = auditMetadataFactory;
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
                buildListQuery(
                        audit.companyId(),
                        audit.accountBookId(),
                        keyword,
                        status,
                        safeQuery.getDeptId(),
                        safeQuery.getManagerUserId()
                )
        );

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    /**
     * CSV output stays outside a transaction because the callback runs after
     * the controller returns. It restores the caller's security context for
     * the tenant-scoped read and restores the callback thread afterwards.
     */
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

    WarehouseEntity requireWarehouse(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        WarehouseEntity entity = warehouseMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("仓库不存在");
        }
        return entity;
    }

    WarehouseResponse toResponse(WarehouseEntity entity) {
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

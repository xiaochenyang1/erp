package com.tuowei.erp.inventory.check.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.check.mapper.InventoryStockCheckLineMapper;
import com.tuowei.erp.inventory.check.mapper.InventoryStockCheckMapper;
import com.tuowei.erp.inventory.check.model.InventoryStockCheckEntity;
import com.tuowei.erp.inventory.check.model.InventoryStockCheckLineEntity;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckLineResponse;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckPageQuery;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class InventoryStockCheckQueryService {

    private final InventoryStockCheckMapper checkMapper;
    private final InventoryStockCheckLineMapper lineMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public InventoryStockCheckQueryService(
            InventoryStockCheckMapper checkMapper,
            InventoryStockCheckLineMapper lineMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.checkMapper = checkMapper;
        this.lineMapper = lineMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryStockCheckResponse> list(InventoryStockCheckPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryStockCheckPageQuery safeQuery = query == null ? new InventoryStockCheckPageQuery() : query;

        LambdaQueryWrapper<InventoryStockCheckEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryStockCheckEntity::getCompanyId, audit.companyId())
                .eq(InventoryStockCheckEntity::getAccountBookId, audit.accountBookId())
                .eq(InventoryStockCheckEntity::getDeletedFlag, 0);
        if (hasText(safeQuery.getCheckNo())) {
            wrapper.like(InventoryStockCheckEntity::getCheckNo, safeQuery.getCheckNo().trim());
        }
        if (safeQuery.getWarehouseId() != null) {
            wrapper.eq(InventoryStockCheckEntity::getWarehouseId, safeQuery.getWarehouseId());
        }
        if (hasText(safeQuery.getStatus())) {
            wrapper.eq(InventoryStockCheckEntity::getStatus, safeQuery.getStatus().trim());
        }
        if (safeQuery.getDateFrom() != null) {
            wrapper.ge(InventoryStockCheckEntity::getCheckDate, safeQuery.getDateFrom());
        }
        if (safeQuery.getDateTo() != null) {
            wrapper.le(InventoryStockCheckEntity::getCheckDate, safeQuery.getDateTo());
        }
        wrapper.orderByDesc(InventoryStockCheckEntity::getCreatedTime);

        Page<InventoryStockCheckEntity> page = new Page<>(safeQuery.getPageNo(), safeQuery.getPageSize());
        IPage<InventoryStockCheckEntity> result = checkMapper.selectPage(page, wrapper);
        Map<Long, List<InventoryStockCheckLineEntity>> linesByCheck = selectLinesForPage(audit, result.getRecords());
        List<InventoryStockCheckResponse> responses = result.getRecords().stream()
                .map(check -> toResponse(check, linesByCheck.getOrDefault(check.getId(), List.of())))
                .toList();
        return new PageResponse<>(result.getCurrent(), result.getSize(), result.getTotal(), responses);
    }

    @Transactional(readOnly = true)
    public InventoryStockCheckResponse getById(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryStockCheckEntity check = checkMapper.selectById(id);
        if (check == null || Integer.valueOf(1).equals(check.getDeletedFlag())
                || !Objects.equals(check.getCompanyId(), audit.companyId())
                || !Objects.equals(check.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("库存盘点单不存在");
        }
        return toResponse(check, selectLines(check));
    }

    private List<InventoryStockCheckLineEntity> selectLines(InventoryStockCheckEntity check) {
        return lineMapper.selectList(new LambdaQueryWrapper<InventoryStockCheckLineEntity>()
                .eq(InventoryStockCheckLineEntity::getCompanyId, check.getCompanyId())
                .eq(InventoryStockCheckLineEntity::getAccountBookId, check.getAccountBookId())
                .eq(InventoryStockCheckLineEntity::getCheckId, check.getId())
                .orderByAsc(InventoryStockCheckLineEntity::getLineNo));
    }

    private Map<Long, List<InventoryStockCheckLineEntity>> selectLinesForPage(
            AuditMetadata audit, List<InventoryStockCheckEntity> checks) {
        if (checks.isEmpty()) {
            return Map.of();
        }
        List<Long> checkIds = checks.stream().map(InventoryStockCheckEntity::getId).toList();
        List<InventoryStockCheckLineEntity> allLines = lineMapper.selectList(
                new LambdaQueryWrapper<InventoryStockCheckLineEntity>()
                        .eq(InventoryStockCheckLineEntity::getCompanyId, audit.companyId())
                        .eq(InventoryStockCheckLineEntity::getAccountBookId, audit.accountBookId())
                        .in(InventoryStockCheckLineEntity::getCheckId, checkIds)
                        .orderByAsc(InventoryStockCheckLineEntity::getLineNo));
        return allLines.stream().collect(Collectors.groupingBy(InventoryStockCheckLineEntity::getCheckId));
    }

    static InventoryStockCheckResponse toResponse(
            InventoryStockCheckEntity check,
            List<InventoryStockCheckLineEntity> lineEntities
    ) {
        List<InventoryStockCheckLineResponse> lines = lineEntities.stream()
                .map(line -> new InventoryStockCheckLineResponse(
                        line.getId(), line.getLineNo(), line.getProductId(), line.getLocationId(),
                        line.getBookQty(), line.getActualQty(), line.getDifferenceQty(), line.getUnitCost(),
                        line.getDifferenceAmount(), line.getLotNo(), line.getProductionDate(),
                        line.getExpiryDate(), line.getSerialNos(), line.getRemark()
                ))
                .toList();
        return new InventoryStockCheckResponse(
                check.getId(), check.getCheckNo(), check.getWarehouseId(), check.getCheckDate(), check.getStatus(),
                check.getGeneratedAdjustmentId(), check.getGeneratedAdjustmentNo(), check.getRemark(), lines
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

package com.tuowei.erp.production.routing.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.production.bom.mapper.ProductionBomMapper;
import com.tuowei.erp.production.bom.model.ProductionBomEntity;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingMapper;
import com.tuowei.erp.production.routing.mapper.ProductionRoutingOperationMapper;
import com.tuowei.erp.production.routing.model.ProductionRoutingEntity;
import com.tuowei.erp.production.routing.model.ProductionRoutingOperationEntity;
import com.tuowei.erp.production.routing.web.ProductionRoutingOperationResponse;
import com.tuowei.erp.production.routing.web.ProductionRoutingPageQuery;
import com.tuowei.erp.production.routing.web.ProductionRoutingResponse;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Read-side filtering, tenant guards and display hydration for production routings. */
@Service
public class ProductionRoutingQueryService {

    private final ProductionRoutingMapper routingMapper;
    private final ProductionRoutingOperationMapper routingOperationMapper;
    private final ProductionBomMapper bomMapper;
    private final ProductionWorkCenterMapper workCenterMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public ProductionRoutingQueryService(
            ProductionRoutingMapper routingMapper,
            ProductionRoutingOperationMapper routingOperationMapper,
            ProductionBomMapper bomMapper,
            ProductionWorkCenterMapper workCenterMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.routingMapper = routingMapper;
        this.routingOperationMapper = routingOperationMapper;
        this.bomMapper = bomMapper;
        this.workCenterMapper = workCenterMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public ProductionRoutingResponse getById(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        return toResponse(requireRouting(id, audit.companyId(), audit.accountBookId()), audit.companyId(), audit.accountBookId());
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductionRoutingResponse> list(ProductionRoutingPageQuery query) {
        ProductionRoutingPageQuery safeQuery = query == null ? new ProductionRoutingPageQuery() : query;
        AuditMetadata audit = auditMetadataFactory.current();
        Page<ProductionRoutingEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        LambdaQueryWrapper<ProductionRoutingEntity> wrapper = new LambdaQueryWrapper<ProductionRoutingEntity>()
                .eq(ProductionRoutingEntity::getCompanyId, audit.companyId())
                .eq(ProductionRoutingEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionRoutingEntity::getDeletedFlag, 0);
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.and(queryWrapper -> queryWrapper.like(ProductionRoutingEntity::getRoutingCode, keyword)
                    .or()
                    .like(ProductionRoutingEntity::getRoutingName, keyword));
        }
        String status = normalizeStatus(safeQuery.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(ProductionRoutingEntity::getStatus, status);
        }
        if (safeQuery.getBomId() != null) {
            wrapper.eq(ProductionRoutingEntity::getBomId, safeQuery.getBomId());
        }
        wrapper.orderByAsc(ProductionRoutingEntity::getRoutingCode);
        Page<ProductionRoutingEntity> result = routingMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(entity -> toResponse(entity, audit.companyId(), audit.accountBookId()))
                        .toList()
        );
    }

    ProductionRoutingEntity requireRouting(Long id, Long companyId, Long accountBookId) {
        ProductionRoutingEntity entity = routingMapper.selectById(id);
        if (entity == null
                || !Objects.equals(entity.getCompanyId(), companyId)
                || !Objects.equals(entity.getAccountBookId(), accountBookId)
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("工艺路线不存在");
        }
        return entity;
    }

    ProductionRoutingResponse toResponse(ProductionRoutingEntity entity, Long companyId, Long accountBookId) {
        ProductionBomEntity bom = findBom(entity.getBomId(), companyId, accountBookId);
        List<ProductionRoutingOperationEntity> operations = routingOperationMapper.selectList(
                new LambdaQueryWrapper<ProductionRoutingOperationEntity>()
                        .eq(ProductionRoutingOperationEntity::getCompanyId, companyId)
                        .eq(ProductionRoutingOperationEntity::getAccountBookId, accountBookId)
                        .eq(ProductionRoutingOperationEntity::getRoutingId, entity.getId())
                        .orderByAsc(ProductionRoutingOperationEntity::getLineNo)
        );
        Map<Long, ProductionWorkCenterEntity> workCenters = loadWorkCenters(operations, companyId, accountBookId);
        return new ProductionRoutingResponse(
                entity.getId(),
                entity.getRoutingCode(),
                entity.getRoutingName(),
                entity.getBomId(),
                bom == null ? null : bom.getBomNo(),
                bom == null ? null : bom.getProductId(),
                entity.getStatus(),
                entity.getRemark(),
                operations.stream().map(operation -> {
                    ProductionWorkCenterEntity workCenter = workCenters.get(operation.getWorkCenterId());
                    return new ProductionRoutingOperationResponse(
                            operation.getId(),
                            operation.getLineNo(),
                            operation.getOperationCode(),
                            operation.getOperationName(),
                            operation.getWorkCenterId(),
                            workCenter == null ? null : workCenter.getWorkCenterCode(),
                            workCenter == null ? null : workCenter.getWorkCenterName(),
                            operation.getStandardMinutes(),
                            operation.getRemark()
                    );
                }).toList()
        );
    }

    private Map<Long, ProductionWorkCenterEntity> loadWorkCenters(
            List<ProductionRoutingOperationEntity> operations,
            Long companyId,
            Long accountBookId
    ) {
        if (operations.isEmpty()) {
            return Map.of();
        }
        List<Long> workCenterIds = operations.stream()
                .map(ProductionRoutingOperationEntity::getWorkCenterId)
                .distinct()
                .toList();
        Map<Long, ProductionWorkCenterEntity> workCenters = new HashMap<>();
        workCenterMapper.selectList(new LambdaQueryWrapper<ProductionWorkCenterEntity>()
                        .eq(ProductionWorkCenterEntity::getCompanyId, companyId)
                        .eq(ProductionWorkCenterEntity::getAccountBookId, accountBookId)
                        .eq(ProductionWorkCenterEntity::getDeletedFlag, 0)
                        .in(ProductionWorkCenterEntity::getId, workCenterIds))
                .forEach(workCenter -> workCenters.put(workCenter.getId(), workCenter));
        return workCenters;
    }

    private ProductionBomEntity findBom(Long bomId, Long companyId, Long accountBookId) {
        ProductionBomEntity bom = bomId == null ? null : bomMapper.selectById(bomId);
        if (bom == null
                || !Objects.equals(bom.getCompanyId(), companyId)
                || !Objects.equals(bom.getAccountBookId(), accountBookId)
                || bom.getDeletedFlag() == null
                || bom.getDeletedFlag() != 0) {
            return null;
        }
        return bom;
    }

    private String normalizeNullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeNullableText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}

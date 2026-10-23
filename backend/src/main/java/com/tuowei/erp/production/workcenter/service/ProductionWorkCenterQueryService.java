package com.tuowei.erp.production.workcenter.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.production.workcenter.mapper.ProductionWorkCenterMapper;
import com.tuowei.erp.production.workcenter.model.ProductionWorkCenterEntity;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterPageQuery;
import com.tuowei.erp.production.workcenter.web.ProductionWorkCenterResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Objects;

/** Read-side work-center detail and listing, plus shared tenant guard, response mapping and text normalization reused by the command side. */
@Service
public class ProductionWorkCenterQueryService {

    private final ProductionWorkCenterMapper workCenterMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public ProductionWorkCenterQueryService(
            ProductionWorkCenterMapper workCenterMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.workCenterMapper = workCenterMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public ProductionWorkCenterResponse getById(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        return toResponse(requireWorkCenter(id, audit.companyId(), audit.accountBookId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductionWorkCenterResponse> list(ProductionWorkCenterPageQuery query) {
        ProductionWorkCenterPageQuery safeQuery = query == null ? new ProductionWorkCenterPageQuery() : query;
        AuditMetadata audit = auditMetadataFactory.current();
        Page<ProductionWorkCenterEntity> page = new Page<>(
                normalizePageNo(safeQuery.getPageNo()),
                normalizePageSize(safeQuery.getPageSize())
        );
        LambdaQueryWrapper<ProductionWorkCenterEntity> wrapper = new LambdaQueryWrapper<ProductionWorkCenterEntity>()
                .eq(ProductionWorkCenterEntity::getCompanyId, audit.companyId())
                .eq(ProductionWorkCenterEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionWorkCenterEntity::getDeletedFlag, 0);
        String keyword = normalizeNullable(safeQuery.getKeyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.and(queryWrapper -> queryWrapper.like(ProductionWorkCenterEntity::getWorkCenterCode, keyword)
                    .or()
                    .like(ProductionWorkCenterEntity::getWorkCenterName, keyword));
        }
        String status = normalizeStatus(safeQuery.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(ProductionWorkCenterEntity::getStatus, status);
        }
        wrapper.orderByAsc(ProductionWorkCenterEntity::getWorkCenterCode);
        Page<ProductionWorkCenterEntity> result = workCenterMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    ProductionWorkCenterEntity requireWorkCenter(Long id, Long companyId, Long accountBookId) {
        ProductionWorkCenterEntity entity = workCenterMapper.selectById(id);
        if (entity == null
                || !Objects.equals(entity.getCompanyId(), companyId)
                || !Objects.equals(entity.getAccountBookId(), accountBookId)
                || entity.getDeletedFlag() == null
                || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("工作中心不存在");
        }
        return entity;
    }

    ProductionWorkCenterResponse toResponse(ProductionWorkCenterEntity entity) {
        return new ProductionWorkCenterResponse(
                entity.getId(),
                entity.getWorkCenterCode(),
                entity.getWorkCenterName(),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    String normalizeNullable(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}

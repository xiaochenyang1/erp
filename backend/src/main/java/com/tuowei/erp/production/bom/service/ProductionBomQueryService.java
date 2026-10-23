package com.tuowei.erp.production.bom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.production.bom.mapper.ProductionBomLineMapper;
import com.tuowei.erp.production.bom.mapper.ProductionBomMapper;
import com.tuowei.erp.production.bom.model.ProductionBomEntity;
import com.tuowei.erp.production.bom.model.ProductionBomLineEntity;
import com.tuowei.erp.production.bom.web.ProductionBomLineResponse;
import com.tuowei.erp.production.bom.web.ProductionBomPageQuery;
import com.tuowei.erp.production.bom.web.ProductionBomResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Read-side tenant guards, filtering and BOM line hydration. */
@Service
public class ProductionBomQueryService {
    private final ProductionBomMapper bomMapper;
    private final ProductionBomLineMapper lineMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public ProductionBomQueryService(ProductionBomMapper bomMapper, ProductionBomLineMapper lineMapper,
                                     AuditMetadataFactory auditMetadataFactory) {
        this.bomMapper = bomMapper;
        this.lineMapper = lineMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public ProductionBomResponse getById(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        return toResponse(requireBom(id, audit.companyId(), audit.accountBookId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductionBomResponse> list(ProductionBomPageQuery query) {
        ProductionBomPageQuery safeQuery = query == null ? new ProductionBomPageQuery() : query;
        AuditMetadata audit = auditMetadataFactory.current();
        Page<ProductionBomEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<ProductionBomEntity> wrapper = new LambdaQueryWrapper<ProductionBomEntity>()
                .eq(ProductionBomEntity::getCompanyId, audit.companyId())
                .eq(ProductionBomEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionBomEntity::getDeletedFlag, 0);
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        if (StringUtils.hasText(keyword)) wrapper.like(ProductionBomEntity::getBomNo, keyword);
        String status = normalizeStatus(safeQuery.getStatus());
        if (StringUtils.hasText(status)) wrapper.eq(ProductionBomEntity::getStatus, status);
        if (safeQuery.getProductId() != null) wrapper.eq(ProductionBomEntity::getProductId, safeQuery.getProductId());
        wrapper.orderByDesc(ProductionBomEntity::getId);
        Page<ProductionBomEntity> result = bomMapper.selectPage(page, wrapper);
        return new PageResponse<>(result.getCurrent(), result.getSize(), result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList());
    }

    public ProductionBomEntity requireBom(Long id, Long companyId, Long accountBookId) {
        ProductionBomEntity bom = bomMapper.selectById(id);
        if (bom == null || !Objects.equals(bom.getCompanyId(), companyId)
                || !Objects.equals(bom.getAccountBookId(), accountBookId)
                || Integer.valueOf(1).equals(bom.getDeletedFlag())) throw new IllegalArgumentException("BOM不存在");
        return bom;
    }

    public List<ProductionBomLineEntity> selectLines(Long bomId) {
        AuditMetadata audit = auditMetadataFactory.current();
        return lineMapper.selectList(new LambdaQueryWrapper<ProductionBomLineEntity>()
                .eq(ProductionBomLineEntity::getCompanyId, audit.companyId())
                .eq(ProductionBomLineEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionBomLineEntity::getBomId, bomId)
                .orderByAsc(ProductionBomLineEntity::getLineNo));
    }

    ProductionBomResponse toResponse(ProductionBomEntity bom) {
        return new ProductionBomResponse(bom.getId(), bom.getBomNo(), bom.getProductId(), bom.getBaseQty(), bom.getStatus(),
                bom.getRemark(), selectLines(bom.getId()).stream().map(line -> new ProductionBomLineResponse(
                        line.getId(), line.getLineNo(), line.getMaterialProductId(), line.getQtyPer(), line.getLossRate(), line.getRemark()
                )).toList());
    }

    private String normalizeNullableText(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private String normalizeStatus(String value) { String normalized = normalizeNullableText(value); return normalized == null ? null : normalized.toUpperCase(Locale.ROOT); }
    private long normalizePageNo(Integer value) { return value == null || value < 1 ? 1L : value; }
    private long normalizePageSize(Integer value) { return value == null || value < 1 ? 20L : Math.min(value, 200); }
}

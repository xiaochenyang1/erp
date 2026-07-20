package com.tuowei.erp.production.bom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.production.bom.mapper.ProductionBomLineMapper;
import com.tuowei.erp.production.bom.mapper.ProductionBomMapper;
import com.tuowei.erp.production.bom.model.ProductionBomEntity;
import com.tuowei.erp.production.bom.model.ProductionBomLineEntity;
import com.tuowei.erp.production.bom.web.ProductionBomCreateRequest;
import com.tuowei.erp.production.bom.web.ProductionBomLineRequest;
import com.tuowei.erp.production.bom.web.ProductionBomLineResponse;
import com.tuowei.erp.production.bom.web.ProductionBomPageQuery;
import com.tuowei.erp.production.bom.web.ProductionBomResponse;
import com.tuowei.erp.production.bom.web.ProductionBomUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class ProductionBomService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final ProductionBomMapper bomMapper;
    private final ProductionBomLineMapper lineMapper;
    private final ProductionBomNumberService numberService;
    private final ProductMapper productMapper;
    private final ProductValidator productValidator;
    private final AuditMetadataFactory auditMetadataFactory;

    public ProductionBomService(
            ProductionBomMapper bomMapper,
            ProductionBomLineMapper lineMapper,
            ProductionBomNumberService numberService,
            ProductMapper productMapper,
            ProductValidator productValidator,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.bomMapper = bomMapper;
        this.lineMapper = lineMapper;
        this.numberService = numberService;
        this.productMapper = productMapper;
        this.productValidator = productValidator;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional
    public ProductionBomResponse create(ProductionBomCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        productValidator.requireProduct(request.productId(), audit.companyId(), audit.accountBookId());
        validateBaseQty(request.baseQty());
        requireNoActiveBom(request.productId(), audit.companyId(), audit.accountBookId(), null);
        validateLines(request.productId(), request.lines(), audit.companyId(), audit.accountBookId());

        ProductionBomEntity bom = new ProductionBomEntity();
        bom.setCompanyId(audit.companyId());
        bom.setAccountBookId(audit.accountBookId());
        bom.setBomNo(numberService.nextBomNo(now.toLocalDate()));
        bom.setProductId(request.productId());
        bom.setBaseQty(ScalePrecision.quantity(request.baseQty()));
        bom.setStatus(STATUS_ACTIVE);
        bom.setDeletedFlag(0);
        bom.setRemark(request.remark());
        fillCreateAudit(bom, audit, now);
        bomMapper.insert(bom);

        insertLines(bom.getId(), request.lines(), audit, now);
        return toResponse(bom);
    }

    @Transactional
    public ProductionBomResponse update(Long id, ProductionBomUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ProductionBomEntity bom = requireBom(id, audit.companyId(), audit.accountBookId());
        validateBaseQty(request.baseQty());
        validateLines(bom.getProductId(), request.lines(), audit.companyId(), audit.accountBookId());

        bom.setBaseQty(ScalePrecision.quantity(request.baseQty()));
        bom.setRemark(request.remark());
        bom.setUpdatedBy(audit.userId());
        bom.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(bomMapper.updateById(bom), "BOM已被其他操作修改，请刷新后重试");
        lineMapper.delete(new LambdaQueryWrapper<ProductionBomLineEntity>()
                .eq(ProductionBomLineEntity::getCompanyId, audit.companyId())
                .eq(ProductionBomLineEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionBomLineEntity::getBomId, id));
        insertLines(id, request.lines(), audit, now);
        return toResponse(bomMapper.selectById(id));
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
        if (StringUtils.hasText(keyword)) {
            wrapper.like(ProductionBomEntity::getBomNo, keyword);
        }
        String status = normalizeStatus(safeQuery.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(ProductionBomEntity::getStatus, status);
        }
        if (safeQuery.getProductId() != null) {
            wrapper.eq(ProductionBomEntity::getProductId, safeQuery.getProductId());
        }
        wrapper.orderByDesc(ProductionBomEntity::getId);
        Page<ProductionBomEntity> result = bomMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    public ProductionBomEntity requireBom(Long id, Long companyId, Long accountBookId) {
        ProductionBomEntity bom = bomMapper.selectById(id);
        if (bom == null || !Objects.equals(bom.getCompanyId(), companyId)
                || !Objects.equals(bom.getAccountBookId(), accountBookId)
                || Integer.valueOf(1).equals(bom.getDeletedFlag())) {
            throw new IllegalArgumentException("BOM不存在");
        }
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

    private void validateBaseQty(BigDecimal baseQty) {
        if (ScalePrecision.quantity(ScalePrecision.zeroDefault(baseQty)).compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("BOM基准数量必须大于0");
        }
    }

    private void validateLines(Long productId, List<ProductionBomLineRequest> lines, Long companyId, Long accountBookId) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("BOM必须至少包含一条材料明细");
        }
        Set<Long> materialIds = new HashSet<>();
        productValidator.requireProducts(
                lines.stream().map(ProductionBomLineRequest::materialProductId).toList(),
                companyId, accountBookId);
        for (ProductionBomLineRequest line : lines) {
            if (Objects.equals(productId, line.materialProductId())) {
                throw new IllegalArgumentException("BOM材料不能和成品相同");
            }
            if (!materialIds.add(line.materialProductId())) {
                throw new IllegalArgumentException("BOM材料不能重复");
            }
            BigDecimal qtyPer = ScalePrecision.quantity(ScalePrecision.zeroDefault(line.qtyPer()));
            if (qtyPer.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("BOM材料用量必须大于0");
            }
            BigDecimal lossRate = ScalePrecision.rate(ScalePrecision.zeroDefault(line.lossRate()));
            if (lossRate.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("BOM损耗率不能小于0");
            }
        }
    }

    private void requireNoActiveBom(Long productId, Long companyId, Long accountBookId, Long excludedBomId) {
        LambdaQueryWrapper<ProductionBomEntity> wrapper = new LambdaQueryWrapper<ProductionBomEntity>()
                .eq(ProductionBomEntity::getCompanyId, companyId)
                .eq(ProductionBomEntity::getAccountBookId, accountBookId)
                .eq(ProductionBomEntity::getProductId, productId)
                .eq(ProductionBomEntity::getStatus, STATUS_ACTIVE)
                .eq(ProductionBomEntity::getDeletedFlag, 0);
        if (excludedBomId != null) {
            wrapper.ne(ProductionBomEntity::getId, excludedBomId);
        }
        if (bomMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("同一成品已存在启用BOM");
        }
    }

    private void insertLines(Long bomId, List<ProductionBomLineRequest> requests, AuditMetadata audit, LocalDateTime now) {
        int lineNo = 1;
        for (ProductionBomLineRequest request : requests) {
            ProductionBomLineEntity line = new ProductionBomLineEntity();
            line.setCompanyId(audit.companyId());
            line.setAccountBookId(audit.accountBookId());
            line.setBomId(bomId);
            line.setLineNo(lineNo++);
            line.setMaterialProductId(request.materialProductId());
            line.setQtyPer(ScalePrecision.quantity(request.qtyPer()));
            line.setLossRate(ScalePrecision.rate(ScalePrecision.zeroDefault(request.lossRate())));
            line.setRemark(request.remark());
            fillCreateAudit(line, audit, now);
            lineMapper.insert(line);
        }
    }

    private ProductionBomResponse toResponse(ProductionBomEntity bom) {
        return new ProductionBomResponse(
                bom.getId(),
                bom.getBomNo(),
                bom.getProductId(),
                bom.getBaseQty(),
                bom.getStatus(),
                bom.getRemark(),
                selectLines(bom.getId()).stream()
                        .map(line -> new ProductionBomLineResponse(
                                line.getId(),
                                line.getLineNo(),
                                line.getMaterialProductId(),
                                line.getQtyPer(),
                                line.getLossRate(),
                                line.getRemark()
                        ))
                        .toList()
        );
    }

    private void fillCreateAudit(ProductionBomEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private void fillCreateAudit(ProductionBomLineEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
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

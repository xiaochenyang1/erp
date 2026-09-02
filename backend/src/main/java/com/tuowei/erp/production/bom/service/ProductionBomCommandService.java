package com.tuowei.erp.production.bom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.production.bom.mapper.ProductionBomLineMapper;
import com.tuowei.erp.production.bom.mapper.ProductionBomMapper;
import com.tuowei.erp.production.bom.model.ProductionBomEntity;
import com.tuowei.erp.production.bom.model.ProductionBomLineEntity;
import com.tuowei.erp.production.bom.web.ProductionBomCreateRequest;
import com.tuowei.erp.production.bom.web.ProductionBomLineRequest;
import com.tuowei.erp.production.bom.web.ProductionBomResponse;
import com.tuowei.erp.production.bom.web.ProductionBomUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Creates and replaces production BOMs and their material lines. */
@Service
public class ProductionBomCommandService {
    private static final String STATUS_ACTIVE = "ACTIVE";
    private final ProductionBomMapper bomMapper;
    private final ProductionBomLineMapper lineMapper;
    private final ProductionBomNumberService numberService;
    private final ProductValidator productValidator;
    private final AuditMetadataFactory auditMetadataFactory;
    private final ProductionBomQueryService queryService;

    public ProductionBomCommandService(
            ProductionBomMapper bomMapper, ProductionBomLineMapper lineMapper,
            ProductionBomNumberService numberService, ProductValidator productValidator,
            AuditMetadataFactory auditMetadataFactory, ProductionBomQueryService queryService
    ) {
        this.bomMapper = bomMapper;
        this.lineMapper = lineMapper;
        this.numberService = numberService;
        this.productValidator = productValidator;
        this.auditMetadataFactory = auditMetadataFactory;
        this.queryService = queryService;
    }

    @Transactional
    public ProductionBomResponse create(ProductionBomCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        productValidator.requireProduct(request.productId(), audit.companyId(), audit.accountBookId());
        validateBaseQty(request.baseQty());
        requireNoActiveBom(request.productId(), audit.companyId(), audit.accountBookId());
        validateLines(request.productId(), request.lines(), audit.companyId(), audit.accountBookId());
        ProductionBomEntity bom = new ProductionBomEntity();
        bom.setCompanyId(audit.companyId()); bom.setAccountBookId(audit.accountBookId());
        bom.setBomNo(numberService.nextBomNo(now.toLocalDate())); bom.setProductId(request.productId());
        bom.setBaseQty(ScalePrecision.quantity(request.baseQty())); bom.setStatus(STATUS_ACTIVE);
        bom.setDeletedFlag(0); bom.setRemark(request.remark()); fillCreateAudit(bom, audit, now);
        bomMapper.insert(bom);
        insertLines(bom.getId(), request.lines(), audit, now);
        return queryService.toResponse(bom);
    }

    @Transactional
    public ProductionBomResponse update(Long id, ProductionBomUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ProductionBomEntity bom = queryService.requireBom(id, audit.companyId(), audit.accountBookId());
        validateBaseQty(request.baseQty());
        validateLines(bom.getProductId(), request.lines(), audit.companyId(), audit.accountBookId());
        bom.setBaseQty(ScalePrecision.quantity(request.baseQty())); bom.setRemark(request.remark());
        bom.setUpdatedBy(audit.userId()); bom.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(bomMapper.updateById(bom), "BOM已被其他操作修改，请刷新后重试");
        lineMapper.delete(new LambdaQueryWrapper<ProductionBomLineEntity>()
                .eq(ProductionBomLineEntity::getCompanyId, audit.companyId())
                .eq(ProductionBomLineEntity::getAccountBookId, audit.accountBookId())
                .eq(ProductionBomLineEntity::getBomId, id));
        insertLines(id, request.lines(), audit, now);
        return queryService.toResponse(queryService.requireBom(id, audit.companyId(), audit.accountBookId()));
    }

    private void validateBaseQty(BigDecimal baseQty) {
        if (ScalePrecision.quantity(ScalePrecision.zeroDefault(baseQty)).compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("BOM基准数量必须大于0");
    }

    private void validateLines(Long productId, List<ProductionBomLineRequest> lines, Long companyId, Long accountBookId) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("BOM必须至少包含一条材料明细");
        Set<Long> materialIds = new HashSet<>();
        productValidator.requireProducts(lines.stream().map(ProductionBomLineRequest::materialProductId).toList(), companyId, accountBookId);
        for (ProductionBomLineRequest line : lines) {
            if (Objects.equals(productId, line.materialProductId())) throw new IllegalArgumentException("BOM材料不能和成品相同");
            if (!materialIds.add(line.materialProductId())) throw new IllegalArgumentException("BOM材料不能重复");
            if (ScalePrecision.quantity(ScalePrecision.zeroDefault(line.qtyPer())).compareTo(BigDecimal.ZERO) <= 0)
                throw new IllegalArgumentException("BOM材料用量必须大于0");
            if (ScalePrecision.rate(ScalePrecision.zeroDefault(line.lossRate())).compareTo(BigDecimal.ZERO) < 0)
                throw new IllegalArgumentException("BOM损耗率不能小于0");
        }
    }

    private void requireNoActiveBom(Long productId, Long companyId, Long accountBookId) {
        if (bomMapper.selectCount(new LambdaQueryWrapper<ProductionBomEntity>()
                .eq(ProductionBomEntity::getCompanyId, companyId)
                .eq(ProductionBomEntity::getAccountBookId, accountBookId)
                .eq(ProductionBomEntity::getProductId, productId)
                .eq(ProductionBomEntity::getStatus, STATUS_ACTIVE)
                .eq(ProductionBomEntity::getDeletedFlag, 0)) > 0)
            throw new IllegalArgumentException("同一成品已存在启用BOM");
    }

    private void insertLines(Long bomId, List<ProductionBomLineRequest> requests, AuditMetadata audit, LocalDateTime now) {
        int lineNo = 1;
        for (ProductionBomLineRequest request : requests) {
            ProductionBomLineEntity line = new ProductionBomLineEntity();
            line.setCompanyId(audit.companyId()); line.setAccountBookId(audit.accountBookId());
            line.setBomId(bomId); line.setLineNo(lineNo++); line.setMaterialProductId(request.materialProductId());
            line.setQtyPer(ScalePrecision.quantity(request.qtyPer()));
            line.setLossRate(ScalePrecision.rate(ScalePrecision.zeroDefault(request.lossRate())));
            line.setRemark(request.remark()); fillCreateAudit(line, audit, now); lineMapper.insert(line);
        }
    }

    private void fillCreateAudit(ProductionBomEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId()); entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(now); entity.setVersion(0);
    }
    private void fillCreateAudit(ProductionBomLineEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId()); entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(now); entity.setVersion(0);
    }
}

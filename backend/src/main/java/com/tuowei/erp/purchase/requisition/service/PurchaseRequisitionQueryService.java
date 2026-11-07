package com.tuowei.erp.purchase.requisition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageQueryNormalizer;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.purchase.requisition.mapper.PurchaseRequisitionLineMapper;
import com.tuowei.erp.purchase.requisition.mapper.PurchaseRequisitionMapper;
import com.tuowei.erp.purchase.requisition.model.PurchaseRequisitionEntity;
import com.tuowei.erp.purchase.requisition.model.PurchaseRequisitionLineEntity;
import com.tuowei.erp.purchase.requisition.web.PurchaseRequisitionLineResponse;
import com.tuowei.erp.purchase.requisition.web.PurchaseRequisitionPageQuery;
import com.tuowei.erp.purchase.requisition.web.PurchaseRequisitionResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Read-side tenant guards, filtering and response assembly for purchase requisitions. */
@Service
public class PurchaseRequisitionQueryService {

    private final PurchaseRequisitionMapper requisitionMapper;
    private final PurchaseRequisitionLineMapper lineMapper;
    private final ProductMapper productMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public PurchaseRequisitionQueryService(
            PurchaseRequisitionMapper requisitionMapper,
            PurchaseRequisitionLineMapper lineMapper,
            ProductMapper productMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.requisitionMapper = requisitionMapper;
        this.lineMapper = lineMapper;
        this.productMapper = productMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PurchaseRequisitionResponse getById(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseRequisitionEntity entity = requireRequisition(id, audit);
        List<PurchaseRequisitionLineEntity> lines = loadLines(entity);
        return toResponse(entity, lines, loadProducts(lines, audit));
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseRequisitionResponse> list(PurchaseRequisitionPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseRequisitionPageQuery safe = query == null ? new PurchaseRequisitionPageQuery() : query;
        long pageNo = PageQueryNormalizer.normalizePageNo(
                safe.getPageNo() == null ? null : safe.getPageNo().intValue()
        );
        long pageSize = PageQueryNormalizer.normalizePageSize(
                safe.getPageSize() == null ? null : safe.getPageSize().intValue()
        );
        LambdaQueryWrapper<PurchaseRequisitionEntity> wrapper = new LambdaQueryWrapper<PurchaseRequisitionEntity>()
                .eq(PurchaseRequisitionEntity::getCompanyId, audit.companyId())
                .eq(PurchaseRequisitionEntity::getAccountBookId, audit.accountBookId())
                .eq(PurchaseRequisitionEntity::getDeletedFlag, 0)
                .orderByDesc(PurchaseRequisitionEntity::getCreatedTime)
                .orderByDesc(PurchaseRequisitionEntity::getId);
        if (StringUtils.hasText(safe.getStatus())) {
            wrapper.eq(
                    PurchaseRequisitionEntity::getStatus,
                    safe.getStatus().trim().toUpperCase(Locale.ROOT)
            );
        }
        if (StringUtils.hasText(safe.getKeyword())) {
            wrapper.like(PurchaseRequisitionEntity::getRequisitionNo, safe.getKeyword().trim());
        }

        Page<PurchaseRequisitionEntity> page = requisitionMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        if (page.getRecords().isEmpty()) {
            return new PageResponse<>(page.getCurrent(), page.getSize(), page.getTotal(), List.of());
        }
        Map<Long, List<PurchaseRequisitionLineEntity>> linesByRequisition = loadLinesForPage(
                page.getRecords(), audit
        );
        List<PurchaseRequisitionLineEntity> allLines = linesByRequisition.values().stream()
                .flatMap(Collection::stream)
                .toList();
        Map<Long, ProductEntity> products = loadProducts(allLines, audit);
        List<PurchaseRequisitionResponse> records = page.getRecords().stream()
                .map(entity -> toResponse(
                        entity,
                        linesByRequisition.getOrDefault(entity.getId(), List.of()),
                        products
                ))
                .toList();
        return new PageResponse<>(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    PurchaseRequisitionEntity requireRequisition(Long id, AuditMetadata audit) {
        PurchaseRequisitionEntity entity = requisitionMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("请购单不存在");
        }
        return entity;
    }

    ProductEntity requireProduct(Long productId, AuditMetadata audit) {
        ProductEntity product = productMapper.selectById(productId);
        if (product == null || product.getDeletedFlag() == null || product.getDeletedFlag() != 0
                || !Objects.equals(product.getCompanyId(), audit.companyId())
                || !Objects.equals(product.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("商品不存在");
        }
        return product;
    }

    List<PurchaseRequisitionLineEntity> loadLines(PurchaseRequisitionEntity entity) {
        return lineMapper.selectList(new LambdaQueryWrapper<PurchaseRequisitionLineEntity>()
                .eq(PurchaseRequisitionLineEntity::getCompanyId, entity.getCompanyId())
                .eq(PurchaseRequisitionLineEntity::getAccountBookId, entity.getAccountBookId())
                .eq(PurchaseRequisitionLineEntity::getRequisitionId, entity.getId())
                .eq(PurchaseRequisitionLineEntity::getDeletedFlag, 0)
                .orderByAsc(PurchaseRequisitionLineEntity::getLineNo));
    }

    Map<Long, ProductEntity> loadProducts(
            List<PurchaseRequisitionLineEntity> lines,
            AuditMetadata audit
    ) {
        Set<Long> ids = lines.stream()
                .map(PurchaseRequisitionLineEntity::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return productMapper.selectBatchIds(ids).stream()
                .filter(product -> Objects.equals(product.getCompanyId(), audit.companyId()))
                .filter(product -> Objects.equals(product.getAccountBookId(), audit.accountBookId()))
                .collect(Collectors.toMap(ProductEntity::getId, product -> product, (left, right) -> left, HashMap::new));
    }

    PurchaseRequisitionResponse toResponse(
            PurchaseRequisitionEntity entity,
            List<PurchaseRequisitionLineEntity> lines,
            Map<Long, ProductEntity> products
    ) {
        List<PurchaseRequisitionLineResponse> lineResponses = lines.stream().map(line -> {
            ProductEntity product = products.get(line.getProductId());
            return new PurchaseRequisitionLineResponse(
                    line.getId(),
                    line.getLineNo(),
                    line.getProductId(),
                    product == null ? null : product.getProductCode(),
                    product == null ? null : product.getProductName(),
                    ScalePrecision.quantity(line.getQty()),
                    line.getRemark()
            );
        }).toList();
        return new PurchaseRequisitionResponse(
                entity.getId(),
                entity.getRequisitionNo(),
                entity.getRequisitionDate(),
                entity.getNeededDate(),
                entity.getStatus(),
                entity.getApprovalStatus(),
                entity.getSupplierId(),
                entity.getConvertedOrderId(),
                entity.getConvertedOrderNo(),
                entity.getConvertedTime(),
                entity.getRemark(),
                lineResponses
        );
    }

    private Map<Long, List<PurchaseRequisitionLineEntity>> loadLinesForPage(
            List<PurchaseRequisitionEntity> requisitions,
            AuditMetadata audit
    ) {
        List<Long> ids = requisitions.stream().map(PurchaseRequisitionEntity::getId).toList();
        List<PurchaseRequisitionLineEntity> lines = lineMapper.selectList(
                new LambdaQueryWrapper<PurchaseRequisitionLineEntity>()
                        .eq(PurchaseRequisitionLineEntity::getCompanyId, audit.companyId())
                        .eq(PurchaseRequisitionLineEntity::getAccountBookId, audit.accountBookId())
                        .in(PurchaseRequisitionLineEntity::getRequisitionId, ids)
                        .eq(PurchaseRequisitionLineEntity::getDeletedFlag, 0)
                        .orderByAsc(PurchaseRequisitionLineEntity::getLineNo)
        );
        return lines.stream().collect(Collectors.groupingBy(PurchaseRequisitionLineEntity::getRequisitionId));
    }
}

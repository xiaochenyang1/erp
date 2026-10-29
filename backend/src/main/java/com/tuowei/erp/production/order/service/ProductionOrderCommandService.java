package com.tuowei.erp.production.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.production.bom.model.ProductionBomEntity;
import com.tuowei.erp.production.bom.model.ProductionBomLineEntity;
import com.tuowei.erp.production.bom.service.ProductionBomService;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.mapper.ProductionOrderMaterialMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.model.ProductionOrderMaterialEntity;
import com.tuowei.erp.production.order.web.ProductionOrderCreateRequest;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.production.order.web.ProductionOrderUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** Creates and edits production orders and expands BOM material snapshots. */
@Service
public class ProductionOrderCommandService {
    private final ProductionOrderMapper orderMapper;
    private final ProductionOrderMaterialMapper materialMapper;
    private final ProductionOrderNumberService numberService;
    private final ProductionBomService bomService;
    private final ProductValidator productValidator;
    private final WarehouseMapper warehouseMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final ProductionOrderQueryService queryService;

    public ProductionOrderCommandService(
            ProductionOrderMapper orderMapper, ProductionOrderMaterialMapper materialMapper,
            ProductionOrderNumberService numberService, ProductionBomService bomService,
            ProductValidator productValidator, WarehouseMapper warehouseMapper,
            AuditMetadataFactory auditMetadataFactory, ProductionOrderQueryService queryService
    ) {
        this.orderMapper = orderMapper;
        this.materialMapper = materialMapper;
        this.numberService = numberService;
        this.bomService = bomService;
        this.productValidator = productValidator;
        this.warehouseMapper = warehouseMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.queryService = queryService;
    }

    @Transactional
    public ProductionOrderResponse create(ProductionOrderCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ProductionBomEntity bom = bomService.requireBom(request.bomId(), audit.companyId(), audit.accountBookId());
        productValidator.requireProduct(bom.getProductId(), audit.companyId(), audit.accountBookId());
        requireWarehouse(request.materialWarehouseId(), audit.companyId(), audit.accountBookId());
        requireWarehouse(request.finishedWarehouseId(), audit.companyId(), audit.accountBookId());
        BigDecimal plannedQty = requirePositiveQty(request.plannedQty(), "生产计划数量必须大于0");
        validateDates(request.plannedStartDate(), request.plannedFinishDate());
        ProductionOrderEntity order = new ProductionOrderEntity();
        order.setCompanyId(audit.companyId()); order.setAccountBookId(audit.accountBookId());
        order.setOrderNo(numberService.nextOrderNo(request.plannedStartDate())); order.setBomId(bom.getId());
        order.setProductId(bom.getProductId()); order.setMaterialWarehouseId(request.materialWarehouseId());
        order.setFinishedWarehouseId(request.finishedWarehouseId()); order.setPlannedQty(plannedQty);
        order.setCompletedQty(ScalePrecision.quantity(BigDecimal.ZERO)); order.setPlannedStartDate(request.plannedStartDate());
        order.setPlannedFinishDate(request.plannedFinishDate()); order.setStatus(ProductionOrderService.STATUS_DRAFT);
        order.setIssuedAmount(ScalePrecision.amount(BigDecimal.ZERO)); order.setFinishedAmount(ScalePrecision.amount(BigDecimal.ZERO));
        order.setDeletedFlag(0); order.setRemark(request.remark()); fillCreateAudit(order, audit, now);
        queryService.assertCanView(order);
        orderMapper.insert(order);
        insertMaterials(order, bom, plannedQty, audit, now);
        return queryService.toResponse(order);
    }

    @Transactional
    public ProductionOrderResponse update(Long id, ProductionOrderUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ProductionOrderEntity order = queryService.requireOrder(id);
        if (!ProductionOrderService.STATUS_DRAFT.equals(order.getStatus()))
            throw new IllegalArgumentException("只有草稿状态的生产工单可以修改");
        requireWarehouse(request.materialWarehouseId(), audit.companyId(), audit.accountBookId());
        requireWarehouse(request.finishedWarehouseId(), audit.companyId(), audit.accountBookId());
        BigDecimal plannedQty = requirePositiveQty(request.plannedQty(), "生产计划数量必须大于0");
        validateDates(request.plannedStartDate(), request.plannedFinishDate());
        ProductionBomEntity bom = bomService.requireBom(order.getBomId(), audit.companyId(), audit.accountBookId());
        order.setMaterialWarehouseId(request.materialWarehouseId()); order.setFinishedWarehouseId(request.finishedWarehouseId());
        order.setPlannedQty(plannedQty); order.setPlannedStartDate(request.plannedStartDate());
        order.setPlannedFinishDate(request.plannedFinishDate()); order.setRemark(request.remark());
        order.setUpdatedBy(audit.userId()); order.setUpdatedTime(now);
        if (orderMapper.updateById(order) != 1) throw new BusinessConflictException("生产工单已被其他操作修改，请重试");
        materialMapper.delete(new LambdaQueryWrapper<ProductionOrderMaterialEntity>()
                .eq(ProductionOrderMaterialEntity::getCompanyId, order.getCompanyId())
                .eq(ProductionOrderMaterialEntity::getAccountBookId, order.getAccountBookId())
                .eq(ProductionOrderMaterialEntity::getOrderId, id));
        insertMaterials(order, bom, plannedQty, audit, now);
        return queryService.toResponse(queryService.requireOrder(id));
    }

    private void insertMaterials(ProductionOrderEntity order, ProductionBomEntity bom, BigDecimal plannedQty,
                                 AuditMetadata audit, LocalDateTime now) {
        List<ProductionBomLineEntity> bomLines = bomService.selectLines(bom.getId());
        for (ProductionBomLineEntity bomLine : bomLines) {
            ProductionOrderMaterialEntity material = new ProductionOrderMaterialEntity();
            material.setCompanyId(audit.companyId()); material.setAccountBookId(audit.accountBookId());
            material.setOrderId(order.getId()); material.setLineNo(bomLine.getLineNo());
            material.setMaterialProductId(bomLine.getMaterialProductId());
            material.setRequiredQty(expandRequiredQty(bom, bomLine, plannedQty));
            material.setIssuedQty(ScalePrecision.quantity(BigDecimal.ZERO));
            material.setIssuedAmount(ScalePrecision.amount(BigDecimal.ZERO)); material.setRemark(bomLine.getRemark());
            fillCreateAudit(material, audit, now); materialMapper.insert(material);
        }
    }

    private BigDecimal expandRequiredQty(ProductionBomEntity bom, ProductionBomLineEntity line, BigDecimal plannedQty) {
        return ScalePrecision.quantity(line.getQtyPer().multiply(plannedQty)
                .divide(ScalePrecision.quantity(bom.getBaseQty()), 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.ONE.add(ScalePrecision.zeroDefault(line.getLossRate()))));
    }
    private void validateDates(java.time.LocalDate start, java.time.LocalDate finish) {
        if (finish.isBefore(start)) throw new IllegalArgumentException("计划完工日期不能早于计划开工日期");
    }
    private BigDecimal requirePositiveQty(BigDecimal qty, String message) {
        BigDecimal scaled = ScalePrecision.quantity(ScalePrecision.zeroDefault(qty));
        if (scaled.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException(message);
        return scaled;
    }
    private WarehouseEntity requireWarehouse(Long id, Long companyId, Long accountBookId) {
        WarehouseEntity warehouse = warehouseMapper.selectById(id);
        if (warehouse == null || warehouse.getDeletedFlag() == null || warehouse.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(warehouse.getStatus())
                || !Objects.equals(warehouse.getCompanyId(), companyId)
                || !Objects.equals(warehouse.getAccountBookId(), accountBookId))
            throw new IllegalArgumentException("仓库不存在或已停用");
        return warehouse;
    }
    private void fillCreateAudit(ProductionOrderEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId()); entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(now); entity.setVersion(0);
    }
    private void fillCreateAudit(ProductionOrderMaterialEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId()); entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(now); entity.setVersion(0);
    }
}

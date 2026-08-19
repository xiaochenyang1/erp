package com.tuowei.erp.production.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationCommand;
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
import com.tuowei.erp.production.order.web.ProductionOrderPageQuery;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.production.order.web.ProductionOrderUpdateRequest;
import com.tuowei.erp.production.operation.service.ProductionOperationService;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class ProductionOrderService {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_RELEASED = "RELEASED";
    public static final String STATUS_MATERIAL_ISSUED = "MATERIAL_ISSUED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String SOURCE_TYPE = "PRODUCTION_ORDER";

    private final ProductionOrderMapper orderMapper;
    private final ProductionOrderMaterialMapper materialMapper;
    private final ProductionOrderNumberService numberService;
    private final ProductionBomService bomService;
    private final InventoryPostingService inventoryPostingService;
    private final ProductValidator productValidator;
    private final WarehouseMapper warehouseMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final ProductionOrderQueryService queryService;
    private final ProductionOperationService productionOperationService;
    private final AttachmentService attachmentService;

    public ProductionOrderService(
            ProductionOrderMapper orderMapper,
            ProductionOrderMaterialMapper materialMapper,
            ProductionOrderNumberService numberService,
            ProductionBomService bomService,
            InventoryPostingService inventoryPostingService,
            ProductValidator productValidator,
            WarehouseMapper warehouseMapper,
            AuditMetadataFactory auditMetadataFactory,
            ProductionOrderQueryService queryService,
            ProductionOperationService productionOperationService,
            AttachmentService attachmentService
    ) {
        this.orderMapper = orderMapper;
        this.materialMapper = materialMapper;
        this.numberService = numberService;
        this.bomService = bomService;
        this.inventoryPostingService = inventoryPostingService;
        this.productValidator = productValidator;
        this.warehouseMapper = warehouseMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.queryService = queryService;
        this.productionOperationService = productionOperationService;
        this.attachmentService = attachmentService;
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
        if (request.plannedFinishDate().isBefore(request.plannedStartDate())) {
            throw new IllegalArgumentException("计划完工日期不能早于计划开工日期");
        }

        ProductionOrderEntity order = new ProductionOrderEntity();
        order.setCompanyId(audit.companyId());
        order.setAccountBookId(audit.accountBookId());
        order.setOrderNo(numberService.nextOrderNo(request.plannedStartDate()));
        order.setBomId(bom.getId());
        order.setProductId(bom.getProductId());
        order.setMaterialWarehouseId(request.materialWarehouseId());
        order.setFinishedWarehouseId(request.finishedWarehouseId());
        order.setPlannedQty(plannedQty);
        order.setCompletedQty(ScalePrecision.quantity(BigDecimal.ZERO));
        order.setPlannedStartDate(request.plannedStartDate());
        order.setPlannedFinishDate(request.plannedFinishDate());
        order.setStatus(STATUS_DRAFT);
        order.setIssuedAmount(ScalePrecision.amount(BigDecimal.ZERO));
        order.setFinishedAmount(ScalePrecision.amount(BigDecimal.ZERO));
        order.setDeletedFlag(0);
        order.setRemark(request.remark());
        fillCreateAudit(order, audit, now);
        queryService.assertCanView(order);
        orderMapper.insert(order);
        insertMaterials(order, bom, plannedQty, audit, now);
        return toResponse(order);
    }

    @Transactional
    public ProductionOrderResponse update(Long id, ProductionOrderUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ProductionOrderEntity order = requireOrder(id);
        if (!STATUS_DRAFT.equals(order.getStatus())) {
            throw new IllegalArgumentException("只有草稿状态的生产工单可以修改");
        }
        requireWarehouse(request.materialWarehouseId(), audit.companyId(), audit.accountBookId());
        requireWarehouse(request.finishedWarehouseId(), audit.companyId(), audit.accountBookId());
        BigDecimal plannedQty = requirePositiveQty(request.plannedQty(), "生产计划数量必须大于0");
        if (request.plannedFinishDate().isBefore(request.plannedStartDate())) {
            throw new IllegalArgumentException("计划完工日期不能早于计划开工日期");
        }
        ProductionBomEntity bom = bomService.requireBom(order.getBomId(), audit.companyId(), audit.accountBookId());
        order.setMaterialWarehouseId(request.materialWarehouseId());
        order.setFinishedWarehouseId(request.finishedWarehouseId());
        order.setPlannedQty(plannedQty);
        order.setPlannedStartDate(request.plannedStartDate());
        order.setPlannedFinishDate(request.plannedFinishDate());
        order.setRemark(request.remark());
        order.setUpdatedBy(audit.userId());
        order.setUpdatedTime(now);
        if (orderMapper.updateById(order) != 1) {
            throw new BusinessConflictException("生产工单已被其他操作修改，请重试");
        }
        materialMapper.delete(new LambdaQueryWrapper<ProductionOrderMaterialEntity>()
                .eq(ProductionOrderMaterialEntity::getCompanyId, order.getCompanyId())
                .eq(ProductionOrderMaterialEntity::getAccountBookId, order.getAccountBookId())
                .eq(ProductionOrderMaterialEntity::getOrderId, id));
        insertMaterials(order, bom, plannedQty, audit, now);
        return toResponse(orderMapper.selectById(id));
    }

    @Transactional(readOnly = true)
    public ProductionOrderResponse getById(Long id) {
        return queryService.getById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductionOrderResponse> list(ProductionOrderPageQuery query) {
        ProductionOrderPageQuery safeQuery = query == null ? new ProductionOrderPageQuery() : query;
        return queryService.list(safeQuery);
    }

    @Transactional
    public ProductionOrderResponse release(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionOrderEntity order = requireOrder(id);
        if (!STATUS_DRAFT.equals(order.getStatus())) {
            throw new IllegalArgumentException("只有草稿状态的生产工单可以释放");
        }
        attachmentService.requireIfConfigured(AttachmentBusinessType.PRODUCTION_ORDER, order.getId());
        List<ProductionOrderMaterialEntity> materials = selectMaterials(order);
        for (ProductionOrderMaterialEntity material : materials) {
            inventoryPostingService.reserve(
                    new InventoryReservationCommand(
                            order.getMaterialWarehouseId(),
                            material.getMaterialProductId(),
                            SOURCE_TYPE,
                            order.getId(),
                            order.getOrderNo(),
                            material.getId(),
                            material.getRequiredQty(),
                            material.getRemark()
                    ),
                    audit,
                    "材料可用量不足，不能释放生产工单"
            );
        }
        order.setStatus(STATUS_RELEASED);
        order.setUpdatedBy(audit.userId());
        order.setUpdatedTime(audit.now());
        if (orderMapper.updateById(order) != 1) {
            throw new BusinessConflictException("生产工单已被其他操作修改，请重试");
        }
        productionOperationService.generateForReleasedOrder(order, audit);
        return toResponse(order);
    }

    @Transactional
    public ProductionOrderResponse cancel(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionOrderEntity order = requireOrder(id);
        if (STATUS_COMPLETED.equals(order.getStatus()) || STATUS_MATERIAL_ISSUED.equals(order.getStatus())) {
            throw new IllegalArgumentException("已领料或已完工的生产工单不能取消");
        }
        if (STATUS_RELEASED.equals(order.getStatus())) {
            inventoryPostingService.releaseAllReservations(SOURCE_TYPE, order.getId(), audit);
        }
        order.setStatus(STATUS_CANCELLED);
        order.setUpdatedBy(audit.userId());
        order.setUpdatedTime(audit.now());
        if (orderMapper.updateById(order) != 1) {
            throw new BusinessConflictException("生产工单已被其他操作修改，请重试");
        }
        return toResponse(order);
    }

    public ProductionOrderEntity requireOrder(Long id) {
        return queryService.requireOrder(id);
    }

    public List<ProductionOrderMaterialEntity> selectMaterials(Long orderId) {
        return queryService.selectMaterials(orderId);
    }

    public List<ProductionOrderMaterialEntity> selectMaterials(ProductionOrderEntity order) {
        return queryService.selectMaterials(order);
    }

    public ProductionOrderResponse toResponse(ProductionOrderEntity order) {
        return queryService.toResponse(order);
    }

    private void insertMaterials(
            ProductionOrderEntity order,
            ProductionBomEntity bom,
            BigDecimal plannedQty,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        List<ProductionBomLineEntity> bomLines = bomService.selectLines(bom.getId());
        for (ProductionBomLineEntity bomLine : bomLines) {
            ProductionOrderMaterialEntity material = new ProductionOrderMaterialEntity();
            material.setCompanyId(audit.companyId());
            material.setAccountBookId(audit.accountBookId());
            material.setOrderId(order.getId());
            material.setLineNo(bomLine.getLineNo());
            material.setMaterialProductId(bomLine.getMaterialProductId());
            material.setRequiredQty(expandRequiredQty(bom, bomLine, plannedQty));
            material.setIssuedQty(ScalePrecision.quantity(BigDecimal.ZERO));
            material.setIssuedAmount(ScalePrecision.amount(BigDecimal.ZERO));
            material.setRemark(bomLine.getRemark());
            fillCreateAudit(material, audit, now);
            materialMapper.insert(material);
        }
    }

    private BigDecimal expandRequiredQty(ProductionBomEntity bom, ProductionBomLineEntity line, BigDecimal plannedQty) {
        BigDecimal baseQty = ScalePrecision.quantity(bom.getBaseQty());
        BigDecimal lossRate = ScalePrecision.zeroDefault(line.getLossRate());
        BigDecimal factor = BigDecimal.ONE.add(lossRate);
        return ScalePrecision.quantity(line.getQtyPer()
                .multiply(plannedQty)
                .divide(baseQty, 8, RoundingMode.HALF_UP)
                .multiply(factor));
    }

    private BigDecimal requirePositiveQty(BigDecimal qty, String message) {
        BigDecimal scaled = ScalePrecision.quantity(ScalePrecision.zeroDefault(qty));
        if (scaled.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(message);
        }
        return scaled;
    }

    private WarehouseEntity requireWarehouse(Long id, Long companyId, Long accountBookId) {
        WarehouseEntity warehouse = warehouseMapper.selectById(id);
        if (warehouse == null || warehouse.getDeletedFlag() == null || warehouse.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(warehouse.getStatus())
                || !Objects.equals(warehouse.getCompanyId(), companyId)
                || !Objects.equals(warehouse.getAccountBookId(), accountBookId)) {
            throw new IllegalArgumentException("仓库不存在或已停用");
        }
        return warehouse;
    }

    private void fillCreateAudit(ProductionOrderEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private void fillCreateAudit(ProductionOrderMaterialEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }
}

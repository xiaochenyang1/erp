package com.tuowei.erp.production.order.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationCommand;
import com.tuowei.erp.production.operation.service.ProductionOperationService;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.model.ProductionOrderMaterialEntity;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.tuowei.erp.production.order.service.ProductionOrderConstants.SOURCE_TYPE;
import static com.tuowei.erp.production.order.service.ProductionOrderConstants.STATUS_CANCELLED;
import static com.tuowei.erp.production.order.service.ProductionOrderConstants.STATUS_COMPLETED;
import static com.tuowei.erp.production.order.service.ProductionOrderConstants.STATUS_DRAFT;
import static com.tuowei.erp.production.order.service.ProductionOrderConstants.STATUS_MATERIAL_ISSUED;
import static com.tuowei.erp.production.order.service.ProductionOrderConstants.STATUS_RELEASED;

/** Release and cancellation orchestration for production orders. */
@Service
public class ProductionOrderPostingService {

    private final ProductionOrderMapper orderMapper;
    private final InventoryPostingService inventoryPostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final ProductionOrderQueryService queryService;
    private final ProductionOperationService productionOperationService;
    private final AttachmentService attachmentService;

    public ProductionOrderPostingService(
            ProductionOrderMapper orderMapper,
            InventoryPostingService inventoryPostingService,
            AuditMetadataFactory auditMetadataFactory,
            ProductionOrderQueryService queryService,
            ProductionOperationService productionOperationService,
            AttachmentService attachmentService
    ) {
        this.orderMapper = orderMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.queryService = queryService;
        this.productionOperationService = productionOperationService;
        this.attachmentService = attachmentService;
    }

    @Transactional
    public ProductionOrderResponse release(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionOrderEntity order = queryService.requireOrder(id);
        if (!STATUS_DRAFT.equals(order.getStatus())) {
            throw new IllegalArgumentException("只有草稿状态的生产工单可以释放");
        }
        attachmentService.requireIfConfigured(AttachmentBusinessType.PRODUCTION_ORDER, order.getId());
        List<ProductionOrderMaterialEntity> materials = queryService.selectMaterials(order);
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
        return queryService.toResponse(order);
    }

    @Transactional
    public ProductionOrderResponse cancel(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionOrderEntity order = queryService.requireOrder(id);
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
        return queryService.toResponse(order);
    }
}

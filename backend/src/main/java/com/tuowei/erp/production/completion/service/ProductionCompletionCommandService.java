package com.tuowei.erp.production.completion.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.production.completion.mapper.ProductionCompletionMapper;
import com.tuowei.erp.production.completion.model.ProductionCompletionEntity;
import com.tuowei.erp.production.operation.service.ProductionOperationService;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.model.ProductionOrderMaterialEntity;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionCompletionRequest;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.qc.inspection.service.QcInspectionGate;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Creates and posts production completion documents. */
@Service
public class ProductionCompletionCommandService {
    private static final String BIZ_TYPE = "PRODUCTION_COMPLETION";
    private final ProductionOrderService productionOrderService;
    private final ProductionOrderMapper orderMapper;
    private final ProductionCompletionMapper completionMapper;
    private final InventoryPostingService inventoryPostingService;
    private final InventorySerialNumberService inventorySerialNumberService;
    private final AccountPeriodGuard accountPeriodGuard;
    private final FinancePostingService financePostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SequenceNumberGenerator sequenceNumberGenerator;
    private final ProductionOperationService productionOperationService;
    private final QcInspectionGate qcInspectionGate;

    public ProductionCompletionCommandService(
            ProductionOrderService productionOrderService, ProductionOrderMapper orderMapper,
            ProductionCompletionMapper completionMapper, InventoryPostingService inventoryPostingService,
            InventorySerialNumberService inventorySerialNumberService, AccountPeriodGuard accountPeriodGuard,
            FinancePostingService financePostingService, AuditMetadataFactory auditMetadataFactory,
            SequenceNumberGenerator sequenceNumberGenerator, ProductionOperationService productionOperationService,
            QcInspectionGate qcInspectionGate
    ) {
        this.productionOrderService = productionOrderService;
        this.orderMapper = orderMapper;
        this.completionMapper = completionMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.inventorySerialNumberService = inventorySerialNumberService;
        this.accountPeriodGuard = accountPeriodGuard;
        this.financePostingService = financePostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.sequenceNumberGenerator = sequenceNumberGenerator;
        this.productionOperationService = productionOperationService;
        this.qcInspectionGate = qcInspectionGate;
    }

    @Transactional
    public ProductionOrderResponse complete(Long orderId) { return complete(orderId, null); }

    @Transactional
    public ProductionOrderResponse complete(Long orderId, ProductionCompletionRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionOrderEntity order = productionOrderService.requireOrder(orderId);
        if (!ProductionOrderService.STATUS_MATERIAL_ISSUED.equals(order.getStatus())) {
            throw new IllegalArgumentException("只有已领料状态的生产工单可以完工入库");
        }
        LocalDate completionDate = request != null && request.completionDate() != null
                ? request.completionDate() : order.getPlannedFinishDate();
        accountPeriodGuard.requireOpen(completionDate, "生产完工入库");
        String actionRemark = request != null && StringUtils.hasText(request.remark())
                ? request.remark().trim() : order.getRemark();
        BigDecimal remainingPlannedQty = ScalePrecision.quantity(
                ScalePrecision.zeroDefault(order.getPlannedQty()).subtract(ScalePrecision.zeroDefault(order.getCompletedQty()))
        );
        BigDecimal completionQty = request != null && request.completedQty() != null
                ? ScalePrecision.quantity(request.completedQty()) : remainingPlannedQty;
        if (completionQty.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("生产完工数量必须大于0");
        if (completionQty.compareTo(remainingPlannedQty) > 0) throw new IllegalArgumentException("生产完工数量超过剩余计划数量");
        BigDecimal maxCompletableQty = maxCompletableQty(order);
        BigDecimal remainingCompletableQty = ScalePrecision.quantity(
                maxCompletableQty.subtract(ScalePrecision.zeroDefault(order.getCompletedQty()))
        );
        if (completionQty.compareTo(remainingCompletableQty) > 0) throw new IllegalArgumentException("生产完工数量超过已领料可完工数量");
        productionOperationService.assertReadyForCompletion(order, completionQty);
        qcInspectionGate.assertProductionInspected(order.getCompanyId(), order.getAccountBookId(), order.getId(), order.getProductId(), completionQty);
        BigDecimal availableAmount = ScalePrecision.amount(
                ScalePrecision.zeroDefault(order.getIssuedAmount()).subtract(ScalePrecision.zeroDefault(order.getFinishedAmount()))
        );
        BigDecimal completionAmount = completionAmount(availableAmount, remainingCompletableQty, completionQty);
        LocalDateTime now = audit.now();

        ProductionCompletionEntity completion = new ProductionCompletionEntity();
        completion.setCompanyId(audit.companyId());
        completion.setAccountBookId(audit.accountBookId());
        completion.setCompletionNo(sequenceNumberGenerator.nextNumber(BIZ_TYPE, "生产完工单", completionDate));
        completion.setOrderId(order.getId());
        completion.setCompletionDate(completionDate);
        completion.setCompletedQty(completionQty);
        completion.setCompletedAmount(completionAmount);
        completion.setLotNo(request == null ? null : request.lotNo());
        completion.setProductionDate(request == null ? null : request.productionDate());
        completion.setExpiryDate(request == null ? null : request.expiryDate());
        completion.setLocationId(request == null ? null : request.locationId());
        completion.setSerialNos(request == null ? null : request.serialNos());
        completion.setRemark(actionRemark);
        fillAudit(completion, audit, now);
        completionMapper.insert(completion);
        inventoryPostingService.postInbound(new InventoryPostingCommand(
                order.getFinishedWarehouseId(), order.getProductId(), BIZ_TYPE, order.getOrderNo(), completion.getId(),
                completionQty, completionAmount, actionRemark, completionDate, completion.getLotNo(),
                completion.getProductionDate(), completion.getExpiryDate(), completion.getLocationId()), audit);
        inventorySerialNumberService.registerInboundSerials(order.getProductId(), order.getFinishedWarehouseId(),
                completion.getLocationId(), completion.getSerialNos(), BIZ_TYPE, order.getOrderNo(), completionQty, audit);
        BigDecimal newCompletedQty = ScalePrecision.quantity(ScalePrecision.zeroDefault(order.getCompletedQty()).add(completionQty));
        order.setStatus(newCompletedQty.compareTo(ScalePrecision.quantity(order.getPlannedQty())) >= 0
                ? ProductionOrderService.STATUS_COMPLETED : ProductionOrderService.STATUS_MATERIAL_ISSUED);
        order.setCompletedQty(newCompletedQty);
        order.setFinishedAmount(ScalePrecision.amount(ScalePrecision.zeroDefault(order.getFinishedAmount()).add(completionAmount)));
        order.setUpdatedBy(audit.userId());
        order.setUpdatedTime(now);
        if (orderMapper.updateById(order) != 1) throw new BusinessConflictException("生产工单已被其他操作修改，请重试");
        financePostingService.recordProductionCompletion(order, completion.getId(), order.getOrderNo(), completionAmount, completionDate, audit);
        return productionOrderService.toResponse(order);
    }

    private BigDecimal maxCompletableQty(ProductionOrderEntity order) {
        List<ProductionOrderMaterialEntity> materials = productionOrderService.selectMaterials(order);
        BigDecimal maxQty = ScalePrecision.quantity(order.getPlannedQty());
        for (ProductionOrderMaterialEntity material : materials) {
            BigDecimal requiredQty = ScalePrecision.quantity(material.getRequiredQty());
            if (requiredQty.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal materialCompletableQty = ScalePrecision.quantity(ScalePrecision.zeroDefault(material.getIssuedQty())
                    .multiply(order.getPlannedQty()).divide(requiredQty, 8, RoundingMode.HALF_UP));
            if (materialCompletableQty.compareTo(maxQty) < 0) maxQty = materialCompletableQty;
        }
        return maxQty;
    }

    private BigDecimal completionAmount(BigDecimal availableAmount, BigDecimal remainingCompletableQty, BigDecimal completionQty) {
        if (remainingCompletableQty.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("没有可结转的生产成本，不能完工");
        if (completionQty.compareTo(remainingCompletableQty) == 0) return ScalePrecision.amount(availableAmount);
        return ScalePrecision.amount(availableAmount.multiply(completionQty).divide(remainingCompletableQty, 8, RoundingMode.HALF_UP));
    }

    private void fillAudit(ProductionCompletionEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId()); entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(now); entity.setVersion(0);
    }
}

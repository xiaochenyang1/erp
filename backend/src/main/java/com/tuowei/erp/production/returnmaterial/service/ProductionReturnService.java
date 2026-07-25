package com.tuowei.erp.production.returnmaterial.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.mapper.ProductionOrderMaterialMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.model.ProductionOrderMaterialEntity;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.production.order.web.ProductionReturnLineRequest;
import com.tuowei.erp.production.order.web.ProductionReturnRequest;
import com.tuowei.erp.production.returnmaterial.mapper.ProductionReturnLineMapper;
import com.tuowei.erp.production.returnmaterial.mapper.ProductionReturnMapper;
import com.tuowei.erp.production.returnmaterial.model.ProductionReturnEntity;
import com.tuowei.erp.production.returnmaterial.model.ProductionReturnLineEntity;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductionReturnService {

    private static final String BIZ_TYPE = "PRODUCTION_RETURN";

    private final ProductionOrderService productionOrderService;
    private final ProductionOrderMapper orderMapper;
    private final ProductionOrderMaterialMapper materialMapper;
    private final ProductionReturnMapper returnMapper;
    private final ProductionReturnLineMapper returnLineMapper;
    private final InventoryPostingService inventoryPostingService;
    private final InventorySerialNumberService inventorySerialNumberService;
    private final AccountPeriodGuard accountPeriodGuard;
    private final FinancePostingService financePostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SequenceNumberGenerator sequenceNumberGenerator;

    public ProductionReturnService(
            ProductionOrderService productionOrderService,
            ProductionOrderMapper orderMapper,
            ProductionOrderMaterialMapper materialMapper,
            ProductionReturnMapper returnMapper,
            ProductionReturnLineMapper returnLineMapper,
            InventoryPostingService inventoryPostingService,
            InventorySerialNumberService inventorySerialNumberService,
            AccountPeriodGuard accountPeriodGuard,
            FinancePostingService financePostingService,
            AuditMetadataFactory auditMetadataFactory,
            SequenceNumberGenerator sequenceNumberGenerator
    ) {
        this.productionOrderService = productionOrderService;
        this.orderMapper = orderMapper;
        this.materialMapper = materialMapper;
        this.returnMapper = returnMapper;
        this.returnLineMapper = returnLineMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.inventorySerialNumberService = inventorySerialNumberService;
        this.accountPeriodGuard = accountPeriodGuard;
        this.financePostingService = financePostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    @Transactional
    public ProductionOrderResponse returnMaterials(Long orderId, ProductionReturnRequest request) {
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            throw new IllegalArgumentException("生产退料必须包含明细");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionOrderEntity order = productionOrderService.requireOrder(orderId);
        if (ProductionOrderService.STATUS_COMPLETED.equals(order.getStatus())
                || ProductionOrderService.STATUS_CANCELLED.equals(order.getStatus())) {
            throw new IllegalArgumentException("已完工或已取消的生产工单不能退料");
        }
        LocalDate returnDate = request.returnDate() == null ? order.getPlannedStartDate() : request.returnDate();
        accountPeriodGuard.requireOpen(returnDate, "生产退料");
        String actionRemark = StringUtils.hasText(request.remark()) ? request.remark().trim() : null;
        LocalDateTime now = audit.now();

        ProductionReturnEntity productionReturn = new ProductionReturnEntity();
        productionReturn.setCompanyId(audit.companyId());
        productionReturn.setAccountBookId(audit.accountBookId());
        productionReturn.setReturnNo(sequenceNumberGenerator.nextNumber(BIZ_TYPE, "生产退料单", returnDate));
        productionReturn.setOrderId(order.getId());
        productionReturn.setReturnDate(returnDate);
        productionReturn.setTotalQty(ScalePrecision.quantity(BigDecimal.ZERO));
        productionReturn.setTotalAmount(ScalePrecision.amount(BigDecimal.ZERO));
        productionReturn.setRemark(actionRemark);
        fillAudit(productionReturn, audit, now);
        returnMapper.insert(productionReturn);

        Map<Long, ProductionOrderMaterialEntity> materials = materialById(order);
        BigDecimal totalQty = ScalePrecision.quantity(BigDecimal.ZERO);
        BigDecimal totalAmount = ScalePrecision.amount(BigDecimal.ZERO);
        for (ProductionReturnLineRequest requestLine : request.lines()) {
            if (requestLine == null) {
                throw new IllegalArgumentException("生产退料明细不能为空");
            }
            ProductionOrderMaterialEntity material = materials.get(requestLine.orderMaterialId());
            if (material == null) {
                throw new IllegalArgumentException("生产退料明细不属于当前工单");
            }
            BigDecimal returnQty = ScalePrecision.quantity(ScalePrecision.zeroDefault(requestLine.returnQty()));
            if (returnQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("生产退料数量必须大于0");
            }
            BigDecimal maxReturnQty = maxReturnQty(order, material);
            if (returnQty.compareTo(maxReturnQty) > 0) {
                throw new IllegalArgumentException("生产退料数量超过可退数量");
            }
            BigDecimal returnAmount = returnAmount(material, returnQty);

            ProductionReturnLineEntity line = new ProductionReturnLineEntity();
            line.setCompanyId(audit.companyId());
            line.setAccountBookId(audit.accountBookId());
            line.setReturnId(productionReturn.getId());
            line.setOrderId(order.getId());
            line.setOrderMaterialId(material.getId());
            line.setMaterialProductId(material.getMaterialProductId());
            line.setReturnQty(returnQty);
            line.setReturnAmount(returnAmount);
            line.setLotNo(requestLine.lotNo());
            line.setProductionDate(requestLine.productionDate());
            line.setExpiryDate(requestLine.expiryDate());
            line.setLocationId(requestLine.locationId());
            line.setSerialNos(requestLine.serialNos());
            line.setRemark(StringUtils.hasText(requestLine.remark()) ? requestLine.remark().trim() : actionRemark);
            fillAudit(line, audit, now);
            returnLineMapper.insert(line);

            inventoryPostingService.postInbound(
                    new InventoryPostingCommand(
                            order.getMaterialWarehouseId(),
                            material.getMaterialProductId(),
                            BIZ_TYPE,
                            order.getOrderNo(),
                            line.getId(),
                            returnQty,
                            returnAmount,
                            line.getRemark(),
                            returnDate,
                            line.getLotNo(),
                            line.getProductionDate(),
                            line.getExpiryDate(),
                            line.getLocationId()
                    ),
                    audit
            );
            inventorySerialNumberService.registerInboundSerials(
                    line.getMaterialProductId(),
                    order.getMaterialWarehouseId(),
                    line.getLocationId(),
                    line.getSerialNos(),
                    BIZ_TYPE,
                    order.getOrderNo(),
                    returnQty,
                    audit
            );
            inventoryPostingService.restoreReservation(
                    ProductionOrderService.SOURCE_TYPE,
                    material.getId(),
                    returnQty,
                    audit,
                    line.getRemark()
            );

            material.setIssuedQty(ScalePrecision.quantity(ScalePrecision.zeroDefault(material.getIssuedQty()).subtract(returnQty)));
            material.setIssuedAmount(ScalePrecision.amount(ScalePrecision.zeroDefault(material.getIssuedAmount()).subtract(returnAmount)));
            material.setUpdatedBy(audit.userId());
            material.setUpdatedTime(now);
            if (materialMapper.updateById(material) != 1) {
                throw new BusinessConflictException("生产工单材料已被其他操作修改，请重试");
            }
            totalQty = ScalePrecision.quantity(totalQty.add(returnQty));
            totalAmount = ScalePrecision.amount(totalAmount.add(returnAmount));
        }

        productionReturn.setTotalQty(totalQty);
        productionReturn.setTotalAmount(totalAmount);
        productionReturn.setUpdatedBy(audit.userId());
        productionReturn.setUpdatedTime(now);
        if (returnMapper.updateById(productionReturn) != 1) {
            throw new BusinessConflictException("生产退料单已被其他操作修改，请重试");
        }

        order.setIssuedAmount(ScalePrecision.amount(ScalePrecision.zeroDefault(order.getIssuedAmount()).subtract(totalAmount)));
        order.setStatus(resolveStatus(order));
        order.setUpdatedBy(audit.userId());
        order.setUpdatedTime(now);
        if (orderMapper.updateById(order) != 1) {
            throw new BusinessConflictException("生产工单已被其他操作修改，请重试");
        }
        financePostingService.recordProductionReturn(order, productionReturn.getId(), order.getOrderNo(), totalAmount, returnDate, audit);
        return productionOrderService.toResponse(order);
    }

    private Map<Long, ProductionOrderMaterialEntity> materialById(ProductionOrderEntity order) {
        List<ProductionOrderMaterialEntity> materials = productionOrderService.selectMaterials(order);
        Map<Long, ProductionOrderMaterialEntity> result = new LinkedHashMap<>();
        for (ProductionOrderMaterialEntity material : materials) {
            result.put(material.getId(), material);
        }
        return result;
    }

    private BigDecimal maxReturnQty(ProductionOrderEntity order, ProductionOrderMaterialEntity material) {
        BigDecimal consumedQty = ScalePrecision.quantity(
                ScalePrecision.zeroDefault(material.getRequiredQty())
                        .multiply(ScalePrecision.zeroDefault(order.getCompletedQty()))
                        .divide(ScalePrecision.zeroDefault(order.getPlannedQty()), 8, RoundingMode.HALF_UP)
        );
        return ScalePrecision.quantity(ScalePrecision.zeroDefault(material.getIssuedQty()).subtract(consumedQty));
    }

    private BigDecimal returnAmount(ProductionOrderMaterialEntity material, BigDecimal returnQty) {
        BigDecimal issuedQty = ScalePrecision.quantity(material.getIssuedQty());
        if (issuedQty.compareTo(returnQty) == 0) {
            return ScalePrecision.amount(material.getIssuedAmount());
        }
        BigDecimal unitCost = ScalePrecision.unitCost(material.getIssuedAmount(), issuedQty);
        return ScalePrecision.amount(unitCost.multiply(returnQty));
    }

    private String resolveStatus(ProductionOrderEntity order) {
        if (ScalePrecision.zeroDefault(order.getCompletedQty()).compareTo(BigDecimal.ZERO) > 0) {
            return ProductionOrderService.STATUS_MATERIAL_ISSUED;
        }
        boolean hasIssued = productionOrderService.selectMaterials(order).stream()
                .anyMatch(material -> ScalePrecision.zeroDefault(material.getIssuedQty()).compareTo(BigDecimal.ZERO) > 0);
        return hasIssued ? ProductionOrderService.STATUS_MATERIAL_ISSUED : ProductionOrderService.STATUS_RELEASED;
    }

    private void fillAudit(ProductionReturnEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private void fillAudit(ProductionReturnLineEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }
}

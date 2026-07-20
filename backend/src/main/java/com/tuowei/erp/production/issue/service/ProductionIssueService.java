package com.tuowei.erp.production.issue.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.production.issue.mapper.ProductionIssueLineMapper;
import com.tuowei.erp.production.issue.mapper.ProductionIssueMapper;
import com.tuowei.erp.production.issue.model.ProductionIssueEntity;
import com.tuowei.erp.production.issue.model.ProductionIssueLineEntity;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.mapper.ProductionOrderMaterialMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.model.ProductionOrderMaterialEntity;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionIssueLineRequest;
import com.tuowei.erp.production.order.web.ProductionIssueRequest;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductionIssueService {

    private static final String BIZ_TYPE = "PRODUCTION_ISSUE";

    private final ProductionOrderService productionOrderService;
    private final ProductionOrderMapper orderMapper;
    private final ProductionOrderMaterialMapper materialMapper;
    private final ProductionIssueMapper issueMapper;
    private final ProductionIssueLineMapper issueLineMapper;
    private final InventoryPostingService inventoryPostingService;
    private final AccountPeriodGuard accountPeriodGuard;
    private final FinancePostingService financePostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SequenceNumberGenerator sequenceNumberGenerator;

    public ProductionIssueService(
            ProductionOrderService productionOrderService,
            ProductionOrderMapper orderMapper,
            ProductionOrderMaterialMapper materialMapper,
            ProductionIssueMapper issueMapper,
            ProductionIssueLineMapper issueLineMapper,
            InventoryPostingService inventoryPostingService,
            AccountPeriodGuard accountPeriodGuard,
            FinancePostingService financePostingService,
            AuditMetadataFactory auditMetadataFactory,
            SequenceNumberGenerator sequenceNumberGenerator
    ) {
        this.productionOrderService = productionOrderService;
        this.orderMapper = orderMapper;
        this.materialMapper = materialMapper;
        this.issueMapper = issueMapper;
        this.issueLineMapper = issueLineMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.accountPeriodGuard = accountPeriodGuard;
        this.financePostingService = financePostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    @Transactional
    public ProductionOrderResponse issue(Long orderId) {
        return issue(orderId, null);
    }

    @Transactional
    public ProductionOrderResponse issue(Long orderId, ProductionIssueRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionOrderEntity order = productionOrderService.requireOrder(orderId);
        if (!ProductionOrderService.STATUS_RELEASED.equals(order.getStatus())
                && !ProductionOrderService.STATUS_MATERIAL_ISSUED.equals(order.getStatus())) {
            throw new IllegalArgumentException("只有已释放或已领料状态的生产工单可以领料");
        }
        LocalDate issueDate = request != null && request.issueDate() != null
                ? request.issueDate()
                : order.getPlannedStartDate();
        accountPeriodGuard.requireOpen(issueDate, "生产领料");
        String actionRemark = request != null && StringUtils.hasText(request.remark())
                ? request.remark().trim()
                : null;

        List<ProductionOrderMaterialEntity> materials = productionOrderService.selectMaterials(order);
        Map<Long, BigDecimal> quantities = resolveIssueQuantities(request, materials);
        Map<Long, ProductionIssueLineRequest> lineRequests = issueLineRequestsByMaterialId(request);
        LocalDateTime now = audit.now();
        ProductionIssueEntity issue = new ProductionIssueEntity();
        issue.setCompanyId(audit.companyId());
        issue.setAccountBookId(audit.accountBookId());
        issue.setIssueNo(sequenceNumberGenerator.nextNumber(BIZ_TYPE, "生产领料单", issueDate));
        issue.setOrderId(order.getId());
        issue.setIssueDate(issueDate);
        issue.setTotalQty(ScalePrecision.quantity(BigDecimal.ZERO));
        issue.setTotalAmount(ScalePrecision.amount(BigDecimal.ZERO));
        issue.setRemark(actionRemark);
        fillAudit(issue, audit, now);
        issueMapper.insert(issue);

        BigDecimal issuedAmount = ScalePrecision.amount(BigDecimal.ZERO);
        BigDecimal issuedQty = ScalePrecision.quantity(BigDecimal.ZERO);
        for (ProductionOrderMaterialEntity material : materials) {
            BigDecimal issueQty = quantities.get(material.getId());
            if (issueQty == null || issueQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            ProductionIssueLineRequest requestLine = lineRequests.get(material.getId());
            ProductionIssueLineEntity line = new ProductionIssueLineEntity();
            line.setCompanyId(audit.companyId());
            line.setAccountBookId(audit.accountBookId());
            line.setIssueId(issue.getId());
            line.setOrderId(order.getId());
            line.setOrderMaterialId(material.getId());
            line.setMaterialProductId(material.getMaterialProductId());
            line.setIssueQty(issueQty);
            line.setIssueAmount(ScalePrecision.amount(BigDecimal.ZERO));
            line.setLotNo(requestLine == null ? null : requestLine.lotNo());
            line.setProductionDate(requestLine == null ? null : requestLine.productionDate());
            line.setExpiryDate(requestLine == null ? null : requestLine.expiryDate());
            line.setRemark(StringUtils.hasText(actionRemark) ? actionRemark : material.getRemark());
            fillAudit(line, audit, now);
            issueLineMapper.insert(line);

            inventoryPostingService.releaseReservation(
                    ProductionOrderService.SOURCE_TYPE,
                    material.getId(),
                    issueQty,
                    audit
            );
            BigDecimal lineAmount = inventoryPostingService.postOutbound(
                    new InventoryPostingCommand(
                            order.getMaterialWarehouseId(),
                            material.getMaterialProductId(),
                            BIZ_TYPE,
                            order.getOrderNo(),
                            line.getId(),
                            issueQty,
                            BigDecimal.ZERO,
                            StringUtils.hasText(actionRemark) ? actionRemark : material.getRemark(),
                            issueDate,
                            line.getLotNo(),
                            line.getProductionDate(),
                            line.getExpiryDate()
                    ),
                    audit,
                    "材料库存不足，不能生产领料"
            );
            line.setIssueAmount(ScalePrecision.amount(lineAmount));
            line.setUpdatedBy(audit.userId());
            line.setUpdatedTime(now);
            if (issueLineMapper.updateById(line) != 1) {
                throw new BusinessConflictException("生产领料明细已被其他操作修改，请重试");
            }

            material.setIssuedQty(ScalePrecision.quantity(ScalePrecision.zeroDefault(material.getIssuedQty()).add(issueQty)));
            material.setIssuedAmount(ScalePrecision.amount(ScalePrecision.zeroDefault(material.getIssuedAmount()).add(lineAmount)));
            material.setUpdatedBy(audit.userId());
            material.setUpdatedTime(now);
            if (materialMapper.updateById(material) != 1) {
                throw new BusinessConflictException("生产工单材料已被其他操作修改，请重试");
            }
            issuedAmount = ScalePrecision.amount(issuedAmount.add(lineAmount));
            issuedQty = ScalePrecision.quantity(issuedQty.add(issueQty));
        }

        if (issuedQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("生产领料数量必须大于0");
        }
        issue.setTotalQty(issuedQty);
        issue.setTotalAmount(issuedAmount);
        issue.setUpdatedBy(audit.userId());
        issue.setUpdatedTime(now);
        if (issueMapper.updateById(issue) != 1) {
            throw new BusinessConflictException("生产领料单已被其他操作修改，请重试");
        }

        order.setStatus(ProductionOrderService.STATUS_MATERIAL_ISSUED);
        order.setIssuedAmount(ScalePrecision.amount(ScalePrecision.zeroDefault(order.getIssuedAmount()).add(issuedAmount)));
        order.setUpdatedBy(audit.userId());
        order.setUpdatedTime(now);
        if (orderMapper.updateById(order) != 1) {
            throw new BusinessConflictException("生产工单已被其他操作修改，请重试");
        }
        financePostingService.recordProductionIssue(order, issue.getId(), order.getOrderNo(), issuedAmount, issueDate, audit);
        return productionOrderService.toResponse(order);
    }

    private Map<Long, BigDecimal> resolveIssueQuantities(
            ProductionIssueRequest request,
            List<ProductionOrderMaterialEntity> materials
    ) {
        Map<Long, ProductionOrderMaterialEntity> materialById = new LinkedHashMap<>();
        for (ProductionOrderMaterialEntity material : materials) {
            materialById.put(material.getId(), material);
        }
        Map<Long, BigDecimal> quantities = new LinkedHashMap<>();
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            for (ProductionOrderMaterialEntity material : materials) {
                BigDecimal remainingQty = ScalePrecision.quantity(
                        ScalePrecision.zeroDefault(material.getRequiredQty())
                                .subtract(ScalePrecision.zeroDefault(material.getIssuedQty()))
                );
                if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                    quantities.put(material.getId(), remainingQty);
                }
            }
            return quantities;
        }
        for (ProductionIssueLineRequest line : request.lines()) {
            if (line == null) {
                throw new IllegalArgumentException("生产领料明细不能为空");
            }
            if (line.orderMaterialId() == null) {
                throw new IllegalArgumentException("生产领料明细必须指定工单材料行");
            }
            ProductionOrderMaterialEntity material = materialById.get(line.orderMaterialId());
            if (material == null) {
                throw new IllegalArgumentException("生产领料明细不属于当前工单");
            }
            BigDecimal issueQty = ScalePrecision.quantity(ScalePrecision.zeroDefault(line.issueQty()));
            if (issueQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("生产领料数量必须大于0");
            }
            BigDecimal remainingQty = ScalePrecision.quantity(
                    ScalePrecision.zeroDefault(material.getRequiredQty())
                            .subtract(ScalePrecision.zeroDefault(material.getIssuedQty()))
            );
            if (issueQty.compareTo(remainingQty) > 0) {
                throw new IllegalArgumentException("生产领料数量超过剩余可领数量");
            }
            quantities.merge(line.orderMaterialId(), issueQty, (left, right) -> ScalePrecision.quantity(left.add(right)));
        }
        return quantities;
    }

    private Map<Long, ProductionIssueLineRequest> issueLineRequestsByMaterialId(ProductionIssueRequest request) {
        Map<Long, ProductionIssueLineRequest> result = new LinkedHashMap<>();
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            return result;
        }
        for (ProductionIssueLineRequest line : request.lines()) {
            if (line == null) {
                throw new IllegalArgumentException("生产领料明细不能为空");
            }
            if (line.orderMaterialId() == null) {
                continue;
            }
            result.put(line.orderMaterialId(), line);
        }
        return result;
    }

    private void fillAudit(ProductionIssueEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private void fillAudit(ProductionIssueLineEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }
}

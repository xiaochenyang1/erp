package com.tuowei.erp.production.completion.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.production.completion.mapper.ProductionCompletionReversalMapper;
import com.tuowei.erp.production.completion.model.ProductionCompletionReversalEntity;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.production.order.web.ProductionCompletionReversalRequest;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.system.config.service.SequenceNumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Creates and posts production completion reversal documents. */
@Service
public class ProductionCompletionReversalCommandService {
    private static final String BIZ_TYPE = "PRODUCTION_COMPLETION_REVERSAL";
    private final ProductionOrderService productionOrderService;
    private final ProductionOrderMapper orderMapper;
    private final ProductionCompletionReversalMapper reversalMapper;
    private final InventoryPostingService inventoryPostingService;
    private final AccountPeriodGuard accountPeriodGuard;
    private final FinancePostingService financePostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SequenceNumberGenerator sequenceNumberGenerator;

    public ProductionCompletionReversalCommandService(
            ProductionOrderService productionOrderService, ProductionOrderMapper orderMapper,
            ProductionCompletionReversalMapper reversalMapper, InventoryPostingService inventoryPostingService,
            AccountPeriodGuard accountPeriodGuard, FinancePostingService financePostingService,
            AuditMetadataFactory auditMetadataFactory, SequenceNumberGenerator sequenceNumberGenerator
    ) {
        this.productionOrderService = productionOrderService;
        this.orderMapper = orderMapper;
        this.reversalMapper = reversalMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.accountPeriodGuard = accountPeriodGuard;
        this.financePostingService = financePostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.sequenceNumberGenerator = sequenceNumberGenerator;
    }

    @Transactional
    public ProductionOrderResponse reverseCompletion(Long orderId, ProductionCompletionReversalRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionOrderEntity order = productionOrderService.requireOrder(orderId);
        BigDecimal completedQty = ScalePrecision.quantity(ScalePrecision.zeroDefault(order.getCompletedQty()));
        if (completedQty.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("生产工单没有可冲回的完工数量");
        LocalDate reversalDate = request != null && request.reversalDate() != null
                ? request.reversalDate() : order.getPlannedFinishDate();
        accountPeriodGuard.requireOpen(reversalDate, "生产完工冲回");
        BigDecimal reversedQty = request != null && request.reversedQty() != null
                ? ScalePrecision.quantity(request.reversedQty()) : completedQty;
        if (reversedQty.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("生产完工冲回数量必须大于0");
        if (reversedQty.compareTo(completedQty) > 0) throw new IllegalArgumentException("生产完工冲回数量超过已完工数量");
        String actionRemark = request != null && StringUtils.hasText(request.remark())
                ? request.remark().trim() : order.getRemark();
        LocalDateTime now = audit.now();

        ProductionCompletionReversalEntity reversal = new ProductionCompletionReversalEntity();
        reversal.setCompanyId(audit.companyId());
        reversal.setAccountBookId(audit.accountBookId());
        reversal.setReversalNo(sequenceNumberGenerator.nextNumber(BIZ_TYPE, "生产反完工单", reversalDate));
        reversal.setOrderId(order.getId());
        reversal.setReversalDate(reversalDate);
        reversal.setReversedQty(reversedQty);
        reversal.setReversedAmount(ScalePrecision.amount(BigDecimal.ZERO));
        reversal.setRemark(actionRemark);
        fillAudit(reversal, audit, now);
        reversalMapper.insert(reversal);

        BigDecimal reversedAmount = inventoryPostingService.postOutbound(new InventoryPostingCommand(
                order.getFinishedWarehouseId(), order.getProductId(), BIZ_TYPE, reversal.getReversalNo(),
                reversal.getId(), reversedQty, BigDecimal.ZERO, actionRemark, reversalDate),
                audit, "成品库存不足，不能生产完工冲回");
        reversal.setReversedAmount(ScalePrecision.amount(reversedAmount));
        reversal.setUpdatedBy(audit.userId());
        reversal.setUpdatedTime(now);
        if (reversalMapper.updateById(reversal) != 1) throw new BusinessConflictException("生产反完工单已被其他操作修改，请重试");

        BigDecimal newCompletedQty = ScalePrecision.quantity(completedQty.subtract(reversedQty));
        BigDecimal newFinishedAmount = ScalePrecision.amount(ScalePrecision.zeroDefault(order.getFinishedAmount()).subtract(reversedAmount));
        if (newFinishedAmount.compareTo(BigDecimal.ZERO) < 0) newFinishedAmount = ScalePrecision.amount(BigDecimal.ZERO);
        order.setCompletedQty(newCompletedQty);
        order.setFinishedAmount(newFinishedAmount);
        order.setStatus(ProductionOrderService.STATUS_MATERIAL_ISSUED);
        order.setUpdatedBy(audit.userId());
        order.setUpdatedTime(now);
        if (orderMapper.updateById(order) != 1) throw new BusinessConflictException("生产工单已被其他操作修改，请重试");
        financePostingService.recordProductionCompletionReversal(order, reversal.getId(), reversal.getReversalNo(),
                reversal.getReversedAmount(), reversalDate, audit);
        return productionOrderService.toResponse(order);
    }

    private void fillAudit(ProductionCompletionReversalEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId()); entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId()); entity.setUpdatedTime(now); entity.setVersion(0);
    }
}

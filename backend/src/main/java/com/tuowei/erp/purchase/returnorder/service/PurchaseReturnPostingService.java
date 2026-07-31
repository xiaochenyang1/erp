package com.tuowei.erp.purchase.returnorder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderLookupService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderReceiptStatusService;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptLineEntity;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnLineMapper;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnLineEntity;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnResponse;
import com.tuowei.erp.purchase.support.AccumulatedQuantityValidator;
import com.tuowei.erp.purchase.support.PurchaseReturnQuantities;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PurchaseReturnPostingService {

    private final PurchaseReturnMapper purchaseReturnMapper;
    private final PurchaseReturnLineMapper purchaseReturnLineMapper;
    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final PurchaseReceiptLineMapper purchaseReceiptLineMapper;
    private final PurchaseOrderLineMapper purchaseOrderLineMapper;
    private final InventoryPostingService inventoryPostingService;
    private final InventorySerialNumberService inventorySerialNumberService;
    private final PurchaseOrderLookupService purchaseOrderLookupService;
    private final PurchaseOrderReceiptStatusService purchaseOrderReceiptStatusService;
    private final FinancePostingService financePostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final PurchaseReturnQueryService purchaseReturnQueryService;
    private final AccountPeriodGuard accountPeriodGuard;

    public PurchaseReturnPostingService(
            PurchaseReturnMapper purchaseReturnMapper,
            PurchaseReturnLineMapper purchaseReturnLineMapper,
            PurchaseReceiptMapper purchaseReceiptMapper,
            PurchaseReceiptLineMapper purchaseReceiptLineMapper,
            PurchaseOrderLineMapper purchaseOrderLineMapper,
            InventoryPostingService inventoryPostingService,
            InventorySerialNumberService inventorySerialNumberService,
            PurchaseOrderLookupService purchaseOrderLookupService,
            PurchaseOrderReceiptStatusService purchaseOrderReceiptStatusService,
            FinancePostingService financePostingService,
            AuditMetadataFactory auditMetadataFactory,
            PurchaseReturnQueryService purchaseReturnQueryService,
            AccountPeriodGuard accountPeriodGuard
    ) {
        this.purchaseReturnMapper = purchaseReturnMapper;
        this.purchaseReturnLineMapper = purchaseReturnLineMapper;
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.purchaseReceiptLineMapper = purchaseReceiptLineMapper;
        this.purchaseOrderLineMapper = purchaseOrderLineMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.inventorySerialNumberService = inventorySerialNumberService;
        this.purchaseOrderLookupService = purchaseOrderLookupService;
        this.purchaseOrderReceiptStatusService = purchaseOrderReceiptStatusService;
        this.financePostingService = financePostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.purchaseReturnQueryService = purchaseReturnQueryService;
        this.accountPeriodGuard = accountPeriodGuard;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PurchaseReturnResponse post(Long id) {
        PurchaseReturnEntity entity = requireReturn(id);
        purchaseReturnQueryService.assertCanView(entity);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前采购退货单状态不允许过账");
        }
        accountPeriodGuard.requireOpen(entity.getReturnDate(), "采购退货过账");

        PurchaseReceiptEntity receipt = requirePostedReceipt(
                entity.getReceiptId(),
                entity.getCompanyId(),
                entity.getAccountBookId()
        );
        purchaseReturnQueryService.assertCanView(receipt);
        PurchaseOrderEntity order = purchaseOrderLookupService.requireOrder(receipt.getOrderId());
        purchaseReturnQueryService.assertCanView(order);
        List<PurchaseReturnLineEntity> returnLines = loadReturnLines(entity);
        Map<Long, PurchaseReceiptLineEntity> receiptLines = loadReceiptLines(receipt);
        Map<Long, PurchaseOrderLineEntity> orderLines = purchaseOrderLookupService.loadOrderLinesAsMap(order);
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        List<PostingLineContext> postingLines = preparePostingLines(
                entity,
                returnLines,
                receiptLines,
                orderLines,
                audit
        );

        entity.setStatus("POSTED");
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                purchaseReturnMapper.updateById(entity),
                "采购退货单已被其他操作修改，请刷新后重试"
        );

        for (PostingLineContext postingLine : postingLines) {
            PurchaseReturnLineEntity returnLine = postingLine.returnLine();
            PurchaseReceiptLineEntity receiptLine = postingLine.receiptLine();
            PurchaseOrderLineEntity orderLine = postingLine.orderLine();
            BigDecimal qty = postingLine.qty();

            receiptLine.setReturnedQty(ScalePrecision.quantity(
                    ScalePrecision.zeroDefault(receiptLine.getReturnedQty()).add(qty)
            ));
            receiptLine.setUpdatedBy(audit.userId());
            receiptLine.setUpdatedTime(now);
            OptimisticLockGuard.requireUpdated(
                    purchaseReceiptLineMapper.updateById(receiptLine),
                    "采购入库明细已被其他操作修改，请刷新后重试"
            );

            orderLine.setReceivedQty(ScalePrecision.quantity(
                    ScalePrecision.zeroDefault(orderLine.getReceivedQty()).subtract(qty)
            ));
            orderLine.setUpdatedBy(audit.userId());
            orderLine.setUpdatedTime(now);
            OptimisticLockGuard.requireUpdated(
                    purchaseOrderLineMapper.updateById(orderLine),
                    "采购订单明细已被其他操作修改，请刷新后重试"
            );

            inventoryPostingService.postOutbound(
                    new InventoryPostingCommand(
                            entity.getWarehouseId(),
                            returnLine.getProductId(),
                            "PURCHASE_RETURN",
                            entity.getReturnNo(),
                            returnLine.getId(),
                            returnLine.getQty(),
                            returnLine.getAmount(),
                            returnLine.getRemark(),
                            entity.getReturnDate(),
                            returnLine.getLotNo(),
                            returnLine.getProductionDate(),
                            returnLine.getExpiryDate(),
                            returnLine.getLocationId()
                    ),
                    audit,
                    "库存不足，不能执行采购退货"
            );
            inventorySerialNumberService.issueOutboundSerials(
                    returnLine.getProductId(),
                    returnLine.getSerialNos(),
                    "PURCHASE_RETURN",
                    entity.getReturnNo(),
                    returnLine.getQty(),
                    audit
            );
        }

        purchaseOrderReceiptStatusService.refreshReceiptStatus(receipt.getOrderId(), audit, now);
        financePostingService.recordPurchaseReturn(entity, order, audit);
        return purchaseReturnQueryService.getById(id);
    }

    private List<PostingLineContext> preparePostingLines(
            PurchaseReturnEntity entity,
            List<PurchaseReturnLineEntity> returnLines,
            Map<Long, PurchaseReceiptLineEntity> receiptLines,
            Map<Long, PurchaseOrderLineEntity> orderLines,
            AuditMetadata audit
    ) {
        AccumulatedQuantityValidator receiptLineQtyValidator =
                new AccumulatedQuantityValidator("退货数量超过采购入库明细剩余可退数量");
        AccumulatedQuantityValidator inventoryQtyValidator =
                new AccumulatedQuantityValidator("库存不足，不能执行采购退货");
        List<PostingLineContext> postingLines = new ArrayList<>(returnLines.size());
        for (PurchaseReturnLineEntity returnLine : returnLines) {
            PurchaseReceiptLineEntity receiptLine = requireReceiptLine(receiptLines, returnLine.getReceiptLineId());
            PurchaseOrderLineEntity orderLine = purchaseOrderLookupService.requireOrderLine(
                    orderLines,
                    returnLine.getOrderLineId()
            );
            BigDecimal qty = ScalePrecision.quantity(returnLine.getQty());
            receiptLineQtyValidator.ensureWithinLimit(
                    receiptLine.getId(),
                    qty,
                    availableQty(receiptLine)
            );
            inventoryQtyValidator.ensureWithinLimit(
                    returnLine.getProductId(),
                    qty,
                    productId -> inventoryPostingService.getQtyAvailable(
                            entity.getWarehouseId(),
                            productId,
                            audit.companyId(),
                            audit.accountBookId()
                    )
            );
            postingLines.add(new PostingLineContext(returnLine, receiptLine, orderLine, qty));
        }
        return postingLines;
    }

    private PurchaseReturnEntity requireReturn(Long id) {
        PurchaseReturnEntity entity = purchaseReturnMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("采购退货单不存在");
        }
        return entity;
    }

    private PurchaseReceiptEntity requirePostedReceipt(Long id, Long companyId, Long accountBookId) {
        PurchaseReceiptEntity entity = purchaseReceiptMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !"POSTED".equals(entity.getStatus())
                || !Objects.equals(entity.getCompanyId(), companyId)
                || !Objects.equals(entity.getAccountBookId(), accountBookId)) {
            throw new IllegalArgumentException("采购入库单未过账，不能创建采购退货单");
        }
        return entity;
    }

    private List<PurchaseReturnLineEntity> loadReturnLines(PurchaseReturnEntity entity) {
        return purchaseReturnLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseReturnLineEntity>()
                        .eq(PurchaseReturnLineEntity::getCompanyId, entity.getCompanyId())
                        .eq(PurchaseReturnLineEntity::getAccountBookId, entity.getAccountBookId())
                        .eq(PurchaseReturnLineEntity::getReturnId, entity.getId())
                        .orderByAsc(PurchaseReturnLineEntity::getLineNo)
        );
    }

    private Map<Long, PurchaseReceiptLineEntity> loadReceiptLines(PurchaseReceiptEntity receipt) {
        return purchaseReceiptLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseReceiptLineEntity>()
                        .eq(PurchaseReceiptLineEntity::getCompanyId, receipt.getCompanyId())
                        .eq(PurchaseReceiptLineEntity::getAccountBookId, receipt.getAccountBookId())
                        .eq(PurchaseReceiptLineEntity::getReceiptId, receipt.getId())
        ).stream().collect(Collectors.toMap(PurchaseReceiptLineEntity::getId, Function.identity()));
    }

    private PurchaseReceiptLineEntity requireReceiptLine(
            Map<Long, PurchaseReceiptLineEntity> receiptLines,
            Long receiptLineId
    ) {
        PurchaseReceiptLineEntity entity = receiptLines.get(receiptLineId);
        if (entity == null) {
            throw new IllegalArgumentException("采购入库单明细不存在");
        }
        return entity;
    }

    private BigDecimal availableQty(PurchaseReceiptLineEntity receiptLine) {
        return PurchaseReturnQuantities.from(
                receiptLine.getQty(),
                receiptLine.getReturnedQty()
        ).availableReturnQty();
    }

    private record PostingLineContext(
            PurchaseReturnLineEntity returnLine,
            PurchaseReceiptLineEntity receiptLine,
            PurchaseOrderLineEntity orderLine,
            BigDecimal qty
    ) {
    }
}

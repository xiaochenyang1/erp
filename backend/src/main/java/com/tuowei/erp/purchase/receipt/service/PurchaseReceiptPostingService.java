package com.tuowei.erp.purchase.receipt.service;

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
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderLookupService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderReceiptStatusService;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptLineEntity;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptResponse;
import com.tuowei.erp.purchase.support.AccumulatedQuantityValidator;
import com.tuowei.erp.purchase.support.PurchaseReceiptQuantities;
import com.tuowei.erp.qc.inspection.service.QcInspectionGate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class PurchaseReceiptPostingService {

    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final PurchaseReceiptLineMapper purchaseReceiptLineMapper;
    private final PurchaseOrderLineMapper purchaseOrderLineMapper;
    private final WarehouseMapper warehouseMapper;
    private final InventoryPostingService inventoryPostingService;
    private final InventorySerialNumberService inventorySerialNumberService;
    private final PurchaseOrderLookupService purchaseOrderLookupService;
    private final PurchaseOrderReceiptStatusService purchaseOrderReceiptStatusService;
    private final FinancePostingService financePostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final PurchaseReceiptQueryService purchaseReceiptQueryService;
    private final AccountPeriodGuard accountPeriodGuard;
    private final QcInspectionGate qcInspectionGate;
    private final ProductValidator productValidator;

    public PurchaseReceiptPostingService(
            PurchaseReceiptMapper purchaseReceiptMapper,
            PurchaseReceiptLineMapper purchaseReceiptLineMapper,
            PurchaseOrderLineMapper purchaseOrderLineMapper,
            WarehouseMapper warehouseMapper,
            InventoryPostingService inventoryPostingService,
            InventorySerialNumberService inventorySerialNumberService,
            PurchaseOrderLookupService purchaseOrderLookupService,
            PurchaseOrderReceiptStatusService purchaseOrderReceiptStatusService,
            FinancePostingService financePostingService,
            AuditMetadataFactory auditMetadataFactory,
            PurchaseReceiptQueryService purchaseReceiptQueryService,
            AccountPeriodGuard accountPeriodGuard,
            QcInspectionGate qcInspectionGate,
            ProductValidator productValidator
    ) {
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.purchaseReceiptLineMapper = purchaseReceiptLineMapper;
        this.purchaseOrderLineMapper = purchaseOrderLineMapper;
        this.warehouseMapper = warehouseMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.inventorySerialNumberService = inventorySerialNumberService;
        this.purchaseOrderLookupService = purchaseOrderLookupService;
        this.purchaseOrderReceiptStatusService = purchaseOrderReceiptStatusService;
        this.financePostingService = financePostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.purchaseReceiptQueryService = purchaseReceiptQueryService;
        this.accountPeriodGuard = accountPeriodGuard;
        this.qcInspectionGate = qcInspectionGate;
        this.productValidator = productValidator;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public PurchaseReceiptResponse post(Long id) {
        PurchaseReceiptEntity receipt = requireReceipt(id);
        purchaseReceiptQueryService.assertCanView(receipt);
        if (!"DRAFT".equals(receipt.getStatus())) {
            throw new IllegalArgumentException("当前采购入库单状态不允许过账");
        }
        accountPeriodGuard.requireOpen(receipt.getReceiptDate(), "采购入库过账");

        PurchaseOrderEntity order = purchaseOrderLookupService.requireOrder(receipt.getOrderId());
        purchaseReceiptQueryService.assertCanView(order);
        if (!"APPROVED".equals(order.getStatus())) {
            throw new IllegalArgumentException("采购订单未审批通过，不能执行入库过账");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        requireWarehouse(receipt.getWarehouseId(), audit.companyId(), audit.accountBookId());

        List<PurchaseReceiptLineEntity> receiptLines = loadReceiptLines(receipt);
        Map<Long, PurchaseOrderLineEntity> orderLines = purchaseOrderLookupService.loadOrderLinesAsMap(order);
        LocalDateTime now = audit.now();
        List<PostingLineContext> postingLines = preparePostingLines(
                receiptLines,
                orderLines,
                audit.companyId(),
                audit.accountBookId()
        );
        qcInspectionGate.assertReceiptInspected(receipt, receiptLines, audit);

        receipt.setStatus("POSTED");
        receipt.setUpdatedBy(audit.userId());
        receipt.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                purchaseReceiptMapper.updateById(receipt),
                "采购入库单已被其他操作修改，请刷新后重试"
        );

        for (PostingLineContext postingLine : postingLines) {
            PurchaseReceiptLineEntity receiptLine = postingLine.receiptLine();
            PurchaseOrderLineEntity orderLine = postingLine.orderLine();
            orderLine.setReceivedQty(PurchaseReceiptQuantities.from(
                    orderLine.getQty(),
                    orderLine.getReceivedQty()
            ).receivedQtyAfter(postingLine.qty()));
            orderLine.setUpdatedBy(audit.userId());
            orderLine.setUpdatedTime(now);
            OptimisticLockGuard.requireUpdated(
                    purchaseOrderLineMapper.updateById(orderLine),
                    "采购订单明细已被其他操作修改，请刷新后重试"
            );

            inventoryPostingService.postInbound(
                    new InventoryPostingCommand(
                            receipt.getWarehouseId(),
                            receiptLine.getProductId(),
                            "PURCHASE_RECEIPT",
                            receipt.getReceiptNo(),
                            receiptLine.getId(),
                            receiptLine.getQty(),
                            receiptLine.getAmount(),
                            receiptLine.getRemark(),
                            receipt.getReceiptDate(),
                            receiptLine.getLotNo(),
                            receiptLine.getProductionDate(),
                            receiptLine.getExpiryDate(),
                            receiptLine.getLocationId()
                    ),
                    audit
            );
            inventorySerialNumberService.registerInboundSerials(
                    receiptLine.getProductId(),
                    receipt.getWarehouseId(),
                    receiptLine.getLocationId(),
                    receiptLine.getSerialNos(),
                    "PURCHASE_RECEIPT",
                    receipt.getReceiptNo(),
                    receiptLine.getQty(),
                    audit
            );
        }

        purchaseOrderReceiptStatusService.refreshReceiptStatus(order.getId(), audit, now);
        financePostingService.recordPurchaseReceipt(receipt, order, audit);
        return purchaseReceiptQueryService.getById(id);
    }

    private List<PostingLineContext> preparePostingLines(
            List<PurchaseReceiptLineEntity> receiptLines,
            Map<Long, PurchaseOrderLineEntity> orderLines,
            Long companyId,
            Long accountBookId
    ) {
        Map<Long, ProductEntity> products = productValidator.requireProducts(
                receiptLines.stream().map(PurchaseReceiptLineEntity::getProductId).toList(),
                companyId,
                accountBookId
        );
        for (PurchaseReceiptLineEntity receiptLine : receiptLines) {
            ProductEntity product = products.get(receiptLine.getProductId());
            if (product.getLotControlled() != null && product.getLotControlled() == 1
                    && (receiptLine.getLotNo() == null || receiptLine.getLotNo().isBlank())) {
                throw new IllegalArgumentException("商品启用批次管理，入库行必须填写批号");
            }
        }

        AccumulatedQuantityValidator quantityValidator =
                new AccumulatedQuantityValidator("入库数量超过采购订单剩余可入库数量");
        List<PostingLineContext> postingLines = new ArrayList<>(receiptLines.size());
        for (PurchaseReceiptLineEntity receiptLine : receiptLines) {
            PurchaseOrderLineEntity orderLine = purchaseOrderLookupService.requireOrderLine(
                    orderLines,
                    receiptLine.getOrderLineId()
            );
            BigDecimal qty = ScalePrecision.quantity(receiptLine.getQty());
            quantityValidator.ensureWithinLimit(
                    orderLine.getId(),
                    qty,
                    availableReceiptQty(orderLine)
            );
            postingLines.add(new PostingLineContext(receiptLine, orderLine, qty));
        }
        return postingLines;
    }

    private PurchaseReceiptEntity requireReceipt(Long id) {
        PurchaseReceiptEntity entity = purchaseReceiptMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("采购入库单不存在");
        }
        return entity;
    }

    private WarehouseEntity requireWarehouse(Long id, Long companyId, Long accountBookId) {
        WarehouseEntity entity = warehouseMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(entity.getStatus())
                || !Objects.equals(entity.getCompanyId(), companyId)
                || !Objects.equals(entity.getAccountBookId(), accountBookId)) {
            throw new IllegalArgumentException("仓库不存在或已停用");
        }
        return entity;
    }

    private List<PurchaseReceiptLineEntity> loadReceiptLines(PurchaseReceiptEntity receipt) {
        return purchaseReceiptLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseReceiptLineEntity>()
                        .eq(PurchaseReceiptLineEntity::getCompanyId, receipt.getCompanyId())
                        .eq(PurchaseReceiptLineEntity::getAccountBookId, receipt.getAccountBookId())
                        .eq(PurchaseReceiptLineEntity::getReceiptId, receipt.getId())
                        .orderByAsc(PurchaseReceiptLineEntity::getLineNo)
        );
    }

    private BigDecimal availableReceiptQty(PurchaseOrderLineEntity orderLine) {
        return PurchaseReceiptQuantities.from(
                orderLine.getQty(),
                orderLine.getReceivedQty()
        ).availableReceiptQty();
    }

    private record PostingLineContext(
            PurchaseReceiptLineEntity receiptLine,
            PurchaseOrderLineEntity orderLine,
            BigDecimal qty
    ) {
    }
}

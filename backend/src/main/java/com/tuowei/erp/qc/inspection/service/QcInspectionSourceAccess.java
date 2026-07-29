package com.tuowei.erp.qc.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptLineEntity;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/** Shared tenant-safe access to source documents used by inspection creation and judgment. */
@Component
public class QcInspectionSourceAccess {

    private static final String STATUS_DRAFT = "DRAFT";

    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final PurchaseReceiptLineMapper purchaseReceiptLineMapper;
    private final SalesDeliveryMapper salesDeliveryMapper;
    private final SalesDeliveryLineMapper salesDeliveryLineMapper;
    private final ProductionOrderMapper productionOrderMapper;

    public QcInspectionSourceAccess(
            PurchaseReceiptMapper purchaseReceiptMapper,
            PurchaseReceiptLineMapper purchaseReceiptLineMapper,
            SalesDeliveryMapper salesDeliveryMapper,
            SalesDeliveryLineMapper salesDeliveryLineMapper,
            ProductionOrderMapper productionOrderMapper
    ) {
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.purchaseReceiptLineMapper = purchaseReceiptLineMapper;
        this.salesDeliveryMapper = salesDeliveryMapper;
        this.salesDeliveryLineMapper = salesDeliveryLineMapper;
        this.productionOrderMapper = productionOrderMapper;
    }

    public PurchaseReceiptEntity requireDraftReceipt(Long receiptId, AuditMetadata audit) {
        PurchaseReceiptEntity receipt = purchaseReceiptMapper.selectById(receiptId);
        if (receipt == null || receipt.getDeletedFlag() == null || receipt.getDeletedFlag() != 0
                || !Objects.equals(receipt.getCompanyId(), audit.companyId())
                || !Objects.equals(receipt.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("采购入库单不存在");
        }
        if (!STATUS_DRAFT.equals(receipt.getStatus())) {
            throw new IllegalArgumentException("采购入库单不是草稿状态，不能进行来料检验");
        }
        return receipt;
    }

    public SalesDeliveryEntity requireDraftDelivery(Long deliveryId, AuditMetadata audit) {
        SalesDeliveryEntity delivery = salesDeliveryMapper.selectById(deliveryId);
        if (delivery == null || delivery.getDeletedFlag() == null || delivery.getDeletedFlag() != 0
                || !Objects.equals(delivery.getCompanyId(), audit.companyId())
                || !Objects.equals(delivery.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("销售出库单不存在");
        }
        if (!STATUS_DRAFT.equals(delivery.getStatus())) {
            throw new IllegalArgumentException("销售出库单不是草稿状态，不能进行出库检验");
        }
        return delivery;
    }

    public ProductionOrderEntity requireInspectableProductionOrder(Long orderId, AuditMetadata audit) {
        ProductionOrderEntity order = productionOrderMapper.selectById(orderId);
        if (order == null
                || !Objects.equals(order.getCompanyId(), audit.companyId())
                || !Objects.equals(order.getAccountBookId(), audit.accountBookId())
                || Integer.valueOf(1).equals(order.getDeletedFlag())) {
            throw new IllegalArgumentException("生产工单不存在");
        }
        if (!ProductionOrderService.STATUS_RELEASED.equals(order.getStatus())
                && !ProductionOrderService.STATUS_MATERIAL_ISSUED.equals(order.getStatus())) {
            throw new IllegalArgumentException("仅已下达/已领料的生产工单可做过程检");
        }
        return order;
    }

    public List<PurchaseReceiptLineEntity> loadReceiptLines(PurchaseReceiptEntity receipt) {
        return purchaseReceiptLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseReceiptLineEntity>()
                        .eq(PurchaseReceiptLineEntity::getCompanyId, receipt.getCompanyId())
                        .eq(PurchaseReceiptLineEntity::getAccountBookId, receipt.getAccountBookId())
                        .eq(PurchaseReceiptLineEntity::getReceiptId, receipt.getId())
                        .orderByAsc(PurchaseReceiptLineEntity::getLineNo)
        );
    }

    public List<SalesDeliveryLineEntity> loadDeliveryLines(SalesDeliveryEntity delivery) {
        return salesDeliveryLineMapper.selectList(
                new LambdaQueryWrapper<SalesDeliveryLineEntity>()
                        .eq(SalesDeliveryLineEntity::getCompanyId, delivery.getCompanyId())
                        .eq(SalesDeliveryLineEntity::getAccountBookId, delivery.getAccountBookId())
                        .eq(SalesDeliveryLineEntity::getDeliveryId, delivery.getId())
                        .orderByAsc(SalesDeliveryLineEntity::getLineNo)
        );
    }
}

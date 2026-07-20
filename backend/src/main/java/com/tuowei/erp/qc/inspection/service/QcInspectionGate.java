package com.tuowei.erp.qc.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptLineEntity;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionLineMapper;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionOrderMapper;
import com.tuowei.erp.qc.inspection.model.QcInspectionLineEntity;
import com.tuowei.erp.qc.inspection.model.QcInspectionOrderEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 质检闸门。
 * <ul>
 *   <li>IQC：采购入库过账前 {@link #assertReceiptInspected}</li>
 *   <li>OQC：销售出库过账前 {@link #assertDeliveryInspected}（gate-only，不改写库行）</li>
 * </ul>
 * 仅依赖 qc/product mapper，不依赖采购/销售 service，避免循环依赖。
 */
@Component
public class QcInspectionGate {

    public static final String TYPE_IQC = "IQC";
    public static final String TYPE_OQC = "OQC";
    public static final String TYPE_IPQC = "IPQC";

    private final QcInspectionOrderMapper qcInspectionOrderMapper;
    private final QcInspectionLineMapper qcInspectionLineMapper;
    private final ProductMapper productMapper;

    public QcInspectionGate(
            QcInspectionOrderMapper qcInspectionOrderMapper,
            QcInspectionLineMapper qcInspectionLineMapper,
            ProductMapper productMapper
    ) {
        this.qcInspectionOrderMapper = qcInspectionOrderMapper;
        this.qcInspectionLineMapper = qcInspectionLineMapper;
        this.productMapper = productMapper;
    }

    public void assertReceiptInspected(
            PurchaseReceiptEntity receipt,
            List<PurchaseReceiptLineEntity> receiptLines,
            AuditMetadata audit
    ) {
        boolean anyRequiresInspection = receiptLines.stream()
                .anyMatch(line -> inspectionRequired(line.getProductId()));
        if (!anyRequiresInspection) {
            return;
        }

        QcInspectionOrderEntity judged = qcInspectionOrderMapper.selectOne(
                new LambdaQueryWrapper<QcInspectionOrderEntity>()
                        .eq(QcInspectionOrderEntity::getCompanyId, receipt.getCompanyId())
                        .eq(QcInspectionOrderEntity::getAccountBookId, receipt.getAccountBookId())
                        .eq(QcInspectionOrderEntity::getInspectionType, TYPE_IQC)
                        .eq(QcInspectionOrderEntity::getReceiptId, receipt.getId())
                        .eq(QcInspectionOrderEntity::getStatus, "JUDGED")
                        .eq(QcInspectionOrderEntity::getDeletedFlag, 0)
                        .orderByDesc(QcInspectionOrderEntity::getId)
                        .last("LIMIT 1")
        );
        if (judged == null) {
            throw new IllegalArgumentException("存在需检验商品尚未完成质检，不能过账");
        }

        Map<Long, QcInspectionLineEntity> qcLinesByReceiptLine = loadInspectionLines(judged).stream()
                .filter(line -> line.getReceiptLineId() != null)
                .collect(Collectors.toMap(
                        QcInspectionLineEntity::getReceiptLineId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        for (PurchaseReceiptLineEntity receiptLine : receiptLines) {
            if (!inspectionRequired(receiptLine.getProductId())) {
                continue;
            }
            QcInspectionLineEntity qcLine = qcLinesByReceiptLine.get(receiptLine.getId());
            if (qcLine == null) {
                throw new IllegalArgumentException("存在需检验商品尚未完成质检，不能过账");
            }
            BigDecimal qualified = qcLine.getQualifiedQty() == null ? BigDecimal.ZERO : qcLine.getQualifiedQty();
            BigDecimal receiptQty = receiptLine.getQty() == null ? BigDecimal.ZERO : receiptLine.getQty();
            if (qualified.compareTo(receiptQty) != 0) {
                throw new IllegalArgumentException("入库数量与质检合格数量不一致，不能过账");
            }
        }
    }

    /**
     * 出库质检闸门：需检验商品必须存在 JUDGED 的 OQC 检验单，且出库数量=合格数量。
     * gate-only：判定侧不回写出库行，数量不一致时由业务人员先改 DRAFT 出库单再过账。
     */
    public void assertDeliveryInspected(
            SalesDeliveryEntity delivery,
            List<SalesDeliveryLineEntity> deliveryLines,
            AuditMetadata audit
    ) {
        boolean anyRequiresInspection = deliveryLines.stream()
                .anyMatch(line -> inspectionRequired(line.getProductId()));
        if (!anyRequiresInspection) {
            return;
        }

        QcInspectionOrderEntity judged = qcInspectionOrderMapper.selectOne(
                new LambdaQueryWrapper<QcInspectionOrderEntity>()
                        .eq(QcInspectionOrderEntity::getCompanyId, delivery.getCompanyId())
                        .eq(QcInspectionOrderEntity::getAccountBookId, delivery.getAccountBookId())
                        .eq(QcInspectionOrderEntity::getInspectionType, TYPE_OQC)
                        .eq(QcInspectionOrderEntity::getDeliveryId, delivery.getId())
                        .eq(QcInspectionOrderEntity::getStatus, "JUDGED")
                        .eq(QcInspectionOrderEntity::getDeletedFlag, 0)
                        .orderByDesc(QcInspectionOrderEntity::getId)
                        .last("LIMIT 1")
        );
        if (judged == null) {
            throw new IllegalArgumentException("存在需检验商品尚未完成出库质检，不能过账");
        }

        Map<Long, QcInspectionLineEntity> qcLinesByDeliveryLine = loadInspectionLines(judged).stream()
                .filter(line -> line.getDeliveryLineId() != null)
                .collect(Collectors.toMap(
                        QcInspectionLineEntity::getDeliveryLineId,
                        Function.identity(),
                        (existing, replacement) -> existing
                ));

        for (SalesDeliveryLineEntity deliveryLine : deliveryLines) {
            if (!inspectionRequired(deliveryLine.getProductId())) {
                continue;
            }
            QcInspectionLineEntity qcLine = qcLinesByDeliveryLine.get(deliveryLine.getId());
            if (qcLine == null) {
                throw new IllegalArgumentException("存在需检验商品尚未完成出库质检，不能过账");
            }
            BigDecimal qualified = qcLine.getQualifiedQty() == null ? BigDecimal.ZERO : qcLine.getQualifiedQty();
            BigDecimal deliveryQty = deliveryLine.getQty() == null ? BigDecimal.ZERO : deliveryLine.getQty();
            if (qualified.compareTo(deliveryQty) != 0) {
                throw new IllegalArgumentException("出库质检合格数量与出库数量不一致，不能过账");
            }
        }
    }

    /**
     * 生产过程检闸门：成品需检验时，必须存在 JUDGED 的 IPQC，且合格量 >= 本次完工量。
     */
    public void assertProductionInspected(
            Long companyId,
            Long accountBookId,
            Long productionOrderId,
            Long productId,
            BigDecimal completionQty
    ) {
        if (productId == null || !inspectionRequired(productId)) {
            return;
        }
        QcInspectionOrderEntity judged = qcInspectionOrderMapper.selectOne(
                new LambdaQueryWrapper<QcInspectionOrderEntity>()
                        .eq(QcInspectionOrderEntity::getCompanyId, companyId)
                        .eq(QcInspectionOrderEntity::getAccountBookId, accountBookId)
                        .eq(QcInspectionOrderEntity::getInspectionType, TYPE_IPQC)
                        .eq(QcInspectionOrderEntity::getProductionOrderId, productionOrderId)
                        .eq(QcInspectionOrderEntity::getStatus, "JUDGED")
                        .eq(QcInspectionOrderEntity::getDeletedFlag, 0)
                        .orderByDesc(QcInspectionOrderEntity::getId)
                        .last("LIMIT 1")
        );
        if (judged == null) {
            throw new IllegalArgumentException("成品需过程检，尚未完成 IPQC 判定，不能完工");
        }
        BigDecimal qualified = judged.getQualifiedQty() == null ? BigDecimal.ZERO : judged.getQualifiedQty();
        BigDecimal need = completionQty == null ? BigDecimal.ZERO : completionQty;
        if (qualified.compareTo(need) < 0) {
            throw new IllegalArgumentException("过程检合格数量不足，不能完工");
        }
    }

    private List<QcInspectionLineEntity> loadInspectionLines(QcInspectionOrderEntity inspection) {
        return qcInspectionLineMapper.selectList(
                new LambdaQueryWrapper<QcInspectionLineEntity>()
                        .eq(QcInspectionLineEntity::getCompanyId, inspection.getCompanyId())
                        .eq(QcInspectionLineEntity::getAccountBookId, inspection.getAccountBookId())
                        .eq(QcInspectionLineEntity::getInspectionId, inspection.getId())
                        .eq(QcInspectionLineEntity::getDeletedFlag, 0)
        );
    }

    private boolean inspectionRequired(Long productId) {
        if (productId == null) {
            return false;
        }
        ProductEntity product = productMapper.selectById(productId);
        return product != null && product.getInspectionRequired() != null && product.getInspectionRequired() == 1;
    }
}

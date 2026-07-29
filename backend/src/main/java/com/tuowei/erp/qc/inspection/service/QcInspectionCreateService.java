package com.tuowei.erp.qc.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.service.ProductionOrderService;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptLineEntity;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionLineMapper;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionOrderMapper;
import com.tuowei.erp.qc.inspection.model.QcInspectionLineEntity;
import com.tuowei.erp.qc.inspection.model.QcInspectionOrderEntity;
import com.tuowei.erp.qc.inspection.web.QcInspectionCreateRequest;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Creates draft IQC, OQC and IPQC inspections from tenant-safe source snapshots. */
@Service
public class QcInspectionCreateService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private static final String TYPE_IQC = QcInspectionGate.TYPE_IQC;
    private static final String TYPE_OQC = QcInspectionGate.TYPE_OQC;
    private static final String TYPE_IPQC = QcInspectionGate.TYPE_IPQC;

    private final QcInspectionOrderMapper qcInspectionOrderMapper;
    private final QcInspectionLineMapper qcInspectionLineMapper;
    private final ProductionOrderMapper productionOrderMapper;
    private final QcInspectionNumberService qcInspectionNumberService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final QcInspectionSourceAccess sourceAccess;

    public QcInspectionCreateService(
            QcInspectionOrderMapper qcInspectionOrderMapper,
            QcInspectionLineMapper qcInspectionLineMapper,
            ProductionOrderMapper productionOrderMapper,
            QcInspectionNumberService qcInspectionNumberService,
            AuditMetadataFactory auditMetadataFactory,
            QcInspectionSourceAccess sourceAccess
    ) {
        this.qcInspectionOrderMapper = qcInspectionOrderMapper;
        this.qcInspectionLineMapper = qcInspectionLineMapper;
        this.productionOrderMapper = productionOrderMapper;
        this.qcInspectionNumberService = qcInspectionNumberService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.sourceAccess = sourceAccess;
    }

    @Transactional
    public CreationResult create(QcInspectionCreateRequest request) {
        String inspectionType = normalizeInspectionType(request.inspectionType());
        if (TYPE_OQC.equals(inspectionType)) {
            return createOqc(request);
        }
        if (TYPE_IPQC.equals(inspectionType)) {
            return createIpqc(request);
        }
        return createIqc(request);
    }

    private CreationResult createIqc(QcInspectionCreateRequest request) {
        if (request.receiptId() == null) {
            throw new IllegalArgumentException("来料检验必须指定采购入库单");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        PurchaseReceiptEntity receipt = sourceAccess.requireDraftReceipt(request.receiptId(), audit);
        assertNoActiveInspectionForReceipt(receipt, audit);

        List<PurchaseReceiptLineEntity> receiptLines = sourceAccess.loadReceiptLines(receipt);
        if (receiptLines.isEmpty()) {
            throw new IllegalArgumentException("采购入库单没有明细，无法创建检验单");
        }
        LocalDateTime now = audit.now();

        QcInspectionOrderEntity inspection = newBaseInspection(audit, request, TYPE_IQC, now);
        inspection.setReceiptId(receipt.getId());
        inspection.setDeliveryId(null);
        inspection.setOrderId(receipt.getOrderId());
        inspection.setWarehouseId(receipt.getWarehouseId());
        inspection.setSupplierId(null);
        BigDecimal totalQty = receiptLines.stream()
                .map(line -> ScalePrecision.quantity(line.getQty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        inspection.setTotalQty(ScalePrecision.quantity(totalQty));
        qcInspectionOrderMapper.insert(inspection);

        List<QcInspectionLineEntity> lines = new ArrayList<>();
        int lineNo = 1;
        for (PurchaseReceiptLineEntity receiptLine : receiptLines) {
            QcInspectionLineEntity line = newBaseLine(audit, inspection, lineNo++, now);
            line.setReceiptLineId(receiptLine.getId());
            line.setDeliveryLineId(null);
            line.setProductId(receiptLine.getProductId());
            line.setInspectedQty(ScalePrecision.quantity(receiptLine.getQty()));
            line.setRemark(receiptLine.getRemark());
            qcInspectionLineMapper.insert(line);
            lines.add(line);
        }

        return new CreationResult(inspection, lines);
    }

    private CreationResult createOqc(QcInspectionCreateRequest request) {
        if (request.deliveryId() == null) {
            throw new IllegalArgumentException("出库检验必须指定销售出库单");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        SalesDeliveryEntity delivery = sourceAccess.requireDraftDelivery(request.deliveryId(), audit);
        assertNoActiveInspectionForDelivery(delivery, audit);

        List<SalesDeliveryLineEntity> deliveryLines = sourceAccess.loadDeliveryLines(delivery);
        if (deliveryLines.isEmpty()) {
            throw new IllegalArgumentException("销售出库单没有明细，无法创建检验单");
        }
        LocalDateTime now = audit.now();

        QcInspectionOrderEntity inspection = newBaseInspection(audit, request, TYPE_OQC, now);
        inspection.setReceiptId(null);
        inspection.setDeliveryId(delivery.getId());
        inspection.setOrderId(delivery.getOrderId());
        inspection.setWarehouseId(delivery.getWarehouseId());
        inspection.setSupplierId(null);
        BigDecimal totalQty = deliveryLines.stream()
                .map(line -> ScalePrecision.quantity(line.getQty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        inspection.setTotalQty(ScalePrecision.quantity(totalQty));
        qcInspectionOrderMapper.insert(inspection);

        List<QcInspectionLineEntity> lines = new ArrayList<>();
        int lineNo = 1;
        for (SalesDeliveryLineEntity deliveryLine : deliveryLines) {
            QcInspectionLineEntity line = newBaseLine(audit, inspection, lineNo++, now);
            line.setReceiptLineId(null);
            line.setDeliveryLineId(deliveryLine.getId());
            line.setProductId(deliveryLine.getProductId());
            line.setInspectedQty(ScalePrecision.quantity(deliveryLine.getQty()));
            line.setRemark(deliveryLine.getRemark());
            qcInspectionLineMapper.insert(line);
            lines.add(line);
        }

        return new CreationResult(inspection, lines);
    }

    private CreationResult createIpqc(QcInspectionCreateRequest request) {
        if (request.productionOrderId() == null) {
            throw new IllegalArgumentException("过程检必须指定生产工单");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        ProductionOrderEntity order = productionOrderMapper.selectById(request.productionOrderId());
        if (order == null
                || !Objects.equals(order.getCompanyId(), audit.companyId())
                || !Objects.equals(order.getAccountBookId(), audit.accountBookId())
                || Integer.valueOf(1).equals(order.getDeletedFlag())) {
            throw new IllegalArgumentException("生产工单不存在");
        }
        if (ProductionOrderService.STATUS_CANCELLED.equals(order.getStatus())
                || ProductionOrderService.STATUS_COMPLETED.equals(order.getStatus())
                || ProductionOrderService.STATUS_DRAFT.equals(order.getStatus())) {
            throw new IllegalArgumentException("仅已下达/已领料的生产工单可做过程检");
        }
        Long active = qcInspectionOrderMapper.selectCount(new LambdaQueryWrapper<QcInspectionOrderEntity>()
                .eq(QcInspectionOrderEntity::getCompanyId, audit.companyId())
                .eq(QcInspectionOrderEntity::getAccountBookId, audit.accountBookId())
                .eq(QcInspectionOrderEntity::getInspectionType, TYPE_IPQC)
                .eq(QcInspectionOrderEntity::getProductionOrderId, order.getId())
                .eq(QcInspectionOrderEntity::getDeletedFlag, 0)
                .in(QcInspectionOrderEntity::getStatus, STATUS_DRAFT, STATUS_SUBMITTED));
        if (active != null && active > 0) {
            throw new IllegalArgumentException("该生产工单已有进行中的过程检");
        }
        LocalDateTime now = audit.now();
        QcInspectionOrderEntity inspection = newBaseInspection(audit, request, TYPE_IPQC, now);
        inspection.setReceiptId(null);
        inspection.setDeliveryId(null);
        inspection.setProductionOrderId(order.getId());
        inspection.setOrderId(order.getId());
        inspection.setWarehouseId(order.getFinishedWarehouseId());
        inspection.setSupplierId(null);
        BigDecimal planned = ScalePrecision.quantity(ScalePrecision.zeroDefault(order.getPlannedQty()));
        inspection.setTotalQty(planned);
        qcInspectionOrderMapper.insert(inspection);

        QcInspectionLineEntity line = newBaseLine(audit, inspection, 1, now);
        line.setReceiptLineId(null);
        line.setDeliveryLineId(null);
        line.setProductId(order.getProductId());
        line.setInspectedQty(planned);
        line.setRemark("过程检-成品");
        qcInspectionLineMapper.insert(line);
        return new CreationResult(inspection, List.of(line));
    }

    private QcInspectionOrderEntity newBaseInspection(
            AuditMetadata audit,
            QcInspectionCreateRequest request,
            String inspectionType,
            LocalDateTime now
    ) {
        QcInspectionOrderEntity inspection = new QcInspectionOrderEntity();
        inspection.setCompanyId(audit.companyId());
        inspection.setAccountBookId(audit.accountBookId());
        inspection.setInspectionNo(qcInspectionNumberService.nextInspectionNo(request.inspectionDate()));
        inspection.setInspectionType(inspectionType);
        inspection.setInspectionDate(request.inspectionDate());
        inspection.setStatus(STATUS_DRAFT);
        inspection.setQualifiedQty(ScalePrecision.quantity(BigDecimal.ZERO));
        inspection.setUnqualifiedQty(ScalePrecision.quantity(BigDecimal.ZERO));
        inspection.setDeletedFlag(0);
        inspection.setRemark(request.remark());
        inspection.setCreatedBy(audit.userId());
        inspection.setCreatedTime(now);
        inspection.setUpdatedBy(audit.userId());
        inspection.setUpdatedTime(now);
        inspection.setVersion(0);
        return inspection;
    }

    private QcInspectionLineEntity newBaseLine(
            AuditMetadata audit,
            QcInspectionOrderEntity inspection,
            int lineNo,
            LocalDateTime now
    ) {
        QcInspectionLineEntity line = new QcInspectionLineEntity();
        line.setCompanyId(audit.companyId());
        line.setAccountBookId(audit.accountBookId());
        line.setInspectionId(inspection.getId());
        line.setLineNo(lineNo);
        line.setQualifiedQty(ScalePrecision.quantity(BigDecimal.ZERO));
        line.setUnqualifiedQty(ScalePrecision.quantity(BigDecimal.ZERO));
        line.setDefectReason(null);
        line.setDeletedFlag(0);
        line.setCreatedBy(audit.userId());
        line.setCreatedTime(now);
        line.setUpdatedBy(audit.userId());
        line.setUpdatedTime(now);
        line.setVersion(0);
        return line;
    }

    private void assertNoActiveInspectionForReceipt(PurchaseReceiptEntity receipt, AuditMetadata audit) {
        boolean exists = qcInspectionOrderMapper.exists(
                new LambdaQueryWrapper<QcInspectionOrderEntity>()
                        .eq(QcInspectionOrderEntity::getCompanyId, audit.companyId())
                        .eq(QcInspectionOrderEntity::getAccountBookId, audit.accountBookId())
                        .eq(QcInspectionOrderEntity::getInspectionType, TYPE_IQC)
                        .eq(QcInspectionOrderEntity::getReceiptId, receipt.getId())
                        .eq(QcInspectionOrderEntity::getDeletedFlag, 0)
                        .ne(QcInspectionOrderEntity::getStatus, STATUS_CANCELLED)
        );
        if (exists) {
            throw new IllegalArgumentException("该采购入库单已存在有效的检验单");
        }
    }

    private void assertNoActiveInspectionForDelivery(SalesDeliveryEntity delivery, AuditMetadata audit) {
        boolean exists = qcInspectionOrderMapper.exists(
                new LambdaQueryWrapper<QcInspectionOrderEntity>()
                        .eq(QcInspectionOrderEntity::getCompanyId, audit.companyId())
                        .eq(QcInspectionOrderEntity::getAccountBookId, audit.accountBookId())
                        .eq(QcInspectionOrderEntity::getInspectionType, TYPE_OQC)
                        .eq(QcInspectionOrderEntity::getDeliveryId, delivery.getId())
                        .eq(QcInspectionOrderEntity::getDeletedFlag, 0)
                        .ne(QcInspectionOrderEntity::getStatus, STATUS_CANCELLED)
        );
        if (exists) {
            throw new IllegalArgumentException("该销售出库单已存在有效的检验单");
        }
    }

    private String normalizeInspectionType(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim() : null;
        if (normalized == null) {
            return TYPE_IQC;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!TYPE_IQC.equals(upper) && !TYPE_OQC.equals(upper) && !TYPE_IPQC.equals(upper)) {
            throw new IllegalArgumentException("检验类型仅支持 IQC、OQC 或 IPQC");
        }
        return upper;
    }

    public record CreationResult(
            QcInspectionOrderEntity inspection,
            List<QcInspectionLineEntity> lines
    ) {
        public CreationResult {
            lines = List.copyOf(lines);
        }
    }
}

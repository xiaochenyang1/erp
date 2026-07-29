package com.tuowei.erp.qc.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageQueryNormalizer;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptLineEntity;
import com.tuowei.erp.purchase.support.PurchaseAmountCalculator;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionLineMapper;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionOrderMapper;
import com.tuowei.erp.qc.inspection.model.QcInspectionLineEntity;
import com.tuowei.erp.qc.inspection.model.QcInspectionOrderEntity;
import com.tuowei.erp.qc.inspection.web.QcInspectionCreateRequest;
import com.tuowei.erp.qc.inspection.web.QcInspectionJudgeLineRequest;
import com.tuowei.erp.qc.inspection.web.QcInspectionJudgeRequest;
import com.tuowei.erp.qc.inspection.web.QcInspectionLineResponse;
import com.tuowei.erp.qc.inspection.web.QcInspectionPageQuery;
import com.tuowei.erp.qc.inspection.web.QcInspectionResponse;
import com.tuowei.erp.qc.inspection.web.QcInspectionUpdateLineRequest;
import com.tuowei.erp.qc.inspection.web.QcInspectionUpdateRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QcInspectionService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_SUBMITTED = "SUBMITTED";
    private static final String STATUS_JUDGED = "JUDGED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private static final String TYPE_IQC = QcInspectionGate.TYPE_IQC;
    private static final String TYPE_OQC = QcInspectionGate.TYPE_OQC;
    private static final String TYPE_IPQC = QcInspectionGate.TYPE_IPQC;

    private static final List<String> INSPECTION_EXPORT_HEADERS = List.of(
            "inspectionNo",
            "inspectionType",
            "receiptId",
            "deliveryId",
            "orderId",
            "warehouseId",
            "inspectionDate",
            "status",
            "totalQty",
            "qualifiedQty",
            "unqualifiedQty",
            "remark"
    );

    private final QcInspectionOrderMapper qcInspectionOrderMapper;
    private final QcInspectionLineMapper qcInspectionLineMapper;
    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final PurchaseReceiptLineMapper purchaseReceiptLineMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final QcInspectionCreateService qcInspectionCreateService;
    private final QcInspectionSourceAccess qcInspectionSourceAccess;

    public QcInspectionService(
            QcInspectionOrderMapper qcInspectionOrderMapper,
            QcInspectionLineMapper qcInspectionLineMapper,
            PurchaseReceiptMapper purchaseReceiptMapper,
            PurchaseReceiptLineMapper purchaseReceiptLineMapper,
            AuditMetadataFactory auditMetadataFactory,
            QcInspectionCreateService qcInspectionCreateService,
            QcInspectionSourceAccess qcInspectionSourceAccess
    ) {
        this.qcInspectionOrderMapper = qcInspectionOrderMapper;
        this.qcInspectionLineMapper = qcInspectionLineMapper;
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.purchaseReceiptLineMapper = purchaseReceiptLineMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.qcInspectionCreateService = qcInspectionCreateService;
        this.qcInspectionSourceAccess = qcInspectionSourceAccess;
    }

    @Transactional
    public QcInspectionResponse create(QcInspectionCreateRequest request) {
        QcInspectionCreateService.CreationResult result = qcInspectionCreateService.create(request);
        return toResponse(result.inspection(), result.lines());
    }

    @Transactional(readOnly = true)
    public PageResponse<QcInspectionResponse> list(QcInspectionPageQuery query) {
        QcInspectionPageQuery safeQuery = query == null ? new QcInspectionPageQuery() : query;
        long pageNo = PageQueryNormalizer.normalizePageNo(safeQuery.getPageNo());
        long pageSize = PageQueryNormalizer.normalizePageSize(safeQuery.getPageSize());
        AuditMetadata audit = auditMetadataFactory.current();

        Page<QcInspectionOrderEntity> page = new Page<>(pageNo, pageSize);
        Page<QcInspectionOrderEntity> result = qcInspectionOrderMapper.selectPage(page, buildListQuery(audit, safeQuery));

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toSummaryResponse).toList()
        );
    }

    public StreamingResponseBody exportInspections(QcInspectionPageQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        QcInspectionPageQuery safeQuery = query == null ? new QcInspectionPageQuery() : query;
        return outputStream -> withAuthentication(authentication, () -> CsvExport.write(outputStream, INSPECTION_EXPORT_HEADERS, rowWriter -> {
            AuditMetadata audit = auditMetadataFactory.current();
            for (QcInspectionOrderEntity entity : qcInspectionOrderMapper.selectList(buildListQuery(audit, safeQuery))) {
                rowWriter.write(inspectionExportRow(entity));
            }
        }));
    }

    @Transactional(readOnly = true)
    public QcInspectionResponse getById(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        QcInspectionOrderEntity inspection = requireInspection(id, audit);
        return toResponse(inspection, loadInspectionLines(inspection));
    }

    @Transactional
    public QcInspectionResponse update(Long id, QcInspectionUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        QcInspectionOrderEntity inspection = requireInspection(id, audit);
        if (!STATUS_DRAFT.equals(inspection.getStatus())) {
            throw new IllegalArgumentException("当前检验单状态不允许编辑");
        }
        List<QcInspectionLineEntity> lines = loadInspectionLines(inspection);
        Map<Long, QcInspectionUpdateLineRequest> lineUpdates = request.lines() == null ? Map.of()
                : request.lines().stream().collect(Collectors.toMap(
                        QcInspectionUpdateLineRequest::lineId,
                        Function.identity(),
                        (existing, replacement) -> replacement));
        LocalDateTime now = audit.now();

        BigDecimal totalQty = BigDecimal.ZERO;
        for (QcInspectionLineEntity line : lines) {
            QcInspectionUpdateLineRequest lineUpdate = lineUpdates.get(line.getId());
            if (lineUpdate != null) {
                BigDecimal inspectedQty = ScalePrecision.quantity(lineUpdate.inspectedQty());
                if (inspectedQty.signum() < 0) {
                    throw new IllegalArgumentException("检验数量不能为负数");
                }
                line.setInspectedQty(inspectedQty);
                line.setDefectReason(lineUpdate.defectReason());
                line.setRemark(lineUpdate.remark());
                line.setUpdatedBy(audit.userId());
                line.setUpdatedTime(now);
                OptimisticLockGuard.requireUpdated(
                        qcInspectionLineMapper.updateById(line),
                        "检验单明细已被其他操作修改，请刷新后重试"
                );
            }
            totalQty = totalQty.add(ScalePrecision.quantity(line.getInspectedQty()));
        }

        inspection.setInspectionDate(request.inspectionDate());
        inspection.setRemark(request.remark());
        inspection.setTotalQty(ScalePrecision.quantity(totalQty));
        inspection.setUpdatedBy(audit.userId());
        inspection.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                qcInspectionOrderMapper.updateById(inspection),
                "检验单已被其他操作修改，请刷新后重试"
        );
        return getById(id);
    }

    @Transactional
    public QcInspectionResponse submit(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        QcInspectionOrderEntity inspection = requireInspection(id, audit);
        if (!STATUS_DRAFT.equals(inspection.getStatus())) {
            throw new IllegalArgumentException("当前检验单状态不允许提交");
        }
        inspection.setStatus(STATUS_SUBMITTED);
        touch(inspection, audit);
        OptimisticLockGuard.requireUpdated(
                qcInspectionOrderMapper.updateById(inspection),
                "检验单已被其他操作修改，请刷新后重试"
        );
        return getById(id);
    }

    @Transactional
    public QcInspectionResponse judge(Long id, QcInspectionJudgeRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        QcInspectionOrderEntity inspection = requireInspection(id, audit);
        if (!STATUS_SUBMITTED.equals(inspection.getStatus())) {
            throw new IllegalArgumentException("当前检验单状态不允许判定");
        }

        String inspectionType = normalizeInspectionType(inspection.getInspectionType());
        if (TYPE_OQC.equals(inspectionType)) {
            return judgeOqc(inspection, request, audit);
        }
        if (TYPE_IPQC.equals(inspectionType)) {
            qcInspectionSourceAccess.requireInspectableProductionOrder(
                    inspection.getProductionOrderId(),
                    audit
            );
            return judgeGateOnly(inspection, request, audit);
        }
        return judgeIqc(inspection, request, audit);
    }

    private QcInspectionResponse judgeIqc(
            QcInspectionOrderEntity inspection,
            QcInspectionJudgeRequest request,
            AuditMetadata audit
    ) {
        PurchaseReceiptEntity receipt = qcInspectionSourceAccess.requireDraftReceipt(inspection.getReceiptId(), audit);
        List<QcInspectionLineEntity> lines = loadInspectionLines(inspection);
        Map<Long, QcInspectionLineEntity> lineById = lines.stream()
                .collect(Collectors.toMap(QcInspectionLineEntity::getId, Function.identity()));
        Map<Long, QcInspectionJudgeLineRequest> judgeByLineId = requireAllLinesJudged(request, lineById);
        LocalDateTime now = audit.now();

        Map<Long, PurchaseReceiptLineEntity> receiptLinesById = qcInspectionSourceAccess.loadReceiptLines(receipt).stream()
                .collect(Collectors.toMap(PurchaseReceiptLineEntity::getId, Function.identity()));

        BigDecimal totalQualified = BigDecimal.ZERO;
        BigDecimal totalUnqualified = BigDecimal.ZERO;
        BigDecimal receiptTotalQuantity = BigDecimal.ZERO;
        BigDecimal receiptTotalAmount = BigDecimal.ZERO;
        BigDecimal receiptTotalTaxAmount = BigDecimal.ZERO;

        for (QcInspectionLineEntity line : lines) {
            QcInspectionJudgeLineRequest judgeLine = judgeByLineId.get(line.getId());
            AppliedJudge applied = applyJudgeToLine(line, judgeLine, audit, now);
            totalQualified = totalQualified.add(applied.qualified());
            totalUnqualified = totalUnqualified.add(applied.unqualified());

            // 仅合格品入库:据合格数量回写引用的 DRAFT 入库单行,过账/应付/凭证读同一份合格口径。
            PurchaseReceiptLineEntity receiptLine = receiptLinesById.get(line.getReceiptLineId());
            if (receiptLine == null) {
                throw new IllegalArgumentException("检验单引用的采购入库单明细不存在");
            }
            PurchaseAmountCalculator.LineAmounts amounts = PurchaseAmountCalculator.line(
                    applied.qualified(),
                    receiptLine.getPrice(),
                    receiptLine.getTaxRate()
            );
            receiptLine.setQty(amounts.qty());
            receiptLine.setAmount(amounts.amount());
            receiptLine.setTaxAmount(amounts.taxAmount());
            receiptLine.setUpdatedBy(audit.userId());
            receiptLine.setUpdatedTime(now);
            OptimisticLockGuard.requireUpdated(
                    purchaseReceiptLineMapper.updateById(receiptLine),
                    "采购入库单明细已被其他操作修改，请刷新后重试"
            );
            receiptTotalQuantity = receiptTotalQuantity.add(amounts.qty());
            receiptTotalAmount = receiptTotalAmount.add(amounts.amount());
            receiptTotalTaxAmount = receiptTotalTaxAmount.add(amounts.taxAmount());
        }

        receipt.setTotalQuantity(ScalePrecision.quantity(receiptTotalQuantity));
        receipt.setTotalAmount(ScalePrecision.amount(receiptTotalAmount));
        receipt.setTotalTaxAmount(ScalePrecision.amount(receiptTotalTaxAmount));
        receipt.setUpdatedBy(audit.userId());
        receipt.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                purchaseReceiptMapper.updateById(receipt),
                "采购入库单已被其他操作修改，请刷新后重试"
        );

        markJudged(inspection, totalQualified, totalUnqualified, audit, now);
        return getById(inspection.getId());
    }

    /**
     * OQC 判定：gate-only，只落合格/不合格结果，不回写销售出库行（避免破坏预占/金额完整性）。
     * 出库过账时由 {@link QcInspectionGate#assertDeliveryInspected} 校验出库数量与合格数量一致。
     */
    private QcInspectionResponse judgeOqc(
            QcInspectionOrderEntity inspection,
            QcInspectionJudgeRequest request,
            AuditMetadata audit
    ) {
        // 确保引用的出库单仍为草稿，避免对已过账单判定
        qcInspectionSourceAccess.requireDraftDelivery(inspection.getDeliveryId(), audit);
        return judgeGateOnly(inspection, request, audit);
    }

    /** OQC/IPQC 仅记录判定结果，后续由各自业务闸门校验合格数量。 */
    private QcInspectionResponse judgeGateOnly(
            QcInspectionOrderEntity inspection,
            QcInspectionJudgeRequest request,
            AuditMetadata audit
    ) {
        List<QcInspectionLineEntity> lines = loadInspectionLines(inspection);
        Map<Long, QcInspectionLineEntity> lineById = lines.stream()
                .collect(Collectors.toMap(QcInspectionLineEntity::getId, Function.identity()));
        Map<Long, QcInspectionJudgeLineRequest> judgeByLineId = requireAllLinesJudged(request, lineById);
        LocalDateTime now = audit.now();

        BigDecimal totalQualified = BigDecimal.ZERO;
        BigDecimal totalUnqualified = BigDecimal.ZERO;
        for (QcInspectionLineEntity line : lines) {
            AppliedJudge applied = applyJudgeToLine(line, judgeByLineId.get(line.getId()), audit, now);
            totalQualified = totalQualified.add(applied.qualified());
            totalUnqualified = totalUnqualified.add(applied.unqualified());
        }

        markJudged(inspection, totalQualified, totalUnqualified, audit, now);
        return getById(inspection.getId());
    }

    private Map<Long, QcInspectionJudgeLineRequest> requireAllLinesJudged(
            QcInspectionJudgeRequest request,
            Map<Long, QcInspectionLineEntity> lineById
    ) {
        Map<Long, QcInspectionJudgeLineRequest> judgeByLineId = request.lines().stream()
                .collect(Collectors.toMap(
                        QcInspectionJudgeLineRequest::lineId,
                        Function.identity(),
                        (existing, replacement) -> replacement));
        if (!judgeByLineId.keySet().containsAll(lineById.keySet())) {
            throw new IllegalArgumentException("检验单存在未判定的明细");
        }
        return judgeByLineId;
    }

    private AppliedJudge applyJudgeToLine(
            QcInspectionLineEntity line,
            QcInspectionJudgeLineRequest judgeLine,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        BigDecimal qualified = ScalePrecision.quantity(judgeLine.qualifiedQty());
        BigDecimal unqualified = ScalePrecision.quantity(judgeLine.unqualifiedQty());
        if (qualified.signum() < 0 || unqualified.signum() < 0) {
            throw new IllegalArgumentException("合格/不合格数量不能为负数");
        }
        BigDecimal inspected = ScalePrecision.quantity(line.getInspectedQty());
        if (qualified.add(unqualified).compareTo(inspected) != 0) {
            throw new IllegalArgumentException("合格数量与不合格数量之和必须等于检验数量");
        }

        line.setQualifiedQty(qualified);
        line.setUnqualifiedQty(unqualified);
        line.setDefectReason(judgeLine.defectReason() != null ? judgeLine.defectReason() : line.getDefectReason());
        line.setUpdatedBy(audit.userId());
        line.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                qcInspectionLineMapper.updateById(line),
                "检验单明细已被其他操作修改，请刷新后重试"
        );
        return new AppliedJudge(qualified, unqualified);
    }

    private void markJudged(
            QcInspectionOrderEntity inspection,
            BigDecimal totalQualified,
            BigDecimal totalUnqualified,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        inspection.setStatus(STATUS_JUDGED);
        inspection.setQualifiedQty(ScalePrecision.quantity(totalQualified));
        inspection.setUnqualifiedQty(ScalePrecision.quantity(totalUnqualified));
        inspection.setUpdatedBy(audit.userId());
        inspection.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                qcInspectionOrderMapper.updateById(inspection),
                "检验单已被其他操作修改，请刷新后重试"
        );
    }

    @Transactional
    public QcInspectionResponse cancel(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        QcInspectionOrderEntity inspection = requireInspection(id, audit);
        if (!STATUS_DRAFT.equals(inspection.getStatus()) && !STATUS_SUBMITTED.equals(inspection.getStatus())) {
            throw new IllegalArgumentException("当前检验单状态不允许作废");
        }
        inspection.setStatus(STATUS_CANCELLED);
        touch(inspection, audit);
        OptimisticLockGuard.requireUpdated(
                qcInspectionOrderMapper.updateById(inspection),
                "检验单已被其他操作修改，请刷新后重试"
        );
        return getById(id);
    }

    private QcInspectionOrderEntity requireInspection(Long id, AuditMetadata audit) {
        QcInspectionOrderEntity entity = qcInspectionOrderMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("检验单不存在");
        }
        return entity;
    }

    private List<QcInspectionLineEntity> loadInspectionLines(QcInspectionOrderEntity inspection) {
        return qcInspectionLineMapper.selectList(
                new LambdaQueryWrapper<QcInspectionLineEntity>()
                        .eq(QcInspectionLineEntity::getCompanyId, inspection.getCompanyId())
                        .eq(QcInspectionLineEntity::getAccountBookId, inspection.getAccountBookId())
                        .eq(QcInspectionLineEntity::getInspectionId, inspection.getId())
                        .eq(QcInspectionLineEntity::getDeletedFlag, 0)
                        .orderByAsc(QcInspectionLineEntity::getLineNo)
        );
    }

    private LambdaQueryWrapper<QcInspectionOrderEntity> buildListQuery(AuditMetadata audit, QcInspectionPageQuery query) {
        LambdaQueryWrapper<QcInspectionOrderEntity> wrapper = new LambdaQueryWrapper<QcInspectionOrderEntity>()
                .eq(QcInspectionOrderEntity::getCompanyId, audit.companyId())
                .eq(QcInspectionOrderEntity::getAccountBookId, audit.accountBookId())
                .eq(QcInspectionOrderEntity::getDeletedFlag, 0);
        String keyword = normalizeNullableText(query.getKeyword());
        if (StringUtils.hasText(keyword)) {
            wrapper.like(QcInspectionOrderEntity::getInspectionNo, keyword);
        }
        if (query.getReceiptId() != null) {
            wrapper.eq(QcInspectionOrderEntity::getReceiptId, query.getReceiptId());
        }
        if (query.getDeliveryId() != null) {
            wrapper.eq(QcInspectionOrderEntity::getDeliveryId, query.getDeliveryId());
        }
        String inspectionType = normalizeInspectionTypeFilter(query.getInspectionType());
        if (StringUtils.hasText(inspectionType)) {
            wrapper.eq(QcInspectionOrderEntity::getInspectionType, inspectionType);
        }
        String status = normalizeStatus(query.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(QcInspectionOrderEntity::getStatus, status);
        }
        if (query.getInspectionDateFrom() != null) {
            wrapper.ge(QcInspectionOrderEntity::getInspectionDate, query.getInspectionDateFrom());
        }
        if (query.getInspectionDateTo() != null) {
            wrapper.le(QcInspectionOrderEntity::getInspectionDate, query.getInspectionDateTo());
        }
        return wrapper.orderByDesc(QcInspectionOrderEntity::getId);
    }

    private List<?> inspectionExportRow(QcInspectionOrderEntity entity) {
        return Arrays.asList(
                entity.getInspectionNo(),
                entity.getInspectionType(),
                entity.getReceiptId(),
                entity.getDeliveryId(),
                entity.getOrderId(),
                entity.getWarehouseId(),
                entity.getInspectionDate(),
                entity.getStatus(),
                entity.getTotalQty(),
                entity.getQualifiedQty(),
                entity.getUnqualifiedQty(),
                entity.getRemark()
        );
    }

    private QcInspectionResponse toResponse(QcInspectionOrderEntity inspection, List<QcInspectionLineEntity> lines) {
        return new QcInspectionResponse(
                inspection.getId(),
                inspection.getInspectionNo(),
                normalizeInspectionType(inspection.getInspectionType()),
                inspection.getReceiptId(),
                inspection.getDeliveryId(),
                inspection.getProductionOrderId(),
                inspection.getOrderId(),
                inspection.getWarehouseId(),
                inspection.getSupplierId(),
                inspection.getInspectionDate(),
                inspection.getStatus(),
                inspection.getTotalQty(),
                inspection.getQualifiedQty(),
                inspection.getUnqualifiedQty(),
                inspection.getRemark(),
                lines.stream().map(this::toLineResponse).toList()
        );
    }

    private QcInspectionResponse toSummaryResponse(QcInspectionOrderEntity inspection) {
        return new QcInspectionResponse(
                inspection.getId(),
                inspection.getInspectionNo(),
                normalizeInspectionType(inspection.getInspectionType()),
                inspection.getReceiptId(),
                inspection.getDeliveryId(),
                inspection.getProductionOrderId(),
                inspection.getOrderId(),
                inspection.getWarehouseId(),
                inspection.getSupplierId(),
                inspection.getInspectionDate(),
                inspection.getStatus(),
                inspection.getTotalQty(),
                inspection.getQualifiedQty(),
                inspection.getUnqualifiedQty(),
                inspection.getRemark(),
                List.of()
        );
    }

    private QcInspectionLineResponse toLineResponse(QcInspectionLineEntity line) {
        return new QcInspectionLineResponse(
                line.getId(),
                line.getLineNo(),
                line.getReceiptLineId(),
                line.getDeliveryLineId(),
                line.getProductId(),
                line.getInspectedQty(),
                line.getQualifiedQty(),
                line.getUnqualifiedQty(),
                line.getDefectReason(),
                line.getRemark()
        );
    }

    private void touch(QcInspectionOrderEntity inspection, AuditMetadata audit) {
        inspection.setUpdatedBy(audit.userId());
        inspection.setUpdatedTime(audit.now());
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    /** 创建/判定用：空则默认 IQC。 */
    private String normalizeInspectionType(String value) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            return TYPE_IQC;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if (!TYPE_IQC.equals(upper) && !TYPE_OQC.equals(upper) && !TYPE_IPQC.equals(upper)) {
            throw new IllegalArgumentException("检验类型仅支持 IQC、OQC 或 IPQC");
        }
        return upper;
    }

    /** 列表筛选：空不过滤；非法类型拒绝。 */
    private String normalizeInspectionTypeFilter(String value) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            return null;
        }
        return normalizeInspectionType(normalized);
    }

    private void withAuthentication(Authentication authentication, ThrowingRunnable action) throws IOException {
        Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            action.run();
        } finally {
            if (previousAuthentication == null) {
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
            }
        }
    }

    private record AppliedJudge(BigDecimal qualified, BigDecimal unqualified) {
    }

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws IOException;
    }
}

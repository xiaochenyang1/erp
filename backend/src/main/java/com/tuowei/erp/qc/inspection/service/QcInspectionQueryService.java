package com.tuowei.erp.qc.inspection.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageQueryNormalizer;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionLineMapper;
import com.tuowei.erp.qc.inspection.mapper.QcInspectionOrderMapper;
import com.tuowei.erp.qc.inspection.model.QcInspectionLineEntity;
import com.tuowei.erp.qc.inspection.model.QcInspectionOrderEntity;
import com.tuowei.erp.qc.inspection.web.QcInspectionLineResponse;
import com.tuowei.erp.qc.inspection.web.QcInspectionPageQuery;
import com.tuowei.erp.qc.inspection.web.QcInspectionResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Read-side filtering, tenant isolation, export and response mapping for quality inspections. */
@Service
public class QcInspectionQueryService {

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
    private final AuditMetadataFactory auditMetadataFactory;

    public QcInspectionQueryService(
            QcInspectionOrderMapper qcInspectionOrderMapper,
            QcInspectionLineMapper qcInspectionLineMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.qcInspectionOrderMapper = qcInspectionOrderMapper;
        this.qcInspectionLineMapper = qcInspectionLineMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<QcInspectionResponse> list(QcInspectionPageQuery query) {
        QcInspectionPageQuery safeQuery = query == null ? new QcInspectionPageQuery() : query;
        long pageNo = PageQueryNormalizer.normalizePageNo(safeQuery.getPageNo());
        long pageSize = PageQueryNormalizer.normalizePageSize(safeQuery.getPageSize());
        AuditMetadata audit = auditMetadataFactory.current();
        Page<QcInspectionOrderEntity> result = qcInspectionOrderMapper.selectPage(
                new Page<>(pageNo, pageSize),
                buildListQuery(audit, safeQuery)
        );

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
        return outputStream -> withAuthentication(authentication, () -> CsvExport.write(
                outputStream,
                INSPECTION_EXPORT_HEADERS,
                rowWriter -> {
                    AuditMetadata audit = auditMetadataFactory.current();
                    for (QcInspectionOrderEntity entity : qcInspectionOrderMapper.selectList(
                            buildListQuery(audit, safeQuery))) {
                        rowWriter.write(inspectionExportRow(entity));
                    }
                }
        ));
    }

    @Transactional(readOnly = true)
    public QcInspectionResponse getById(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        QcInspectionOrderEntity inspection = requireInspection(id, audit);
        return toResponse(inspection, loadInspectionLines(inspection));
    }

    QcInspectionOrderEntity requireInspection(Long id, AuditMetadata audit) {
        QcInspectionOrderEntity entity = qcInspectionOrderMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !Objects.equals(entity.getCompanyId(), audit.companyId())
                || !Objects.equals(entity.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("检验单不存在");
        }
        return entity;
    }

    List<QcInspectionLineEntity> loadInspectionLines(QcInspectionOrderEntity inspection) {
        return qcInspectionLineMapper.selectList(
                new LambdaQueryWrapper<QcInspectionLineEntity>()
                        .eq(QcInspectionLineEntity::getCompanyId, inspection.getCompanyId())
                        .eq(QcInspectionLineEntity::getAccountBookId, inspection.getAccountBookId())
                        .eq(QcInspectionLineEntity::getInspectionId, inspection.getId())
                        .eq(QcInspectionLineEntity::getDeletedFlag, 0)
                        .orderByAsc(QcInspectionLineEntity::getLineNo)
        );
    }

    QcInspectionResponse toResponse(
            QcInspectionOrderEntity inspection,
            List<QcInspectionLineEntity> lines
    ) {
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

    private LambdaQueryWrapper<QcInspectionOrderEntity> buildListQuery(
            AuditMetadata audit,
            QcInspectionPageQuery query
    ) {
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

    private QcInspectionResponse toSummaryResponse(QcInspectionOrderEntity inspection) {
        return toResponse(inspection, List.of());
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

    @FunctionalInterface
    private interface ThrowingRunnable {

        void run() throws IOException;
    }
}

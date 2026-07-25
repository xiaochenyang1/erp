package com.tuowei.erp.finance.payable.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.payable.web.PayablePageQuery;
import com.tuowei.erp.finance.payable.web.PayableResponse;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PayableQueryService {

    private static final List<String> PAYABLE_EXPORT_HEADERS = List.of(
            "payableNo",
            "supplierId",
            "bizDate",
            "sourceType",
            "sourceNo",
            "direction",
            "originalAmount",
            "settledAmount",
            "remainingAmount",
            "status",
            "remark"
    );

    private final PayableMapper payableMapper;
    private final SupplierMapper supplierMapper;
    private final FinanceSettlementScopeSupport financeSettlementScopeSupport;
    private final AuditMetadataFactory auditMetadataFactory;

    public PayableQueryService(
            PayableMapper payableMapper,
            SupplierMapper supplierMapper,
            FinanceSettlementScopeSupport financeSettlementScopeSupport,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.payableMapper = payableMapper;
        this.supplierMapper = supplierMapper;
        this.financeSettlementScopeSupport = financeSettlementScopeSupport;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<PayableResponse> list(PayablePageQuery query) {
        PayablePageQuery safeQuery = query == null ? new PayablePageQuery() : query;
        Page<PayableEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<PayableEntity> wrapper = buildQuery(safeQuery);
        Page<PayableEntity> result = payableMapper.selectPage(page, wrapper);
        Map<Long, String> supplierNames = loadSupplierNames(result.getRecords());
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(entity -> toResponse(entity, supplierNames.get(entity.getSupplierId())))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public PayableResponse detail(Long id) {
        PayableEntity entity = requirePayable(id);
        financeSettlementScopeSupport.assertCanViewPayable(entity);
        Map<Long, String> supplierNames = loadSupplierNames(List.of(entity));
        return toResponse(entity, supplierNames.get(entity.getSupplierId()));
    }

    public StreamingResponseBody exportPayables(PayablePageQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        PayablePageQuery safeQuery = query == null ? new PayablePageQuery() : query;
        return outputStream -> withAuthentication(authentication, () -> CsvExport.write(outputStream, PAYABLE_EXPORT_HEADERS, rowWriter -> {
            List<PayableEntity> payables = payableMapper.selectList(buildQuery(safeQuery));
            for (PayableEntity entity : payables) {
                rowWriter.write(payableExportRow(toResponse(entity, null)));
            }
        }));
    }

    private PayableEntity requirePayable(Long id) {
        PayableEntity entity = payableMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("应付记录不存在");
        }
        return entity;
    }

    private LambdaQueryWrapper<PayableEntity> buildQuery(PayablePageQuery safeQuery) {
        LambdaQueryWrapper<PayableEntity> wrapper = new LambdaQueryWrapper<>();
        String payableNo = normalizeText(safeQuery.getPayableNo());
        if (StringUtils.hasText(payableNo)) {
            wrapper.like(PayableEntity::getPayableNo, payableNo);
        }
        if (safeQuery.getSupplierId() != null) {
            wrapper.eq(PayableEntity::getSupplierId, safeQuery.getSupplierId());
        }
        String status = normalizeUpper(safeQuery.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(PayableEntity::getStatus, status);
        }
        String sourceType = normalizeUpper(safeQuery.getSourceType());
        if (StringUtils.hasText(sourceType)) {
            wrapper.eq(PayableEntity::getSourceType, sourceType);
        }
        if (safeQuery.getBizDateFrom() != null) {
            wrapper.ge(PayableEntity::getBizDate, safeQuery.getBizDateFrom());
        }
        if (safeQuery.getBizDateTo() != null) {
            wrapper.le(PayableEntity::getBizDate, safeQuery.getBizDateTo());
        }
        wrapper = financeSettlementScopeSupport.applyPayableScope(wrapper);
        return wrapper.orderByDesc(PayableEntity::getBizDate).orderByDesc(PayableEntity::getId);
    }

    private PayableResponse toResponse(PayableEntity entity, String supplierName) {
        return new PayableResponse(
                entity.getId(),
                entity.getPayableNo(),
                entity.getSupplierId(),
                supplierName,
                entity.getBizDate(),
                entity.getSourceType(),
                entity.getSourceId(),
                entity.getSourceNo(),
                entity.getDirection(),
                entity.getOriginalAmount(),
                entity.getSettledAmount(),
                remaining(entity.getOriginalAmount(), entity.getSettledAmount()),
                entity.getStatus(),
                entity.getCreatedTime(),
                entity.getUpdatedTime(),
                entity.getRemark()
        );
    }

    private Map<Long, String> loadSupplierNames(List<PayableEntity> payables) {
        Set<Long> supplierIds = payables.stream()
                .map(PayableEntity::getSupplierId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (supplierIds.isEmpty()) {
            return Map.of();
        }

        AuditMetadata audit = auditMetadataFactory.current();
        return supplierMapper.selectList(new LambdaQueryWrapper<SupplierEntity>()
                        .select(
                                SupplierEntity::getId,
                                SupplierEntity::getCompanyId,
                                SupplierEntity::getAccountBookId,
                                SupplierEntity::getSupplierName,
                                SupplierEntity::getDeletedFlag
                        )
                        .eq(SupplierEntity::getCompanyId, audit.companyId())
                        .eq(SupplierEntity::getAccountBookId, audit.accountBookId())
                        .eq(SupplierEntity::getDeletedFlag, 0)
                        .in(SupplierEntity::getId, supplierIds))
                .stream()
                .filter(supplier -> Objects.equals(supplier.getCompanyId(), audit.companyId()))
                .filter(supplier -> Objects.equals(supplier.getAccountBookId(), audit.accountBookId()))
                .filter(supplier -> Objects.equals(supplier.getDeletedFlag(), 0))
                .collect(Collectors.toMap(
                        SupplierEntity::getId,
                        SupplierEntity::getSupplierName,
                        (first, ignored) -> first,
                        HashMap::new
                ));
    }

    private List<?> payableExportRow(PayableResponse record) {
        return Arrays.asList(
                record.payableNo(),
                record.supplierId(),
                record.bizDate(),
                record.sourceType(),
                record.sourceNo(),
                record.direction(),
                record.originalAmount(),
                record.settledAmount(),
                record.remainingAmount(),
                record.status(),
                record.remark()
        );
    }

    private BigDecimal remaining(BigDecimal originalAmount, BigDecimal settledAmount) {
        return ScalePrecision.amount(ScalePrecision.zeroDefault(originalAmount).subtract(ScalePrecision.zeroDefault(settledAmount)));
    }

    private String normalizeUpper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : null;
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
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

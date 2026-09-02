package com.tuowei.erp.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.config.ReportProperties;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import com.tuowei.erp.report.mapper.FinanceSettlementReportMapper;
import com.tuowei.erp.report.web.FinanceSettlementReportQuery;
import com.tuowei.erp.report.web.FinanceSettlementReportResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/** Payable/receivable report filtering, scoped merged pagination, mapping and streaming export. */
@Service
public class FinanceSettlementReportQueryService {

    private final PayableMapper payableMapper;
    private final ReceivableMapper receivableMapper;
    private final FinanceSettlementReportMapper financeSettlementReportMapper;
    private final FinanceSettlementReportDataScopeService financeSettlementReportDataScopeService;
    private final ReportProperties reportProperties;

    @Autowired
    public FinanceSettlementReportQueryService(
            PayableMapper payableMapper,
            ReceivableMapper receivableMapper,
            FinanceSettlementReportMapper financeSettlementReportMapper,
            FinanceSettlementReportDataScopeService financeSettlementReportDataScopeService,
            ReportProperties reportProperties
    ) {
        this.payableMapper = payableMapper;
        this.receivableMapper = receivableMapper;
        this.financeSettlementReportMapper = financeSettlementReportMapper;
        this.financeSettlementReportDataScopeService = financeSettlementReportDataScopeService;
        this.reportProperties = reportProperties;
    }

    /** Backward-compatible constructor for isolated report tests and integrations. */
    public FinanceSettlementReportQueryService(
            PayableMapper payableMapper,
            ReceivableMapper receivableMapper,
            FinanceSettlementReportMapper financeSettlementReportMapper,
            FinanceSettlementScopeSupport financeSettlementScopeSupport,
            ReportProperties reportProperties
    ) {
        this(payableMapper, receivableMapper, financeSettlementReportMapper,
                new FinanceSettlementReportDataScopeService(financeSettlementScopeSupport), reportProperties);
    }

    @Transactional(readOnly = true)
    public PageResponse<FinanceSettlementReportResponse> listFinanceSettlements(FinanceSettlementReportQuery query) {
        FinanceSettlementReportQuery safeQuery = query == null ? new FinanceSettlementReportQuery() : query;
        String direction = normalizeUpper(safeQuery.getDirection());
        if ("PAYABLE".equals(direction)) {
            return listPayables(safeQuery);
        }
        if ("RECEIVABLE".equals(direction)) {
            return listReceivables(safeQuery);
        }
        return listAllSettlements(safeQuery);
    }

    @Transactional(readOnly = true)
    public void assertFinanceSettlementExportWithinLimit(FinanceSettlementReportQuery query) {
        FinanceSettlementReportQuery safeQuery = query == null ? new FinanceSettlementReportQuery() : query;
        String direction = normalizeUpper(safeQuery.getDirection());
        if ("PAYABLE".equals(direction)) {
            assertExportRowsWithinLimit(countExportRows(payableMapper, payableWrapper(safeQuery)));
            return;
        }
        if ("RECEIVABLE".equals(direction)) {
            assertExportRowsWithinLimit(countExportRows(receivableMapper, receivableWrapper(safeQuery)));
            return;
        }
        long payableCount = countExportRows(payableMapper, payableWrapper(safeQuery));
        long receivableCount = countExportRows(receivableMapper, receivableWrapper(safeQuery));
        assertExportRowsWithinLimit(payableCount + receivableCount);
    }

    @Transactional(readOnly = true)
    public void streamFinanceSettlements(
            FinanceSettlementReportQuery query,
            Consumer<FinanceSettlementReportResponse> consumer
    ) {
        FinanceSettlementReportQuery safeQuery = query == null ? new FinanceSettlementReportQuery() : query;
        String direction = normalizeUpper(safeQuery.getDirection());
        if ("PAYABLE".equals(direction)) {
            streamExportRows(
                    payableMapper,
                    payableWrapper(safeQuery)
                            .orderByDesc(PayableEntity::getBizDate)
                            .orderByDesc(PayableEntity::getId),
                    this::toPayableReport,
                    consumer
            );
            return;
        }
        if ("RECEIVABLE".equals(direction)) {
            streamExportRows(
                    receivableMapper,
                    receivableWrapper(safeQuery)
                            .orderByDesc(ReceivableEntity::getBizDate)
                            .orderByDesc(ReceivableEntity::getId),
                    this::toReceivableReport,
                    consumer
            );
            return;
        }
        streamAllFinanceSettlements(safeQuery, consumer);
    }

    private PageResponse<FinanceSettlementReportResponse> listPayables(FinanceSettlementReportQuery query) {
        Page<PayableEntity> page = new Page<>(normalizePageNo(query.getPageNo()), normalizePageSize(query.getPageSize()));
        Page<PayableEntity> result = payableMapper.selectPage(
                page,
                payableWrapper(query).orderByDesc(PayableEntity::getBizDate).orderByDesc(PayableEntity::getId)
        );
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toPayableReport).toList()
        );
    }

    private PageResponse<FinanceSettlementReportResponse> listReceivables(FinanceSettlementReportQuery query) {
        Page<ReceivableEntity> page = new Page<>(normalizePageNo(query.getPageNo()), normalizePageSize(query.getPageSize()));
        Page<ReceivableEntity> result = receivableMapper.selectPage(
                page,
                receivableWrapper(query).orderByDesc(ReceivableEntity::getBizDate).orderByDesc(ReceivableEntity::getId)
        );
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toReceivableReport).toList()
        );
    }

    private PageResponse<FinanceSettlementReportResponse> listAllSettlements(FinanceSettlementReportQuery query) {
        long pageNo = normalizePageNo(query.getPageNo());
        long pageSize = normalizePageSize(query.getPageSize());
        long pageStart = (pageNo - 1) * pageSize;
        long payableTotal = payableMapper.selectCount(payableWrapper(query));
        long receivableTotal = receivableMapper.selectCount(receivableWrapper(query));
        long total = payableTotal + receivableTotal;
        if (pageStart >= total) {
            return new PageResponse<>(pageNo, pageSize, total, List.of());
        }

        List<FinanceSettlementReportResponse> records = financeSettlementReportMapper.selectAllSettlementPage(
                payableWrapper(query, "payableWrapper"),
                receivableWrapper(query, "receivableWrapper"),
                pageSize,
                pageStart
        );
        return new PageResponse<>(pageNo, pageSize, total, records);
    }

    private LambdaQueryWrapper<PayableEntity> payableWrapper(FinanceSettlementReportQuery query) {
        return payableWrapper(query, null);
    }

    private LambdaQueryWrapper<PayableEntity> payableWrapper(
            FinanceSettlementReportQuery query,
            String paramAlias
    ) {
        LambdaQueryWrapper<PayableEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(paramAlias)) {
            wrapper.setParamAlias(paramAlias);
        }
        wrapper.eq(PayableEntity::getDeletedFlag, 0);
        if (query.getPartnerId() != null) {
            wrapper.eq(PayableEntity::getSupplierId, query.getPartnerId());
        }
        String status = normalizeUpper(query.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(PayableEntity::getStatus, status);
        }
        String sourceType = normalizeUpper(query.getSourceType());
        if (StringUtils.hasText(sourceType)) {
            wrapper.eq(PayableEntity::getSourceType, sourceType);
        }
        if (query.getBizDateFrom() != null) {
            wrapper.ge(PayableEntity::getBizDate, query.getBizDateFrom());
        }
        if (query.getBizDateTo() != null) {
            wrapper.le(PayableEntity::getBizDate, query.getBizDateTo());
        }
        return financeSettlementReportDataScopeService.applyPayableScope(wrapper);
    }

    private LambdaQueryWrapper<ReceivableEntity> receivableWrapper(FinanceSettlementReportQuery query) {
        return receivableWrapper(query, null);
    }

    private LambdaQueryWrapper<ReceivableEntity> receivableWrapper(
            FinanceSettlementReportQuery query,
            String paramAlias
    ) {
        LambdaQueryWrapper<ReceivableEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(paramAlias)) {
            wrapper.setParamAlias(paramAlias);
        }
        wrapper.eq(ReceivableEntity::getDeletedFlag, 0);
        if (query.getPartnerId() != null) {
            wrapper.eq(ReceivableEntity::getCustomerId, query.getPartnerId());
        }
        String status = normalizeUpper(query.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(ReceivableEntity::getStatus, status);
        }
        String sourceType = normalizeUpper(query.getSourceType());
        if (StringUtils.hasText(sourceType)) {
            wrapper.eq(ReceivableEntity::getSourceType, sourceType);
        }
        if (query.getBizDateFrom() != null) {
            wrapper.ge(ReceivableEntity::getBizDate, query.getBizDateFrom());
        }
        if (query.getBizDateTo() != null) {
            wrapper.le(ReceivableEntity::getBizDate, query.getBizDateTo());
        }
        return financeSettlementReportDataScopeService.applyReceivableScope(wrapper);
    }

    private FinanceSettlementReportResponse toPayableReport(PayableEntity entity) {
        return new FinanceSettlementReportResponse(
                entity.getId(),
                "PAYABLE",
                entity.getPayableNo(),
                entity.getSupplierId(),
                entity.getBizDate(),
                entity.getSourceType(),
                entity.getSourceNo(),
                entity.getOriginalAmount(),
                entity.getSettledAmount(),
                remaining(entity.getOriginalAmount(), entity.getSettledAmount()),
                entity.getStatus()
        );
    }

    private FinanceSettlementReportResponse toReceivableReport(ReceivableEntity entity) {
        return new FinanceSettlementReportResponse(
                entity.getId(),
                "RECEIVABLE",
                entity.getReceivableNo(),
                entity.getCustomerId(),
                entity.getBizDate(),
                entity.getSourceType(),
                entity.getSourceNo(),
                entity.getOriginalAmount(),
                entity.getSettledAmount(),
                remaining(entity.getOriginalAmount(), entity.getSettledAmount()),
                entity.getStatus()
        );
    }

    private BigDecimal remaining(BigDecimal originalAmount, BigDecimal settledAmount) {
        return ScalePrecision.amount(
                ScalePrecision.zeroDefault(originalAmount).subtract(ScalePrecision.zeroDefault(settledAmount))
        );
    }

    private <T> long countExportRows(BaseMapper<T> mapper, LambdaQueryWrapper<T> wrapper) {
        Long count = mapper.selectCount(wrapper);
        return count == null ? 0 : count;
    }

    private <T, R> void streamExportRows(
            BaseMapper<T> mapper,
            LambdaQueryWrapper<T> wrapper,
            Function<T, R> responseMapper,
            Consumer<R> consumer
    ) {
        long pageNo = 1;
        long batchSize = exportBatchSize();
        while (true) {
            Page<T> page = new Page<>(pageNo, batchSize, false);
            page.setMaxLimit(batchSize);
            List<T> records = mapper.selectPage(page, wrapper).getRecords();
            if (records.isEmpty()) {
                return;
            }
            records.stream().map(responseMapper).forEach(consumer);
            if (records.size() < batchSize) {
                return;
            }
            pageNo++;
        }
    }

    private void streamAllFinanceSettlements(
            FinanceSettlementReportQuery query,
            Consumer<FinanceSettlementReportResponse> consumer
    ) {
        long batchSize = exportBatchSize();
        long pageStart = 0;
        while (true) {
            List<FinanceSettlementReportResponse> records = financeSettlementReportMapper.selectAllSettlementPage(
                    payableWrapper(query, "payableWrapper"),
                    receivableWrapper(query, "receivableWrapper"),
                    batchSize,
                    pageStart
            );
            if (records.isEmpty()) {
                return;
            }
            records.forEach(consumer);
            if (records.size() < batchSize) {
                return;
            }
            pageStart += batchSize;
        }
    }

    private long exportBatchSize() {
        return Math.min(reportProperties.exportBatchSize(), reportProperties.maxExportRows());
    }

    private void assertExportRowsWithinLimit(long rowCount) {
        int maxRows = reportProperties.maxExportRows();
        if (rowCount > maxRows) {
            throw new IllegalArgumentException("导出结果超过" + maxRows + "行，请缩小筛选范围后重试");
        }
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeUpper(String value) {
        String normalized = normalizeNullableText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20L : Math.min(pageSize, 200);
    }
}

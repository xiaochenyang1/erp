package com.tuowei.erp.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.config.ReportProperties;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import com.tuowei.erp.report.mapper.FxGainLossReportMapper;
import com.tuowei.erp.report.web.FxGainLossReportQuery;
import com.tuowei.erp.report.web.FxGainLossReportResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 已实现汇兑损益报表：按结算日期倒序列出收付款核销产生的汇兑差额。
 *
 * <p>可见性沿用应收应付的数据范围（{@link FinanceSettlementScopeSupport}），与结算报表一致。
 */
@Service
public class FxGainLossReportQueryService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int EXPORT_BATCH_SIZE = 500;

    private final FxGainLossReportMapper fxGainLossReportMapper;
    private final FinanceSettlementScopeSupport financeSettlementScopeSupport;
    private final ReportProperties reportProperties;

    public FxGainLossReportQueryService(
            FxGainLossReportMapper fxGainLossReportMapper,
            FinanceSettlementScopeSupport financeSettlementScopeSupport,
            ReportProperties reportProperties
    ) {
        this.fxGainLossReportMapper = fxGainLossReportMapper;
        this.financeSettlementScopeSupport = financeSettlementScopeSupport;
        this.reportProperties = reportProperties;
    }

    @Transactional(readOnly = true)
    public PageResponse<FxGainLossReportResponse> listFxGainLoss(FxGainLossReportQuery query) {
        FxGainLossReportQuery safeQuery = query == null ? new FxGainLossReportQuery() : query;
        long pageNo = normalizePageNo(safeQuery.getPageNo());
        long pageSize = normalizePageSize(safeQuery.getPageSize());
        long offset = (pageNo - 1) * pageSize;
        long total = count(safeQuery);
        if (offset >= total) {
            return new PageResponse<>(pageNo, pageSize, total, List.of());
        }
        return new PageResponse<>(pageNo, pageSize, total, selectPage(safeQuery, pageSize, offset));
    }

    @Transactional(readOnly = true)
    public void assertFxGainLossExportWithinLimit(FxGainLossReportQuery query) {
        FxGainLossReportQuery safeQuery = query == null ? new FxGainLossReportQuery() : query;
        int maxRows = reportProperties.maxExportRows();
        if (count(safeQuery) > maxRows) {
            throw new IllegalArgumentException("导出结果超过" + maxRows + "行，请缩小筛选范围后重试");
        }
    }

    @Transactional(readOnly = true)
    public void streamFxGainLoss(FxGainLossReportQuery query, Consumer<FxGainLossReportResponse> consumer) {
        FxGainLossReportQuery safeQuery = query == null ? new FxGainLossReportQuery() : query;
        long offset = 0;
        while (true) {
            List<FxGainLossReportResponse> batch = selectPage(safeQuery, EXPORT_BATCH_SIZE, offset);
            batch.forEach(consumer);
            if (batch.size() < EXPORT_BATCH_SIZE) {
                return;
            }
            offset += batch.size();
        }
    }

    private long count(FxGainLossReportQuery query) {
        return fxGainLossReportMapper.countFxGainLoss(
                payableWrapper(query),
                receivableWrapper(query),
                normalizeDirection(query.getDirection()),
                query.getSettlementDateFrom(),
                query.getSettlementDateTo()
        );
    }

    private List<FxGainLossReportResponse> selectPage(FxGainLossReportQuery query, long limit, long offset) {
        return fxGainLossReportMapper.selectFxGainLossPage(
                payableWrapper(query),
                receivableWrapper(query),
                normalizeDirection(query.getDirection()),
                query.getSettlementDateFrom(),
                query.getSettlementDateTo(),
                limit,
                offset
        );
    }

    private LambdaQueryWrapper<PayableEntity> payableWrapper(FxGainLossReportQuery query) {
        LambdaQueryWrapper<PayableEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.setParamAlias("payableWrapper");
        String currencyCode = normalizeUpper(query.getCurrencyCode());
        if (StringUtils.hasText(currencyCode)) {
            wrapper.eq(PayableEntity::getCurrencyCode, currencyCode);
        }
        if (query.getPartnerId() != null) {
            wrapper.eq(PayableEntity::getSupplierId, query.getPartnerId());
        }
        return financeSettlementScopeSupport.applyPayableScope(wrapper);
    }

    private LambdaQueryWrapper<ReceivableEntity> receivableWrapper(FxGainLossReportQuery query) {
        LambdaQueryWrapper<ReceivableEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.setParamAlias("receivableWrapper");
        String currencyCode = normalizeUpper(query.getCurrencyCode());
        if (StringUtils.hasText(currencyCode)) {
            wrapper.eq(ReceivableEntity::getCurrencyCode, currencyCode);
        }
        if (query.getPartnerId() != null) {
            wrapper.eq(ReceivableEntity::getCustomerId, query.getPartnerId());
        }
        return financeSettlementScopeSupport.applyReceivableScope(wrapper);
    }

    private String normalizeDirection(String direction) {
        String normalized = normalizeUpper(direction);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        if (!"PAYABLE".equals(normalized) && !"RECEIVABLE".equals(normalized)) {
            throw new IllegalArgumentException("方向只能是 PAYABLE 或 RECEIVABLE");
        }
        return normalized;
    }

    private String normalizeUpper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        return pageNo == null || pageNo < 1 ? 1L : pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}

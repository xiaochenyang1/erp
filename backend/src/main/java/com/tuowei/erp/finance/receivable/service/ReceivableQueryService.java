package com.tuowei.erp.finance.receivable.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.receivable.web.ReceivablePageQuery;
import com.tuowei.erp.finance.receivable.web.ReceivableResponse;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class ReceivableQueryService {

    private static final List<String> RECEIVABLE_EXPORT_HEADERS = List.of(
            "receivableNo",
            "customerId",
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

    private final ReceivableMapper receivableMapper;
    private final FinanceSettlementScopeSupport financeSettlementScopeSupport;

    public ReceivableQueryService(
            ReceivableMapper receivableMapper,
            FinanceSettlementScopeSupport financeSettlementScopeSupport
    ) {
        this.receivableMapper = receivableMapper;
        this.financeSettlementScopeSupport = financeSettlementScopeSupport;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReceivableResponse> list(ReceivablePageQuery query) {
        ReceivablePageQuery safeQuery = query == null ? new ReceivablePageQuery() : query;
        Page<ReceivableEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<ReceivableEntity> wrapper = buildQuery(safeQuery);
        Page<ReceivableEntity> result = receivableMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public ReceivableResponse detail(Long id) {
        ReceivableEntity entity = requireReceivable(id);
        financeSettlementScopeSupport.assertCanViewReceivable(entity);
        return toResponse(entity);
    }

    public StreamingResponseBody exportReceivables(ReceivablePageQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ReceivablePageQuery safeQuery = query == null ? new ReceivablePageQuery() : query;
        return outputStream -> withAuthentication(authentication, () -> CsvExport.write(outputStream, RECEIVABLE_EXPORT_HEADERS, rowWriter -> {
            List<ReceivableEntity> receivables = receivableMapper.selectList(buildQuery(safeQuery));
            for (ReceivableEntity entity : receivables) {
                rowWriter.write(receivableExportRow(toResponse(entity)));
            }
        }));
    }

    private ReceivableEntity requireReceivable(Long id) {
        ReceivableEntity entity = receivableMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("应收记录不存在");
        }
        return entity;
    }

    private LambdaQueryWrapper<ReceivableEntity> buildQuery(ReceivablePageQuery safeQuery) {
        LambdaQueryWrapper<ReceivableEntity> wrapper = new LambdaQueryWrapper<>();
        if (safeQuery.getCustomerId() != null) {
            wrapper.eq(ReceivableEntity::getCustomerId, safeQuery.getCustomerId());
        }
        String status = normalizeUpper(safeQuery.getStatus());
        if (StringUtils.hasText(status)) {
            wrapper.eq(ReceivableEntity::getStatus, status);
        }
        String sourceType = normalizeUpper(safeQuery.getSourceType());
        if (StringUtils.hasText(sourceType)) {
            wrapper.eq(ReceivableEntity::getSourceType, sourceType);
        }
        if (safeQuery.getBizDateFrom() != null) {
            wrapper.ge(ReceivableEntity::getBizDate, safeQuery.getBizDateFrom());
        }
        if (safeQuery.getBizDateTo() != null) {
            wrapper.le(ReceivableEntity::getBizDate, safeQuery.getBizDateTo());
        }
        wrapper = financeSettlementScopeSupport.applyReceivableScope(wrapper);
        return wrapper.orderByDesc(ReceivableEntity::getBizDate).orderByDesc(ReceivableEntity::getId);
    }

    private ReceivableResponse toResponse(ReceivableEntity entity) {
        return new ReceivableResponse(
                entity.getId(),
                entity.getReceivableNo(),
                entity.getCustomerId(),
                entity.getBizDate(),
                entity.getSourceType(),
                entity.getSourceId(),
                entity.getSourceNo(),
                entity.getDirection(),
                entity.getOriginalAmount(),
                entity.getSettledAmount(),
                remaining(entity.getOriginalAmount(), entity.getSettledAmount()),
                entity.getStatus(),
                entity.getRemark()
        );
    }

    private List<?> receivableExportRow(ReceivableResponse record) {
        return Arrays.asList(
                record.receivableNo(),
                record.customerId(),
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

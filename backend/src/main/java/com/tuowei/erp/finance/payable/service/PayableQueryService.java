package com.tuowei.erp.finance.payable.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.payable.web.PayablePageQuery;
import com.tuowei.erp.finance.payable.web.PayableResponse;
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
    private final FinanceSettlementScopeSupport financeSettlementScopeSupport;

    public PayableQueryService(
            PayableMapper payableMapper,
            FinanceSettlementScopeSupport financeSettlementScopeSupport
    ) {
        this.payableMapper = payableMapper;
        this.financeSettlementScopeSupport = financeSettlementScopeSupport;
    }

    @Transactional(readOnly = true)
    public PageResponse<PayableResponse> list(PayablePageQuery query) {
        PayablePageQuery safeQuery = query == null ? new PayablePageQuery() : query;
        Page<PayableEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<PayableEntity> wrapper = buildQuery(safeQuery);
        Page<PayableEntity> result = payableMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public PayableResponse detail(Long id) {
        PayableEntity entity = requirePayable(id);
        financeSettlementScopeSupport.assertCanViewPayable(entity);
        return toResponse(entity);
    }

    public StreamingResponseBody exportPayables(PayablePageQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        PayablePageQuery safeQuery = query == null ? new PayablePageQuery() : query;
        return outputStream -> withAuthentication(authentication, () -> CsvExport.write(outputStream, PAYABLE_EXPORT_HEADERS, rowWriter -> {
            List<PayableEntity> payables = payableMapper.selectList(buildQuery(safeQuery));
            for (PayableEntity entity : payables) {
                rowWriter.write(payableExportRow(toResponse(entity)));
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

    private PayableResponse toResponse(PayableEntity entity) {
        return new PayableResponse(
                entity.getId(),
                entity.getPayableNo(),
                entity.getSupplierId(),
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

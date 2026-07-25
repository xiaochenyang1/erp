package com.tuowei.erp.finance.receivable.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.receivable.web.ReceivablePageQuery;
import com.tuowei.erp.finance.receivable.web.ReceivableResponse;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
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
public class ReceivableQueryService {

    private static final List<String> RECEIVABLE_EXPORT_HEADERS = List.of(
            "receivableNo",
            "customerId",
            "bizDate",
            "dueDate",
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
    private final CustomerMapper customerMapper;
    private final FinanceSettlementScopeSupport financeSettlementScopeSupport;
    private final AuditMetadataFactory auditMetadataFactory;

    public ReceivableQueryService(
            ReceivableMapper receivableMapper,
            CustomerMapper customerMapper,
            FinanceSettlementScopeSupport financeSettlementScopeSupport,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this.receivableMapper = receivableMapper;
        this.customerMapper = customerMapper;
        this.financeSettlementScopeSupport = financeSettlementScopeSupport;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public PageResponse<ReceivableResponse> list(ReceivablePageQuery query) {
        ReceivablePageQuery safeQuery = query == null ? new ReceivablePageQuery() : query;
        Page<ReceivableEntity> page = new Page<>(normalizePageNo(safeQuery.getPageNo()), normalizePageSize(safeQuery.getPageSize()));
        LambdaQueryWrapper<ReceivableEntity> wrapper = buildQuery(safeQuery);
        Page<ReceivableEntity> result = receivableMapper.selectPage(page, wrapper);
        Map<Long, String> customerNames = loadCustomerNames(result.getRecords());
        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream()
                        .map(entity -> toResponse(entity, customerNames.get(entity.getCustomerId())))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public ReceivableResponse detail(Long id) {
        ReceivableEntity entity = requireReceivable(id);
        financeSettlementScopeSupport.assertCanViewReceivable(entity);
        Map<Long, String> customerNames = loadCustomerNames(List.of(entity));
        return toResponse(entity, customerNames.get(entity.getCustomerId()));
    }

    public StreamingResponseBody exportReceivables(ReceivablePageQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        ReceivablePageQuery safeQuery = query == null ? new ReceivablePageQuery() : query;
        return outputStream -> withAuthentication(authentication, () -> CsvExport.write(outputStream, RECEIVABLE_EXPORT_HEADERS, rowWriter -> {
            List<ReceivableEntity> receivables = receivableMapper.selectList(buildQuery(safeQuery));
            for (ReceivableEntity entity : receivables) {
                rowWriter.write(receivableExportRow(toResponse(entity, null)));
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
        String receivableNo = normalizeText(safeQuery.getReceivableNo());
        if (StringUtils.hasText(receivableNo)) {
            wrapper.like(ReceivableEntity::getReceivableNo, receivableNo);
        }
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

    private ReceivableResponse toResponse(ReceivableEntity entity, String customerName) {
        return new ReceivableResponse(
                entity.getId(),
                entity.getReceivableNo(),
                entity.getCustomerId(),
                customerName,
                entity.getBizDate(),
                entity.getDueDate() != null ? entity.getDueDate() : entity.getBizDate(),
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

    private Map<Long, String> loadCustomerNames(List<ReceivableEntity> receivables) {
        Set<Long> customerIds = receivables.stream()
                .map(ReceivableEntity::getCustomerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (customerIds.isEmpty()) {
            return Map.of();
        }

        AuditMetadata audit = auditMetadataFactory.current();
        return customerMapper.selectList(new LambdaQueryWrapper<CustomerEntity>()
                        .select(
                                CustomerEntity::getId,
                                CustomerEntity::getCompanyId,
                                CustomerEntity::getAccountBookId,
                                CustomerEntity::getCustomerName,
                                CustomerEntity::getDeletedFlag
                        )
                        .eq(CustomerEntity::getCompanyId, audit.companyId())
                        .eq(CustomerEntity::getAccountBookId, audit.accountBookId())
                        .eq(CustomerEntity::getDeletedFlag, 0)
                        .in(CustomerEntity::getId, customerIds))
                .stream()
                .filter(customer -> Objects.equals(customer.getCompanyId(), audit.companyId()))
                .filter(customer -> Objects.equals(customer.getAccountBookId(), audit.accountBookId()))
                .filter(customer -> Objects.equals(customer.getDeletedFlag(), 0))
                .collect(Collectors.toMap(
                        CustomerEntity::getId,
                        CustomerEntity::getCustomerName,
                        (first, ignored) -> first,
                        HashMap::new
                ));
    }

    private List<?> receivableExportRow(ReceivableResponse record) {
        return Arrays.asList(
                record.receivableNo(),
                record.customerId(),
                record.bizDate(),
                record.dueDate(),
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

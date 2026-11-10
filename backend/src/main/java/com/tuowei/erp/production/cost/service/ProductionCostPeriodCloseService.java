package com.tuowei.erp.production.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.period.mapper.AccountPeriodMapper;
import com.tuowei.erp.finance.period.model.AccountPeriodEntity;
import com.tuowei.erp.production.cost.web.ProductionCostCloseRequest;
import com.tuowei.erp.production.cost.web.ProductionCostCloseResponse;
import com.tuowei.erp.production.cost.web.ProductionCostPeriodCloseBatchItemResponse;
import com.tuowei.erp.production.cost.web.ProductionCostPeriodCloseBatchRequest;
import com.tuowei.erp.production.cost.web.ProductionCostPeriodCloseBatchResponse;
import com.tuowei.erp.production.cost.web.ProductionCostPeriodCloseEvidenceResponse;
import com.tuowei.erp.production.cost.web.ProductionCostPeriodCloseItemResponse;
import com.tuowei.erp.production.cost.web.ProductionCostPeriodClosePreviewResponse;
import com.tuowei.erp.production.cost.web.ProductionCostPeriodResponse;
import com.tuowei.erp.production.order.mapper.ProductionOrderMapper;
import com.tuowei.erp.production.order.model.ProductionOrderEntity;
import com.tuowei.erp.production.order.service.ProductionOrderQueryService;
import com.tuowei.erp.system.log.service.SystemLogService;
import com.tuowei.erp.system.log.web.AuditLogPageQuery;
import com.tuowei.erp.system.log.web.AuditLogResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Period-oriented preview, partial-success batch close, and audit evidence for production costs. */
@Service
public class ProductionCostPeriodCloseService {
    private static final String AUDIT_TYPE = "BUSINESS";
    private static final String BUSINESS_TYPE = "PRODUCTION_COST_PERIOD_CLOSE";
    private static final String ACTION_BATCH_CLOSE = "BATCH_CLOSE";

    private final AccountPeriodMapper accountPeriodMapper;
    private final ProductionOrderMapper orderMapper;
    private final ProductionOrderQueryService orderQueryService;
    private final ProductionCostCloseService closeService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final CurrentUserContext currentUserContext;
    private final SystemLogService systemLogService;
    private final ObjectMapper objectMapper;

    public ProductionCostPeriodCloseService(
            AccountPeriodMapper accountPeriodMapper,
            ProductionOrderMapper orderMapper,
            ProductionOrderQueryService orderQueryService,
            ProductionCostCloseService closeService,
            AuditMetadataFactory auditMetadataFactory,
            CurrentUserContext currentUserContext,
            SystemLogService systemLogService,
            ObjectMapper objectMapper
    ) {
        this.accountPeriodMapper = accountPeriodMapper;
        this.orderMapper = orderMapper;
        this.orderQueryService = orderQueryService;
        this.closeService = closeService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.currentUserContext = currentUserContext;
        this.systemLogService = systemLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<ProductionCostPeriodResponse> listPeriods() {
        AuditMetadata audit = auditMetadataFactory.current();
        return accountPeriodMapper.selectList(new LambdaQueryWrapper<AccountPeriodEntity>()
                        .eq(AccountPeriodEntity::getCompanyId, audit.companyId())
                        .eq(AccountPeriodEntity::getAccountBookId, audit.accountBookId())
                        .orderByAsc(AccountPeriodEntity::getPeriodMonth))
                .stream()
                .map(period -> new ProductionCostPeriodResponse(
                        period.getId(),
                        period.getPeriodYear(),
                        period.getPeriodMonth(),
                        period.getStartDate(),
                        period.getEndDate(),
                        period.getStatus()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductionCostPeriodClosePreviewResponse preview(Long periodId) {
        AuditMetadata audit = auditMetadataFactory.current();
        AccountPeriodEntity period = requirePeriod(periodId, audit);
        return preview(period, audit);
    }

    public ProductionCostPeriodCloseBatchResponse closeBatch(
            Long periodId,
            ProductionCostPeriodCloseBatchRequest request
    ) {
        AuditMetadata audit = auditMetadataFactory.current();
        AccountPeriodEntity period = requirePeriod(periodId, audit);
        if (!"OPEN".equals(period.getStatus())) {
            throw new BusinessConflictException("只有开放会计期间可以执行生产成本批量关单");
        }
        Set<Long> requestedIds = new LinkedHashSet<>(request.orderIds());
        if (requestedIds.size() != request.orderIds().size()) {
            throw new IllegalArgumentException("批量关单工单不能重复");
        }

        ProductionCostPeriodClosePreviewResponse preview = preview(period, audit);
        Map<Long, ProductionCostPeriodCloseItemResponse> candidates = new LinkedHashMap<>();
        for (ProductionCostPeriodCloseItemResponse item : preview.items()) {
            candidates.put(item.orderId(), item);
        }

        List<ProductionCostPeriodCloseBatchItemResponse> results = new ArrayList<>();
        BigDecimal closedVariance = BigDecimal.ZERO;
        for (Long orderId : requestedIds) {
            ProductionCostPeriodCloseItemResponse candidate = candidates.get(orderId);
            if (candidate == null) {
                results.add(failure(orderId, null, "NOT_IN_PERIOD", "工单不属于当前期间待关单范围"));
                continue;
            }
            try {
                ProductionCostCloseResponse close = closeService.close(
                        orderId,
                        new ProductionCostCloseRequest(period.getEndDate(), normalize(request.remark()))
                );
                closedVariance = closedVariance.add(close.amount());
                results.add(new ProductionCostPeriodCloseBatchItemResponse(
                        orderId,
                        candidate.orderNo(),
                        true,
                        close.id(),
                        close.closeNo(),
                        close.amount(),
                        null,
                        null
                ));
            } catch (BusinessConflictException ex) {
                results.add(failure(orderId, candidate.orderNo(), "CONFLICT", ex.getMessage()));
            } catch (IllegalArgumentException ex) {
                results.add(failure(orderId, candidate.orderNo(), "VALIDATION", ex.getMessage()));
            } catch (RuntimeException ex) {
                results.add(failure(orderId, candidate.orderNo(), "FAILED", "关单失败，请刷新后重试"));
            }
        }

        int successCount = (int) results.stream().filter(ProductionCostPeriodCloseBatchItemResponse::success).count();
        ProductionCostPeriodCloseBatchResponse response = new ProductionCostPeriodCloseBatchResponse(
                period.getId(),
                period.getPeriodMonth(),
                period.getEndDate(),
                results.size(),
                successCount,
                results.size() - successCount,
                ScalePrecision.amount(closedVariance),
                audit.userId(),
                audit.now(),
                List.copyOf(results)
        );
        recordAudit(response, currentUserContext.requireCurrentUser().username());
        return response;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductionCostPeriodCloseEvidenceResponse> evidence(
            Long periodId,
            Integer pageNo,
            Integer pageSize
    ) {
        AuditMetadata audit = auditMetadataFactory.current();
        requirePeriod(periodId, audit);
        AuditLogPageQuery query = new AuditLogPageQuery();
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        query.setAuditType(AUDIT_TYPE);
        query.setBusinessType(BUSINESS_TYPE);
        query.setBusinessId(periodId);
        query.setAction(ACTION_BATCH_CLOSE);
        query.setOperatorId(audit.userId());
        PageResponse<AuditLogResponse> page = systemLogService.listAuditLogs(query);
        return new PageResponse<>(
                page.pageNo(),
                page.pageSize(),
                page.total(),
                page.records().stream().map(this::toEvidence).toList()
        );
    }

    private ProductionCostPeriodClosePreviewResponse preview(AccountPeriodEntity period, AuditMetadata audit) {
        List<ProductionCostPeriodCloseItemResponse> items = orderMapper.selectList(
                        new LambdaQueryWrapper<ProductionOrderEntity>()
                                .eq(ProductionOrderEntity::getCompanyId, audit.companyId())
                                .eq(ProductionOrderEntity::getAccountBookId, audit.accountBookId())
                                .eq(ProductionOrderEntity::getDeletedFlag, 0)
                                .eq(ProductionOrderEntity::getStatus, "COMPLETED")
                                .eq(ProductionOrderEntity::getCostCloseStatus, ProductionCostCloseService.STATUS_OPEN)
                                .orderByAsc(ProductionOrderEntity::getOrderNo)
                                .orderByAsc(ProductionOrderEntity::getId))
                .stream()
                .filter(this::canView)
                .map(order -> toPreviewItem(order, period.getEndDate()))
                .filter(java.util.Objects::nonNull)
                .toList();
        BigDecimal positive = BigDecimal.ZERO;
        BigDecimal negative = BigDecimal.ZERO;
        for (ProductionCostPeriodCloseItemResponse item : items) {
            if (item.unsettledVarianceAmount().signum() >= 0) {
                positive = positive.add(item.unsettledVarianceAmount());
            } else {
                negative = negative.add(item.unsettledVarianceAmount());
            }
        }
        return new ProductionCostPeriodClosePreviewResponse(
                period.getId(),
                period.getPeriodMonth(),
                period.getStartDate(),
                period.getEndDate(),
                period.getStatus(),
                items.size(),
                ScalePrecision.amount(positive),
                ScalePrecision.amount(negative),
                ScalePrecision.amount(positive.add(negative)),
                items
        );
    }

    private boolean canView(ProductionOrderEntity order) {
        try {
            orderQueryService.assertCanView(order);
            return true;
        } catch (AccessDeniedException ex) {
            return false;
        }
    }

    private ProductionCostPeriodCloseItemResponse toPreviewItem(ProductionOrderEntity order, LocalDate cutoffDate) {
        LocalDate completionDate = closeService.latestCompletionDate(order);
        if (completionDate == null || completionDate.isAfter(cutoffDate)) {
            return null;
        }
        LocalDate latestActivityDate = closeService.latestActivityDate(order);
        if (latestActivityDate != null && latestActivityDate.isAfter(cutoffDate)) {
            return null;
        }
        BigDecimal finished = ScalePrecision.zeroDefault(order.getFinishedMaterialAmount())
                .add(ScalePrecision.zeroDefault(order.getFinishedLaborAmount()))
                .add(ScalePrecision.zeroDefault(order.getFinishedOverheadAmount()));
        return new ProductionCostPeriodCloseItemResponse(
                order.getId(),
                order.getOrderNo(),
                order.getProductId(),
                completionDate,
                latestActivityDate,
                ScalePrecision.amount(ScalePrecision.zeroDefault(order.getIssuedAmount())),
                ScalePrecision.amount(ScalePrecision.zeroDefault(order.getLaborAmount())),
                ScalePrecision.amount(ScalePrecision.zeroDefault(order.getOverheadAmount())),
                ScalePrecision.amount(finished),
                closeService.unsettledVariance(order)
        );
    }

    private AccountPeriodEntity requirePeriod(Long periodId, AuditMetadata audit) {
        AccountPeriodEntity period = accountPeriodMapper.selectOne(new LambdaQueryWrapper<AccountPeriodEntity>()
                .eq(AccountPeriodEntity::getCompanyId, audit.companyId())
                .eq(AccountPeriodEntity::getAccountBookId, audit.accountBookId())
                .eq(AccountPeriodEntity::getId, periodId));
        if (period == null) {
            throw new IllegalArgumentException("会计期间不存在");
        }
        return period;
    }

    private void recordAudit(ProductionCostPeriodCloseBatchResponse response, String operatorName) {
        String message = String.format(
                "期间批量关单：成功 %d 张，失败 %d 张，结转差异 %s",
                response.successCount(),
                response.failureCount(),
                response.closedVarianceAmount().toPlainString()
        );
        systemLogService.recordAudit(
                AUDIT_TYPE,
                BUSINESS_TYPE,
                response.periodId(),
                response.periodMonth(),
                ACTION_BATCH_CLOSE,
                response.operatorId(),
                operatorName,
                toJson(response),
                message,
                response.executedTime()
        );
    }

    private ProductionCostPeriodCloseEvidenceResponse toEvidence(AuditLogResponse audit) {
        try {
            return new ProductionCostPeriodCloseEvidenceResponse(
                    audit.id(),
                    audit.operatorId(),
                    audit.operatorName(),
                    audit.auditTime(),
                    audit.message(),
                    objectMapper.readValue(audit.snapshotJson(), ProductionCostPeriodCloseBatchResponse.class)
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("生产成本批量关单审计快照无法解析", ex);
        }
    }

    private String toJson(ProductionCostPeriodCloseBatchResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("生产成本批量关单审计快照无法生成", ex);
        }
    }

    private ProductionCostPeriodCloseBatchItemResponse failure(
            Long orderId,
            String orderNo,
            String errorCode,
            String errorMessage
    ) {
        return new ProductionCostPeriodCloseBatchItemResponse(
                orderId, orderNo, false, null, null, null, errorCode, errorMessage
        );
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

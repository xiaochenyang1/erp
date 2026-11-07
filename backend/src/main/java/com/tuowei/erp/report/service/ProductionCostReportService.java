package com.tuowei.erp.report.service;

import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.common.config.ReportProperties;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.production.order.service.ProductionOrderQueryService;
import com.tuowei.erp.production.order.web.ProductionOrderMaterialResponse;
import com.tuowei.erp.production.order.web.ProductionOrderPageQuery;
import com.tuowei.erp.production.order.web.ProductionOrderResponse;
import com.tuowei.erp.report.web.ProductionCostReportQuery;
import com.tuowei.erp.report.web.ProductionCostReportResponse;
import com.tuowei.erp.report.mapper.ProductionCostLedgerMapper;
import com.tuowei.erp.report.mapper.ProductionCostLedgerRow;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductionCostReportService {
    private final ProductionOrderQueryService productionOrderQueryService;
    private final ProductMapper productMapper;
    private final ReportProperties reportProperties;
    private final CurrentUserContext currentUserContext;
    private final ProductionCostLedgerMapper productionCostLedgerMapper;

    @Autowired
    public ProductionCostReportService(ProductionOrderQueryService productionOrderQueryService, ProductMapper productMapper,
                                       ReportProperties reportProperties, CurrentUserContext currentUserContext,
                                       ProductionCostLedgerMapper productionCostLedgerMapper) {
        this.productionOrderQueryService = productionOrderQueryService;
        this.productMapper = productMapper;
        this.reportProperties = reportProperties;
        this.currentUserContext = currentUserContext;
        this.productionCostLedgerMapper = productionCostLedgerMapper;
    }

    /** Compatibility constructor retained for focused unit tests and embedders. */
    public ProductionCostReportService(ProductionOrderQueryService productionOrderQueryService, ProductMapper productMapper,
                                       ReportProperties reportProperties) {
        this(productionOrderQueryService, productMapper, reportProperties, null, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductionCostReportResponse> list(ProductionCostReportQuery query) {
        ProductionCostReportQuery safe = query == null ? new ProductionCostReportQuery() : query;
        ProductionOrderPageQuery orderQuery = toOrderQuery(safe, pageNo(safe.getPageNo()), pageSize(safe.getPageSize()));
        PageResponse<ProductionOrderResponse> page = productionOrderQueryService.list(orderQuery);
        return new PageResponse<>(page.pageNo(), page.pageSize(), page.total(), mapRows(page.records()));
    }

    @Transactional(readOnly = true)
    public void stream(ProductionCostReportQuery query, Consumer<ProductionCostReportResponse> consumer, int batchSize) {
        ProductionCostReportQuery safe = query == null ? new ProductionCostReportQuery() : query;
        int pageNo = 1;
        int emitted = 0;
        int maxRows = reportProperties.maxExportRows();
        while (true) {
            PageResponse<ProductionOrderResponse> page = productionOrderQueryService.list(
                    toOrderQuery(safe, pageNo, batchSize));
            for (ProductionCostReportResponse row : mapRows(page.records())) {
                if (emitted >= maxRows) {
                    throw new IllegalArgumentException("导出结果超过" + maxRows + "行，请缩小筛选范围后重试");
                }
                consumer.accept(row);
                emitted++;
            }
            if (page.records().size() < batchSize) return;
            pageNo++;
        }
    }

    private List<ProductionCostReportResponse> mapRows(List<ProductionOrderResponse> orders) {
        if (orders.isEmpty()) return List.of();
        var ids = orders.stream().map(ProductionOrderResponse::productId).filter(java.util.Objects::nonNull).toList();
        LambdaQueryWrapper<ProductEntity> productQuery = new LambdaQueryWrapper<ProductEntity>()
                .in(ProductEntity::getId, ids)
                .eq(ProductEntity::getDeletedFlag, 0);
        if (currentUserContext != null) {
            CurrentUser currentUser = currentUserContext.requireCurrentUser();
            productQuery.eq(ProductEntity::getCompanyId, currentUser.companyId())
                    .eq(ProductEntity::getAccountBookId, currentUser.accountBookId());
        }
        Map<Long, ProductEntity> products = productMapper.selectList(productQuery)
                .stream().collect(Collectors.toMap(ProductEntity::getId, Function.identity(), (left, right) -> left));
        Map<Long, BigDecimal> ledgerAmounts = loadLedgerAmounts(orders);
        return orders.stream().map(order -> toResponse(
                order,
                products.get(order.productId()),
                ledgerAmounts.get(order.id())
        )).toList();
    }

    private ProductionCostReportResponse toResponse(
            ProductionOrderResponse order,
            ProductEntity product,
            BigDecimal ledgerAmount
    ) {
        BigDecimal planned = zero(order.plannedQty());
        BigDecimal completed = zero(order.completedQty());
        BigDecimal materialCost = zero(order.issuedAmount());
        BigDecimal laborCost = zero(order.laborAmount());
        BigDecimal overheadCost = zero(order.overheadAmount());
        BigDecimal totalInputCost = amount(materialCost.add(laborCost).add(overheadCost));
        BigDecimal finishedMaterialCost = zero(order.finishedMaterialAmount());
        BigDecimal finishedLaborCost = zero(order.finishedLaborAmount());
        BigDecimal finishedOverheadCost = zero(order.finishedOverheadAmount());
        BigDecimal finishedCost = amount(finishedMaterialCost.add(finishedLaborCost).add(finishedOverheadCost));
        BigDecimal materialWip = amount(materialCost.subtract(finishedMaterialCost));
        BigDecimal laborWip = amount(laborCost.subtract(finishedLaborCost));
        BigDecimal overheadWip = amount(overheadCost.subtract(finishedOverheadCost));
        BigDecimal costVariance = amount(materialWip.add(laborWip).add(overheadWip));
        BigDecimal settledVariance = amount(zero(order.settledVarianceAmount()));
        BigDecimal wip = amount(costVariance.subtract(settledVariance));
        BigDecimal ledgerWip = ledgerAmount == null ? wip : amount(ledgerAmount);
        BigDecimal reconciliationDifference = amount(wip.subtract(ledgerWip));
        BigDecimal rate = planned.signum() == 0 ? BigDecimal.ZERO : completed.divide(planned, 4, RoundingMode.HALF_UP);
        BigDecimal unitCost = completed.signum() == 0 ? BigDecimal.ZERO : finishedCost.divide(completed, 4, RoundingMode.HALF_UP);
        String costStatus = costStatus(order.status(), order.costCloseStatus(), wip, reconciliationDifference);
        return new ProductionCostReportResponse(
                order.id(), order.orderNo(), order.productId(), product == null ? null : product.getProductCode(),
                product == null ? null : product.getProductName(), order.status(), order.plannedStartDate(),
                order.plannedFinishDate(), planned, completed, rate,
                materialCost, laborCost, overheadCost, totalInputCost,
                finishedMaterialCost, finishedLaborCost, finishedOverheadCost, finishedCost,
                costVariance, settledVariance, order.costCloseStatus(), order.costClosedDate(),
                materialWip, laborWip, overheadWip, wip,
                ledgerWip, reconciliationDifference, unitCost, costStatus
        );
    }

    private Map<Long, BigDecimal> loadLedgerAmounts(List<ProductionOrderResponse> orders) {
        if (productionCostLedgerMapper == null || currentUserContext == null || orders.isEmpty()) return Map.of();
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        List<Long> orderIds = orders.stream().map(ProductionOrderResponse::id).toList();
        Map<Long, BigDecimal> amounts = new LinkedHashMap<>();
        orderIds.forEach(orderId -> amounts.put(orderId, amount(BigDecimal.ZERO)));
        productionCostLedgerMapper.summarize(currentUser.companyId(), currentUser.accountBookId(), orderIds)
                .forEach(row -> amounts.put(row.getOrderId(), amount(row.getLedgerAmount())));
        return amounts;
    }

    private String costStatus(String orderStatus, String closeStatus, BigDecimal wip, BigDecimal difference) {
        if (difference.signum() != 0) return "UNRECONCILED";
        if ("CLOSED".equals(closeStatus)) return "CLOSED";
        return switch (orderStatus) {
            case "COMPLETED" -> wip.signum() == 0 ? "BALANCED" : "COST_VARIANCE";
            case "MATERIAL_ISSUED", "RELEASED", "IN_PROGRESS" -> "WIP";
            default -> "NOT_POSTED";
        };
    }

    private ProductionOrderPageQuery toOrderQuery(ProductionCostReportQuery query, int pageNo, int pageSize) {
        ProductionOrderPageQuery result = new ProductionOrderPageQuery();
        result.setPageNo(pageNo); result.setPageSize(pageSize); result.setKeyword(normalize(query.getKeyword()));
        result.setStatus(normalize(query.getStatus())); result.setProductId(query.getProductId());
        result.setPlannedStartDateFrom(query.getPlannedStartDateFrom()); result.setPlannedStartDateTo(query.getPlannedStartDateTo());
        return result;
    }

    private String normalize(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
    private BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal amount(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private int pageNo(Integer value) { return value == null || value < 1 ? 1 : value; }
    private int pageSize(Integer value) { return value == null || value < 1 ? 20 : Math.min(value, 200); }
}

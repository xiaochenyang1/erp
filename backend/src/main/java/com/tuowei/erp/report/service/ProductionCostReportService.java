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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    public ProductionCostReportService(ProductionOrderQueryService productionOrderQueryService, ProductMapper productMapper,
                                       ReportProperties reportProperties, CurrentUserContext currentUserContext) {
        this.productionOrderQueryService = productionOrderQueryService;
        this.productMapper = productMapper;
        this.reportProperties = reportProperties;
        this.currentUserContext = currentUserContext;
    }

    /** Compatibility constructor retained for focused unit tests and embedders. */
    public ProductionCostReportService(ProductionOrderQueryService productionOrderQueryService, ProductMapper productMapper,
                                       ReportProperties reportProperties) {
        this(productionOrderQueryService, productMapper, reportProperties, null);
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
        return orders.stream().map(order -> toResponse(order, products.get(order.productId()))).toList();
    }

    private ProductionCostReportResponse toResponse(ProductionOrderResponse order, ProductEntity product) {
        BigDecimal planned = zero(order.plannedQty());
        BigDecimal completed = zero(order.completedQty());
        BigDecimal materialCost = order.materials() == null ? BigDecimal.ZERO : order.materials().stream()
                .map(ProductionOrderMaterialResponse::issuedAmount).map(this::zero).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal finishedCost = zero(order.finishedAmount());
        BigDecimal wip = materialCost.subtract(finishedCost);
        BigDecimal rate = planned.signum() == 0 ? BigDecimal.ZERO : completed.divide(planned, 4, RoundingMode.HALF_UP);
        BigDecimal unitCost = completed.signum() == 0 ? BigDecimal.ZERO : finishedCost.divide(completed, 4, RoundingMode.HALF_UP);
        String costStatus = switch (order.status()) {
            case "COMPLETED" -> wip.signum() == 0 ? "BALANCED" : "COST_VARIANCE";
            case "MATERIAL_ISSUED", "RELEASED" -> "WIP";
            default -> "NOT_POSTED";
        };
        return new ProductionCostReportResponse(
                order.id(), order.orderNo(), order.productId(), product == null ? null : product.getProductCode(),
                product == null ? null : product.getProductName(), order.status(), order.plannedStartDate(),
                order.plannedFinishDate(), planned, completed, rate, materialCost, finishedCost, wip, unitCost, costStatus
        );
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
    private int pageNo(Integer value) { return value == null || value < 1 ? 1 : value; }
    private int pageSize(Integer value) { return value == null || value < 1 ? 20 : Math.min(value, 200); }
}

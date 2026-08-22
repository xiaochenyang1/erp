package com.tuowei.erp.sales.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ProductAuxUnitConversion;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.sales.order.web.SalesOrderCreateRequest;
import com.tuowei.erp.sales.order.web.SalesOrderCreditPreviewRequest;
import com.tuowei.erp.sales.order.web.SalesOrderCreditPreviewResponse;
import com.tuowei.erp.sales.order.web.SalesOrderLineRequest;
import com.tuowei.erp.sales.order.web.SalesOrderResponse;
import com.tuowei.erp.sales.order.web.SalesOrderUpdateRequest;
import com.tuowei.erp.sales.support.SalesAmountCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Write-side creation and editing, plus the credit exposure preview used by the order form. */
@Service
public class SalesOrderCommandService {
    private final SalesOrderMapper salesOrderMapper;
    private final SalesOrderLineMapper salesOrderLineMapper;
    private final CustomerMapper customerMapper;
    private final ProductValidator productValidator;
    private final WarehouseMapper warehouseMapper;
    private final SalesOrderNumberService salesOrderNumberService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SalesOrderQueryService salesOrderQueryService;
    private final SalesCreditEvaluator salesCreditEvaluator;
    private final SalesPriceEvaluator salesPriceEvaluator;

    public SalesOrderCommandService(
            SalesOrderMapper salesOrderMapper,
            SalesOrderLineMapper salesOrderLineMapper,
            CustomerMapper customerMapper,
            ProductValidator productValidator,
            WarehouseMapper warehouseMapper,
            SalesOrderNumberService salesOrderNumberService,
            AuditMetadataFactory auditMetadataFactory,
            SalesOrderQueryService salesOrderQueryService,
            SalesCreditEvaluator salesCreditEvaluator,
            SalesPriceEvaluator salesPriceEvaluator
    ) {
        this.salesOrderMapper = salesOrderMapper;
        this.salesOrderLineMapper = salesOrderLineMapper;
        this.customerMapper = customerMapper;
        this.productValidator = productValidator;
        this.warehouseMapper = warehouseMapper;
        this.salesOrderNumberService = salesOrderNumberService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.salesOrderQueryService = salesOrderQueryService;
        this.salesCreditEvaluator = salesCreditEvaluator;
        this.salesPriceEvaluator = salesPriceEvaluator;
    }

    @Transactional
    public SalesOrderResponse create(SalesOrderCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        CustomerEntity customer = requireActiveCustomer(request.customerId(), audit.companyId(), audit.accountBookId());
        WarehouseEntity warehouse = requireActiveWarehouse(request.warehouseId(), audit.companyId(), audit.accountBookId());
        salesPriceEvaluator.assertLinesWithinMinPrice(
                audit.companyId(), audit.accountBookId(), customer.getId(), request.orderDate(), request.lines());
        OrderTotals totals = calculateTotals(request.lines());
        LocalDateTime now = audit.now();

        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setOrderNo(salesOrderNumberService.nextOrderNo(request.orderDate()));
        entity.setCustomerId(customer.getId());
        entity.setWarehouseId(warehouse.getId());
        entity.setOrderDate(request.orderDate());
        entity.setDeliveryDate(request.deliveryDate());
        entity.setStatus("DRAFT");
        entity.setApprovalStatus("NOT_SUBMITTED");
        entity.setDeliveryStatus("NOT_DELIVERED");
        entity.setTotalQuantity(totals.totalQuantity());
        entity.setTotalAmount(totals.totalAmount());
        entity.setTotalTaxAmount(totals.totalTaxAmount());
        entity.setDeletedFlag(0);
        entity.setRemark(request.remark());
        fillCreateAudit(entity, audit, now);
        salesOrderMapper.insert(entity);

        List<SalesOrderLineEntity> lines = saveOrderLines(entity.getId(), request.lines(), audit, now);
        return salesOrderQueryService.toResponse(entity, customer.getCustomerName(), lines);
    }

    @Transactional(readOnly = true)
    public SalesOrderCreditPreviewResponse previewCredit(SalesOrderCreditPreviewRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        CustomerEntity customer = requireActiveCustomer(request.customerId(), audit.companyId(), audit.accountBookId());
        List<SalesOrderLineRequest> lines = request.lines() == null ? List.of() : request.lines();
        OrderTotals totals = calculateTotals(lines);
        SalesCreditPreview preview = salesCreditEvaluator.preview(
                customer, totals.totalAmount().add(totals.totalTaxAmount()));
        return new SalesOrderCreditPreviewResponse(
                customer.getId(),
                preview.creditLimit(),
                preview.outstandingReceivable(),
                preview.openOrderExposure(),
                preview.currentExposure(),
                preview.orderAmount(),
                preview.projectedExposure(),
                preview.availableCredit(),
                preview.projectedAvailableCredit(),
                preview.unlimited(),
                preview.exceeded()
        );
    }

    @Transactional
    public SalesOrderResponse update(Long id, SalesOrderUpdateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        SalesOrderEntity entity = salesOrderQueryService.requireOrder(id);
        if (!"DRAFT".equals(entity.getStatus()) && !"REJECTED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前销售订单状态不允许编辑");
        }

        CustomerEntity customer = requireActiveCustomer(request.customerId(), audit.companyId(), audit.accountBookId());
        WarehouseEntity warehouse = requireActiveWarehouse(request.warehouseId(), audit.companyId(), audit.accountBookId());
        salesPriceEvaluator.assertLinesWithinMinPrice(
                audit.companyId(), audit.accountBookId(), customer.getId(), request.orderDate(), request.lines());
        OrderTotals totals = calculateTotals(request.lines());
        LocalDateTime now = audit.now();

        entity.setCustomerId(customer.getId());
        entity.setWarehouseId(warehouse.getId());
        entity.setOrderDate(request.orderDate());
        entity.setDeliveryDate(request.deliveryDate());
        entity.setTotalQuantity(totals.totalQuantity());
        entity.setTotalAmount(totals.totalAmount());
        entity.setTotalTaxAmount(totals.totalTaxAmount());
        entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                salesOrderMapper.updateById(entity), "销售订单已被其他操作修改，请刷新后重试");

        salesOrderLineMapper.delete(new LambdaQueryWrapper<SalesOrderLineEntity>()
                .eq(SalesOrderLineEntity::getCompanyId, entity.getCompanyId())
                .eq(SalesOrderLineEntity::getAccountBookId, entity.getAccountBookId())
                .eq(SalesOrderLineEntity::getOrderId, entity.getId()));
        saveOrderLines(entity.getId(), request.lines(), audit, now);
        return salesOrderQueryService.getById(id);
    }

    private CustomerEntity requireActiveCustomer(Long customerId, Long companyId, Long accountBookId) {
        CustomerEntity customer = customerMapper.selectById(customerId);
        if (customer == null || customer.getDeletedFlag() == null || customer.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(customer.getStatus())
                || !Objects.equals(customer.getCompanyId(), companyId)
                || !Objects.equals(customer.getAccountBookId(), accountBookId)) {
            throw new IllegalArgumentException("客户不存在或已停用");
        }
        return customer;
    }

    private WarehouseEntity requireActiveWarehouse(Long warehouseId, Long companyId, Long accountBookId) {
        WarehouseEntity warehouse = warehouseMapper.selectById(warehouseId);
        if (warehouse == null || warehouse.getDeletedFlag() == null || warehouse.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(warehouse.getStatus())
                || !Objects.equals(warehouse.getCompanyId(), companyId)
                || !Objects.equals(warehouse.getAccountBookId(), accountBookId)) {
            throw new IllegalArgumentException("仓库不存在或已停用");
        }
        return warehouse;
    }

    private OrderTotals calculateTotals(List<SalesOrderLineRequest> lines) {
        SalesAmountCalculator.DocumentTotals totals = SalesAmountCalculator.DocumentTotals.zero();
        for (SalesOrderLineRequest line : lines) {
            totals = totals.add(SalesAmountCalculator.line(line.qty(), line.price(), line.taxRate()));
        }
        return new OrderTotals(totals.totalQuantity(), totals.totalAmount(), totals.totalTaxAmount());
    }

    private List<SalesOrderLineEntity> saveOrderLines(
            Long orderId, List<SalesOrderLineRequest> lineRequests, AuditMetadata audit, LocalDateTime now) {
        List<SalesOrderLineEntity> lines = new ArrayList<>();
        productValidator.requireProducts(
                lineRequests.stream().map(SalesOrderLineRequest::productId).toList(),
                audit.companyId(), audit.accountBookId());
        for (int i = 0; i < lineRequests.size(); i++) {
            SalesOrderLineRequest lineRequest = lineRequests.get(i);
            ProductAuxUnitConversion.ResolvedAuxUnit aux = ProductAuxUnitConversion.resolve(
                    lineRequest.qty(), lineRequest.auxQty(), lineRequest.auxUnitName(), lineRequest.conversionFactor());
            SalesAmountCalculator.LineAmounts amounts = SalesAmountCalculator.line(
                    aux.stockQty(), lineRequest.price(), lineRequest.taxRate());

            SalesOrderLineEntity line = new SalesOrderLineEntity();
            line.setCompanyId(audit.companyId());
            line.setAccountBookId(audit.accountBookId());
            line.setOrderId(orderId);
            line.setLineNo(i + 1);
            line.setProductId(lineRequest.productId());
            line.setQty(amounts.qty());
            line.setAuxQty(aux.auxQty());
            line.setAuxUnitName(aux.auxUnitName());
            line.setConversionFactor(aux.conversionFactor());
            line.setPrice(amounts.price());
            line.setTaxRate(amounts.taxRate());
            line.setAmount(amounts.amount());
            line.setTaxAmount(amounts.taxAmount());
            line.setDeliveredQty(BigDecimal.ZERO);
            line.setRemark(lineRequest.remark());
            line.setCreatedBy(audit.userId());
            line.setCreatedTime(now);
            line.setUpdatedBy(audit.userId());
            line.setUpdatedTime(now);
            line.setVersion(0);
            salesOrderLineMapper.insert(line);
            lines.add(line);
        }
        return lines;
    }

    private void fillCreateAudit(SalesOrderEntity entity, AuditMetadata audit, LocalDateTime now) {
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
    }

    private record OrderTotals(BigDecimal totalQuantity, BigDecimal totalAmount, BigDecimal totalTaxAmount) {
    }
}

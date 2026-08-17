package com.tuowei.erp.sales.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ProductAuxUnitConversion;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.stock.service.InventoryReservationCommand;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.sales.order.web.SalesOrderApproveRequest;
import com.tuowei.erp.sales.order.web.SalesOrderCreateRequest;
import com.tuowei.erp.sales.order.web.SalesOrderCreditPreviewRequest;
import com.tuowei.erp.sales.order.web.SalesOrderCreditPreviewResponse;
import com.tuowei.erp.sales.order.web.SalesOrderLineRequest;
import com.tuowei.erp.sales.order.web.SalesOrderPageQuery;
import com.tuowei.erp.sales.order.web.SalesOrderRejectRequest;
import com.tuowei.erp.sales.order.web.SalesOrderResponse;
import com.tuowei.erp.sales.order.web.SalesOrderSubmitRequest;
import com.tuowei.erp.sales.order.web.SalesOrderUpdateRequest;
import com.tuowei.erp.sales.support.SalesAmountCalculator;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.workflow.service.WorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class SalesOrderService {

    private final SalesOrderMapper salesOrderMapper;
    private final SalesOrderLineMapper salesOrderLineMapper;
    private final CustomerMapper customerMapper;
    private final ProductValidator productValidator;
    private final WarehouseMapper warehouseMapper;
    private final InventoryPostingService inventoryPostingService;
    private final SalesOrderNumberService salesOrderNumberService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SalesOrderQueryService salesOrderQueryService;
    private final WorkflowService workflowService;
    private final SalesCreditEvaluator salesCreditEvaluator;
    private final SalesPriceEvaluator salesPriceEvaluator;
    private final AttachmentService attachmentService;

    public SalesOrderService(
            SalesOrderMapper salesOrderMapper,
            SalesOrderLineMapper salesOrderLineMapper,
            CustomerMapper customerMapper,
            ProductValidator productValidator,
            WarehouseMapper warehouseMapper,
            InventoryPostingService inventoryPostingService,
            SalesOrderNumberService salesOrderNumberService,
            AuditMetadataFactory auditMetadataFactory,
            SalesOrderQueryService salesOrderQueryService,
            WorkflowService workflowService,
            SalesCreditEvaluator salesCreditEvaluator,
            SalesPriceEvaluator salesPriceEvaluator,
            AttachmentService attachmentService
    ) {
        this.salesOrderMapper = salesOrderMapper;
        this.salesOrderLineMapper = salesOrderLineMapper;
        this.customerMapper = customerMapper;
        this.productValidator = productValidator;
        this.warehouseMapper = warehouseMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.salesOrderNumberService = salesOrderNumberService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.salesOrderQueryService = salesOrderQueryService;
        this.workflowService = workflowService;
        this.salesCreditEvaluator = salesCreditEvaluator;
        this.salesPriceEvaluator = salesPriceEvaluator;
        this.attachmentService = attachmentService;
    }

    @Transactional
    public SalesOrderResponse create(SalesOrderCreateRequest request) {
        AuditMetadata audit = auditMetadataFactory.current();
        CustomerEntity customer = requireActiveCustomer(request.customerId(), audit.companyId(), audit.accountBookId());
        WarehouseEntity warehouse = requireActiveWarehouse(request.warehouseId(), audit.companyId(), audit.accountBookId());
        salesPriceEvaluator.assertLinesWithinMinPrice(
                audit.companyId(),
                audit.accountBookId(),
                customer.getId(),
                request.orderDate(),
                request.lines()
        );
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
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
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
                customer,
                totals.totalAmount().add(totals.totalTaxAmount())
        );
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

    @Transactional(readOnly = true)
    public SalesOrderResponse getById(Long id) {
        return salesOrderQueryService.getById(id);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesOrderResponse> list(SalesOrderPageQuery query) {
        SalesOrderPageQuery safeQuery = query == null ? new SalesOrderPageQuery() : query;
        return salesOrderQueryService.list(safeQuery);
    }

    @Transactional
    public SalesOrderResponse update(Long id, SalesOrderUpdateRequest request) {
        SalesOrderEntity entity = requireOrder(id);
        assertCanView(entity);
        if (!"DRAFT".equals(entity.getStatus()) && !"REJECTED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前销售订单状态不允许编辑");
        }

        AuditMetadata audit = auditMetadataFactory.current();
        CustomerEntity customer = requireActiveCustomer(request.customerId(), audit.companyId(), audit.accountBookId());
        WarehouseEntity warehouse = requireActiveWarehouse(request.warehouseId(), audit.companyId(), audit.accountBookId());
        salesPriceEvaluator.assertLinesWithinMinPrice(
                audit.companyId(),
                audit.accountBookId(),
                customer.getId(),
                request.orderDate(),
                request.lines()
        );
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
        OptimisticLockGuard.requireUpdated(salesOrderMapper.updateById(entity), "销售订单已被其他操作修改，请刷新后重试");

        salesOrderLineMapper.delete(new LambdaQueryWrapper<SalesOrderLineEntity>()
                .eq(SalesOrderLineEntity::getCompanyId, entity.getCompanyId())
                .eq(SalesOrderLineEntity::getAccountBookId, entity.getAccountBookId())
                .eq(SalesOrderLineEntity::getOrderId, entity.getId()));
        saveOrderLines(entity.getId(), request.lines(), audit, now);

        return salesOrderQueryService.getById(id);
    }

    @Transactional
    public SalesOrderResponse submit(Long id, SalesOrderSubmitRequest request) {
        SalesOrderEntity entity = requireOrder(id);
        assertCanView(entity);
        if (!"DRAFT".equals(entity.getStatus()) && !"REJECTED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前销售订单状态不允许提交审批");
        }
        attachmentService.requireIfConfigured(AttachmentBusinessType.SALES_ORDER, entity.getId());
        List<SalesOrderLineEntity> existingLines = salesOrderLineMapper.selectList(new LambdaQueryWrapper<SalesOrderLineEntity>()
                .eq(SalesOrderLineEntity::getCompanyId, entity.getCompanyId())
                .eq(SalesOrderLineEntity::getAccountBookId, entity.getAccountBookId())
                .eq(SalesOrderLineEntity::getOrderId, entity.getId())
                .orderByAsc(SalesOrderLineEntity::getLineNo));
        List<SalesOrderLineRequest> lineRequests = existingLines.stream()
                .map(line -> new SalesOrderLineRequest(
                        line.getProductId(),
                        line.getQty(),
                        line.getPrice(),
                        line.getTaxRate(),
                        line.getRemark()
                ))
                .toList();
        salesPriceEvaluator.assertLinesWithinMinPrice(
                entity.getCompanyId(),
                entity.getAccountBookId(),
                entity.getCustomerId(),
                entity.getOrderDate(),
                lineRequests
        );
        CustomerEntity customer = customerMapper.selectById(entity.getCustomerId());
        if (customer != null) {
            salesCreditEvaluator.assertWithinCreditLimit(customer, entity, "提交");
        }
        SalesOrderResponse response = transitionWorkflowStatus(entity, "SUBMITTED", "IN_APPROVAL");
        workflowService.submit("SALES_ORDER", entity.getId(), entity.getOrderNo(), "销售订单 " + entity.getOrderNo(), request.remark());
        return response;
    }

    @Transactional
    public SalesOrderResponse approve(Long id, SalesOrderApproveRequest request) {
        return approve(id, request, null);
    }

    @Transactional
    public SalesOrderResponse approveWorkflowTask(Long taskId, Long id, SalesOrderApproveRequest request) {
        return approve(id, request, taskId);
    }

    private SalesOrderResponse approve(Long id, SalesOrderApproveRequest request, Long workflowTaskId) {
        SalesOrderEntity entity = requireOrder(id);
        assertCanView(entity);
        if (!"SUBMITTED".equals(entity.getStatus()) || !"IN_APPROVAL".equals(entity.getApprovalStatus())) {
            throw new IllegalArgumentException("当前销售订单状态不允许审批通过");
        }
        CustomerEntity customer = customerMapper.selectById(entity.getCustomerId());
        if (customer != null) {
            salesCreditEvaluator.assertWithinCreditLimit(customer, entity, "审批");
        }
        reserveOrder(entity);
        SalesOrderResponse response = transitionWorkflowStatus(entity, "APPROVED", "APPROVED");
        if (workflowTaskId == null) {
            workflowService.approve("SALES_ORDER", entity.getId(), request.remark());
        } else {
            workflowService.approveTaskForBusiness(workflowTaskId, "SALES_ORDER", entity.getId(), request.remark());
        }
        return response;
    }

    @Transactional
    public SalesOrderResponse reject(Long id, SalesOrderRejectRequest request) {
        return reject(id, request, null);
    }

    @Transactional
    public SalesOrderResponse rejectWorkflowTask(Long taskId, Long id, SalesOrderRejectRequest request) {
        return reject(id, request, taskId);
    }

    private SalesOrderResponse reject(Long id, SalesOrderRejectRequest request, Long workflowTaskId) {
        SalesOrderEntity entity = requireOrder(id);
        assertCanView(entity);
        if (!"SUBMITTED".equals(entity.getStatus()) || !"IN_APPROVAL".equals(entity.getApprovalStatus())) {
            throw new IllegalArgumentException("当前销售订单状态不允许驳回");
        }
        SalesOrderResponse response = transitionWorkflowStatus(entity, "REJECTED", "REJECTED");
        if (workflowTaskId == null) {
            workflowService.reject("SALES_ORDER", entity.getId(), request.reason());
        } else {
            workflowService.rejectTaskForBusiness(workflowTaskId, "SALES_ORDER", entity.getId(), request.reason());
        }
        return response;
    }

    @Transactional
    public SalesOrderResponse unapprove(Long id) {
        SalesOrderEntity entity = requireOrder(id);
        assertCanView(entity);
        if (!"APPROVED".equals(entity.getStatus()) || !"APPROVED".equals(entity.getApprovalStatus())) {
            throw new IllegalArgumentException("当前销售订单状态不允许反审核");
        }
        if (!"NOT_DELIVERED".equals(entity.getDeliveryStatus())) {
            throw new IllegalArgumentException("已出库销售订单不允许反审核");
        }
        inventoryPostingService.releaseAllReservations("SALES_ORDER", entity.getId(), auditMetadataFactory.current());
        return transitionWorkflowStatus(entity, "DRAFT", "NOT_SUBMITTED");
    }

    @Transactional
    public SalesOrderResponse cancel(Long id) {
        SalesOrderEntity entity = requireOrder(id);
        assertCanView(entity);
        if (!"DRAFT".equals(entity.getStatus()) && !"REJECTED".equals(entity.getStatus())
                && !"SUBMITTED".equals(entity.getStatus()) && !"APPROVED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前销售订单状态不允许作废");
        }
        if ("APPROVED".equals(entity.getStatus()) && !"NOT_DELIVERED".equals(entity.getDeliveryStatus())) {
            throw new IllegalArgumentException("已出库销售订单不允许作废");
        }
        if ("APPROVED".equals(entity.getStatus())) {
            inventoryPostingService.releaseAllReservations("SALES_ORDER", entity.getId(), auditMetadataFactory.current());
        }
        SalesOrderResponse response = transitionWorkflowStatus(entity, "CANCELLED", "CANCELLED");
        workflowService.cancel("SALES_ORDER", entity.getId(), "作废销售订单");
        return response;
    }

    private SalesOrderEntity requireOrder(Long id) {
        SalesOrderEntity entity = salesOrderMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("销售订单不存在");
        }
        return entity;
    }

    private void assertCanView(SalesOrderEntity entity) {
        salesOrderQueryService.assertCanView(entity);
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
            Long orderId,
            List<SalesOrderLineRequest> lineRequests,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        List<SalesOrderLineEntity> lines = new ArrayList<>();
        productValidator.requireProducts(
                lineRequests.stream().map(SalesOrderLineRequest::productId).toList(),
                audit.companyId(), audit.accountBookId());
        for (int i = 0; i < lineRequests.size(); i++) {
            SalesOrderLineRequest lineRequest = lineRequests.get(i);
            ProductAuxUnitConversion.ResolvedAuxUnit aux = ProductAuxUnitConversion.resolve(
                    lineRequest.qty(),
                    lineRequest.auxQty(),
                    lineRequest.auxUnitName(),
                    lineRequest.conversionFactor()
            );
            SalesAmountCalculator.LineAmounts amounts = SalesAmountCalculator.line(
                    aux.stockQty(),
                    lineRequest.price(),
                    lineRequest.taxRate()
            );

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

    private void touch(SalesOrderEntity entity) {
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
    }

    private void reserveOrder(SalesOrderEntity entity) {
        AuditMetadata audit = auditMetadataFactory.current();
        List<SalesOrderLineEntity> lines = salesOrderLineMapper.selectList(new LambdaQueryWrapper<SalesOrderLineEntity>()
                .eq(SalesOrderLineEntity::getCompanyId, entity.getCompanyId())
                .eq(SalesOrderLineEntity::getAccountBookId, entity.getAccountBookId())
                .eq(SalesOrderLineEntity::getOrderId, entity.getId())
                .orderByAsc(SalesOrderLineEntity::getLineNo));
        for (SalesOrderLineEntity line : lines) {
            inventoryPostingService.reserve(
                    new InventoryReservationCommand(
                            entity.getWarehouseId(),
                            line.getProductId(),
                            "SALES_ORDER",
                            entity.getId(),
                            entity.getOrderNo(),
                            line.getId(),
                            line.getQty(),
                            line.getRemark()
                    ),
                    audit,
                    "库存可用量不足，不能审批销售订单"
            );
        }
    }

    private SalesOrderResponse transitionWorkflowStatus(
            SalesOrderEntity entity,
            String status,
            String approvalStatus
    ) {
        entity.setStatus(status);
        entity.setApprovalStatus(approvalStatus);
        touch(entity);
        OptimisticLockGuard.requireUpdated(salesOrderMapper.updateById(entity), "销售订单已被其他操作修改，请刷新后重试");
        return salesOrderQueryService.getById(entity.getId());
    }

    private record OrderTotals(BigDecimal totalQuantity, BigDecimal totalAmount, BigDecimal totalTaxAmount) {
    }
}

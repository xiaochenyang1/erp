package com.tuowei.erp.purchase.receipt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderLookupService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderReceiptStatusService;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptLineEntity;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptCreateRequest;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptLineRequest;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptPageQuery;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptResponse;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptUpdateRequest;
import com.tuowei.erp.purchase.support.AccumulatedQuantityValidator;
import com.tuowei.erp.purchase.support.PurchaseAmountCalculator;
import com.tuowei.erp.purchase.support.PurchaseReceiptQuantities;
import com.tuowei.erp.qc.inspection.service.QcInspectionGate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class PurchaseReceiptService {

    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final PurchaseReceiptLineMapper purchaseReceiptLineMapper;
    private final PurchaseOrderLineMapper purchaseOrderLineMapper;
    private final WarehouseMapper warehouseMapper;
    private final InventoryPostingService inventoryPostingService;
    private final InventorySerialNumberService inventorySerialNumberService;
    private final PurchaseOrderLookupService purchaseOrderLookupService;
    private final PurchaseOrderReceiptStatusService purchaseOrderReceiptStatusService;
    private final PurchaseReceiptNumberService purchaseReceiptNumberService;
    private final FinancePostingService financePostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final AccountPeriodGuard accountPeriodGuard;
    private final QcInspectionGate qcInspectionGate;
    private final ProductValidator productValidator;
    private final PurchaseReceiptQueryService purchaseReceiptQueryService;

    public PurchaseReceiptService(
            PurchaseReceiptMapper purchaseReceiptMapper,
            PurchaseReceiptLineMapper purchaseReceiptLineMapper,
            PurchaseOrderLineMapper purchaseOrderLineMapper,
            WarehouseMapper warehouseMapper,
            InventoryPostingService inventoryPostingService,
            InventorySerialNumberService inventorySerialNumberService,
            PurchaseOrderLookupService purchaseOrderLookupService,
            PurchaseOrderReceiptStatusService purchaseOrderReceiptStatusService,
            PurchaseReceiptNumberService purchaseReceiptNumberService,
            FinancePostingService financePostingService,
            AuditMetadataFactory auditMetadataFactory,
            AccountPeriodGuard accountPeriodGuard,
            QcInspectionGate qcInspectionGate,
            ProductValidator productValidator,
            PurchaseReceiptQueryService purchaseReceiptQueryService
    ) {
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.purchaseReceiptLineMapper = purchaseReceiptLineMapper;
        this.purchaseOrderLineMapper = purchaseOrderLineMapper;
        this.warehouseMapper = warehouseMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.inventorySerialNumberService = inventorySerialNumberService;
        this.purchaseOrderLookupService = purchaseOrderLookupService;
        this.purchaseOrderReceiptStatusService = purchaseOrderReceiptStatusService;
        this.purchaseReceiptNumberService = purchaseReceiptNumberService;
        this.financePostingService = financePostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.accountPeriodGuard = accountPeriodGuard;
        this.qcInspectionGate = qcInspectionGate;
        this.productValidator = productValidator;
        this.purchaseReceiptQueryService = purchaseReceiptQueryService;
    }

    @Transactional
    public PurchaseReceiptResponse create(PurchaseReceiptCreateRequest request) {
        PurchaseOrderEntity order = requireApprovedOrder(request.orderId());
        assertCanView(order);
        AuditMetadata audit = auditMetadataFactory.current();
        requireWarehouse(request.warehouseId(), audit.companyId(), audit.accountBookId());

        Map<Long, PurchaseOrderLineEntity> orderLines = purchaseOrderLookupService.loadOrderLinesAsMap(order);
        ReceiptTotals totals = calculateTotals(request.lines(), orderLines);
        LocalDateTime now = audit.now();

        PurchaseReceiptEntity receipt = new PurchaseReceiptEntity();
        receipt.setCompanyId(audit.companyId());
        receipt.setAccountBookId(audit.accountBookId());
        receipt.setReceiptNo(purchaseReceiptNumberService.nextReceiptNo(request.receiptDate()));
        receipt.setOrderId(request.orderId());
        receipt.setWarehouseId(request.warehouseId());
        receipt.setReceiptDate(request.receiptDate());
        receipt.setStatus("DRAFT");
        receipt.setTotalQuantity(totals.totalQuantity());
        receipt.setTotalAmount(totals.totalAmount());
        receipt.setTotalTaxAmount(totals.totalTaxAmount());
        receipt.setDeletedFlag(0);
        receipt.setRemark(request.remark());
        receipt.setCreatedBy(audit.userId());
        receipt.setCreatedTime(now);
        receipt.setUpdatedBy(audit.userId());
        receipt.setUpdatedTime(now);
        receipt.setVersion(0);
        assertCanView(receipt);
        purchaseReceiptMapper.insert(receipt);

        List<PurchaseReceiptLineEntity> receiptLines = saveReceiptLines(receipt.getId(), request.lines(), orderLines, audit, now);

        return purchaseReceiptQueryService.toResponse(receipt, receiptLines);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseReceiptResponse> list(PurchaseReceiptPageQuery query) {
        PurchaseReceiptPageQuery safeQuery = query == null ? new PurchaseReceiptPageQuery() : query;
        return purchaseReceiptQueryService.list(safeQuery);
    }

    public StreamingResponseBody exportReceipts(PurchaseReceiptPageQuery query) {
        return purchaseReceiptQueryService.exportReceipts(query);
    }

    @Transactional(readOnly = true)
    public PurchaseReceiptResponse getById(Long id) {
        return purchaseReceiptQueryService.getById(id);
    }

    @Transactional
    public PurchaseReceiptResponse update(Long id, PurchaseReceiptUpdateRequest request) {
        PurchaseReceiptEntity receipt = requireReceipt(id);
        assertCanView(receipt);
        if (!"DRAFT".equals(receipt.getStatus())) {
            throw new IllegalArgumentException("当前采购入库单状态不允许编辑");
        }

        PurchaseOrderEntity order = requireApprovedOrder(request.orderId());
        assertCanView(order);
        AuditMetadata audit = auditMetadataFactory.current();
        requireWarehouse(request.warehouseId(), audit.companyId(), audit.accountBookId());
        Map<Long, PurchaseOrderLineEntity> orderLines = purchaseOrderLookupService.loadOrderLinesAsMap(order);
        ReceiptTotals totals = calculateTotals(request.lines(), orderLines);
        LocalDateTime now = audit.now();

        receipt.setOrderId(request.orderId());
        receipt.setWarehouseId(request.warehouseId());
        receipt.setReceiptDate(request.receiptDate());
        receipt.setTotalQuantity(totals.totalQuantity());
        receipt.setTotalAmount(totals.totalAmount());
        receipt.setTotalTaxAmount(totals.totalTaxAmount());
        receipt.setRemark(request.remark());
        receipt.setUpdatedBy(audit.userId());
        receipt.setUpdatedTime(now);
        assertCanView(receipt);
        OptimisticLockGuard.requireUpdated(
                purchaseReceiptMapper.updateById(receipt),
                "采购入库单已被其他操作修改，请刷新后重试"
        );

        purchaseReceiptLineMapper.delete(new LambdaQueryWrapper<PurchaseReceiptLineEntity>()
                .eq(PurchaseReceiptLineEntity::getCompanyId, receipt.getCompanyId())
                .eq(PurchaseReceiptLineEntity::getAccountBookId, receipt.getAccountBookId())
                .eq(PurchaseReceiptLineEntity::getReceiptId, receipt.getId()));
        List<PurchaseReceiptLineEntity> receiptLines = saveReceiptLines(receipt.getId(), request.lines(), orderLines, audit, now);
        return purchaseReceiptQueryService.toResponse(receipt, receiptLines);
    }

    @Transactional
    public PurchaseReceiptResponse cancel(Long id) {
        PurchaseReceiptEntity receipt = requireReceipt(id);
        assertCanView(receipt);
        if (!"DRAFT".equals(receipt.getStatus())) {
            throw new IllegalArgumentException("当前采购入库单状态不允许作废");
        }
        receipt.setStatus("CANCELLED");
        touch(receipt);
        OptimisticLockGuard.requireUpdated(
                purchaseReceiptMapper.updateById(receipt),
                "采购入库单已被其他操作修改，请刷新后重试"
        );
        return getById(id);
    }

    @Transactional
    public PurchaseReceiptResponse post(Long id) {
        PurchaseReceiptEntity receipt = requireReceipt(id);
        assertCanView(receipt);
        if (!"DRAFT".equals(receipt.getStatus())) {
            throw new IllegalArgumentException("当前采购入库单状态不允许过账");
        }
        accountPeriodGuard.requireOpen(receipt.getReceiptDate(), "采购入库过账");

        PurchaseOrderEntity order = purchaseOrderLookupService.requireOrder(receipt.getOrderId());
        assertCanView(order);
        if (!"APPROVED".equals(order.getStatus())) {
            throw new IllegalArgumentException("采购订单未审批通过，不能执行入库过账");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        requireWarehouse(receipt.getWarehouseId(), audit.companyId(), audit.accountBookId());

        List<PurchaseReceiptLineEntity> receiptLines = purchaseReceiptLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseReceiptLineEntity>()
                        .eq(PurchaseReceiptLineEntity::getCompanyId, receipt.getCompanyId())
                        .eq(PurchaseReceiptLineEntity::getAccountBookId, receipt.getAccountBookId())
                        .eq(PurchaseReceiptLineEntity::getReceiptId, receipt.getId())
                        .orderByAsc(PurchaseReceiptLineEntity::getLineNo)
        );
        Map<Long, PurchaseOrderLineEntity> orderLines = purchaseOrderLookupService.loadOrderLinesAsMap(order);
        LocalDateTime now = audit.now();
        AccumulatedQuantityValidator quantityValidator = new AccumulatedQuantityValidator("入库数量超过采购订单剩余可入库数量");

        productValidator.requireProducts(
                receiptLines.stream().map(PurchaseReceiptLineEntity::getProductId).toList(),
                audit.companyId(), audit.accountBookId());

        for (PurchaseReceiptLineEntity receiptLine : receiptLines) {
            var product = productValidator.requireProduct(receiptLine.getProductId(), audit.companyId(), audit.accountBookId());
            if (product.getLotControlled() != null && product.getLotControlled() == 1
                    && (receiptLine.getLotNo() == null || receiptLine.getLotNo().isBlank())) {
                throw new IllegalArgumentException("商品启用批次管理，入库行必须填写批号");
            }
        }

        for (PurchaseReceiptLineEntity receiptLine : receiptLines) {
            PurchaseOrderLineEntity orderLine = requireOrderLine(orderLines, receiptLine.getOrderLineId());

            BigDecimal remainingQty = availableReceiptQty(orderLine);
            BigDecimal requestQty = ScalePrecision.quantity(receiptLine.getQty());
            quantityValidator.ensureWithinLimit(orderLine.getId(), requestQty, remainingQty);
        }

        // 来料质检闸门:需检验商品必须存在已判定检验单，且入库数量=合格数量，否则拦截过账。
        qcInspectionGate.assertReceiptInspected(receipt, receiptLines, audit);

        receipt.setStatus("POSTED");
        receipt.setUpdatedBy(audit.userId());
        receipt.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                purchaseReceiptMapper.updateById(receipt),
                "采购入库单已被其他操作修改，请刷新后重试"
        );

        for (PurchaseReceiptLineEntity receiptLine : receiptLines) {
            PurchaseOrderLineEntity orderLine = requireOrderLine(orderLines, receiptLine.getOrderLineId());
            PurchaseReceiptQuantities.OrderLineQuantities quantities = PurchaseReceiptQuantities.from(
                    orderLine.getQty(),
                    orderLine.getReceivedQty()
            );
            orderLine.setReceivedQty(quantities.receivedQtyAfter(receiptLine.getQty()));
            orderLine.setUpdatedBy(audit.userId());
            orderLine.setUpdatedTime(now);
            OptimisticLockGuard.requireUpdated(
                    purchaseOrderLineMapper.updateById(orderLine),
                    "采购订单明细已被其他操作修改，请刷新后重试"
            );

            inventoryPostingService.postInbound(
                    new InventoryPostingCommand(
                            receipt.getWarehouseId(),
                            receiptLine.getProductId(),
                            "PURCHASE_RECEIPT",
                            receipt.getReceiptNo(),
                            receiptLine.getId(),
                            receiptLine.getQty(),
                            receiptLine.getAmount(),
                            receiptLine.getRemark(),
                            receipt.getReceiptDate(),
                            receiptLine.getLotNo(),
                            receiptLine.getProductionDate(),
                            receiptLine.getExpiryDate(),
                            receiptLine.getLocationId()
                    ),
                    audit
            );
            inventorySerialNumberService.registerInboundSerials(
                    receiptLine.getProductId(),
                    receipt.getWarehouseId(),
                    receiptLine.getLocationId(),
                    receiptLine.getSerialNos(),
                    "PURCHASE_RECEIPT",
                    receipt.getReceiptNo(),
                    receiptLine.getQty(),
                    audit
            );
        }

        purchaseOrderReceiptStatusService.refreshReceiptStatus(order.getId(), audit, now);
        financePostingService.recordPurchaseReceipt(receipt, order, audit);

        return getById(id);
    }

    private PurchaseOrderEntity requireApprovedOrder(Long id) {
        PurchaseOrderEntity entity = purchaseOrderLookupService.requireOrder(id);
        if (!"APPROVED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("采购订单未审批通过，不能创建采购入库单");
        }
        return entity;
    }

    private WarehouseEntity requireWarehouse(Long id, Long companyId, Long accountBookId) {
        WarehouseEntity entity = warehouseMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(entity.getStatus())
                || !Objects.equals(entity.getCompanyId(), companyId)
                || !Objects.equals(entity.getAccountBookId(), accountBookId)) {
            throw new IllegalArgumentException("仓库不存在或已停用");
        }
        return entity;
    }

    private PurchaseReceiptEntity requireReceipt(Long id) {
        PurchaseReceiptEntity entity = purchaseReceiptMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("采购入库单不存在");
        }
        return entity;
    }

    private List<PurchaseReceiptLineEntity> saveReceiptLines(
            Long receiptId,
            List<PurchaseReceiptLineRequest> lineRequests,
            Map<Long, PurchaseOrderLineEntity> orderLines,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        List<PurchaseReceiptLineEntity> receiptLines = new ArrayList<>();
        for (int i = 0; i < lineRequests.size(); i++) {
            PurchaseReceiptLineRequest lineRequest = lineRequests.get(i);
            PurchaseOrderLineEntity orderLine = requireOrderLine(orderLines, lineRequest.orderLineId());
            PurchaseReceiptLineEntity receiptLine = new PurchaseReceiptLineEntity();
            receiptLine.setCompanyId(audit.companyId());
            receiptLine.setAccountBookId(audit.accountBookId());
            receiptLine.setReceiptId(receiptId);
            receiptLine.setLineNo(i + 1);
            receiptLine.setOrderLineId(orderLine.getId());
            receiptLine.setProductId(orderLine.getProductId());
            PurchaseAmountCalculator.LineAmounts amounts = PurchaseAmountCalculator.line(
                    lineRequest.qty(),
                    orderLine.getPrice(),
                    orderLine.getTaxRate()
            );
            receiptLine.setQty(amounts.qty());
            receiptLine.setPrice(amounts.price());
            receiptLine.setTaxRate(amounts.taxRate());
            receiptLine.setAmount(amounts.amount());
            receiptLine.setTaxAmount(amounts.taxAmount());
            receiptLine.setLotNo(lineRequest.lotNo());
            receiptLine.setProductionDate(lineRequest.productionDate());
            receiptLine.setExpiryDate(lineRequest.expiryDate());
            receiptLine.setLocationId(lineRequest.locationId());
            receiptLine.setSerialNos(lineRequest.serialNos());
            receiptLine.setRemark(lineRequest.remark());
            receiptLine.setCreatedBy(audit.userId());
            receiptLine.setCreatedTime(now);
            receiptLine.setUpdatedBy(audit.userId());
            receiptLine.setUpdatedTime(now);
            receiptLine.setVersion(0);
            purchaseReceiptLineMapper.insert(receiptLine);
            receiptLines.add(receiptLine);
        }
        return receiptLines;
    }

    private PurchaseOrderLineEntity requireOrderLine(Map<Long, PurchaseOrderLineEntity> orderLines, Long orderLineId) {
        return purchaseOrderLookupService.requireOrderLine(orderLines, orderLineId);
    }

    private ReceiptTotals calculateTotals(
            List<PurchaseReceiptLineRequest> lines,
            Map<Long, PurchaseOrderLineEntity> orderLines
    ) {
        PurchaseAmountCalculator.DocumentTotals totals = PurchaseAmountCalculator.DocumentTotals.zero();
        AccumulatedQuantityValidator quantityValidator = new AccumulatedQuantityValidator("入库数量超过采购订单剩余可入库数量");

        for (PurchaseReceiptLineRequest line : lines) {
            PurchaseOrderLineEntity orderLine = requireOrderLine(orderLines, line.orderLineId());
            PurchaseAmountCalculator.LineAmounts amounts = PurchaseAmountCalculator.line(
                    line.qty(),
                    orderLine.getPrice(),
                    orderLine.getTaxRate()
            );
            BigDecimal qty = amounts.qty();
            BigDecimal remainingQty = availableReceiptQty(orderLine);
            quantityValidator.ensureWithinLimit(orderLine.getId(), qty, remainingQty);
            totals = totals.add(amounts);
        }

        return new ReceiptTotals(
                totals.totalQuantity(),
                totals.totalAmount(),
                totals.totalTaxAmount()
        );
    }

    private void assertCanView(PurchaseReceiptEntity receipt) {
        purchaseReceiptQueryService.assertCanView(receipt);
    }

    private void assertCanView(PurchaseOrderEntity order) {
        purchaseReceiptQueryService.assertCanView(order);
    }

    private BigDecimal availableReceiptQty(PurchaseOrderLineEntity orderLine) {
        return PurchaseReceiptQuantities.from(orderLine.getQty(), orderLine.getReceivedQty()).availableReceiptQty();
    }

    private void touch(PurchaseReceiptEntity receipt) {
        AuditMetadata audit = auditMetadataFactory.current();
        receipt.setUpdatedBy(audit.userId());
        receipt.setUpdatedTime(audit.now());
    }

    private record ReceiptTotals(BigDecimal totalQuantity, BigDecimal totalAmount, BigDecimal totalTaxAmount) {
    }
}

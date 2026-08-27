package com.tuowei.erp.purchase.receipt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderLookupService;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptLineEntity;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptCreateRequest;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptLineRequest;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptResponse;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptUpdateRequest;
import com.tuowei.erp.purchase.support.AccumulatedQuantityValidator;
import com.tuowei.erp.purchase.support.PurchaseAmountCalculator;
import com.tuowei.erp.purchase.support.PurchaseReceiptQuantities;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Creation, editing and cancellation commands for purchase receipts. */
@Service
public class PurchaseReceiptCommandService {

    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final PurchaseReceiptLineMapper purchaseReceiptLineMapper;
    private final WarehouseMapper warehouseMapper;
    private final PurchaseOrderLookupService purchaseOrderLookupService;
    private final PurchaseReceiptNumberService purchaseReceiptNumberService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final PurchaseReceiptQueryService purchaseReceiptQueryService;

    public PurchaseReceiptCommandService(
            PurchaseReceiptMapper purchaseReceiptMapper,
            PurchaseReceiptLineMapper purchaseReceiptLineMapper,
            WarehouseMapper warehouseMapper,
            PurchaseOrderLookupService purchaseOrderLookupService,
            PurchaseReceiptNumberService purchaseReceiptNumberService,
            AuditMetadataFactory auditMetadataFactory,
            PurchaseReceiptQueryService purchaseReceiptQueryService
    ) {
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.purchaseReceiptLineMapper = purchaseReceiptLineMapper;
        this.warehouseMapper = warehouseMapper;
        this.purchaseOrderLookupService = purchaseOrderLookupService;
        this.purchaseReceiptNumberService = purchaseReceiptNumberService;
        this.auditMetadataFactory = auditMetadataFactory;
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
        receipt.setCompanyId(audit.companyId()); receipt.setAccountBookId(audit.accountBookId());
        receipt.setReceiptNo(purchaseReceiptNumberService.nextReceiptNo(request.receiptDate()));
        receipt.setOrderId(request.orderId()); receipt.setWarehouseId(request.warehouseId());
        receipt.setReceiptDate(request.receiptDate()); receipt.setStatus("DRAFT");
        receipt.setTotalQuantity(totals.totalQuantity()); receipt.setTotalAmount(totals.totalAmount());
        receipt.setTotalTaxAmount(totals.totalTaxAmount()); receipt.setDeletedFlag(0); receipt.setRemark(request.remark());
        receipt.setCreatedBy(audit.userId()); receipt.setCreatedTime(now); receipt.setUpdatedBy(audit.userId());
        receipt.setUpdatedTime(now); receipt.setVersion(0);
        assertCanView(receipt);
        purchaseReceiptMapper.insert(receipt);
        List<PurchaseReceiptLineEntity> receiptLines = saveReceiptLines(receipt.getId(), request.lines(), orderLines, audit, now);
        return purchaseReceiptQueryService.toResponse(receipt, receiptLines);
    }

    @Transactional
    public PurchaseReceiptResponse update(Long id, PurchaseReceiptUpdateRequest request) {
        PurchaseReceiptEntity receipt = requireReceipt(id);
        assertCanView(receipt);
        if (!"DRAFT".equals(receipt.getStatus())) throw new IllegalArgumentException("当前采购入库单状态不允许编辑");
        PurchaseOrderEntity order = requireApprovedOrder(request.orderId());
        assertCanView(order);
        AuditMetadata audit = auditMetadataFactory.current();
        requireWarehouse(request.warehouseId(), audit.companyId(), audit.accountBookId());
        Map<Long, PurchaseOrderLineEntity> orderLines = purchaseOrderLookupService.loadOrderLinesAsMap(order);
        ReceiptTotals totals = calculateTotals(request.lines(), orderLines);
        LocalDateTime now = audit.now();
        receipt.setOrderId(request.orderId()); receipt.setWarehouseId(request.warehouseId());
        receipt.setReceiptDate(request.receiptDate()); receipt.setTotalQuantity(totals.totalQuantity());
        receipt.setTotalAmount(totals.totalAmount()); receipt.setTotalTaxAmount(totals.totalTaxAmount());
        receipt.setRemark(request.remark()); receipt.setUpdatedBy(audit.userId()); receipt.setUpdatedTime(now);
        assertCanView(receipt);
        OptimisticLockGuard.requireUpdated(purchaseReceiptMapper.updateById(receipt), "采购入库单已被其他操作修改，请刷新后重试");
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
        if (!"DRAFT".equals(receipt.getStatus())) throw new IllegalArgumentException("当前采购入库单状态不允许作废");
        receipt.setStatus("CANCELLED");
        touch(receipt);
        OptimisticLockGuard.requireUpdated(purchaseReceiptMapper.updateById(receipt), "采购入库单已被其他操作修改，请刷新后重试");
        return purchaseReceiptQueryService.getById(id);
    }

    private PurchaseOrderEntity requireApprovedOrder(Long id) {
        PurchaseOrderEntity entity = purchaseOrderLookupService.requireOrder(id);
        if (!"APPROVED".equals(entity.getStatus())) throw new IllegalArgumentException("采购订单未审批通过，不能创建采购入库单");
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

    private List<PurchaseReceiptLineEntity> saveReceiptLines(Long receiptId, List<PurchaseReceiptLineRequest> lineRequests,
                                                              Map<Long, PurchaseOrderLineEntity> orderLines,
                                                              AuditMetadata audit, LocalDateTime now) {
        List<PurchaseReceiptLineEntity> receiptLines = new ArrayList<>();
        for (int i = 0; i < lineRequests.size(); i++) {
            PurchaseReceiptLineRequest request = lineRequests.get(i);
            PurchaseOrderLineEntity orderLine = requireOrderLine(orderLines, request.orderLineId());
            PurchaseReceiptLineEntity line = new PurchaseReceiptLineEntity();
            line.setCompanyId(audit.companyId()); line.setAccountBookId(audit.accountBookId());
            line.setReceiptId(receiptId); line.setLineNo(i + 1); line.setOrderLineId(orderLine.getId());
            line.setProductId(orderLine.getProductId());
            PurchaseAmountCalculator.LineAmounts amounts = PurchaseAmountCalculator.line(request.qty(), orderLine.getPrice(), orderLine.getTaxRate());
            line.setQty(amounts.qty()); line.setPrice(amounts.price()); line.setTaxRate(amounts.taxRate());
            line.setAmount(amounts.amount()); line.setTaxAmount(amounts.taxAmount());
            line.setLotNo(request.lotNo()); line.setProductionDate(request.productionDate()); line.setExpiryDate(request.expiryDate());
            line.setLocationId(request.locationId()); line.setSerialNos(request.serialNos()); line.setRemark(request.remark());
            line.setCreatedBy(audit.userId()); line.setCreatedTime(now); line.setUpdatedBy(audit.userId()); line.setUpdatedTime(now); line.setVersion(0);
            purchaseReceiptLineMapper.insert(line); receiptLines.add(line);
        }
        return receiptLines;
    }

    private PurchaseOrderLineEntity requireOrderLine(Map<Long, PurchaseOrderLineEntity> orderLines, Long orderLineId) {
        return purchaseOrderLookupService.requireOrderLine(orderLines, orderLineId);
    }

    private ReceiptTotals calculateTotals(List<PurchaseReceiptLineRequest> lines, Map<Long, PurchaseOrderLineEntity> orderLines) {
        PurchaseAmountCalculator.DocumentTotals totals = PurchaseAmountCalculator.DocumentTotals.zero();
        AccumulatedQuantityValidator validator = new AccumulatedQuantityValidator("入库数量超过采购订单剩余可入库数量");
        for (PurchaseReceiptLineRequest line : lines) {
            PurchaseOrderLineEntity orderLine = requireOrderLine(orderLines, line.orderLineId());
            PurchaseAmountCalculator.LineAmounts amounts = PurchaseAmountCalculator.line(line.qty(), orderLine.getPrice(), orderLine.getTaxRate());
            validator.ensureWithinLimit(orderLine.getId(), amounts.qty(), availableReceiptQty(orderLine));
            totals = totals.add(amounts);
        }
        return new ReceiptTotals(totals.totalQuantity(), totals.totalAmount(), totals.totalTaxAmount());
    }

    private BigDecimal availableReceiptQty(PurchaseOrderLineEntity orderLine) {
        return PurchaseReceiptQuantities.from(orderLine.getQty(), orderLine.getReceivedQty()).availableReceiptQty();
    }

    private void touch(PurchaseReceiptEntity receipt) {
        AuditMetadata audit = auditMetadataFactory.current();
        receipt.setUpdatedBy(audit.userId()); receipt.setUpdatedTime(audit.now());
    }

    private void assertCanView(PurchaseReceiptEntity receipt) { purchaseReceiptQueryService.assertCanView(receipt); }
    private void assertCanView(PurchaseOrderEntity order) { purchaseReceiptQueryService.assertCanView(order); }

    private record ReceiptTotals(BigDecimal totalQuantity, BigDecimal totalAmount, BigDecimal totalTaxAmount) { }
}

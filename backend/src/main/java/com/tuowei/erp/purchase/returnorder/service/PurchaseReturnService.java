package com.tuowei.erp.purchase.returnorder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderLookupService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderReceiptStatusService;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptLineEntity;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnLineMapper;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnLineEntity;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnCreateRequest;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnLineRequest;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnPageQuery;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnResponse;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnUpdateRequest;
import com.tuowei.erp.purchase.support.AccumulatedQuantityValidator;
import com.tuowei.erp.purchase.support.PurchaseAmountCalculator;
import com.tuowei.erp.purchase.support.PurchaseReturnLineViewData;
import com.tuowei.erp.purchase.support.PurchaseReturnQuantities;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PurchaseReturnService {

    private final PurchaseReturnMapper purchaseReturnMapper;
    private final PurchaseReturnLineMapper purchaseReturnLineMapper;
    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final PurchaseReceiptLineMapper purchaseReceiptLineMapper;
    private final PurchaseOrderLineMapper purchaseOrderLineMapper;
    private final ProductValidator productValidator;
    private final InventoryPostingService inventoryPostingService;
    private final InventorySerialNumberService inventorySerialNumberService;
    private final PurchaseOrderLookupService purchaseOrderLookupService;
    private final PurchaseOrderReceiptStatusService purchaseOrderReceiptStatusService;
    private final PurchaseReturnNumberService purchaseReturnNumberService;
    private final FinancePostingService financePostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final PurchaseReturnQueryService purchaseReturnQueryService;
    private final AccountPeriodGuard accountPeriodGuard;

    public PurchaseReturnService(PurchaseReturnMapper purchaseReturnMapper, PurchaseReturnLineMapper purchaseReturnLineMapper,
                                 PurchaseReceiptMapper purchaseReceiptMapper, PurchaseReceiptLineMapper purchaseReceiptLineMapper,
                                 PurchaseOrderLineMapper purchaseOrderLineMapper,
                                 ProductValidator productValidator,
                                 InventoryPostingService inventoryPostingService,
                                 InventorySerialNumberService inventorySerialNumberService,
                                 PurchaseOrderLookupService purchaseOrderLookupService,
                                 PurchaseOrderReceiptStatusService purchaseOrderReceiptStatusService,
                                 PurchaseReturnNumberService purchaseReturnNumberService,
                                 FinancePostingService financePostingService,
                                 AuditMetadataFactory auditMetadataFactory,
                                 PurchaseReturnQueryService purchaseReturnQueryService,
                                 AccountPeriodGuard accountPeriodGuard) {
        this.purchaseReturnMapper = purchaseReturnMapper;
        this.purchaseReturnLineMapper = purchaseReturnLineMapper;
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.purchaseReceiptLineMapper = purchaseReceiptLineMapper;
        this.purchaseOrderLineMapper = purchaseOrderLineMapper;
        this.productValidator = productValidator;
        this.inventoryPostingService = inventoryPostingService;
        this.inventorySerialNumberService = inventorySerialNumberService;
        this.purchaseOrderLookupService = purchaseOrderLookupService;
        this.purchaseOrderReceiptStatusService = purchaseOrderReceiptStatusService;
        this.purchaseReturnNumberService = purchaseReturnNumberService;
        this.financePostingService = financePostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.purchaseReturnQueryService = purchaseReturnQueryService;
        this.accountPeriodGuard = accountPeriodGuard;
    }

    @Transactional
    public PurchaseReturnResponse create(PurchaseReturnCreateRequest request) {
        PurchaseReceiptEntity receipt = requirePostedReceipt(request.receiptId());
        assertCanView(receipt);
        Map<Long, PurchaseReceiptLineEntity> receiptLines = loadReceiptLines(receipt);
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        PurchaseReturnQueryService.ReceiptContext context = purchaseReturnQueryService.loadContext(receipt);

        PurchaseReturnEntity entity = new PurchaseReturnEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setReturnNo(purchaseReturnNumberService.nextReturnNo(request.returnDate()));
        entity.setReceiptId(receipt.getId());
        entity.setWarehouseId(receipt.getWarehouseId());
        entity.setReturnDate(request.returnDate());
        entity.setStatus("DRAFT");
        entity.setDeletedFlag(0);
        entity.setRemark(request.remark());
        entity.setCreatedBy(audit.userId());
        entity.setCreatedTime(now);
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        entity.setVersion(0);
        assertCanView(entity);
        purchaseReturnMapper.insert(entity);

        List<PurchaseReturnLineEntity> lineEntities = saveLines(entity.getId(), request.lines(), receiptLines, audit, now);
        recalculateTotals(entity, lineEntities);
        return purchaseReturnQueryService.toResponse(entity, context, lineEntities);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseReturnResponse> list(PurchaseReturnPageQuery query) {
        PurchaseReturnPageQuery safeQuery = query == null ? new PurchaseReturnPageQuery() : query;
        return purchaseReturnQueryService.list(safeQuery);
    }

    public StreamingResponseBody exportReturns(PurchaseReturnPageQuery query) {
        return purchaseReturnQueryService.exportReturns(query);
    }

    @Transactional(readOnly = true)
    public PurchaseReturnResponse getById(Long id) {
        return purchaseReturnQueryService.getById(id);
    }

    @Transactional
    public PurchaseReturnResponse update(Long id, PurchaseReturnUpdateRequest request) {
        PurchaseReturnEntity entity = requireReturn(id);
        assertCanView(entity);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前采购退货单状态不允许编辑");
        }
        if (!entity.getReceiptId().equals(request.receiptId())) {
            throw new IllegalArgumentException("采购退货单不允许变更来源采购入库单");
        }

        PurchaseReceiptEntity receipt = requirePostedReceipt(
                entity.getReceiptId(),
                entity.getCompanyId(),
                entity.getAccountBookId()
        );
        assertCanView(receipt);
        Map<Long, PurchaseReceiptLineEntity> receiptLines = loadReceiptLines(receipt);
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();

        entity.setReturnDate(request.returnDate());
        entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                purchaseReturnMapper.updateById(entity),
                "采购退货单已被其他操作修改，请刷新后重试"
        );

        purchaseReturnLineMapper.delete(new LambdaQueryWrapper<PurchaseReturnLineEntity>()
                .eq(PurchaseReturnLineEntity::getCompanyId, entity.getCompanyId())
                .eq(PurchaseReturnLineEntity::getAccountBookId, entity.getAccountBookId())
                .eq(PurchaseReturnLineEntity::getReturnId, entity.getId()));
        List<PurchaseReturnLineEntity> lineEntities = saveLines(entity.getId(), request.lines(), receiptLines, audit, now);
        recalculateTotals(entity, lineEntities);
        return purchaseReturnQueryService.toResponse(entity, receipt, lineEntities);
    }

    @Transactional
    public PurchaseReturnResponse cancel(Long id) {
        PurchaseReturnEntity entity = requireReturn(id);
        assertCanView(entity);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前采购退货单状态不允许作废");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setStatus("CANCELLED");
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(
                purchaseReturnMapper.updateById(entity),
                "采购退货单已被其他操作修改，请刷新后重试"
        );
        return getById(id);
    }

    @Transactional
    public PurchaseReturnResponse post(Long id) {
        PurchaseReturnEntity entity = requireReturn(id);
        assertCanView(entity);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前采购退货单状态不允许过账");
        }
        accountPeriodGuard.requireOpen(entity.getReturnDate(), "采购退货过账");

        PurchaseReceiptEntity receipt = requirePostedReceipt(
                entity.getReceiptId(),
                entity.getCompanyId(),
                entity.getAccountBookId()
        );
        assertCanView(receipt);
        PurchaseOrderEntity order = purchaseOrderLookupService.requireOrder(receipt.getOrderId());
        assertCanView(order);
        List<PurchaseReturnLineEntity> returnLines = purchaseReturnLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseReturnLineEntity>()
                        .eq(PurchaseReturnLineEntity::getCompanyId, entity.getCompanyId())
                        .eq(PurchaseReturnLineEntity::getAccountBookId, entity.getAccountBookId())
                        .eq(PurchaseReturnLineEntity::getReturnId, id)
                        .orderByAsc(PurchaseReturnLineEntity::getLineNo)
        );
        Map<Long, PurchaseReceiptLineEntity> receiptLines = loadReceiptLines(receipt);
        Map<Long, PurchaseOrderLineEntity> orderLines = purchaseOrderLookupService.loadOrderLinesAsMap(order);
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        AccumulatedQuantityValidator receiptLineQtyValidator = new AccumulatedQuantityValidator("退货数量超过采购入库明细剩余可退数量");
        AccumulatedQuantityValidator inventoryQtyValidator = new AccumulatedQuantityValidator("库存不足，不能执行采购退货");

        for (PurchaseReturnLineEntity returnLine : returnLines) {
            PurchaseReceiptLineEntity receiptLine = requireReceiptLine(receiptLines, returnLine.getReceiptLineId());
            BigDecimal returnQty = ScalePrecision.quantity(returnLine.getQty());
            receiptLineQtyValidator.ensureWithinLimit(receiptLine.getId(), returnQty, availableQty(receiptLine));
            inventoryQtyValidator.ensureWithinLimit(
                    returnLine.getProductId(),
                    returnQty,
                    productId -> inventoryPostingService.getQtyAvailable(
                            entity.getWarehouseId(),
                            productId,
                            audit.companyId(),
                            audit.accountBookId()
                    )
            );
        }

        entity.setStatus("POSTED");
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                purchaseReturnMapper.updateById(entity),
                "采购退货单已被其他操作修改，请刷新后重试"
        );

        for (PurchaseReturnLineEntity returnLine : returnLines) {
            PurchaseReceiptLineEntity receiptLine = requireReceiptLine(receiptLines, returnLine.getReceiptLineId());
            BigDecimal newReturnedQty = ScalePrecision.quantity(ScalePrecision.zeroDefault(receiptLine.getReturnedQty()).add(ScalePrecision.quantity(returnLine.getQty())));
            receiptLine.setReturnedQty(newReturnedQty);
            receiptLine.setUpdatedBy(audit.userId());
            receiptLine.setUpdatedTime(now);
            OptimisticLockGuard.requireUpdated(
                    purchaseReceiptLineMapper.updateById(receiptLine),
                    "采购入库明细已被其他操作修改，请刷新后重试"
            );

            PurchaseOrderLineEntity orderLine = requireOrderLine(orderLines, returnLine.getOrderLineId());
            BigDecimal newReceivedQty = ScalePrecision.quantity(ScalePrecision.zeroDefault(orderLine.getReceivedQty()).subtract(ScalePrecision.quantity(returnLine.getQty())));
            orderLine.setReceivedQty(newReceivedQty);
            orderLine.setUpdatedBy(audit.userId());
            orderLine.setUpdatedTime(now);
            OptimisticLockGuard.requireUpdated(
                    purchaseOrderLineMapper.updateById(orderLine),
                    "采购订单明细已被其他操作修改，请刷新后重试"
            );

            inventoryPostingService.postOutbound(
                    new InventoryPostingCommand(
                            entity.getWarehouseId(),
                            returnLine.getProductId(),
                            "PURCHASE_RETURN",
                            entity.getReturnNo(),
                            returnLine.getId(),
                            returnLine.getQty(),
                            returnLine.getAmount(),
                            returnLine.getRemark(),
                            entity.getReturnDate(),
                            returnLine.getLotNo(),
                            returnLine.getProductionDate(),
                            returnLine.getExpiryDate(),
                            returnLine.getLocationId()
                    ),
                    audit,
                    "库存不足，不能执行采购退货"
            );
            inventorySerialNumberService.issueOutboundSerials(
                    returnLine.getProductId(),
                    returnLine.getSerialNos(),
                    "PURCHASE_RETURN",
                    entity.getReturnNo(),
                    returnLine.getQty(),
                    audit
            );
        }

        purchaseOrderReceiptStatusService.refreshReceiptStatus(receipt.getOrderId(), audit, now);
        financePostingService.recordPurchaseReturn(entity, order, audit);

        return getById(id);
    }

    private void recalculateTotals(PurchaseReturnEntity entity, List<PurchaseReturnLineEntity> lineEntities) {
        PurchaseAmountCalculator.DocumentTotals totals = PurchaseAmountCalculator.DocumentTotals.zero();
        for (PurchaseReturnLineEntity line : lineEntities) {
            totals = totals.add(line.getQty(), line.getAmount(), line.getTaxAmount());
        }
        entity.setTotalQuantity(totals.totalQuantity());
        entity.setTotalAmount(totals.totalAmount());
        entity.setTotalTaxAmount(totals.totalTaxAmount());
        OptimisticLockGuard.requireUpdated(
                purchaseReturnMapper.updateById(entity),
                "采购退货单已被其他操作修改，请刷新后重试"
        );
    }

    private List<PurchaseReturnLineEntity> saveLines(Long returnId, List<PurchaseReturnLineRequest> requests,
                                                     Map<Long, PurchaseReceiptLineEntity> receiptLines, AuditMetadata audit, LocalDateTime now) {
        List<PurchaseReturnLineEntity> lines = new java.util.ArrayList<>();
        AccumulatedQuantityValidator quantityValidator = new AccumulatedQuantityValidator("退货数量超过可退数量");
        List<Long> productIds = requests.stream()
                .map(r -> requireReceiptLine(receiptLines, r.receiptLineId()).getProductId())
                .toList();
        Map<Long, ProductEntity> productMap = productValidator.requireProducts(productIds, audit.companyId(), audit.accountBookId());
        for (int i = 0; i < requests.size(); i++) {
            PurchaseReturnLineRequest request = requests.get(i);
            PurchaseReceiptLineEntity receiptLine = requireReceiptLine(receiptLines, request.receiptLineId());
            BigDecimal qty = ScalePrecision.quantity(request.qty());
            quantityValidator.ensureWithinLimit(receiptLine.getId(), qty, availableQty(receiptLine));
            PurchaseReturnLineEntity line = new PurchaseReturnLineEntity();
            line.setCompanyId(audit.companyId());
            line.setAccountBookId(audit.accountBookId());
            line.setReturnId(returnId);
            line.setLineNo(i + 1);
            line.setReceiptLineId(receiptLine.getId());
            line.setOrderLineId(receiptLine.getOrderLineId());
            line.setProductId(receiptLine.getProductId());
            PurchaseAmountCalculator.LineAmounts amounts = PurchaseAmountCalculator.line(
                    qty,
                    receiptLine.getPrice(),
                    receiptLine.getTaxRate()
            );
            line.setQty(amounts.qty());
            line.setPrice(amounts.price());
            line.setTaxRate(amounts.taxRate());
            line.setAmount(amounts.amount());
            line.setTaxAmount(amounts.taxAmount());
            PurchaseReturnLineViewData.from(
                    receiptLine,
                    productMap.get(receiptLine.getProductId())
            ).applyTo(line);
            line.setLotNo(request.lotNo());
            line.setProductionDate(request.productionDate());
            line.setExpiryDate(request.expiryDate());
            line.setLocationId(request.locationId() != null ? request.locationId() : receiptLine.getLocationId());
            line.setSerialNos(request.serialNos());
            line.setRemark(request.remark());
            line.setCreatedBy(audit.userId());
            line.setCreatedTime(now);
            line.setUpdatedBy(audit.userId());
            line.setUpdatedTime(now);
            line.setVersion(0);
            purchaseReturnLineMapper.insert(line);
            lines.add(line);
        }
        return lines;
    }

    private PurchaseReceiptEntity requirePostedReceipt(Long id) {
        PurchaseReceiptEntity entity = purchaseReceiptMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0 || !"POSTED".equals(entity.getStatus())) {
            throw new IllegalArgumentException("采购入库单未过账，不能创建采购退货单");
        }
        return entity;
    }

    private PurchaseReceiptEntity requirePostedReceipt(Long id, Long companyId, Long accountBookId) {
        PurchaseReceiptEntity entity = requirePostedReceipt(id);
        if (!Objects.equals(entity.getCompanyId(), companyId)
                || !Objects.equals(entity.getAccountBookId(), accountBookId)) {
            throw new IllegalArgumentException("采购入库单未过账，不能创建采购退货单");
        }
        return entity;
    }

    private PurchaseReturnEntity requireReturn(Long id) {
        PurchaseReturnEntity entity = purchaseReturnMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("采购退货单不存在");
        }
        return entity;
    }

    private Map<Long, PurchaseReceiptLineEntity> loadReceiptLines(PurchaseReceiptEntity receipt) {
        return purchaseReceiptLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseReceiptLineEntity>()
                        .eq(PurchaseReceiptLineEntity::getCompanyId, receipt.getCompanyId())
                        .eq(PurchaseReceiptLineEntity::getAccountBookId, receipt.getAccountBookId())
                        .eq(PurchaseReceiptLineEntity::getReceiptId, receipt.getId())
        ).stream().collect(Collectors.toMap(PurchaseReceiptLineEntity::getId, Function.identity()));
    }

    private PurchaseReceiptLineEntity requireReceiptLine(Map<Long, PurchaseReceiptLineEntity> receiptLines, Long receiptLineId) {
        PurchaseReceiptLineEntity entity = receiptLines.get(receiptLineId);
        if (entity == null) {
            throw new IllegalArgumentException("采购入库单明细不存在");
        }
        return entity;
    }

    private PurchaseOrderLineEntity requireOrderLine(Map<Long, PurchaseOrderLineEntity> orderLines, Long orderLineId) {
        return purchaseOrderLookupService.requireOrderLine(orderLines, orderLineId);
    }

    private BigDecimal availableQty(PurchaseReceiptLineEntity receiptLine) {
        return PurchaseReturnQuantities.from(receiptLine.getQty(), receiptLine.getReturnedQty()).availableReturnQty();
    }

    private void assertCanView(PurchaseReturnEntity entity) {
        purchaseReturnQueryService.assertCanView(entity);
    }

    private void assertCanView(PurchaseReceiptEntity entity) {
        purchaseReturnQueryService.assertCanView(entity);
    }

    private void assertCanView(PurchaseOrderEntity entity) {
        purchaseReturnQueryService.assertCanView(entity);
    }
}

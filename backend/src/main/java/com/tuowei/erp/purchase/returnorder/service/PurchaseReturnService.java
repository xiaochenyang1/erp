package com.tuowei.erp.purchase.returnorder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.export.CsvExport;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
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
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnLineResponse;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnPageQuery;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnResponse;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnUpdateRequest;
import com.tuowei.erp.purchase.support.AccumulatedQuantityValidator;
import com.tuowei.erp.purchase.support.PurchaseAmountCalculator;
import com.tuowei.erp.purchase.support.PurchaseReturnLineViewData;
import com.tuowei.erp.purchase.support.PurchaseReturnQuantities;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PurchaseReturnService {

    private static final List<String> RETURN_EXPORT_HEADERS = List.of(
            "returnNo",
            "receiptId",
            "warehouseId",
            "returnDate",
            "status",
            "totalQuantity",
            "totalAmount",
            "totalTaxAmount",
            "remark"
    );

    private final PurchaseReturnMapper purchaseReturnMapper;
    private final PurchaseReturnLineMapper purchaseReturnLineMapper;
    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final PurchaseReceiptLineMapper purchaseReceiptLineMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderLineMapper purchaseOrderLineMapper;
    private final WarehouseMapper warehouseMapper;
    private final ProductMapper productMapper;
    private final ProductValidator productValidator;
    private final InventoryPostingService inventoryPostingService;
    private final InventorySerialNumberService inventorySerialNumberService;
    private final PurchaseOrderLookupService purchaseOrderLookupService;
    private final PurchaseOrderReceiptStatusService purchaseOrderReceiptStatusService;
    private final PurchaseReturnNumberService purchaseReturnNumberService;
    private final FinancePostingService financePostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final UserMapper userMapper;
    private final AccountPeriodGuard accountPeriodGuard;

    public PurchaseReturnService(PurchaseReturnMapper purchaseReturnMapper, PurchaseReturnLineMapper purchaseReturnLineMapper,
                                 PurchaseReceiptMapper purchaseReceiptMapper, PurchaseReceiptLineMapper purchaseReceiptLineMapper,
                                 PurchaseOrderMapper purchaseOrderMapper, PurchaseOrderLineMapper purchaseOrderLineMapper,
                                 WarehouseMapper warehouseMapper, ProductMapper productMapper,
                                 ProductValidator productValidator,
                                 InventoryPostingService inventoryPostingService,
                                 InventorySerialNumberService inventorySerialNumberService,
                                 PurchaseOrderLookupService purchaseOrderLookupService,
                                 PurchaseOrderReceiptStatusService purchaseOrderReceiptStatusService,
                                 PurchaseReturnNumberService purchaseReturnNumberService,
                                 FinancePostingService financePostingService,
                                 AuditMetadataFactory auditMetadataFactory,
                                 CurrentUserContext currentUserContext,
                                 DataScopeService dataScopeService,
                                 ScopedUserResolver scopedUserResolver,
                                 UserMapper userMapper,
                                 AccountPeriodGuard accountPeriodGuard) {
        this.purchaseReturnMapper = purchaseReturnMapper;
        this.purchaseReturnLineMapper = purchaseReturnLineMapper;
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.purchaseReceiptLineMapper = purchaseReceiptLineMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.purchaseOrderLineMapper = purchaseOrderLineMapper;
        this.warehouseMapper = warehouseMapper;
        this.productMapper = productMapper;
        this.productValidator = productValidator;
        this.inventoryPostingService = inventoryPostingService;
        this.inventorySerialNumberService = inventorySerialNumberService;
        this.purchaseOrderLookupService = purchaseOrderLookupService;
        this.purchaseOrderReceiptStatusService = purchaseOrderReceiptStatusService;
        this.purchaseReturnNumberService = purchaseReturnNumberService;
        this.financePostingService = financePostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.userMapper = userMapper;
        this.accountPeriodGuard = accountPeriodGuard;
    }

    @Transactional
    public PurchaseReturnResponse create(PurchaseReturnCreateRequest request) {
        PurchaseReceiptEntity receipt = requirePostedReceipt(request.receiptId());
        assertCanView(receipt);
        Map<Long, PurchaseReceiptLineEntity> receiptLines = loadReceiptLines(receipt);
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        ReceiptContext context = loadContext(receipt);

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
        return toResponse(entity, context, lineEntities);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseReturnResponse> list(PurchaseReturnPageQuery query) {
        PurchaseReturnPageQuery safeQuery = query == null ? new PurchaseReturnPageQuery() : query;
        long pageNo = normalizePageNo(safeQuery.getPageNo());
        long pageSize = normalizePageSize(safeQuery.getPageSize());
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String status = normalizeStatus(safeQuery.getStatus());
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);

        Page<PurchaseReturnEntity> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<PurchaseReturnEntity> wrapper = buildListQuery(keyword, safeQuery.getReceiptId(), safeQuery.getWarehouseId(), status, safeQuery.getReturnDateFrom(), safeQuery.getReturnDateTo());
        wrapper = dataScopeService.applyPurchaseReturnScope(
                wrapper,
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
        Page<PurchaseReturnEntity> result = purchaseReturnMapper.selectPage(
                page,
                wrapper
        );

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toSummaryResponse).toList()
        );
    }

    public StreamingResponseBody exportReturns(PurchaseReturnPageQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        PurchaseReturnPageQuery safeQuery = query == null ? new PurchaseReturnPageQuery() : query;
        return outputStream -> withAuthentication(authentication, () -> CsvExport.write(outputStream, RETURN_EXPORT_HEADERS, rowWriter -> {
            LambdaQueryWrapper<PurchaseReturnEntity> wrapper = scopedListQuery(safeQuery);
            for (PurchaseReturnEntity entity : purchaseReturnMapper.selectList(wrapper)) {
                rowWriter.write(returnExportRow(entity));
            }
        }));
    }

    @Transactional(readOnly = true)
    public PurchaseReturnResponse getById(Long id) {
        PurchaseReturnEntity entity = requireReturn(id);
        assertCanView(entity);
        ReceiptContext context = loadContext(requirePostedReceipt(
                entity.getReceiptId(),
                entity.getCompanyId(),
                entity.getAccountBookId()
        ));
        List<PurchaseReturnLineEntity> lines = purchaseReturnLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseReturnLineEntity>()
                        .eq(PurchaseReturnLineEntity::getCompanyId, entity.getCompanyId())
                        .eq(PurchaseReturnLineEntity::getAccountBookId, entity.getAccountBookId())
                        .eq(PurchaseReturnLineEntity::getReturnId, id)
                        .orderByAsc(PurchaseReturnLineEntity::getLineNo)
        ).stream().map(this::enrichLine).toList();
        return toResponse(entity, context, lines);
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
        return toResponse(entity, loadContext(receipt), lineEntities);
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

    private ReceiptContext loadContext(PurchaseReceiptEntity receipt) {
        PurchaseOrderEntity order = purchaseOrderMapper.selectById(receipt.getOrderId());
        WarehouseEntity warehouse = warehouseMapper.selectById(receipt.getWarehouseId());
        return new ReceiptContext(receipt.getReceiptNo(), order == null ? null : order.getOrderNo(), warehouse == null ? null : warehouse.getWarehouseName());
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

    private PurchaseReturnResponse toResponse(PurchaseReturnEntity entity, ReceiptContext context, List<PurchaseReturnLineEntity> lines) {
        return new PurchaseReturnResponse(entity.getId(), entity.getReturnNo(), entity.getReceiptId(), context.receiptNo(),
                context.orderNo(), entity.getWarehouseId(), context.warehouseName(), entity.getReturnDate(),
                entity.getStatus(), entity.getTotalQuantity(), entity.getTotalAmount(), entity.getTotalTaxAmount(),
                entity.getRemark(), lines.stream().map(this::toLineResponse).toList());
    }

    private PurchaseReturnResponse toSummaryResponse(PurchaseReturnEntity entity) {
        ReceiptContext context = loadContext(requirePostedReceipt(
                entity.getReceiptId(),
                entity.getCompanyId(),
                entity.getAccountBookId()
        ));
        return new PurchaseReturnResponse(
                entity.getId(),
                entity.getReturnNo(),
                entity.getReceiptId(),
                context.receiptNo(),
                context.orderNo(),
                entity.getWarehouseId(),
                context.warehouseName(),
                entity.getReturnDate(),
                entity.getStatus(),
                entity.getTotalQuantity(),
                entity.getTotalAmount(),
                entity.getTotalTaxAmount(),
                entity.getRemark(),
                List.of()
        );
    }

    private PurchaseReturnLineResponse toLineResponse(PurchaseReturnLineEntity line) {
        return new PurchaseReturnLineResponse(line.getId(), line.getLineNo(), line.getReceiptLineId(), line.getOrderLineId(),
                line.getProductId(), line.getProductName(), line.getQty(), line.getPrice(), line.getTaxRate(), line.getAmount(),
                line.getTaxAmount(), line.getReceiptQty(), line.getReturnedQty(), line.getAvailableReturnQty(),
                line.getLotNo(), line.getProductionDate(), line.getExpiryDate(), line.getLocationId(), line.getSerialNos(), line.getRemark());
    }

    private PurchaseReturnLineEntity enrichLine(PurchaseReturnLineEntity line) {
        PurchaseReceiptLineEntity receiptLine = purchaseReceiptLineMapper.selectOne(new LambdaQueryWrapper<PurchaseReceiptLineEntity>()
                .eq(PurchaseReceiptLineEntity::getCompanyId, line.getCompanyId())
                .eq(PurchaseReceiptLineEntity::getAccountBookId, line.getAccountBookId())
                .eq(PurchaseReceiptLineEntity::getId, line.getReceiptLineId()));
        if (receiptLine == null) {
            return line;
        }
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        ProductEntity product = productValidator.requireProduct(
                receiptLine.getProductId(),
                currentUser.companyId(),
                currentUser.accountBookId()
        );
        PurchaseReturnLineViewData.from(receiptLine, product).applyTo(line);
        return line;
    }

    private BigDecimal availableQty(PurchaseReceiptLineEntity receiptLine) {
        return PurchaseReturnQuantities.from(receiptLine.getQty(), receiptLine.getReturnedQty()).availableReturnQty();
    }

    private LambdaQueryWrapper<PurchaseReturnEntity> buildListQuery(
            String keyword,
            Long receiptId,
            Long warehouseId,
            String status,
            LocalDate returnDateFrom,
            LocalDate returnDateTo
    ) {
        LambdaQueryWrapper<PurchaseReturnEntity> wrapper = new LambdaQueryWrapper<PurchaseReturnEntity>()
                .eq(PurchaseReturnEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(PurchaseReturnEntity::getReturnNo, keyword);
        }
        if (receiptId != null) {
            wrapper.eq(PurchaseReturnEntity::getReceiptId, receiptId);
        }
        if (warehouseId != null) {
            wrapper.eq(PurchaseReturnEntity::getWarehouseId, warehouseId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(PurchaseReturnEntity::getStatus, status);
        }
        if (returnDateFrom != null) {
            wrapper.ge(PurchaseReturnEntity::getReturnDate, returnDateFrom);
        }
        if (returnDateTo != null) {
            wrapper.le(PurchaseReturnEntity::getReturnDate, returnDateTo);
        }
        return wrapper.orderByDesc(PurchaseReturnEntity::getId);
    }

    private LambdaQueryWrapper<PurchaseReturnEntity> scopedListQuery(PurchaseReturnPageQuery safeQuery) {
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String status = normalizeStatus(safeQuery.getStatus());
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);
        LambdaQueryWrapper<PurchaseReturnEntity> wrapper = buildListQuery(
                keyword,
                safeQuery.getReceiptId(),
                safeQuery.getWarehouseId(),
                status,
                safeQuery.getReturnDateFrom(),
                safeQuery.getReturnDateTo()
        );
        return dataScopeService.applyPurchaseReturnScope(
                wrapper,
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
    }

    private List<?> returnExportRow(PurchaseReturnEntity entity) {
        return Arrays.asList(
                entity.getReturnNo(),
                entity.getReceiptId(),
                entity.getWarehouseId(),
                entity.getReturnDate(),
                entity.getStatus(),
                entity.getTotalQuantity(),
                entity.getTotalAmount(),
                entity.getTotalTaxAmount(),
                entity.getRemark()
        );
    }

    private void assertCanView(PurchaseReturnEntity entity) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = entity.getCreatedBy() == null ? null : userMapper.selectById(entity.getCreatedBy());
        dataScopeService.assertCanViewPurchaseReturn(
                entity,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    private void assertCanView(PurchaseReceiptEntity entity) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = entity.getCreatedBy() == null ? null : userMapper.selectById(entity.getCreatedBy());
        dataScopeService.assertCanViewPurchaseReceipt(
                entity,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    private void assertCanView(PurchaseOrderEntity entity) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = entity.getCreatedBy() == null ? null : userMapper.selectById(entity.getCreatedBy());
        dataScopeService.assertCanViewPurchaseOrder(
                entity,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    private String normalizeNullableText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeStatus(String value) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            return null;
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private long normalizePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return 1L;
        }
        return pageNo;
    }

    private long normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20L;
        }
        return Math.min(pageSize, 200);
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

    private record ReceiptContext(String receiptNo, String orderNo, String warehouseName) {}
}

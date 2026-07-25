package com.tuowei.erp.purchase.receipt.service;

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
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
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
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptCreateRequest;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptLineRequest;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptLineResponse;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptPageQuery;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptResponse;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptUpdateRequest;
import com.tuowei.erp.purchase.support.AccumulatedQuantityValidator;
import com.tuowei.erp.purchase.support.PurchaseAmountCalculator;
import com.tuowei.erp.purchase.support.PurchaseReceiptQuantities;
import com.tuowei.erp.qc.inspection.service.QcInspectionGate;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class PurchaseReceiptService {

    private static final List<String> RECEIPT_EXPORT_HEADERS = List.of(
            "receiptNo",
            "orderId",
            "warehouseId",
            "receiptDate",
            "status",
            "totalQuantity",
            "totalAmount",
            "totalTaxAmount",
            "remark"
    );

    private final PurchaseReceiptMapper purchaseReceiptMapper;
    private final PurchaseReceiptLineMapper purchaseReceiptLineMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderLineMapper purchaseOrderLineMapper;
    private final WarehouseMapper warehouseMapper;
    private final InventoryPostingService inventoryPostingService;
    private final InventorySerialNumberService inventorySerialNumberService;
    private final PurchaseOrderLookupService purchaseOrderLookupService;
    private final PurchaseOrderReceiptStatusService purchaseOrderReceiptStatusService;
    private final PurchaseReceiptNumberService purchaseReceiptNumberService;
    private final FinancePostingService financePostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final UserMapper userMapper;
    private final AccountPeriodGuard accountPeriodGuard;
    private final QcInspectionGate qcInspectionGate;
    private final ProductValidator productValidator;

    public PurchaseReceiptService(
            PurchaseReceiptMapper purchaseReceiptMapper,
            PurchaseReceiptLineMapper purchaseReceiptLineMapper,
            PurchaseOrderMapper purchaseOrderMapper,
            PurchaseOrderLineMapper purchaseOrderLineMapper,
            WarehouseMapper warehouseMapper,
            InventoryPostingService inventoryPostingService,
            InventorySerialNumberService inventorySerialNumberService,
            PurchaseOrderLookupService purchaseOrderLookupService,
            PurchaseOrderReceiptStatusService purchaseOrderReceiptStatusService,
            PurchaseReceiptNumberService purchaseReceiptNumberService,
            FinancePostingService financePostingService,
            AuditMetadataFactory auditMetadataFactory,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            UserMapper userMapper,
            AccountPeriodGuard accountPeriodGuard,
            QcInspectionGate qcInspectionGate,
            ProductValidator productValidator
    ) {
        this.purchaseReceiptMapper = purchaseReceiptMapper;
        this.purchaseReceiptLineMapper = purchaseReceiptLineMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.purchaseOrderLineMapper = purchaseOrderLineMapper;
        this.warehouseMapper = warehouseMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.inventorySerialNumberService = inventorySerialNumberService;
        this.purchaseOrderLookupService = purchaseOrderLookupService;
        this.purchaseOrderReceiptStatusService = purchaseOrderReceiptStatusService;
        this.purchaseReceiptNumberService = purchaseReceiptNumberService;
        this.financePostingService = financePostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.userMapper = userMapper;
        this.accountPeriodGuard = accountPeriodGuard;
        this.qcInspectionGate = qcInspectionGate;
        this.productValidator = productValidator;
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

        return toResponse(receipt, receiptLines);
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseReceiptResponse> list(PurchaseReceiptPageQuery query) {
        PurchaseReceiptPageQuery safeQuery = query == null ? new PurchaseReceiptPageQuery() : query;
        long pageNo = normalizePageNo(safeQuery.getPageNo());
        long pageSize = normalizePageSize(safeQuery.getPageSize());
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String status = normalizeStatus(safeQuery.getStatus());
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);

        Page<PurchaseReceiptEntity> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<PurchaseReceiptEntity> wrapper = buildListQuery(keyword, safeQuery.getOrderId(), safeQuery.getWarehouseId(), status, safeQuery.getReceiptDateFrom(), safeQuery.getReceiptDateTo());
        wrapper = dataScopeService.applyPurchaseReceiptScope(
                wrapper,
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
        Page<PurchaseReceiptEntity> result = purchaseReceiptMapper.selectPage(
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

    public StreamingResponseBody exportReceipts(PurchaseReceiptPageQuery query) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        PurchaseReceiptPageQuery safeQuery = query == null ? new PurchaseReceiptPageQuery() : query;
        return outputStream -> withAuthentication(authentication, () -> CsvExport.write(outputStream, RECEIPT_EXPORT_HEADERS, rowWriter -> {
            LambdaQueryWrapper<PurchaseReceiptEntity> wrapper = scopedListQuery(safeQuery);
            for (PurchaseReceiptEntity entity : purchaseReceiptMapper.selectList(wrapper)) {
                rowWriter.write(receiptExportRow(entity));
            }
        }));
    }

    @Transactional(readOnly = true)
    public PurchaseReceiptResponse getById(Long id) {
        PurchaseReceiptEntity receipt = requireReceipt(id);
        assertCanView(receipt);
        List<PurchaseReceiptLineEntity> lines = purchaseReceiptLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseReceiptLineEntity>()
                        .eq(PurchaseReceiptLineEntity::getCompanyId, receipt.getCompanyId())
                        .eq(PurchaseReceiptLineEntity::getAccountBookId, receipt.getAccountBookId())
                        .eq(PurchaseReceiptLineEntity::getReceiptId, id)
                        .orderByAsc(PurchaseReceiptLineEntity::getLineNo)
        );
        return toResponse(receipt, lines);
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
        return toResponse(receipt, receiptLines);
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

    private LambdaQueryWrapper<PurchaseReceiptEntity> buildListQuery(
            String keyword,
            Long orderId,
            Long warehouseId,
            String status,
            LocalDate receiptDateFrom,
            LocalDate receiptDateTo
    ) {
        LambdaQueryWrapper<PurchaseReceiptEntity> wrapper = new LambdaQueryWrapper<PurchaseReceiptEntity>()
                .eq(PurchaseReceiptEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(PurchaseReceiptEntity::getReceiptNo, keyword);
        }
        if (orderId != null) {
            wrapper.eq(PurchaseReceiptEntity::getOrderId, orderId);
        }
        if (warehouseId != null) {
            wrapper.eq(PurchaseReceiptEntity::getWarehouseId, warehouseId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(PurchaseReceiptEntity::getStatus, status);
        }
        if (receiptDateFrom != null) {
            wrapper.ge(PurchaseReceiptEntity::getReceiptDate, receiptDateFrom);
        }
        if (receiptDateTo != null) {
            wrapper.le(PurchaseReceiptEntity::getReceiptDate, receiptDateTo);
        }
        return wrapper.orderByDesc(PurchaseReceiptEntity::getId);
    }

    private LambdaQueryWrapper<PurchaseReceiptEntity> scopedListQuery(PurchaseReceiptPageQuery safeQuery) {
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String status = normalizeStatus(safeQuery.getStatus());
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);
        LambdaQueryWrapper<PurchaseReceiptEntity> wrapper = buildListQuery(
                keyword,
                safeQuery.getOrderId(),
                safeQuery.getWarehouseId(),
                status,
                safeQuery.getReceiptDateFrom(),
                safeQuery.getReceiptDateTo()
        );
        return dataScopeService.applyPurchaseReceiptScope(
                wrapper,
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
    }

    private List<?> receiptExportRow(PurchaseReceiptEntity entity) {
        return Arrays.asList(
                entity.getReceiptNo(),
                entity.getOrderId(),
                entity.getWarehouseId(),
                entity.getReceiptDate(),
                entity.getStatus(),
                entity.getTotalQuantity(),
                entity.getTotalAmount(),
                entity.getTotalTaxAmount(),
                entity.getRemark()
        );
    }

    private void assertCanView(PurchaseReceiptEntity receipt) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = receipt.getCreatedBy() == null ? null : userMapper.selectById(receipt.getCreatedBy());
        dataScopeService.assertCanViewPurchaseReceipt(
                receipt,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    private void assertCanView(PurchaseOrderEntity order) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = order.getCreatedBy() == null ? null : userMapper.selectById(order.getCreatedBy());
        dataScopeService.assertCanViewPurchaseOrder(
                order,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    private PurchaseReceiptResponse toResponse(PurchaseReceiptEntity receipt, List<PurchaseReceiptLineEntity> lines) {
        return new PurchaseReceiptResponse(
                receipt.getId(),
                receipt.getReceiptNo(),
                receipt.getOrderId(),
                receipt.getWarehouseId(),
                receipt.getReceiptDate(),
                receipt.getStatus(),
                receipt.getTotalQuantity(),
                receipt.getTotalAmount(),
                receipt.getTotalTaxAmount(),
                receipt.getRemark(),
                lines.stream().map(this::toLineResponse).toList()
        );
    }

    private PurchaseReceiptResponse toSummaryResponse(PurchaseReceiptEntity receipt) {
        return new PurchaseReceiptResponse(
                receipt.getId(),
                receipt.getReceiptNo(),
                receipt.getOrderId(),
                receipt.getWarehouseId(),
                receipt.getReceiptDate(),
                receipt.getStatus(),
                receipt.getTotalQuantity(),
                receipt.getTotalAmount(),
                receipt.getTotalTaxAmount(),
                receipt.getRemark(),
                List.of()
        );
    }

    private PurchaseReceiptLineResponse toLineResponse(PurchaseReceiptLineEntity line) {
        return new PurchaseReceiptLineResponse(
                line.getId(),
                line.getLineNo(),
                line.getOrderLineId(),
                line.getProductId(),
                line.getQty(),
                line.getPrice(),
                line.getTaxRate(),
                line.getAmount(),
                line.getTaxAmount(),
                line.getLotNo(),
                line.getProductionDate(),
                line.getExpiryDate(),
                line.getLocationId(),
                line.getSerialNos(),
                line.getRemark()
        );
    }

    private BigDecimal availableReceiptQty(PurchaseOrderLineEntity orderLine) {
        return PurchaseReceiptQuantities.from(orderLine.getQty(), orderLine.getReceivedQty()).availableReceiptQty();
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

    private void touch(PurchaseReceiptEntity receipt) {
        AuditMetadata audit = auditMetadataFactory.current();
        receipt.setUpdatedBy(audit.userId());
        receipt.setUpdatedTime(audit.now());
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

    private record ReceiptTotals(BigDecimal totalQuantity, BigDecimal totalAmount, BigDecimal totalTaxAmount) {
    }
}

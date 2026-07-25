package com.tuowei.erp.sales.returnorder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
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
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.purchase.support.AccumulatedQuantityValidator;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnLineMapper;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnMapper;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import com.tuowei.erp.sales.returnorder.model.SalesReturnLineEntity;
import com.tuowei.erp.sales.returnorder.web.SalesReturnCreateRequest;
import com.tuowei.erp.sales.returnorder.web.SalesReturnLineRequest;
import com.tuowei.erp.sales.returnorder.web.SalesReturnLineResponse;
import com.tuowei.erp.sales.returnorder.web.SalesReturnPageQuery;
import com.tuowei.erp.sales.returnorder.web.SalesReturnResponse;
import com.tuowei.erp.sales.returnorder.web.SalesReturnUpdateRequest;
import com.tuowei.erp.sales.support.SalesAmountCalculator;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SalesReturnService {

    private static final int MAX_STATUS_REFRESH_ATTEMPTS = 8;

    private final SalesReturnMapper salesReturnMapper;
    private final SalesReturnLineMapper salesReturnLineMapper;
    private final SalesDeliveryMapper salesDeliveryMapper;
    private final SalesDeliveryLineMapper salesDeliveryLineMapper;
    private final SalesOrderMapper salesOrderMapper;
    private final SalesOrderLineMapper salesOrderLineMapper;
    private final ProductMapper productMapper;
    private final ProductValidator productValidator;
    private final InventoryTransactionMapper inventoryTransactionMapper;
    private final InventoryPostingService inventoryPostingService;
    private final InventorySerialNumberService inventorySerialNumberService;
    private final SalesReturnNumberService salesReturnNumberService;
    private final FinancePostingService financePostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final UserMapper userMapper;
    private final AccountPeriodGuard accountPeriodGuard;

    public SalesReturnService(
            SalesReturnMapper salesReturnMapper,
            SalesReturnLineMapper salesReturnLineMapper,
            SalesDeliveryMapper salesDeliveryMapper,
            SalesDeliveryLineMapper salesDeliveryLineMapper,
            SalesOrderMapper salesOrderMapper,
            SalesOrderLineMapper salesOrderLineMapper,
            ProductMapper productMapper,
            ProductValidator productValidator,
            InventoryTransactionMapper inventoryTransactionMapper,
            InventoryPostingService inventoryPostingService,
            InventorySerialNumberService inventorySerialNumberService,
            SalesReturnNumberService salesReturnNumberService,
            FinancePostingService financePostingService,
            AuditMetadataFactory auditMetadataFactory,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            UserMapper userMapper,
            AccountPeriodGuard accountPeriodGuard
    ) {
        this.salesReturnMapper = salesReturnMapper;
        this.salesReturnLineMapper = salesReturnLineMapper;
        this.salesDeliveryMapper = salesDeliveryMapper;
        this.salesDeliveryLineMapper = salesDeliveryLineMapper;
        this.salesOrderMapper = salesOrderMapper;
        this.salesOrderLineMapper = salesOrderLineMapper;
        this.productMapper = productMapper;
        this.productValidator = productValidator;
        this.inventoryTransactionMapper = inventoryTransactionMapper;
        this.inventoryPostingService = inventoryPostingService;
        this.inventorySerialNumberService = inventorySerialNumberService;
        this.salesReturnNumberService = salesReturnNumberService;
        this.financePostingService = financePostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.userMapper = userMapper;
        this.accountPeriodGuard = accountPeriodGuard;
    }

    @Transactional
    public SalesReturnResponse create(SalesReturnCreateRequest request) {
        SalesDeliveryEntity delivery = requirePostedDelivery(request.deliveryId());
        assertCanView(delivery);
        Map<Long, SalesDeliveryLineEntity> deliveryLines = loadDeliveryLinesAsMap(delivery);
        ReturnTotals totals = calculateTotals(request.lines(), deliveryLines);
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();

        SalesReturnEntity entity = new SalesReturnEntity();
        entity.setCompanyId(audit.companyId());
        entity.setAccountBookId(audit.accountBookId());
        entity.setReturnNo(salesReturnNumberService.nextReturnNo(request.returnDate()));
        entity.setDeliveryId(delivery.getId());
        entity.setWarehouseId(delivery.getWarehouseId());
        entity.setReturnDate(request.returnDate());
        entity.setStatus("DRAFT");
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
        assertCanView(entity);
        salesReturnMapper.insert(entity);

        List<SalesReturnLineEntity> lines = saveReturnLines(entity.getId(), request.lines(), deliveryLines, audit, now);
        return toResponse(entity, lines);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesReturnResponse> list(SalesReturnPageQuery query) {
        SalesReturnPageQuery safeQuery = query == null ? new SalesReturnPageQuery() : query;
        long pageNo = normalizePageNo(safeQuery.getPageNo());
        long pageSize = normalizePageSize(safeQuery.getPageSize());
        String keyword = normalizeNullableText(safeQuery.getKeyword());
        String status = normalizeStatus(safeQuery.getStatus());
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);

        LambdaQueryWrapper<SalesReturnEntity> wrapper = buildListQuery(
                keyword,
                safeQuery.getDeliveryId(),
                safeQuery.getWarehouseId(),
                status,
                safeQuery.getReturnDateFrom(),
                safeQuery.getReturnDateTo()
        );
        wrapper = dataScopeService.applySalesReturnScope(
                wrapper,
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
        Page<SalesReturnEntity> result = salesReturnMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                result.getRecords().stream().map(this::toSummaryResponse).toList()
        );
    }

    @Transactional(readOnly = true)
    public SalesReturnResponse getById(Long id) {
        SalesReturnEntity entity = requireReturn(id);
        assertCanView(entity);
        List<SalesReturnLineEntity> lines = salesReturnLineMapper.selectList(new LambdaQueryWrapper<SalesReturnLineEntity>()
                .eq(SalesReturnLineEntity::getCompanyId, entity.getCompanyId())
                .eq(SalesReturnLineEntity::getAccountBookId, entity.getAccountBookId())
                .eq(SalesReturnLineEntity::getReturnId, id)
                .orderByAsc(SalesReturnLineEntity::getLineNo));
        return toResponse(entity, lines);
    }

    @Transactional
    public SalesReturnResponse update(Long id, SalesReturnUpdateRequest request) {
        SalesReturnEntity entity = requireReturn(id);
        assertCanView(entity);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前销售退货单状态不允许编辑");
        }
        if (!entity.getDeliveryId().equals(request.deliveryId())) {
            throw new IllegalArgumentException("销售退货单不允许变更来源销售出库单");
        }

        SalesDeliveryEntity delivery = requirePostedDelivery(entity.getDeliveryId());
        assertCanView(delivery);
        Map<Long, SalesDeliveryLineEntity> deliveryLines = loadDeliveryLinesAsMap(delivery);
        ReturnTotals totals = calculateTotals(request.lines(), deliveryLines);
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();

        entity.setReturnDate(request.returnDate());
        entity.setTotalQuantity(totals.totalQuantity());
        entity.setTotalAmount(totals.totalAmount());
        entity.setTotalTaxAmount(totals.totalTaxAmount());
        entity.setRemark(request.remark());
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                salesReturnMapper.updateById(entity),
                "销售退货单已被其他操作修改，请刷新后重试"
        );

        salesReturnLineMapper.delete(new LambdaQueryWrapper<SalesReturnLineEntity>()
                .eq(SalesReturnLineEntity::getCompanyId, entity.getCompanyId())
                .eq(SalesReturnLineEntity::getAccountBookId, entity.getAccountBookId())
                .eq(SalesReturnLineEntity::getReturnId, entity.getId()));
        List<SalesReturnLineEntity> lines = saveReturnLines(entity.getId(), request.lines(), deliveryLines, audit, now);
        return toResponse(entity, lines);
    }

    @Transactional
    public SalesReturnResponse cancel(Long id) {
        SalesReturnEntity entity = requireReturn(id);
        assertCanView(entity);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前销售退货单状态不允许作废");
        }
        touch(entity);
        entity.setStatus("CANCELLED");
        OptimisticLockGuard.requireUpdated(
                salesReturnMapper.updateById(entity),
                "销售退货单已被其他操作修改，请刷新后重试"
        );
        return getById(id);
    }

    @Transactional
    public SalesReturnResponse post(Long id) {
        SalesReturnEntity entity = requireReturn(id);
        assertCanView(entity);
        if (!"DRAFT".equals(entity.getStatus())) {
            throw new IllegalArgumentException("当前销售退货单状态不允许过账");
        }
        accountPeriodGuard.requireOpen(entity.getReturnDate(), "销售退货过账");

        SalesDeliveryEntity delivery = requirePostedDelivery(entity.getDeliveryId());
        assertCanView(delivery);
        SalesOrderEntity order = requireOrder(delivery.getOrderId());
        assertCanView(order);
        List<SalesReturnLineEntity> returnLines = salesReturnLineMapper.selectList(new LambdaQueryWrapper<SalesReturnLineEntity>()
                .eq(SalesReturnLineEntity::getCompanyId, entity.getCompanyId())
                .eq(SalesReturnLineEntity::getAccountBookId, entity.getAccountBookId())
                .eq(SalesReturnLineEntity::getReturnId, entity.getId())
                .orderByAsc(SalesReturnLineEntity::getLineNo));
        Map<Long, SalesDeliveryLineEntity> deliveryLines = loadDeliveryLinesAsMap(delivery);
        Map<Long, SalesOrderLineEntity> orderLines = loadOrderLinesAsMap(order);
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        AccumulatedQuantityValidator returnQtyValidator = new AccumulatedQuantityValidator("退货数量超过销售出库明细剩余可退数量");
        AccumulatedQuantityValidator deliveredQtyValidator = new AccumulatedQuantityValidator("退货数量超过销售订单已出库数量");

        for (SalesReturnLineEntity returnLine : returnLines) {
            SalesDeliveryLineEntity deliveryLine = requireDeliveryLine(deliveryLines, returnLine.getDeliveryLineId());
            SalesOrderLineEntity orderLine = requireOrderLine(orderLines, returnLine.getOrderLineId());
            BigDecimal qty = ScalePrecision.quantity(returnLine.getQty());
            returnQtyValidator.ensureWithinLimit(deliveryLine.getId(), qty, availableReturnQty(deliveryLine));
            deliveredQtyValidator.ensureWithinLimit(orderLine.getId(), qty, ScalePrecision.zeroDefault(orderLine.getDeliveredQty()));
            validateReturnLotIntent(returnLine, deliveryLine, audit.companyId(), audit.accountBookId());
        }

        entity.setStatus("POSTED");
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(now);
        OptimisticLockGuard.requireUpdated(
                salesReturnMapper.updateById(entity),
                "销售退货单已被其他操作修改，请刷新后重试"
        );

        BigDecimal totalReturnCostAmount = BigDecimal.ZERO;
        for (SalesReturnLineEntity returnLine : returnLines) {
            SalesDeliveryLineEntity deliveryLine = requireDeliveryLine(deliveryLines, returnLine.getDeliveryLineId());
            BigDecimal returnCostAmount = resolveReturnCostAmount(
                    returnLine,
                    deliveryLine,
                    audit.companyId(),
                    audit.accountBookId()
            );
            BigDecimal qty = ScalePrecision.quantity(returnLine.getQty());
            deliveryLine.setReturnedQty(ScalePrecision.quantity(ScalePrecision.zeroDefault(deliveryLine.getReturnedQty()).add(qty)));
            deliveryLine.setUpdatedBy(audit.userId());
            deliveryLine.setUpdatedTime(now);
            OptimisticLockGuard.requireUpdated(
                    salesDeliveryLineMapper.updateById(deliveryLine),
                    "销售出库明细已被其他操作修改，请刷新后重试"
            );

            SalesOrderLineEntity orderLine = requireOrderLine(orderLines, returnLine.getOrderLineId());
            orderLine.setDeliveredQty(ScalePrecision.quantity(ScalePrecision.zeroDefault(orderLine.getDeliveredQty()).subtract(qty)));
            orderLine.setUpdatedBy(audit.userId());
            orderLine.setUpdatedTime(now);
            OptimisticLockGuard.requireUpdated(
                    salesOrderLineMapper.updateById(orderLine),
                    "销售订单明细已被其他操作修改，请刷新后重试"
            );

            inventoryPostingService.postInbound(
                    new InventoryPostingCommand(
                            entity.getWarehouseId(),
                            returnLine.getProductId(),
                            "SALES_RETURN",
                            entity.getReturnNo(),
                            returnLine.getId(),
                            returnLine.getQty(),
                            returnCostAmount,
                            returnLine.getRemark(),
                            entity.getReturnDate(),
                            returnLine.getLotNo(),
                            returnLine.getProductionDate(),
                            returnLine.getExpiryDate(),
                            returnLine.getLocationId()
                    ),
                    audit
            );
            inventorySerialNumberService.registerInboundSerials(
                    returnLine.getProductId(),
                    entity.getWarehouseId(),
                    returnLine.getLocationId(),
                    returnLine.getSerialNos(),
                    "SALES_RETURN",
                    entity.getReturnNo(),
                    returnLine.getQty(),
                    audit
            );
            totalReturnCostAmount = ScalePrecision.amount(
                    totalReturnCostAmount.add(returnCostAmount)
            );
        }

        refreshDeliveryStatus(delivery.getOrderId(), audit, now);
        financePostingService.recordSalesReturn(entity, order, totalReturnCostAmount, audit);
        return getById(id);
    }

    private SalesDeliveryEntity requirePostedDelivery(Long id) {
        SalesDeliveryEntity delivery = salesDeliveryMapper.selectById(id);
        if (delivery == null || delivery.getDeletedFlag() == null || delivery.getDeletedFlag() != 0
                || !"POSTED".equals(delivery.getStatus())) {
            throw new IllegalArgumentException("销售出库单未过账，不能创建销售退货单");
        }
        return delivery;
    }

    private SalesReturnEntity requireReturn(Long id) {
        SalesReturnEntity entity = salesReturnMapper.selectById(id);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("销售退货单不存在");
        }
        return entity;
    }

    private SalesOrderEntity requireOrder(Long id) {
        SalesOrderEntity order = salesOrderMapper.selectById(id);
        if (order == null || order.getDeletedFlag() == null || order.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("销售订单不存在");
        }
        return order;
    }

    private Map<Long, SalesDeliveryLineEntity> loadDeliveryLinesAsMap(SalesDeliveryEntity delivery) {
        return salesDeliveryLineMapper.selectList(new LambdaQueryWrapper<SalesDeliveryLineEntity>()
                        .eq(SalesDeliveryLineEntity::getCompanyId, delivery.getCompanyId())
                        .eq(SalesDeliveryLineEntity::getAccountBookId, delivery.getAccountBookId())
                        .eq(SalesDeliveryLineEntity::getDeliveryId, delivery.getId()))
                .stream()
                .collect(Collectors.toMap(SalesDeliveryLineEntity::getId, Function.identity()));
    }

    private Map<Long, SalesOrderLineEntity> loadOrderLinesAsMap(SalesOrderEntity order) {
        return salesOrderLineMapper.selectList(new LambdaQueryWrapper<SalesOrderLineEntity>()
                        .eq(SalesOrderLineEntity::getCompanyId, order.getCompanyId())
                        .eq(SalesOrderLineEntity::getAccountBookId, order.getAccountBookId())
                        .eq(SalesOrderLineEntity::getOrderId, order.getId()))
                .stream()
                .collect(Collectors.toMap(SalesOrderLineEntity::getId, Function.identity()));
    }

    private SalesDeliveryLineEntity requireDeliveryLine(Map<Long, SalesDeliveryLineEntity> deliveryLines, Long deliveryLineId) {
        SalesDeliveryLineEntity deliveryLine = deliveryLines.get(deliveryLineId);
        if (deliveryLine == null) {
            throw new IllegalArgumentException("销售出库明细不存在");
        }
        return deliveryLine;
    }

    private SalesOrderLineEntity requireOrderLine(Map<Long, SalesOrderLineEntity> orderLines, Long orderLineId) {
        SalesOrderLineEntity orderLine = orderLines.get(orderLineId);
        if (orderLine == null) {
            throw new IllegalArgumentException("销售订单明细不存在");
        }
        return orderLine;
    }

    private ReturnTotals calculateTotals(
            List<SalesReturnLineRequest> requests,
            Map<Long, SalesDeliveryLineEntity> deliveryLines
    ) {
        SalesAmountCalculator.DocumentTotals totals = SalesAmountCalculator.DocumentTotals.zero();
        AccumulatedQuantityValidator quantityValidator = new AccumulatedQuantityValidator("退货数量超过销售出库明细剩余可退数量");
        for (SalesReturnLineRequest request : requests) {
            SalesDeliveryLineEntity deliveryLine = requireDeliveryLine(deliveryLines, request.deliveryLineId());
            SalesAmountCalculator.LineAmounts amounts = SalesAmountCalculator.line(
                    request.qty(),
                    deliveryLine.getPrice(),
                    deliveryLine.getTaxRate()
            );
            quantityValidator.ensureWithinLimit(deliveryLine.getId(), amounts.qty(), availableReturnQty(deliveryLine));
            totals = totals.add(amounts);
        }
        return new ReturnTotals(totals.totalQuantity(), totals.totalAmount(), totals.totalTaxAmount());
    }

    private List<SalesReturnLineEntity> saveReturnLines(
            Long returnId,
            List<SalesReturnLineRequest> requests,
            Map<Long, SalesDeliveryLineEntity> deliveryLines,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        List<SalesReturnLineEntity> returnLines = new ArrayList<>();
        productValidator.requireProducts(
                requests.stream()
                        .map(r -> deliveryLines.get(r.deliveryLineId()).getProductId())
                        .toList(),
                audit.companyId(), audit.accountBookId());
        for (int i = 0; i < requests.size(); i++) {
            SalesReturnLineRequest request = requests.get(i);
            SalesDeliveryLineEntity deliveryLine = requireDeliveryLine(deliveryLines, request.deliveryLineId());
            SalesAmountCalculator.LineAmounts amounts = SalesAmountCalculator.line(
                    request.qty(),
                    deliveryLine.getPrice(),
                    deliveryLine.getTaxRate()
            );

            SalesReturnLineEntity returnLine = new SalesReturnLineEntity();
            returnLine.setCompanyId(audit.companyId());
            returnLine.setAccountBookId(audit.accountBookId());
            returnLine.setReturnId(returnId);
            returnLine.setLineNo(i + 1);
            returnLine.setDeliveryLineId(deliveryLine.getId());
            returnLine.setOrderLineId(deliveryLine.getOrderLineId());
            returnLine.setProductId(deliveryLine.getProductId());
            returnLine.setQty(amounts.qty());
            returnLine.setPrice(amounts.price());
            returnLine.setTaxRate(amounts.taxRate());
            returnLine.setAmount(amounts.amount());
            returnLine.setTaxAmount(amounts.taxAmount());
            ReturnLotIntent lotIntent = resolveReturnLotIntent(request, deliveryLine);
            returnLine.setLotNo(lotIntent.lotNo());
            returnLine.setProductionDate(lotIntent.productionDate());
            returnLine.setExpiryDate(lotIntent.expiryDate());
            returnLine.setLocationId(request.locationId() != null ? request.locationId() : deliveryLine.getLocationId());
            returnLine.setSerialNos(request.serialNos());
            returnLine.setRemark(request.remark());
            returnLine.setCreatedBy(audit.userId());
            returnLine.setCreatedTime(now);
            returnLine.setUpdatedBy(audit.userId());
            returnLine.setUpdatedTime(now);
            returnLine.setVersion(0);
            salesReturnLineMapper.insert(returnLine);
            returnLines.add(returnLine);
        }
        return returnLines;
    }

    private LambdaQueryWrapper<SalesReturnEntity> buildListQuery(
            String keyword,
            Long deliveryId,
            Long warehouseId,
            String status,
            LocalDate returnDateFrom,
            LocalDate returnDateTo
    ) {
        LambdaQueryWrapper<SalesReturnEntity> wrapper = new LambdaQueryWrapper<SalesReturnEntity>()
                .eq(SalesReturnEntity::getDeletedFlag, 0);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(SalesReturnEntity::getReturnNo, keyword);
        }
        if (deliveryId != null) {
            wrapper.eq(SalesReturnEntity::getDeliveryId, deliveryId);
        }
        if (warehouseId != null) {
            wrapper.eq(SalesReturnEntity::getWarehouseId, warehouseId);
        }
        if (StringUtils.hasText(status)) {
            wrapper.eq(SalesReturnEntity::getStatus, status);
        }
        if (returnDateFrom != null) {
            wrapper.ge(SalesReturnEntity::getReturnDate, returnDateFrom);
        }
        if (returnDateTo != null) {
            wrapper.le(SalesReturnEntity::getReturnDate, returnDateTo);
        }
        return wrapper.orderByDesc(SalesReturnEntity::getId);
    }

    private void assertCanView(SalesReturnEntity entity) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = entity.getCreatedBy() == null ? null : userMapper.selectById(entity.getCreatedBy());
        dataScopeService.assertCanViewSalesReturn(
                entity,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    private void assertCanView(SalesDeliveryEntity entity) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = entity.getCreatedBy() == null ? null : userMapper.selectById(entity.getCreatedBy());
        dataScopeService.assertCanViewSalesDelivery(
                entity,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    private void assertCanView(SalesOrderEntity entity) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = entity.getCreatedBy() == null ? null : userMapper.selectById(entity.getCreatedBy());
        dataScopeService.assertCanViewSalesOrder(
                entity,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    private void refreshDeliveryStatus(Long orderId, AuditMetadata audit, LocalDateTime now) {
        for (int attempt = 0; attempt < MAX_STATUS_REFRESH_ATTEMPTS; attempt++) {
            SalesOrderEntity order = salesOrderMapper.selectById(orderId);
            if (order == null || order.getDeletedFlag() == null || order.getDeletedFlag() != 0) {
                throw new IllegalArgumentException("销售订单不存在");
            }
            List<SalesOrderLineEntity> orderLines = loadOrderLinesAsMap(order).values().stream().toList();
            order.setDeliveryStatus(resolveDeliveryStatus(orderLines));
            order.setUpdatedBy(audit.userId());
            order.setUpdatedTime(now);
            if (salesOrderMapper.updateById(order) == 1) {
                return;
            }
        }
        throw new BusinessConflictException("销售订单已被其他操作修改，请刷新后重试");
    }

    private String resolveDeliveryStatus(List<SalesOrderLineEntity> orderLines) {
        boolean anyDelivered = false;
        boolean allDelivered = !orderLines.isEmpty();
        for (SalesOrderLineEntity orderLine : orderLines) {
            BigDecimal orderedQty = ScalePrecision.quantity(orderLine.getQty());
            BigDecimal deliveredQty = ScalePrecision.quantity(ScalePrecision.zeroDefault(orderLine.getDeliveredQty()));
            if (deliveredQty.compareTo(BigDecimal.ZERO) > 0) {
                anyDelivered = true;
            }
            if (deliveredQty.compareTo(orderedQty) < 0) {
                allDelivered = false;
            }
        }
        if (allDelivered) {
            return "FULL_DELIVERED";
        }
        if (anyDelivered) {
            return "PARTIAL_DELIVERED";
        }
        return "NOT_DELIVERED";
    }

    private SalesReturnResponse toResponse(SalesReturnEntity entity, List<SalesReturnLineEntity> lines) {
        return new SalesReturnResponse(
                entity.getId(),
                entity.getReturnNo(),
                entity.getDeliveryId(),
                entity.getWarehouseId(),
                entity.getReturnDate(),
                entity.getStatus(),
                entity.getTotalQuantity(),
                entity.getTotalAmount(),
                entity.getTotalTaxAmount(),
                entity.getRemark(),
                lines.stream().map(this::toLineResponse).toList()
        );
    }

    private SalesReturnResponse toSummaryResponse(SalesReturnEntity entity) {
        return new SalesReturnResponse(
                entity.getId(),
                entity.getReturnNo(),
                entity.getDeliveryId(),
                entity.getWarehouseId(),
                entity.getReturnDate(),
                entity.getStatus(),
                entity.getTotalQuantity(),
                entity.getTotalAmount(),
                entity.getTotalTaxAmount(),
                entity.getRemark(),
                List.of()
        );
    }

    private SalesReturnLineResponse toLineResponse(SalesReturnLineEntity line) {
        return new SalesReturnLineResponse(
                line.getId(),
                line.getLineNo(),
                line.getDeliveryLineId(),
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

    private ReturnLotIntent resolveReturnLotIntent(SalesReturnLineRequest request, SalesDeliveryLineEntity deliveryLine) {
        if (StringUtils.hasText(request.lotNo()) || request.productionDate() != null || request.expiryDate() != null) {
            return new ReturnLotIntent(request.lotNo(), request.productionDate(), request.expiryDate());
        }
        if (StringUtils.hasText(deliveryLine.getLotNo())) {
            return new ReturnLotIntent(deliveryLine.getLotNo(), deliveryLine.getProductionDate(), deliveryLine.getExpiryDate());
        }
        return new ReturnLotIntent(null, null, null);
    }

    private void validateReturnLotIntent(
            SalesReturnLineEntity returnLine,
            SalesDeliveryLineEntity deliveryLine,
            Long companyId,
            Long accountBookId
    ) {
        if (StringUtils.hasText(returnLine.getLotNo())) {
            return;
        }
        List<InventoryTransactionEntity> deliveryLotTransactions = inventoryTransactionMapper.selectList(
                new LambdaQueryWrapper<InventoryTransactionEntity>()
                        .eq(InventoryTransactionEntity::getCompanyId, companyId)
                        .eq(InventoryTransactionEntity::getAccountBookId, accountBookId)
                        .eq(InventoryTransactionEntity::getBizType, "SALES_DELIVERY")
                        .eq(InventoryTransactionEntity::getBizLineId, deliveryLine.getId())
                        .eq(InventoryTransactionEntity::getDirection, "OUT")
                        .orderByAsc(InventoryTransactionEntity::getId)
        );
        List<InventoryTransactionEntity> lotTransactions = deliveryLotTransactions.stream()
                .filter(txn -> StringUtils.hasText(txn.getLotNo()))
                .toList();
        Set<String> lots = lotTransactions.stream()
                .map(InventoryTransactionEntity::getLotNo)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (lots.size() > 1) {
            throw new IllegalArgumentException("销售退货必须指定批次号，原销售出库明细已拆分多个批次");
        }
        if (lots.size() == 1) {
            InventoryTransactionEntity txn = lotTransactions.get(0);
            returnLine.setLotNo(txn.getLotNo());
            returnLine.setProductionDate(txn.getProductionDate());
            returnLine.setExpiryDate(txn.getExpiryDate());
        }
    }

    private BigDecimal resolveReturnCostAmount(
            SalesReturnLineEntity returnLine,
            SalesDeliveryLineEntity deliveryLine,
            Long companyId,
            Long accountBookId
    ) {
        List<InventoryTransactionEntity> deliveryTransactions = inventoryTransactionMapper.selectList(
                new LambdaQueryWrapper<InventoryTransactionEntity>()
                        .eq(InventoryTransactionEntity::getCompanyId, companyId)
                        .eq(InventoryTransactionEntity::getAccountBookId, accountBookId)
                        .eq(InventoryTransactionEntity::getBizType, "SALES_DELIVERY")
                        .eq(InventoryTransactionEntity::getBizLineId, deliveryLine.getId())
                        .eq(InventoryTransactionEntity::getDirection, "OUT")
                        .orderByAsc(InventoryTransactionEntity::getId)
        );
        if (deliveryTransactions.isEmpty()) {
            throw new IllegalStateException("销售出库库存分录不存在，不能按成本冲回");
        }
        String lotNo = normalizeNullableText(returnLine.getLotNo());
        List<InventoryTransactionEntity> matchedTransactions = deliveryTransactions;
        if (lotNo != null) {
            matchedTransactions = deliveryTransactions.stream()
                    .filter(txn -> Objects.equals(lotNo, normalizeNullableText(txn.getLotNo())))
                    .toList();
            if (matchedTransactions.isEmpty()) {
                throw new IllegalArgumentException("销售退货批次不存在原销售出库记录");
            }
        }
        BigDecimal totalQty = matchedTransactions.stream()
                .map(InventoryTransactionEntity::getQty)
                .map(ScalePrecision::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAmount = matchedTransactions.stream()
                .map(InventoryTransactionEntity::getAmount)
                .map(ScalePrecision::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal returnQty = ScalePrecision.quantity(returnLine.getQty());
        if (totalQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("销售出库库存分录数量无效，不能按成本冲回");
        }
        if (returnQty.compareTo(totalQty) > 0) {
            throw new IllegalArgumentException("销售退货数量超过原销售出库库存数量");
        }
        if (returnQty.compareTo(totalQty) == 0) {
            return ScalePrecision.amount(totalAmount);
        }
        BigDecimal unitCost = ScalePrecision.unitCost(totalAmount, totalQty);
        return ScalePrecision.amount(unitCost.multiply(returnQty));
    }

    private BigDecimal availableReturnQty(SalesDeliveryLineEntity deliveryLine) {
        return ScalePrecision.quantity(
                ScalePrecision.quantity(deliveryLine.getQty())
                        .subtract(ScalePrecision.quantity(ScalePrecision.zeroDefault(deliveryLine.getReturnedQty())))
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

    private void touch(SalesReturnEntity entity) {
        AuditMetadata audit = auditMetadataFactory.current();
        entity.setUpdatedBy(audit.userId());
        entity.setUpdatedTime(audit.now());
    }

    private record ReturnTotals(BigDecimal totalQuantity, BigDecimal totalAmount, BigDecimal totalTaxAmount) {
    }

    private record ReturnLotIntent(String lotNo, LocalDate productionDate, LocalDate expiryDate) {
    }
}

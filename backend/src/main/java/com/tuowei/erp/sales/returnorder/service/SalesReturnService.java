package com.tuowei.erp.sales.returnorder.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.purchase.support.AccumulatedQuantityValidator;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnLineMapper;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnMapper;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import com.tuowei.erp.sales.returnorder.model.SalesReturnLineEntity;
import com.tuowei.erp.sales.returnorder.web.SalesReturnCreateRequest;
import com.tuowei.erp.sales.returnorder.web.SalesReturnLineRequest;
import com.tuowei.erp.sales.returnorder.web.SalesReturnPageQuery;
import com.tuowei.erp.sales.returnorder.web.SalesReturnResponse;
import com.tuowei.erp.sales.returnorder.web.SalesReturnUpdateRequest;
import com.tuowei.erp.sales.support.SalesAmountCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SalesReturnService {

    private final SalesReturnMapper salesReturnMapper;
    private final SalesReturnLineMapper salesReturnLineMapper;
    private final SalesDeliveryMapper salesDeliveryMapper;
    private final SalesDeliveryLineMapper salesDeliveryLineMapper;
    private final ProductValidator productValidator;
    private final SalesReturnNumberService salesReturnNumberService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final SalesReturnQueryService salesReturnQueryService;
    private final SalesReturnPostingService salesReturnPostingService;

    public SalesReturnService(
            SalesReturnMapper salesReturnMapper,
            SalesReturnLineMapper salesReturnLineMapper,
            SalesDeliveryMapper salesDeliveryMapper,
            SalesDeliveryLineMapper salesDeliveryLineMapper,
            ProductValidator productValidator,
            SalesReturnNumberService salesReturnNumberService,
            AuditMetadataFactory auditMetadataFactory,
            SalesReturnQueryService salesReturnQueryService,
            SalesReturnPostingService salesReturnPostingService
    ) {
        this.salesReturnMapper = salesReturnMapper;
        this.salesReturnLineMapper = salesReturnLineMapper;
        this.salesDeliveryMapper = salesDeliveryMapper;
        this.salesDeliveryLineMapper = salesDeliveryLineMapper;
        this.productValidator = productValidator;
        this.salesReturnNumberService = salesReturnNumberService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.salesReturnQueryService = salesReturnQueryService;
        this.salesReturnPostingService = salesReturnPostingService;
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
        return salesReturnQueryService.toResponse(entity, lines);
    }

    @Transactional(readOnly = true)
    public PageResponse<SalesReturnResponse> list(SalesReturnPageQuery query) {
        SalesReturnPageQuery safeQuery = query == null ? new SalesReturnPageQuery() : query;
        return salesReturnQueryService.list(safeQuery);
    }

    @Transactional(readOnly = true)
    public SalesReturnResponse getById(Long id) {
        return salesReturnQueryService.getById(id);
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
        return salesReturnQueryService.toResponse(entity, lines);
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
        return salesReturnPostingService.post(id);
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

    private Map<Long, SalesDeliveryLineEntity> loadDeliveryLinesAsMap(SalesDeliveryEntity delivery) {
        return salesDeliveryLineMapper.selectList(new LambdaQueryWrapper<SalesDeliveryLineEntity>()
                        .eq(SalesDeliveryLineEntity::getCompanyId, delivery.getCompanyId())
                        .eq(SalesDeliveryLineEntity::getAccountBookId, delivery.getAccountBookId())
                        .eq(SalesDeliveryLineEntity::getDeliveryId, delivery.getId()))
                .stream()
                .collect(Collectors.toMap(SalesDeliveryLineEntity::getId, Function.identity()));
    }

    private SalesDeliveryLineEntity requireDeliveryLine(Map<Long, SalesDeliveryLineEntity> deliveryLines, Long deliveryLineId) {
        SalesDeliveryLineEntity deliveryLine = deliveryLines.get(deliveryLineId);
        if (deliveryLine == null) {
            throw new IllegalArgumentException("销售出库明细不存在");
        }
        return deliveryLine;
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

    private void assertCanView(SalesReturnEntity entity) {
        salesReturnQueryService.assertCanView(entity);
    }

    private void assertCanView(SalesDeliveryEntity entity) {
        salesReturnQueryService.assertCanView(entity);
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

    private BigDecimal availableReturnQty(SalesDeliveryLineEntity deliveryLine) {
        return ScalePrecision.quantity(
                ScalePrecision.quantity(deliveryLine.getQty())
                        .subtract(ScalePrecision.quantity(ScalePrecision.zeroDefault(deliveryLine.getReturnedQty())))
        );
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

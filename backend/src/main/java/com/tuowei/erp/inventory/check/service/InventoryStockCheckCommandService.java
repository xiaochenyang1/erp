package com.tuowei.erp.inventory.check.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.inventory.adjust.service.InventoryAdjustmentService;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentCreateRequest;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentLineRequest;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentResponse;
import com.tuowei.erp.inventory.check.mapper.InventoryStockCheckLineMapper;
import com.tuowei.erp.inventory.check.mapper.InventoryStockCheckMapper;
import com.tuowei.erp.inventory.check.model.InventoryStockCheckEntity;
import com.tuowei.erp.inventory.check.model.InventoryStockCheckLineEntity;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckCreateRequest;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckResponse;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckUpdateLineRequest;
import com.tuowei.erp.inventory.check.web.InventoryStockCheckUpdateRequest;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryStockCheckCommandService {

    private static final String STATUS_COUNTED = "COUNTED";
    private static final String STATUS_ADJUSTED = "ADJUSTED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final InventoryStockCheckMapper checkMapper;
    private final InventoryStockCheckLineMapper lineMapper;
    private final InventoryStockCheckNumberService numberService;
    private final InventoryPostingService inventoryPostingService;
    private final InventoryAdjustmentService adjustmentService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final WarehouseMapper warehouseMapper;
    private final ProductValidator productValidator;
    private final AccountPeriodGuard accountPeriodGuard;
    private final AttachmentService attachmentService;

    public InventoryStockCheckCommandService(
            InventoryStockCheckMapper checkMapper,
            InventoryStockCheckLineMapper lineMapper,
            InventoryStockCheckNumberService numberService,
            InventoryPostingService inventoryPostingService,
            InventoryAdjustmentService adjustmentService,
            AuditMetadataFactory auditMetadataFactory,
            WarehouseMapper warehouseMapper,
            ProductValidator productValidator,
            AccountPeriodGuard accountPeriodGuard,
            AttachmentService attachmentService
    ) {
        this.checkMapper = checkMapper;
        this.lineMapper = lineMapper;
        this.numberService = numberService;
        this.inventoryPostingService = inventoryPostingService;
        this.adjustmentService = adjustmentService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.warehouseMapper = warehouseMapper;
        this.productValidator = productValidator;
        this.accountPeriodGuard = accountPeriodGuard;
        this.attachmentService = attachmentService;
    }

    @Transactional
    public InventoryStockCheckResponse create(InventoryStockCheckCreateRequest request) {
        validateCreateRequest(request);
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        requireWarehouse(request.warehouseId(), audit.companyId(), audit.accountBookId());

        InventoryStockCheckEntity check = new InventoryStockCheckEntity();
        check.setCompanyId(audit.companyId());
        check.setAccountBookId(audit.accountBookId());
        check.setCheckNo(numberService.nextCheckNo(request.checkDate()));
        check.setWarehouseId(request.warehouseId());
        check.setCheckDate(request.checkDate());
        check.setStatus(STATUS_COUNTED);
        check.setDeletedFlag(0);
        check.setRemark(request.remark());
        fillCreateAudit(check, audit, now);
        checkMapper.insert(check);

        int lineNo = 1;
        List<Long> productIds = request.lines().stream().map(line -> line.productId()).toList();
        productValidator.requireProducts(productIds, audit.companyId(), audit.accountBookId());
        for (var requestLine : request.lines()) {
            BigDecimal bookQty = inventoryPostingService.getQtyOnHand(
                    request.warehouseId(), requestLine.productId(), requestLine.locationId(),
                    audit.companyId(), audit.accountBookId()
            );
            BigDecimal actualQty = ScalePrecision.quantity(requestLine.actualQty());
            BigDecimal diffQty = ScalePrecision.quantity(actualQty.subtract(bookQty));
            BigDecimal unitCost = ScalePrecision.quantity(requestLine.unitCost());
            BigDecimal diffAmount = ScalePrecision.amount(diffQty.abs().multiply(unitCost));

            InventoryStockCheckLineEntity line = new InventoryStockCheckLineEntity();
            line.setCompanyId(audit.companyId());
            line.setAccountBookId(audit.accountBookId());
            line.setCheckId(check.getId());
            line.setLineNo(lineNo++);
            line.setProductId(requestLine.productId());
            line.setLocationId(requestLine.locationId());
            line.setBookQty(bookQty);
            line.setActualQty(actualQty);
            line.setDifferenceQty(diffQty);
            line.setUnitCost(unitCost);
            line.setDifferenceAmount(diffAmount);
            line.setLotNo(requestLine.lotNo());
            line.setProductionDate(requestLine.productionDate());
            line.setExpiryDate(requestLine.expiryDate());
            line.setSerialNos(requestLine.serialNos());
            line.setRemark(requestLine.remark());
            fillCreateAudit(line, audit, now);
            lineMapper.insert(line);
        }
        return InventoryStockCheckQueryService.toResponse(check, selectLines(check));
    }

    @Transactional
    public InventoryStockCheckResponse postAdjustment(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryStockCheckEntity check = requireCheck(id, audit);
        if (!STATUS_COUNTED.equals(check.getStatus())) {
            throw new IllegalArgumentException("只有已录入盘点结果的盘点单可以生成调整");
        }
        attachmentService.requireIfConfigured(AttachmentBusinessType.INVENTORY_CHECK, check.getId());
        accountPeriodGuard.requireOpen(check.getCheckDate(), "库存盘点调整");

        List<InventoryAdjustmentLineRequest> adjustmentLines = selectLines(check).stream()
                .filter(line -> line.getDifferenceQty().compareTo(BigDecimal.ZERO) != 0)
                .map(line -> new InventoryAdjustmentLineRequest(
                        line.getProductId(),
                        line.getDifferenceQty().compareTo(BigDecimal.ZERO) > 0 ? "IN" : "OUT",
                        line.getDifferenceQty().abs(), line.getUnitCost(), line.getLotNo(),
                        line.getProductionDate(), line.getExpiryDate(), line.getLocationId(),
                        line.getSerialNos(), line.getRemark()
                ))
                .toList();
        if (adjustmentLines.isEmpty()) {
            throw new IllegalArgumentException("盘点无差异，不需要生成调整单");
        }

        InventoryAdjustmentResponse adjustment = adjustmentService.create(new InventoryAdjustmentCreateRequest(
                check.getWarehouseId(), check.getCheckDate(), "盘点调整：" + check.getCheckNo(), adjustmentLines
        ));
        InventoryAdjustmentResponse postedAdjustment = adjustmentService.post(adjustment.id());
        check.setStatus(STATUS_ADJUSTED);
        check.setGeneratedAdjustmentId(postedAdjustment.id());
        check.setGeneratedAdjustmentNo(postedAdjustment.adjustmentNo());
        check.setUpdatedBy(audit.userId());
        check.setUpdatedTime(audit.now());
        if (checkMapper.updateById(check) != 1) {
            throw new BusinessConflictException("库存盘点单已被其他操作修改，请重试");
        }
        return InventoryStockCheckQueryService.toResponse(check, selectLines(check));
    }

    @Transactional
    public InventoryStockCheckResponse update(Long id, InventoryStockCheckUpdateRequest request) {
        validateUpdateRequest(request);
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryStockCheckEntity check = requireCheck(id, audit);
        if (!STATUS_COUNTED.equals(check.getStatus())) {
            throw new IllegalArgumentException("只有已录入盘点结果的盘点单可以编辑");
        }

        Map<Long, InventoryStockCheckLineEntity> existingLines = selectLines(check).stream()
                .collect(Collectors.toMap(InventoryStockCheckLineEntity::getId, Function.identity()));
        for (InventoryStockCheckUpdateLineRequest requestLine : request.items()) {
            InventoryStockCheckLineEntity line = existingLines.get(requestLine.id());
            if (line == null || !Objects.equals(line.getProductId(), requestLine.productId())) {
                throw new IllegalArgumentException("库存盘点明细不存在");
            }
            BigDecimal actualQty = ScalePrecision.quantity(requestLine.actualQty());
            BigDecimal unitCost = ScalePrecision.quantity(requestLine.unitCost());
            BigDecimal diffQty = ScalePrecision.quantity(actualQty.subtract(line.getBookQty()));
            BigDecimal diffAmount = ScalePrecision.amount(diffQty.abs().multiply(unitCost));
            line.setActualQty(actualQty);
            line.setDifferenceQty(diffQty);
            line.setUnitCost(unitCost);
            line.setDifferenceAmount(diffAmount);
            line.setLotNo(requestLine.lotNo());
            line.setProductionDate(requestLine.productionDate());
            line.setExpiryDate(requestLine.expiryDate());
            if (requestLine.locationId() != null) {
                line.setLocationId(requestLine.locationId());
            }
            line.setSerialNos(requestLine.serialNos());
            line.setRemark(requestLine.remark());
            line.setUpdatedBy(audit.userId());
            line.setUpdatedTime(audit.now());
            if (lineMapper.updateById(line) != 1) {
                throw new BusinessConflictException("库存盘点明细已被其他操作修改，请重试");
            }
        }
        check.setUpdatedBy(audit.userId());
        check.setUpdatedTime(audit.now());
        if (checkMapper.updateById(check) != 1) {
            throw new BusinessConflictException("库存盘点单已被其他操作修改，请重试");
        }
        return InventoryStockCheckQueryService.toResponse(check, selectLines(check));
    }

    @Transactional
    public InventoryStockCheckResponse cancel(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryStockCheckEntity check = requireCheck(id, audit);
        if (STATUS_ADJUSTED.equals(check.getStatus())) {
            throw new IllegalArgumentException("已生成调整的盘点单不能取消");
        }
        if (STATUS_CANCELLED.equals(check.getStatus())) {
            throw new IllegalArgumentException("盘点单已经取消");
        }
        check.setStatus(STATUS_CANCELLED);
        check.setUpdatedBy(audit.userId());
        check.setUpdatedTime(audit.now());
        if (checkMapper.updateById(check) != 1) {
            throw new BusinessConflictException("库存盘点单已被其他操作修改，请重试");
        }
        return InventoryStockCheckQueryService.toResponse(check, selectLines(check));
    }

    private InventoryStockCheckEntity requireCheck(Long id, AuditMetadata audit) {
        InventoryStockCheckEntity check = checkMapper.selectById(id);
        if (check == null || Integer.valueOf(1).equals(check.getDeletedFlag())
                || !Objects.equals(check.getCompanyId(), audit.companyId())
                || !Objects.equals(check.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("库存盘点单不存在");
        }
        return check;
    }

    private WarehouseEntity requireWarehouse(Long id, Long companyId, Long accountBookId) {
        WarehouseEntity warehouse = warehouseMapper.selectById(id);
        if (warehouse == null || warehouse.getDeletedFlag() == null || warehouse.getDeletedFlag() != 0
                || !"ACTIVE".equalsIgnoreCase(warehouse.getStatus())
                || !Objects.equals(warehouse.getCompanyId(), companyId)
                || !Objects.equals(warehouse.getAccountBookId(), accountBookId)) {
            throw new IllegalArgumentException("仓库不存在或已停用");
        }
        return warehouse;
    }

    private List<InventoryStockCheckLineEntity> selectLines(InventoryStockCheckEntity check) {
        return lineMapper.selectList(new LambdaQueryWrapper<InventoryStockCheckLineEntity>()
                .eq(InventoryStockCheckLineEntity::getCompanyId, check.getCompanyId())
                .eq(InventoryStockCheckLineEntity::getAccountBookId, check.getAccountBookId())
                .eq(InventoryStockCheckLineEntity::getCheckId, check.getId())
                .orderByAsc(InventoryStockCheckLineEntity::getLineNo));
    }

    private void validateCreateRequest(InventoryStockCheckCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("库存盘点请求不能为空");
        }
        if (request.lines() == null || request.lines().isEmpty()
                || request.lines().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("库存盘点明细不能为空");
        }
    }

    private void validateUpdateRequest(InventoryStockCheckUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("库存盘点请求不能为空");
        }
        if (request.items() == null || request.items().isEmpty()
                || request.items().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("库存盘点明细不能为空");
        }
    }

    private void fillCreateAudit(InventoryStockCheckEntity check, AuditMetadata audit, LocalDateTime now) {
        check.setCreatedBy(audit.userId());
        check.setCreatedTime(now);
        check.setUpdatedBy(audit.userId());
        check.setUpdatedTime(now);
        check.setVersion(0);
    }

    private void fillCreateAudit(InventoryStockCheckLineEntity line, AuditMetadata audit, LocalDateTime now) {
        line.setCreatedBy(audit.userId());
        line.setCreatedTime(now);
        line.setUpdatedBy(audit.userId());
        line.setUpdatedTime(now);
        line.setVersion(0);
    }
}

package com.tuowei.erp.inventory.adjust.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.adjust.mapper.InventoryAdjustmentLineMapper;
import com.tuowei.erp.inventory.adjust.mapper.InventoryAdjustmentMapper;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentEntity;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentLineEntity;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentCreateRequest;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentLineRequest;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentLineResponse;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentPageQuery;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentResponse;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
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
import java.util.stream.Collectors;

@Service
public class InventoryAdjustmentService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_POSTED = "POSTED";
    private static final String BIZ_TYPE = "INVENTORY_ADJUSTMENT";

    private final InventoryAdjustmentMapper adjustmentMapper;
    private final InventoryAdjustmentLineMapper lineMapper;
    private final InventoryAdjustmentNumberService numberService;
    private final InventoryPostingService inventoryPostingService;
    private final InventorySerialNumberService inventorySerialNumberService;
    private final FinancePostingService financePostingService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final WarehouseMapper warehouseMapper;
    private final ProductValidator productValidator;
    private final AccountPeriodGuard accountPeriodGuard;
    private final AttachmentService attachmentService;

    public InventoryAdjustmentService(
            InventoryAdjustmentMapper adjustmentMapper,
            InventoryAdjustmentLineMapper lineMapper,
            InventoryAdjustmentNumberService numberService,
            InventoryPostingService inventoryPostingService,
            InventorySerialNumberService inventorySerialNumberService,
            FinancePostingService financePostingService,
            AuditMetadataFactory auditMetadataFactory,
            WarehouseMapper warehouseMapper,
            ProductValidator productValidator,
            AccountPeriodGuard accountPeriodGuard,
            AttachmentService attachmentService
    ) {
        this.adjustmentMapper = adjustmentMapper;
        this.lineMapper = lineMapper;
        this.numberService = numberService;
        this.inventoryPostingService = inventoryPostingService;
        this.inventorySerialNumberService = inventorySerialNumberService;
        this.financePostingService = financePostingService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.warehouseMapper = warehouseMapper;
        this.productValidator = productValidator;
        this.accountPeriodGuard = accountPeriodGuard;
        this.attachmentService = attachmentService;
    }

    @Transactional
    public InventoryAdjustmentResponse create(InventoryAdjustmentCreateRequest request) {
        List<InventoryAdjustmentLineRequest> requestLines = requireLines(request);
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        requireWarehouse(request.warehouseId(), audit.companyId(), audit.accountBookId());
        List<Long> productIds = requestLines.stream()
                .map(line -> line.productId())
                .toList();
        productValidator.requireProducts(productIds, audit.companyId(), audit.accountBookId());
        List<CalculatedLine> calculatedLines = requestLines.stream()
                .map(line -> calculateLine(line, audit.companyId(), audit.accountBookId()))
                .toList();

        InventoryAdjustmentEntity adjustment = new InventoryAdjustmentEntity();
        adjustment.setCompanyId(audit.companyId());
        adjustment.setAccountBookId(audit.accountBookId());
        adjustment.setAdjustmentNo(numberService.nextAdjustmentNo(request.adjustmentDate()));
        adjustment.setWarehouseId(request.warehouseId());
        adjustment.setAdjustmentDate(request.adjustmentDate());
        adjustment.setStatus(STATUS_DRAFT);
        adjustment.setTotalQuantity(calculatedLines.stream()
                .map(line -> line.entity().getQty())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        adjustment.setTotalQuantity(ScalePrecision.quantity(adjustment.getTotalQuantity()));
        adjustment.setTotalAmount(calculatedLines.stream()
                .map(line -> line.entity().getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        adjustment.setTotalAmount(ScalePrecision.amount(adjustment.getTotalAmount()));
        adjustment.setDeletedFlag(0);
        adjustment.setRemark(request.remark());
        fillCreateAudit(adjustment, audit, now);
        adjustmentMapper.insert(adjustment);

        int lineNo = 1;
        for (CalculatedLine calculatedLine : calculatedLines) {
            InventoryAdjustmentLineEntity line = calculatedLine.entity();
            line.setAdjustmentId(adjustment.getId());
            line.setLineNo(lineNo++);
            fillCreateAudit(line, audit, now);
            lineMapper.insert(line);
        }

        return toResponse(adjustment);
    }

    private List<InventoryAdjustmentLineRequest> requireLines(InventoryAdjustmentCreateRequest request) {
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            throw new IllegalArgumentException("库存调整单明细不能为空");
        }
        return request.lines();
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryAdjustmentResponse> list(InventoryAdjustmentPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryAdjustmentPageQuery safeQuery = query == null ? new InventoryAdjustmentPageQuery() : query;

        LambdaQueryWrapper<InventoryAdjustmentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryAdjustmentEntity::getCompanyId, audit.companyId())
               .eq(InventoryAdjustmentEntity::getAccountBookId, audit.accountBookId())
               .eq(InventoryAdjustmentEntity::getDeletedFlag, 0);

        if (safeQuery.getAdjustmentNo() != null && !safeQuery.getAdjustmentNo().trim().isEmpty()) {
            wrapper.like(InventoryAdjustmentEntity::getAdjustmentNo, safeQuery.getAdjustmentNo().trim());
        }
        if (safeQuery.getWarehouseId() != null) {
            wrapper.eq(InventoryAdjustmentEntity::getWarehouseId, safeQuery.getWarehouseId());
        }
        if (safeQuery.getStatus() != null && !safeQuery.getStatus().trim().isEmpty()) {
            wrapper.eq(InventoryAdjustmentEntity::getStatus, safeQuery.getStatus().trim());
        }
        if (safeQuery.getDateFrom() != null) {
            wrapper.ge(InventoryAdjustmentEntity::getAdjustmentDate, safeQuery.getDateFrom());
        }
        if (safeQuery.getDateTo() != null) {
            wrapper.le(InventoryAdjustmentEntity::getAdjustmentDate, safeQuery.getDateTo());
        }

        wrapper.orderByDesc(InventoryAdjustmentEntity::getCreatedTime);

        Page<InventoryAdjustmentEntity> page = new Page<>(safeQuery.getPageNo(), safeQuery.getPageSize());
        IPage<InventoryAdjustmentEntity> result = adjustmentMapper.selectPage(page, wrapper);

        Map<Long, List<InventoryAdjustmentLineEntity>> linesByAdjustment = selectLinesForPage(audit, result.getRecords());
        List<InventoryAdjustmentResponse> responses = result.getRecords().stream()
                .map(adjustment -> toResponse(adjustment,
                        linesByAdjustment.getOrDefault(adjustment.getId(), List.of())))
                .toList();

        return new PageResponse<>(
                result.getCurrent(),
                result.getSize(),
                result.getTotal(),
                responses
        );
    }

    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getById(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryAdjustmentEntity adjustment = adjustmentMapper.selectById(id);
        if (adjustment == null || Integer.valueOf(1).equals(adjustment.getDeletedFlag())
                || !Objects.equals(adjustment.getCompanyId(), audit.companyId())
                || !Objects.equals(adjustment.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("库存调整单不存在");
        }
        return toResponse(adjustment);
    }

    @Transactional
    public InventoryAdjustmentResponse post(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryAdjustmentEntity adjustment = adjustmentMapper.selectById(id);
        if (adjustment == null || Integer.valueOf(1).equals(adjustment.getDeletedFlag())
                || !Objects.equals(adjustment.getCompanyId(), audit.companyId())
                || !Objects.equals(adjustment.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("库存调整单不存在");
        }
        if (!STATUS_DRAFT.equals(adjustment.getStatus())) {
            throw new IllegalArgumentException("只有草稿状态的库存调整单可以过账");
        }
        attachmentService.requireIfConfigured(AttachmentBusinessType.INVENTORY_ADJUSTMENT, adjustment.getId());
        accountPeriodGuard.requireOpen(adjustment.getAdjustmentDate(), "库存调整过账");

        List<InventoryAdjustmentLineEntity> lines = selectLines(adjustment);
        for (InventoryAdjustmentLineEntity line : lines) {
            InventoryPostingCommand command = new InventoryPostingCommand(
                    adjustment.getWarehouseId(),
                    line.getProductId(),
                    BIZ_TYPE,
                    adjustment.getAdjustmentNo(),
                    line.getId(),
                    line.getQty(),
                    line.getAmount(),
                    line.getReason(),
                    adjustment.getAdjustmentDate(),
                    line.getLotNo(),
                    line.getProductionDate(),
                    line.getExpiryDate(),
                    line.getLocationId()
            );
            if ("IN".equals(line.getDirection())) {
                inventoryPostingService.postInbound(command, audit);
                inventorySerialNumberService.registerInboundSerials(
                        line.getProductId(),
                        adjustment.getWarehouseId(),
                        line.getLocationId(),
                        line.getSerialNos(),
                        BIZ_TYPE,
                        adjustment.getAdjustmentNo(),
                        line.getQty(),
                        audit
                );
            } else if ("OUT".equals(line.getDirection())) {
                BigDecimal outboundAmount = inventoryPostingService.postOutbound(command, audit, "库存不足，不能执行库存调整");
                line.setAmount(ScalePrecision.amount(ScalePrecision.zeroDefault(outboundAmount)));
                inventorySerialNumberService.issueOutboundSerials(
                        line.getProductId(),
                        line.getSerialNos(),
                        BIZ_TYPE,
                        adjustment.getAdjustmentNo(),
                        line.getQty(),
                        audit
                );
            } else {
                throw new IllegalArgumentException("库存调整方向不正确");
            }
        }
        financePostingService.recordInventoryAdjustment(adjustment, lines, audit);

        adjustment.setStatus(STATUS_POSTED);
        adjustment.setUpdatedBy(audit.userId());
        adjustment.setUpdatedTime(audit.now());
        if (adjustmentMapper.updateById(adjustment) != 1) {
            throw new BusinessConflictException("库存调整单已被其他操作修改，请重试");
        }
        return toResponse(adjustment);
    }

    @Transactional
    public InventoryAdjustmentResponse cancel(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryAdjustmentEntity adjustment = adjustmentMapper.selectById(id);
        if (adjustment == null || Integer.valueOf(1).equals(adjustment.getDeletedFlag())
                || !Objects.equals(adjustment.getCompanyId(), audit.companyId())
                || !Objects.equals(adjustment.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("库存调整单不存在");
        }
        if (STATUS_POSTED.equals(adjustment.getStatus())) {
            throw new IllegalArgumentException("已过账的库存调整单不能取消");
        }
        if ("CANCELLED".equals(adjustment.getStatus())) {
            throw new IllegalArgumentException("库存调整单已经取消");
        }

        adjustment.setStatus("CANCELLED");
        adjustment.setUpdatedBy(audit.userId());
        adjustment.setUpdatedTime(audit.now());
        if (adjustmentMapper.updateById(adjustment) != 1) {
            throw new BusinessConflictException("库存调整单已被其他操作修改，请重试");
        }
        return toResponse(adjustment);
    }

    private CalculatedLine calculateLine(InventoryAdjustmentLineRequest request, Long companyId, Long accountBookId) {
        if (!"IN".equals(request.direction()) && !"OUT".equals(request.direction())) {
            throw new IllegalArgumentException("库存调整方向不正确");
        }
        BigDecimal qty = ScalePrecision.quantity(request.qty());
        BigDecimal unitCost = ScalePrecision.quantity(request.unitCost());
        BigDecimal amount = ScalePrecision.amount(qty.multiply(unitCost));

        InventoryAdjustmentLineEntity line = new InventoryAdjustmentLineEntity();
        line.setCompanyId(companyId);
        line.setAccountBookId(accountBookId);
        line.setProductId(request.productId());
        line.setDirection(request.direction());
        line.setQty(qty);
        line.setUnitCost(unitCost);
        line.setAmount(amount);
        line.setLotNo(request.lotNo());
        line.setProductionDate(request.productionDate());
        line.setExpiryDate(request.expiryDate());
        line.setLocationId(request.locationId());
        line.setSerialNos(request.serialNos());
        line.setReason(request.reason());
        line.setRemark(request.reason());
        line.setVersion(0);
        return new CalculatedLine(line);
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

    private InventoryAdjustmentResponse toResponse(InventoryAdjustmentEntity adjustment) {
        return toResponse(adjustment, selectLines(adjustment));
    }

    private InventoryAdjustmentResponse toResponse(InventoryAdjustmentEntity adjustment,
                                                   List<InventoryAdjustmentLineEntity> lineEntities) {
        List<InventoryAdjustmentLineResponse> lines = lineEntities.stream()
                .map(line -> new InventoryAdjustmentLineResponse(
                        line.getId(),
                        line.getLineNo(),
                        line.getProductId(),
                        line.getDirection(),
                        line.getQty(),
                        line.getUnitCost(),
                        line.getAmount(),
                        line.getLotNo(),
                        line.getProductionDate(),
                        line.getExpiryDate(),
                        line.getLocationId(),
                        line.getSerialNos(),
                        line.getReason(),
                        line.getRemark()
                ))
                .toList();

        return new InventoryAdjustmentResponse(
                adjustment.getId(),
                adjustment.getAdjustmentNo(),
                adjustment.getWarehouseId(),
                adjustment.getAdjustmentDate(),
                adjustment.getStatus(),
                adjustment.getTotalQuantity(),
                adjustment.getTotalAmount(),
                adjustment.getRemark(),
                lines
        );
    }

    private List<InventoryAdjustmentLineEntity> selectLines(InventoryAdjustmentEntity adjustment) {
        return lineMapper.selectList(new LambdaQueryWrapper<InventoryAdjustmentLineEntity>()
                .eq(InventoryAdjustmentLineEntity::getCompanyId, adjustment.getCompanyId())
                .eq(InventoryAdjustmentLineEntity::getAccountBookId, adjustment.getAccountBookId())
                .eq(InventoryAdjustmentLineEntity::getAdjustmentId, adjustment.getId())
                .orderByAsc(InventoryAdjustmentLineEntity::getLineNo));
    }

    /**
     * 整页一次性批量查明细，避免列表逐行查询造成的 N+1。返回按调整单 ID 分组、组内按行号升序的明细。
     */
    private Map<Long, List<InventoryAdjustmentLineEntity>> selectLinesForPage(
            AuditMetadata audit, List<InventoryAdjustmentEntity> adjustments) {
        if (adjustments.isEmpty()) {
            return Map.of();
        }
        List<Long> adjustmentIds = adjustments.stream()
                .map(InventoryAdjustmentEntity::getId)
                .toList();
        List<InventoryAdjustmentLineEntity> allLines = lineMapper.selectList(
                new LambdaQueryWrapper<InventoryAdjustmentLineEntity>()
                        .eq(InventoryAdjustmentLineEntity::getCompanyId, audit.companyId())
                        .eq(InventoryAdjustmentLineEntity::getAccountBookId, audit.accountBookId())
                        .in(InventoryAdjustmentLineEntity::getAdjustmentId, adjustmentIds)
                        .orderByAsc(InventoryAdjustmentLineEntity::getLineNo));
        return allLines.stream()
                .collect(Collectors.groupingBy(InventoryAdjustmentLineEntity::getAdjustmentId));
    }

    private void fillCreateAudit(InventoryAdjustmentEntity adjustment, AuditMetadata audit, LocalDateTime now) {
        adjustment.setCreatedBy(audit.userId());
        adjustment.setCreatedTime(now);
        adjustment.setUpdatedBy(audit.userId());
        adjustment.setUpdatedTime(now);
        adjustment.setVersion(0);
    }

    private void fillCreateAudit(InventoryAdjustmentLineEntity line, AuditMetadata audit, LocalDateTime now) {
        line.setCreatedBy(audit.userId());
        line.setCreatedTime(now);
        line.setUpdatedBy(audit.userId());
        line.setUpdatedTime(now);
    }

    private record CalculatedLine(InventoryAdjustmentLineEntity entity) {
    }
}

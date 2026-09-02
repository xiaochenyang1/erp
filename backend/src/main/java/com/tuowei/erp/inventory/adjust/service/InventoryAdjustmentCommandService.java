package com.tuowei.erp.inventory.adjust.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.adjust.mapper.InventoryAdjustmentLineMapper;
import com.tuowei.erp.inventory.adjust.mapper.InventoryAdjustmentMapper;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentEntity;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentLineEntity;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentCreateRequest;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentLineRequest;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentResponse;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class InventoryAdjustmentCommandService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_POSTED = "POSTED";
    private static final String STATUS_CANCELLED = "CANCELLED";
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
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final UserMapper userMapper;

    @Autowired
    public InventoryAdjustmentCommandService(
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
            AttachmentService attachmentService,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            UserMapper userMapper
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
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.userMapper = userMapper;
    }

    public InventoryAdjustmentCommandService(
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
        this(adjustmentMapper, lineMapper, numberService, inventoryPostingService, inventorySerialNumberService,
                financePostingService, auditMetadataFactory, warehouseMapper, productValidator, accountPeriodGuard,
                attachmentService, null, null, null);
    }

    @Transactional
    public InventoryAdjustmentResponse create(InventoryAdjustmentCreateRequest request) {
        List<InventoryAdjustmentLineRequest> requestLines = requireLines(request);
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        requireWarehouse(request.warehouseId(), audit.companyId(), audit.accountBookId());
        productValidator.requireProducts(
                requestLines.stream().map(InventoryAdjustmentLineRequest::productId).toList(),
                audit.companyId(), audit.accountBookId()
        );
        List<InventoryAdjustmentLineEntity> lines = requestLines.stream()
                .map(line -> calculateLine(line, audit.companyId(), audit.accountBookId()))
                .toList();

        InventoryAdjustmentEntity adjustment = new InventoryAdjustmentEntity();
        adjustment.setCompanyId(audit.companyId());
        adjustment.setAccountBookId(audit.accountBookId());
        adjustment.setAdjustmentNo(numberService.nextAdjustmentNo(request.adjustmentDate()));
        adjustment.setWarehouseId(request.warehouseId());
        adjustment.setAdjustmentDate(request.adjustmentDate());
        adjustment.setStatus(STATUS_DRAFT);
        adjustment.setTotalQuantity(ScalePrecision.quantity(lines.stream()
                .map(InventoryAdjustmentLineEntity::getQty).reduce(BigDecimal.ZERO, BigDecimal::add)));
        adjustment.setTotalAmount(ScalePrecision.amount(lines.stream()
                .map(InventoryAdjustmentLineEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)));
        adjustment.setDeletedFlag(0);
        adjustment.setRemark(request.remark());
        fillCreateAudit(adjustment, audit, now);
        assertCanView(adjustment);
        adjustmentMapper.insert(adjustment);
        int lineNo = 1;
        for (InventoryAdjustmentLineEntity line : lines) {
            line.setAdjustmentId(adjustment.getId());
            line.setLineNo(lineNo++);
            fillCreateAudit(line, audit, now);
            lineMapper.insert(line);
        }
        return InventoryAdjustmentQueryService.toResponse(adjustment, lines);
    }

    @Transactional
    public InventoryAdjustmentResponse post(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryAdjustmentEntity adjustment = requireAdjustment(id, audit);
        if (!STATUS_DRAFT.equals(adjustment.getStatus())) {
            throw new IllegalArgumentException("只有草稿状态的库存调整单可以过账");
        }
        attachmentService.requireIfConfigured(AttachmentBusinessType.INVENTORY_ADJUSTMENT, adjustment.getId());
        accountPeriodGuard.requireOpen(adjustment.getAdjustmentDate(), "库存调整过账");
        List<InventoryAdjustmentLineEntity> lines = selectLines(adjustment);
        for (InventoryAdjustmentLineEntity line : lines) {
            InventoryPostingCommand command = new InventoryPostingCommand(
                    adjustment.getWarehouseId(), line.getProductId(), BIZ_TYPE, adjustment.getAdjustmentNo(),
                    line.getId(), line.getQty(), line.getAmount(), line.getReason(), adjustment.getAdjustmentDate(),
                    line.getLotNo(), line.getProductionDate(), line.getExpiryDate(), line.getLocationId()
            );
            if ("IN".equals(line.getDirection())) {
                inventoryPostingService.postInbound(command, audit);
                inventorySerialNumberService.registerInboundSerials(
                        line.getProductId(), adjustment.getWarehouseId(), line.getLocationId(), line.getSerialNos(),
                        BIZ_TYPE, adjustment.getAdjustmentNo(), line.getQty(), audit
                );
            } else if ("OUT".equals(line.getDirection())) {
                BigDecimal outboundAmount = inventoryPostingService.postOutbound(
                        command, audit, "库存不足，不能执行库存调整");
                line.setAmount(ScalePrecision.amount(ScalePrecision.zeroDefault(outboundAmount)));
                inventorySerialNumberService.issueOutboundSerials(
                        line.getProductId(), line.getSerialNos(), BIZ_TYPE, adjustment.getAdjustmentNo(),
                        line.getQty(), audit
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
        return InventoryAdjustmentQueryService.toResponse(adjustment, lines);
    }

    @Transactional
    public InventoryAdjustmentResponse cancel(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryAdjustmentEntity adjustment = requireAdjustment(id, audit);
        if (STATUS_POSTED.equals(adjustment.getStatus())) {
            throw new IllegalArgumentException("已过账的库存调整单不能取消");
        }
        if (STATUS_CANCELLED.equals(adjustment.getStatus())) {
            throw new IllegalArgumentException("库存调整单已经取消");
        }
        adjustment.setStatus(STATUS_CANCELLED);
        adjustment.setUpdatedBy(audit.userId());
        adjustment.setUpdatedTime(audit.now());
        if (adjustmentMapper.updateById(adjustment) != 1) {
            throw new BusinessConflictException("库存调整单已被其他操作修改，请重试");
        }
        return InventoryAdjustmentQueryService.toResponse(adjustment, selectLines(adjustment));
    }

    private List<InventoryAdjustmentLineRequest> requireLines(InventoryAdjustmentCreateRequest request) {
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            throw new IllegalArgumentException("库存调整单明细不能为空");
        }
        return request.lines();
    }

    private InventoryAdjustmentEntity requireAdjustment(Long id, AuditMetadata audit) {
        InventoryAdjustmentEntity adjustment = adjustmentMapper.selectById(id);
        if (adjustment == null || Integer.valueOf(1).equals(adjustment.getDeletedFlag())
                || !Objects.equals(adjustment.getCompanyId(), audit.companyId())
                || !Objects.equals(adjustment.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("库存调整单不存在");
        }
        assertCanView(adjustment);
        return adjustment;
    }

    private void assertCanView(InventoryAdjustmentEntity adjustment) {
        if (currentUserContext == null) {
            return;
        }
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = adjustment.getCreatedBy() == null ? null : userMapper.selectById(adjustment.getCreatedBy());
        dataScopeService.assertCanViewInventoryAdjustment(
                adjustment, currentUser, snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId());
    }

    private InventoryAdjustmentLineEntity calculateLine(
            InventoryAdjustmentLineRequest request, Long companyId, Long accountBookId) {
        if (!"IN".equals(request.direction()) && !"OUT".equals(request.direction())) {
            throw new IllegalArgumentException("库存调整方向不正确");
        }
        BigDecimal qty = ScalePrecision.quantity(request.qty());
        BigDecimal unitCost = ScalePrecision.quantity(request.unitCost());
        InventoryAdjustmentLineEntity line = new InventoryAdjustmentLineEntity();
        line.setCompanyId(companyId);
        line.setAccountBookId(accountBookId);
        line.setProductId(request.productId());
        line.setDirection(request.direction());
        line.setQty(qty);
        line.setUnitCost(unitCost);
        line.setAmount(ScalePrecision.amount(qty.multiply(unitCost)));
        line.setLotNo(request.lotNo());
        line.setProductionDate(request.productionDate());
        line.setExpiryDate(request.expiryDate());
        line.setLocationId(request.locationId());
        line.setSerialNos(request.serialNos());
        line.setReason(request.reason());
        line.setRemark(request.reason());
        line.setVersion(0);
        return line;
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

    private List<InventoryAdjustmentLineEntity> selectLines(InventoryAdjustmentEntity adjustment) {
        return lineMapper.selectList(new LambdaQueryWrapper<InventoryAdjustmentLineEntity>()
                .eq(InventoryAdjustmentLineEntity::getCompanyId, adjustment.getCompanyId())
                .eq(InventoryAdjustmentLineEntity::getAccountBookId, adjustment.getAccountBookId())
                .eq(InventoryAdjustmentLineEntity::getAdjustmentId, adjustment.getId())
                .orderByAsc(InventoryAdjustmentLineEntity::getLineNo));
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
        line.setVersion(0);
    }
}

package com.tuowei.erp.inventory.transfer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.inventory.stock.service.LotAllocation;
import com.tuowei.erp.inventory.transfer.mapper.InventoryTransferLineMapper;
import com.tuowei.erp.inventory.transfer.mapper.InventoryTransferMapper;
import com.tuowei.erp.inventory.transfer.model.InventoryTransferEntity;
import com.tuowei.erp.inventory.transfer.model.InventoryTransferLineEntity;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferCreateRequest;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferLineRequest;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferResponse;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.system.attachment.service.AttachmentBusinessType;
import com.tuowei.erp.system.attachment.service.AttachmentService;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class InventoryTransferCommandService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_POSTED = "POSTED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String BIZ_TYPE = "INVENTORY_TRANSFER";

    private final InventoryTransferMapper transferMapper;
    private final InventoryTransferLineMapper lineMapper;
    private final InventoryTransferNumberService numberService;
    private final InventoryPostingService inventoryPostingService;
    private final InventorySerialNumberService inventorySerialNumberService;
    private final AuditMetadataFactory auditMetadataFactory;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final UserMapper userMapper;
    private final WarehouseMapper warehouseMapper;
    private final ProductValidator productValidator;
    private final AccountPeriodGuard accountPeriodGuard;
    private final AttachmentService attachmentService;

    public InventoryTransferCommandService(
            InventoryTransferMapper transferMapper,
            InventoryTransferLineMapper lineMapper,
            InventoryTransferNumberService numberService,
            InventoryPostingService inventoryPostingService,
            InventorySerialNumberService inventorySerialNumberService,
            AuditMetadataFactory auditMetadataFactory,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            UserMapper userMapper,
            WarehouseMapper warehouseMapper,
            ProductValidator productValidator,
            AccountPeriodGuard accountPeriodGuard,
            AttachmentService attachmentService
    ) {
        this.transferMapper = transferMapper;
        this.lineMapper = lineMapper;
        this.numberService = numberService;
        this.inventoryPostingService = inventoryPostingService;
        this.inventorySerialNumberService = inventorySerialNumberService;
        this.auditMetadataFactory = auditMetadataFactory;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.userMapper = userMapper;
        this.warehouseMapper = warehouseMapper;
        this.productValidator = productValidator;
        this.accountPeriodGuard = accountPeriodGuard;
        this.attachmentService = attachmentService;
    }

    @Transactional
    public InventoryTransferResponse create(InventoryTransferCreateRequest request) {
        List<InventoryTransferLineRequest> requestLines = requireLines(request);
        if (request.fromWarehouseId().equals(request.toWarehouseId())) {
            throw new IllegalArgumentException("调出仓和调入仓不能相同");
        }
        AuditMetadata audit = auditMetadataFactory.current();
        LocalDateTime now = audit.now();
        requireWarehouse(request.fromWarehouseId(), audit.companyId(), audit.accountBookId());
        requireWarehouse(request.toWarehouseId(), audit.companyId(), audit.accountBookId());
        productValidator.requireProducts(
                requestLines.stream().map(InventoryTransferLineRequest::productId).toList(),
                audit.companyId(), audit.accountBookId()
        );
        List<InventoryTransferLineEntity> lines = requestLines.stream()
                .map(line -> calculateLine(line, audit.companyId(), audit.accountBookId()))
                .toList();

        InventoryTransferEntity transfer = new InventoryTransferEntity();
        transfer.setCompanyId(audit.companyId());
        transfer.setAccountBookId(audit.accountBookId());
        transfer.setTransferNo(numberService.nextTransferNo(request.transferDate()));
        transfer.setFromWarehouseId(request.fromWarehouseId());
        transfer.setToWarehouseId(request.toWarehouseId());
        transfer.setTransferDate(request.transferDate());
        transfer.setStatus(STATUS_DRAFT);
        transfer.setTotalQuantity(ScalePrecision.quantity(lines.stream()
                .map(InventoryTransferLineEntity::getQty).reduce(BigDecimal.ZERO, BigDecimal::add)));
        transfer.setTotalAmount(ScalePrecision.amount(lines.stream()
                .map(InventoryTransferLineEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)));
        transfer.setDeletedFlag(0);
        transfer.setRemark(request.remark());
        fillCreateAudit(transfer, audit, now);
        assertCanView(transfer);
        transferMapper.insert(transfer);
        int lineNo = 1;
        for (InventoryTransferLineEntity line : lines) {
            line.setTransferId(transfer.getId());
            line.setLineNo(lineNo++);
            fillCreateAudit(line, audit, now);
            lineMapper.insert(line);
        }
        return InventoryTransferQueryService.toResponse(transfer, lines);
    }

    @Transactional
    public InventoryTransferResponse post(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryTransferEntity transfer = requireTransfer(id);
        if (!STATUS_DRAFT.equals(transfer.getStatus())) {
            throw new IllegalArgumentException("只有草稿状态的库存调拨单可以过账");
        }
        assertCanView(transfer);
        attachmentService.requireIfConfigured(AttachmentBusinessType.INVENTORY_TRANSFER, transfer.getId());
        accountPeriodGuard.requireOpen(transfer.getTransferDate(), "库存调拨过账");
        List<InventoryTransferLineEntity> lines = selectLines(transfer);
        for (InventoryTransferLineEntity line : lines) {
            InventoryPostingCommand outbound = new InventoryPostingCommand(
                    transfer.getFromWarehouseId(), line.getProductId(), BIZ_TYPE, transfer.getTransferNo(),
                    line.getId(), line.getQty(), line.getAmount(), line.getRemark(), transfer.getTransferDate(),
                    line.getLotNo(), line.getProductionDate(), line.getExpiryDate(), line.getFromLocationId()
            );
            List<LotAllocation> allocations = inventoryPostingService.postOutboundWithAllocations(
                    outbound, audit, "库存不足，不能执行库存调拨");
            for (LotAllocation allocation : allocations) {
                inventoryPostingService.postInbound(new InventoryPostingCommand(
                        transfer.getToWarehouseId(), line.getProductId(), BIZ_TYPE, transfer.getTransferNo(),
                        line.getId(), allocation.qty(), allocation.amount(), line.getRemark(), transfer.getTransferDate(),
                        allocation.lot() == null ? line.getLotNo() : allocation.lot().getLotNo(),
                        allocation.lot() == null ? line.getProductionDate() : allocation.lot().getProductionDate(),
                        allocation.lot() == null ? line.getExpiryDate() : allocation.lot().getExpiryDate(),
                        line.getToLocationId()
                ), audit);
            }
            inventorySerialNumberService.moveInStockSerials(
                    line.getProductId(), transfer.getToWarehouseId(), line.getToLocationId(),
                    line.getSerialNos(), line.getQty(), audit
            );
        }
        transfer.setStatus(STATUS_POSTED);
        transfer.setUpdatedBy(audit.userId());
        transfer.setUpdatedTime(audit.now());
        if (transferMapper.updateById(transfer) != 1) {
            throw new BusinessConflictException("库存调拨单已被其他操作修改，请重试");
        }
        return InventoryTransferQueryService.toResponse(transfer, lines);
    }

    @Transactional
    public InventoryTransferResponse cancel(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryTransferEntity transfer = requireTransfer(id);
        if (STATUS_POSTED.equals(transfer.getStatus())) {
            throw new IllegalArgumentException("已过账的库存调拨单不能取消");
        }
        if (STATUS_CANCELLED.equals(transfer.getStatus())) {
            throw new IllegalArgumentException("库存调拨单已经取消");
        }
        assertCanView(transfer);
        transfer.setStatus(STATUS_CANCELLED);
        transfer.setUpdatedBy(audit.userId());
        transfer.setUpdatedTime(audit.now());
        if (transferMapper.updateById(transfer) != 1) {
            throw new BusinessConflictException("库存调拨单已被其他操作修改，请重试");
        }
        return InventoryTransferQueryService.toResponse(transfer, selectLines(transfer));
    }

    private List<InventoryTransferLineRequest> requireLines(InventoryTransferCreateRequest request) {
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            throw new IllegalArgumentException("库存调拨单明细不能为空");
        }
        return request.lines();
    }

    private InventoryTransferEntity requireTransfer(Long id) {
        InventoryTransferEntity transfer = transferMapper.selectById(id);
        if (transfer == null || Integer.valueOf(1).equals(transfer.getDeletedFlag())) {
            throw new IllegalArgumentException("库存调拨单不存在");
        }
        return transfer;
    }

    private InventoryTransferLineEntity calculateLine(
            InventoryTransferLineRequest request, Long companyId, Long accountBookId) {
        BigDecimal qty = ScalePrecision.quantity(request.qty());
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("调拨数量必须大于0");
        }
        BigDecimal unitCost = ScalePrecision.quantity(request.unitCost());
        if (unitCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("调拨单价不能小于0");
        }
        InventoryTransferLineEntity line = new InventoryTransferLineEntity();
        line.setCompanyId(companyId);
        line.setAccountBookId(accountBookId);
        line.setProductId(request.productId());
        line.setQty(qty);
        line.setUnitCost(unitCost);
        line.setAmount(ScalePrecision.amount(qty.multiply(unitCost)));
        line.setLotNo(request.lotNo());
        line.setProductionDate(request.productionDate());
        line.setExpiryDate(request.expiryDate());
        line.setFromLocationId(request.fromLocationId());
        line.setToLocationId(request.toLocationId());
        line.setSerialNos(request.serialNos());
        line.setRemark(request.remark());
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

    private List<InventoryTransferLineEntity> selectLines(InventoryTransferEntity transfer) {
        return lineMapper.selectList(new LambdaQueryWrapper<InventoryTransferLineEntity>()
                .eq(InventoryTransferLineEntity::getCompanyId, transfer.getCompanyId())
                .eq(InventoryTransferLineEntity::getAccountBookId, transfer.getAccountBookId())
                .eq(InventoryTransferLineEntity::getTransferId, transfer.getId())
                .orderByAsc(InventoryTransferLineEntity::getLineNo));
    }

    private void assertCanView(InventoryTransferEntity transfer) {
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        if (!Objects.equals(transfer.getCompanyId(), currentUser.companyId())
                || !Objects.equals(transfer.getAccountBookId(), currentUser.accountBookId())) {
            throw new IllegalArgumentException("库存调拨单不存在");
        }
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        UserEntity creator = transfer.getCreatedBy() == null ? null : userMapper.selectById(transfer.getCreatedBy());
        dataScopeService.assertCanViewInventoryTransfer(
                transfer, currentUser, snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    private void fillCreateAudit(InventoryTransferEntity transfer, AuditMetadata audit, LocalDateTime now) {
        transfer.setCreatedBy(audit.userId());
        transfer.setCreatedTime(now);
        transfer.setUpdatedBy(audit.userId());
        transfer.setUpdatedTime(now);
        transfer.setVersion(0);
    }

    private void fillCreateAudit(InventoryTransferLineEntity line, AuditMetadata audit, LocalDateTime now) {
        line.setCreatedBy(audit.userId());
        line.setCreatedTime(now);
        line.setUpdatedBy(audit.userId());
        line.setUpdatedTime(now);
        line.setVersion(0);
    }
}

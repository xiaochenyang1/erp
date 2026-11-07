package com.tuowei.erp.inventory.transfer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.transfer.mapper.InventoryTransferLineMapper;
import com.tuowei.erp.inventory.transfer.mapper.InventoryTransferMapper;
import com.tuowei.erp.inventory.transfer.model.InventoryTransferEntity;
import com.tuowei.erp.inventory.transfer.model.InventoryTransferLineEntity;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferLineResponse;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferPageQuery;
import com.tuowei.erp.inventory.transfer.web.InventoryTransferResponse;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class InventoryTransferQueryService {

    private final InventoryTransferMapper transferMapper;
    private final InventoryTransferLineMapper lineMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final UserMapper userMapper;

    public InventoryTransferQueryService(
            InventoryTransferMapper transferMapper,
            InventoryTransferLineMapper lineMapper,
            AuditMetadataFactory auditMetadataFactory,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            UserMapper userMapper
    ) {
        this.transferMapper = transferMapper;
        this.lineMapper = lineMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.userMapper = userMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryTransferResponse> list(InventoryTransferPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        CurrentUser currentUser = currentUserContext.requireCurrentUser();
        DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
        ScopedUserResolver.ScopedUserIds scopedUserIds = scopedUserResolver.resolve(currentUser, snapshot);
        InventoryTransferPageQuery safeQuery = query == null ? new InventoryTransferPageQuery() : query;

        LambdaQueryWrapper<InventoryTransferEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryTransferEntity::getCompanyId, audit.companyId())
                .eq(InventoryTransferEntity::getAccountBookId, audit.accountBookId())
                .eq(InventoryTransferEntity::getDeletedFlag, 0);
        if (hasText(safeQuery.getTransferNo())) {
            wrapper.like(InventoryTransferEntity::getTransferNo, safeQuery.getTransferNo().trim());
        }
        if (safeQuery.getFromWarehouseId() != null) {
            wrapper.eq(InventoryTransferEntity::getFromWarehouseId, safeQuery.getFromWarehouseId());
        }
        if (safeQuery.getToWarehouseId() != null) {
            wrapper.eq(InventoryTransferEntity::getToWarehouseId, safeQuery.getToWarehouseId());
        }
        if (hasText(safeQuery.getStatus())) {
            wrapper.eq(InventoryTransferEntity::getStatus, safeQuery.getStatus().trim());
        }
        if (safeQuery.getDateFrom() != null) {
            wrapper.ge(InventoryTransferEntity::getTransferDate, safeQuery.getDateFrom());
        }
        if (safeQuery.getDateTo() != null) {
            wrapper.le(InventoryTransferEntity::getTransferDate, safeQuery.getDateTo());
        }

        wrapper = dataScopeService.applyInventoryTransferScope(
                wrapper,
                currentUser,
                snapshot,
                scopedUserIds.deptUserIds(),
                scopedUserIds.postUserIds()
        );
        wrapper.orderByDesc(InventoryTransferEntity::getCreatedTime);

        Page<InventoryTransferEntity> page = new Page<>(safeQuery.getPageNo(), safeQuery.getPageSize());
        IPage<InventoryTransferEntity> result = transferMapper.selectPage(page, wrapper);
        Map<Long, List<InventoryTransferLineEntity>> linesByTransfer = selectLinesForPage(audit, result.getRecords());
        List<InventoryTransferResponse> responses = result.getRecords().stream()
                .map(transfer -> toResponse(
                        transfer,
                        linesByTransfer.getOrDefault(transfer.getId(), List.of())
                ))
                .toList();
        return new PageResponse<>(result.getCurrent(), result.getSize(), result.getTotal(), responses);
    }

    @Transactional(readOnly = true)
    public InventoryTransferResponse getById(Long id) {
        InventoryTransferEntity transfer = requireTransfer(id);
        assertCanView(transfer);
        return toResponse(transfer, selectLines(transfer));
    }

    private InventoryTransferEntity requireTransfer(Long id) {
        InventoryTransferEntity transfer = transferMapper.selectById(id);
        if (transfer == null || Integer.valueOf(1).equals(transfer.getDeletedFlag())) {
            throw new IllegalArgumentException("库存调拨单不存在");
        }
        return transfer;
    }

    private List<InventoryTransferLineEntity> selectLines(InventoryTransferEntity transfer) {
        return lineMapper.selectList(new LambdaQueryWrapper<InventoryTransferLineEntity>()
                .eq(InventoryTransferLineEntity::getCompanyId, transfer.getCompanyId())
                .eq(InventoryTransferLineEntity::getAccountBookId, transfer.getAccountBookId())
                .eq(InventoryTransferLineEntity::getTransferId, transfer.getId())
                .orderByAsc(InventoryTransferLineEntity::getLineNo));
    }

    private Map<Long, List<InventoryTransferLineEntity>> selectLinesForPage(
            AuditMetadata audit,
            List<InventoryTransferEntity> transfers
    ) {
        if (transfers.isEmpty()) {
            return Map.of();
        }
        List<Long> transferIds = transfers.stream().map(InventoryTransferEntity::getId).toList();
        List<InventoryTransferLineEntity> allLines = lineMapper.selectList(
                new LambdaQueryWrapper<InventoryTransferLineEntity>()
                        .eq(InventoryTransferLineEntity::getCompanyId, audit.companyId())
                        .eq(InventoryTransferLineEntity::getAccountBookId, audit.accountBookId())
                        .in(InventoryTransferLineEntity::getTransferId, transferIds)
                        .orderByAsc(InventoryTransferLineEntity::getLineNo)
        );
        return allLines.stream().collect(Collectors.groupingBy(InventoryTransferLineEntity::getTransferId));
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
                transfer,
                currentUser,
                snapshot,
                creator == null ? null : creator.getDeptId(),
                creator == null ? null : creator.getPostId()
        );
    }

    static InventoryTransferResponse toResponse(
            InventoryTransferEntity transfer,
            List<InventoryTransferLineEntity> lineEntities
    ) {
        List<InventoryTransferLineResponse> lines = lineEntities.stream()
                .map(line -> new InventoryTransferLineResponse(
                        line.getId(),
                        line.getLineNo(),
                        line.getProductId(),
                        line.getQty(),
                        line.getUnitCost(),
                        line.getAmount(),
                        line.getLotNo(),
                        line.getProductionDate(),
                        line.getExpiryDate(),
                        line.getFromLocationId(),
                        line.getToLocationId(),
                        line.getSerialNos(),
                        line.getRemark()
                ))
                .toList();
        return new InventoryTransferResponse(
                transfer.getId(),
                transfer.getTransferNo(),
                transfer.getFromWarehouseId(),
                transfer.getToWarehouseId(),
                transfer.getTransferDate(),
                transfer.getStatus(),
                transfer.getTotalQuantity(),
                transfer.getTotalAmount(),
                transfer.getRemark(),
                lines
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

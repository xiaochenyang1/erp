package com.tuowei.erp.inventory.adjust.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.common.web.PageResponse;
import com.tuowei.erp.inventory.adjust.mapper.InventoryAdjustmentLineMapper;
import com.tuowei.erp.inventory.adjust.mapper.InventoryAdjustmentMapper;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentEntity;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentLineEntity;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentLineResponse;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentPageQuery;
import com.tuowei.erp.inventory.adjust.web.InventoryAdjustmentResponse;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class InventoryAdjustmentQueryService {

    private final InventoryAdjustmentMapper adjustmentMapper;
    private final InventoryAdjustmentLineMapper lineMapper;
    private final AuditMetadataFactory auditMetadataFactory;
    private final CurrentUserContext currentUserContext;
    private final DataScopeService dataScopeService;
    private final ScopedUserResolver scopedUserResolver;
    private final UserMapper userMapper;

    @Autowired
    public InventoryAdjustmentQueryService(
            InventoryAdjustmentMapper adjustmentMapper,
            InventoryAdjustmentLineMapper lineMapper,
            AuditMetadataFactory auditMetadataFactory,
            CurrentUserContext currentUserContext,
            DataScopeService dataScopeService,
            ScopedUserResolver scopedUserResolver,
            UserMapper userMapper
    ) {
        this.adjustmentMapper = adjustmentMapper;
        this.lineMapper = lineMapper;
        this.auditMetadataFactory = auditMetadataFactory;
        this.currentUserContext = currentUserContext;
        this.dataScopeService = dataScopeService;
        this.scopedUserResolver = scopedUserResolver;
        this.userMapper = userMapper;
    }

    public InventoryAdjustmentQueryService(
            InventoryAdjustmentMapper adjustmentMapper,
            InventoryAdjustmentLineMapper lineMapper,
            AuditMetadataFactory auditMetadataFactory
    ) {
        this(adjustmentMapper, lineMapper, auditMetadataFactory, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryAdjustmentResponse> list(InventoryAdjustmentPageQuery query) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryAdjustmentPageQuery safeQuery = query == null ? new InventoryAdjustmentPageQuery() : query;
        LambdaQueryWrapper<InventoryAdjustmentEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InventoryAdjustmentEntity::getCompanyId, audit.companyId())
                .eq(InventoryAdjustmentEntity::getAccountBookId, audit.accountBookId())
                .eq(InventoryAdjustmentEntity::getDeletedFlag, 0);
        if (hasText(safeQuery.getAdjustmentNo())) {
            wrapper.like(InventoryAdjustmentEntity::getAdjustmentNo, safeQuery.getAdjustmentNo().trim());
        }
        if (safeQuery.getWarehouseId() != null) {
            wrapper.eq(InventoryAdjustmentEntity::getWarehouseId, safeQuery.getWarehouseId());
        }
        if (hasText(safeQuery.getStatus())) {
            wrapper.eq(InventoryAdjustmentEntity::getStatus, safeQuery.getStatus().trim());
        }
        if (safeQuery.getDateFrom() != null) {
            wrapper.ge(InventoryAdjustmentEntity::getAdjustmentDate, safeQuery.getDateFrom());
        }
        if (safeQuery.getDateTo() != null) {
            wrapper.le(InventoryAdjustmentEntity::getAdjustmentDate, safeQuery.getDateTo());
        }
        if (currentUserContext != null) {
            CurrentUser currentUser = currentUserContext.requireCurrentUser();
            DataScopeSnapshot snapshot = currentUserContext.requirePrincipal().dataScopeSnapshot();
            ScopedUserResolver.ScopedUserIds scoped = scopedUserResolver.resolve(currentUser, snapshot);
            wrapper = dataScopeService.applyInventoryAdjustmentScope(
                    wrapper, currentUser, snapshot, scoped.deptUserIds(), scoped.postUserIds());
        }
        wrapper.orderByDesc(InventoryAdjustmentEntity::getCreatedTime);

        Page<InventoryAdjustmentEntity> page = new Page<>(safeQuery.getPageNo(), safeQuery.getPageSize());
        IPage<InventoryAdjustmentEntity> result = adjustmentMapper.selectPage(page, wrapper);
        Map<Long, List<InventoryAdjustmentLineEntity>> linesByAdjustment = selectLinesForPage(
                audit,
                result.getRecords()
        );
        List<InventoryAdjustmentResponse> responses = result.getRecords().stream()
                .map(adjustment -> toResponse(
                        adjustment,
                        linesByAdjustment.getOrDefault(adjustment.getId(), List.of())
                ))
                .toList();
        return new PageResponse<>(result.getCurrent(), result.getSize(), result.getTotal(), responses);
    }

    @Transactional(readOnly = true)
    public InventoryAdjustmentResponse getById(Long id) {
        AuditMetadata audit = auditMetadataFactory.current();
        InventoryAdjustmentEntity adjustment = requireAdjustment(id, audit);
        return toResponse(adjustment, selectLines(adjustment));
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

    private List<InventoryAdjustmentLineEntity> selectLines(InventoryAdjustmentEntity adjustment) {
        return lineMapper.selectList(new LambdaQueryWrapper<InventoryAdjustmentLineEntity>()
                .eq(InventoryAdjustmentLineEntity::getCompanyId, adjustment.getCompanyId())
                .eq(InventoryAdjustmentLineEntity::getAccountBookId, adjustment.getAccountBookId())
                .eq(InventoryAdjustmentLineEntity::getAdjustmentId, adjustment.getId())
                .orderByAsc(InventoryAdjustmentLineEntity::getLineNo));
    }

    private Map<Long, List<InventoryAdjustmentLineEntity>> selectLinesForPage(
            AuditMetadata audit,
            List<InventoryAdjustmentEntity> adjustments
    ) {
        if (adjustments.isEmpty()) {
            return Map.of();
        }
        List<Long> adjustmentIds = adjustments.stream().map(InventoryAdjustmentEntity::getId).toList();
        List<InventoryAdjustmentLineEntity> allLines = lineMapper.selectList(
                new LambdaQueryWrapper<InventoryAdjustmentLineEntity>()
                        .eq(InventoryAdjustmentLineEntity::getCompanyId, audit.companyId())
                        .eq(InventoryAdjustmentLineEntity::getAccountBookId, audit.accountBookId())
                        .in(InventoryAdjustmentLineEntity::getAdjustmentId, adjustmentIds)
                        .orderByAsc(InventoryAdjustmentLineEntity::getLineNo)
        );
        return allLines.stream().collect(Collectors.groupingBy(InventoryAdjustmentLineEntity::getAdjustmentId));
    }

    static InventoryAdjustmentResponse toResponse(
            InventoryAdjustmentEntity adjustment,
            List<InventoryAdjustmentLineEntity> lineEntities
    ) {
        List<InventoryAdjustmentLineResponse> lines = lineEntities.stream()
                .map(line -> new InventoryAdjustmentLineResponse(
                        line.getId(), line.getLineNo(), line.getProductId(), line.getDirection(),
                        line.getQty(), line.getUnitCost(), line.getAmount(), line.getLotNo(),
                        line.getProductionDate(), line.getExpiryDate(), line.getLocationId(),
                        line.getSerialNos(), line.getReason(), line.getRemark()
                ))
                .toList();
        return new InventoryAdjustmentResponse(
                adjustment.getId(), adjustment.getAdjustmentNo(), adjustment.getWarehouseId(),
                adjustment.getAdjustmentDate(), adjustment.getStatus(), adjustment.getTotalQuantity(),
                adjustment.getTotalAmount(), adjustment.getRemark(), lines
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

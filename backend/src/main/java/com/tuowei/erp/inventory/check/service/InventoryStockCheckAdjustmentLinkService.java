package com.tuowei.erp.inventory.check.service;

import com.tuowei.erp.common.exception.OptimisticLockGuard;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.adjust.model.InventoryAdjustmentEntity;
import com.tuowei.erp.inventory.check.mapper.InventoryStockCheckMapper;
import com.tuowei.erp.inventory.check.model.InventoryStockCheckEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Updates a linked count in the adjustment transaction, without depending on either command facade. */
@Service
public class InventoryStockCheckAdjustmentLinkService {
    public static final String SOURCE_TYPE = "INVENTORY_CHECK";
    public static final String PENDING = "ADJUSTMENT_PENDING";

    private final InventoryStockCheckMapper checkMapper;

    public InventoryStockCheckAdjustmentLinkService(InventoryStockCheckMapper checkMapper) {
        this.checkMapper = checkMapper;
    }

    @Transactional
    public void complete(InventoryAdjustmentEntity adjustment, AuditMetadata audit) {
        update(adjustment, audit, false);
    }

    @Transactional
    public void cancel(InventoryAdjustmentEntity adjustment, AuditMetadata audit) {
        update(adjustment, audit, true);
    }

    private void update(InventoryAdjustmentEntity adjustment, AuditMetadata audit, boolean cancelled) {
        if (!SOURCE_TYPE.equals(adjustment.getSourceType())) return;
        InventoryStockCheckEntity check = checkMapper.selectById(adjustment.getSourceId());
        if (check == null || Integer.valueOf(1).equals(check.getDeletedFlag())
                || !Objects.equals(check.getCompanyId(), audit.companyId())
                || !Objects.equals(check.getAccountBookId(), audit.accountBookId())
                || !Objects.equals(check.getGeneratedAdjustmentId(), adjustment.getId())
                || !Objects.equals(check.getWarehouseId(), adjustment.getWarehouseId())
                || !PENDING.equals(check.getStatus())) {
            throw new IllegalArgumentException("关联盘点单不存在或状态已改变");
        }
        check.setStatus(cancelled ? "COUNTED" : "ADJUSTED");
        if (cancelled) {
            // MyBatis-Plus normally ignores nulls, so these fields explicitly support clearing.
            check.setGeneratedAdjustmentId(null);
            check.setGeneratedAdjustmentNo(null);
        }
        check.setUpdatedBy(audit.userId());
        check.setUpdatedTime(audit.now());
        OptimisticLockGuard.requireUpdated(checkMapper.updateById(check),
                "关联盘点单已被其他操作修改，请刷新后重试");
    }
}

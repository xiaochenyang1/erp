package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationEventMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryReservationMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEntity;
import com.tuowei.erp.inventory.stock.model.InventoryReservationEventEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Releases and restores inventory reservations. */
@Service
public class InventoryReservationReleaseService {
    private static final int MAX_ATTEMPTS = 8;
    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final InventoryReservationMapper inventoryReservationMapper;
    private final InventoryReservationEventMapper inventoryReservationEventMapper;

    public InventoryReservationReleaseService(InventoryBalanceMapper inventoryBalanceMapper,
                                               InventoryReservationMapper inventoryReservationMapper,
                                               InventoryReservationEventMapper inventoryReservationEventMapper) {
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.inventoryReservationMapper = inventoryReservationMapper;
        this.inventoryReservationEventMapper = inventoryReservationEventMapper;
    }

    @Transactional
    public void releaseReservation(String sourceType, Long sourceLineId, BigDecimal qty, AuditMetadata audit) {
        BigDecimal scaledQty = requirePositiveQuantity(qty);
        LocalDateTime now = audit.now();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            InventoryReservationEntity reservation = findReservation(sourceType, sourceLineId, audit.companyId(), audit.accountBookId());
            if (reservation == null) throw new IllegalArgumentException("销售订单预占不存在，不能出库");
            if (ScalePrecision.zeroDefault(reservation.getRemainingQty()).compareTo(BigDecimal.ZERO) <= 0
                    || ScalePrecision.quantity(reservation.getRemainingQty()).compareTo(scaledQty) < 0) {
                throw new IllegalArgumentException("销售出库数量超过销售订单预占数量");
            }
            BigDecimal remainingBefore = ScalePrecision.quantity(reservation.getRemainingQty());
            BigDecimal remainingAfter = ScalePrecision.quantity(remainingBefore.subtract(scaledQty));
            reservation.setReleasedQty(ScalePrecision.quantity(ScalePrecision.zeroDefault(reservation.getReleasedQty()).add(scaledQty)));
            reservation.setRemainingQty(remainingAfter);
            reservation.setStatus(remainingAfter.compareTo(BigDecimal.ZERO) == 0 ? "RELEASED" : "ACTIVE");
            reservation.setUpdatedBy(audit.userId());
            reservation.setUpdatedTime(now);
            if (inventoryReservationMapper.updateById(reservation) != 1) continue;
            List<InventoryBalanceEntity> balances = listBalances(audit.companyId(), audit.accountBookId(), reservation.getWarehouseId(), reservation.getProductId());
            if (!releaseReserved(balances, scaledQty, audit, now)) throw new IllegalArgumentException("库存预占余额不足，不能释放预占");
            insertReservationEvent(reservation, "RELEASE", scaledQty, remainingBefore, remainingAfter, audit, null, now);
            return;
        }
        throw new BusinessConflictException("库存预占已被其他操作修改，请重试");
    }

    @Transactional
    public void manualReleaseReservation(Long reservationId, BigDecimal qty, AuditMetadata audit, String reason) {
        BigDecimal scaledQty = requirePositiveQuantity(qty);
        LocalDateTime now = audit.now();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            InventoryReservationEntity reservation = inventoryReservationMapper.selectById(reservationId);
            if (reservation == null || !audit.companyId().equals(reservation.getCompanyId()) || !audit.accountBookId().equals(reservation.getAccountBookId())) {
                throw new IllegalArgumentException("库存预占不存在");
            }
            BigDecimal remainingBefore = ScalePrecision.quantity(ScalePrecision.zeroDefault(reservation.getRemainingQty()));
            if (remainingBefore.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("库存预占已全部释放");
            if (remainingBefore.compareTo(scaledQty) < 0) throw new IllegalArgumentException("人工释放数量超过库存预占剩余数量");
            List<InventoryBalanceEntity> balances = listBalances(audit.companyId(), audit.accountBookId(), reservation.getWarehouseId(), reservation.getProductId());
            if (balances.isEmpty()) throw new IllegalArgumentException("库存预占对应的库存余额不存在");
            BigDecimal remainingAfter = ScalePrecision.quantity(remainingBefore.subtract(scaledQty));
            reservation.setReleasedQty(ScalePrecision.quantity(ScalePrecision.zeroDefault(reservation.getReleasedQty()).add(scaledQty)));
            reservation.setRemainingQty(remainingAfter);
            reservation.setStatus(remainingAfter.compareTo(BigDecimal.ZERO) == 0 ? "RELEASED" : "ACTIVE");
            reservation.setUpdatedBy(audit.userId());
            reservation.setUpdatedTime(now);
            if (inventoryReservationMapper.updateById(reservation) != 1) continue;
            if (!releaseReserved(balances, scaledQty, audit, now)) throw new IllegalArgumentException("库存预占余额不足，不能释放预占");
            insertReservationEvent(reservation, "MANUAL_RELEASE", scaledQty, remainingBefore, remainingAfter, audit, reason, now);
            return;
        }
        throw new BusinessConflictException("库存预占已被其他操作修改，请刷新后重试");
    }

    @Transactional
    public void releaseAllReservations(String sourceType, Long sourceId, AuditMetadata audit) {
        List<InventoryReservationEntity> reservations = inventoryReservationMapper.selectList(new LambdaQueryWrapper<InventoryReservationEntity>()
                .eq(InventoryReservationEntity::getCompanyId, audit.companyId())
                .eq(InventoryReservationEntity::getAccountBookId, audit.accountBookId())
                .eq(InventoryReservationEntity::getSourceType, sourceType)
                .eq(InventoryReservationEntity::getSourceId, sourceId)
                .gt(InventoryReservationEntity::getRemainingQty, BigDecimal.ZERO)
                .orderByAsc(InventoryReservationEntity::getId));
        for (InventoryReservationEntity reservation : reservations) {
            releaseReservation(sourceType, reservation.getSourceLineId(), reservation.getRemainingQty(), audit);
        }
    }

    @Transactional
    public void restoreReservation(String sourceType, Long sourceLineId, BigDecimal qty, AuditMetadata audit, String reason) {
        BigDecimal scaledQty = requirePositiveQuantity(qty);
        LocalDateTime now = audit.now();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            InventoryReservationEntity reservation = findReservation(sourceType, sourceLineId, audit.companyId(), audit.accountBookId());
            if (reservation == null) throw new IllegalArgumentException("生产工单预占不存在，不能退料恢复预占");
            BigDecimal releasedQty = ScalePrecision.quantity(ScalePrecision.zeroDefault(reservation.getReleasedQty()));
            if (releasedQty.compareTo(scaledQty) < 0) throw new IllegalArgumentException("生产退料数量超过已领料数量");
            List<InventoryBalanceEntity> balances = listBalances(audit.companyId(), audit.accountBookId(), reservation.getWarehouseId(), reservation.getProductId());
            if (balances.isEmpty()) throw new IllegalArgumentException("库存预占对应的库存余额不存在");
            BigDecimal remainingBefore = ScalePrecision.quantity(ScalePrecision.zeroDefault(reservation.getRemainingQty()));
            BigDecimal remainingAfter = ScalePrecision.quantity(remainingBefore.add(scaledQty));
            reservation.setReleasedQty(ScalePrecision.quantity(releasedQty.subtract(scaledQty)));
            reservation.setRemainingQty(remainingAfter);
            reservation.setStatus("ACTIVE");
            reservation.setUpdatedBy(audit.userId());
            reservation.setUpdatedTime(now);
            if (inventoryReservationMapper.updateById(reservation) != 1) continue;
            if (!allocateReserved(balances, scaledQty, audit, now)) throw new BusinessConflictException("库存余额已被其他操作修改，请重试");
            insertReservationEvent(reservation, "RESTORE", scaledQty, remainingBefore, remainingAfter, audit, reason, now);
            return;
        }
        throw new BusinessConflictException("库存余额已被其他操作修改，请重试");
    }

    private BigDecimal requirePositiveQuantity(BigDecimal qty) {
        BigDecimal scaledQty = ScalePrecision.quantity(ScalePrecision.zeroDefault(qty));
        if (scaledQty.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("库存过账数量必须大于0");
        return scaledQty;
    }
    private List<InventoryBalanceEntity> listBalances(Long companyId, Long accountBookId, Long warehouseId, Long productId) {
        return inventoryBalanceMapper.selectList(new LambdaQueryWrapper<InventoryBalanceEntity>()
                .eq(InventoryBalanceEntity::getCompanyId, companyId)
                .eq(InventoryBalanceEntity::getAccountBookId, accountBookId)
                .eq(InventoryBalanceEntity::getWarehouseId, warehouseId)
                .eq(InventoryBalanceEntity::getProductId, productId)
                .orderByAsc(InventoryBalanceEntity::getId));
    }
    private boolean allocateReserved(List<InventoryBalanceEntity> balances, BigDecimal qty, AuditMetadata audit, LocalDateTime now) {
        BigDecimal remaining = ScalePrecision.quantity(qty);
        for (InventoryBalanceEntity balance : balances) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal available = ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyOnHand()).subtract(ScalePrecision.zeroDefault(balance.getQtyReserved())));
            if (available.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal take = available.min(remaining);
            balance.setQtyReserved(ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyReserved()).add(take)));
            balance.setUpdatedBy(audit.userId());
            balance.setUpdatedTime(now);
            if (inventoryBalanceMapper.updateById(balance) != 1) return false;
            remaining = ScalePrecision.quantity(remaining.subtract(take));
        }
        return remaining.compareTo(BigDecimal.ZERO) <= 0;
    }
    private boolean releaseReserved(List<InventoryBalanceEntity> balances, BigDecimal qty, AuditMetadata audit, LocalDateTime now) {
        BigDecimal remaining = ScalePrecision.quantity(qty);
        for (int i = balances.size() - 1; i >= 0; i--) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            InventoryBalanceEntity balance = balances.get(i);
            BigDecimal reserved = ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyReserved()));
            if (reserved.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal take = reserved.min(remaining);
            balance.setQtyReserved(ScalePrecision.quantity(reserved.subtract(take)));
            balance.setUpdatedBy(audit.userId());
            balance.setUpdatedTime(now);
            if (inventoryBalanceMapper.updateById(balance) != 1) return false;
            remaining = ScalePrecision.quantity(remaining.subtract(take));
        }
        return remaining.compareTo(BigDecimal.ZERO) <= 0;
    }
    private InventoryReservationEntity findReservation(String sourceType, Long sourceLineId, Long companyId, Long accountBookId) {
        return inventoryReservationMapper.selectOne(new LambdaQueryWrapper<InventoryReservationEntity>()
                .eq(InventoryReservationEntity::getCompanyId, companyId)
                .eq(InventoryReservationEntity::getAccountBookId, accountBookId)
                .eq(InventoryReservationEntity::getSourceType, sourceType)
                .eq(InventoryReservationEntity::getSourceLineId, sourceLineId)
                .last("limit 1"));
    }
    private void insertReservationEvent(InventoryReservationEntity reservation, String eventType, BigDecimal eventQty,
                                         BigDecimal remainingQtyBefore, BigDecimal remainingQtyAfter, AuditMetadata audit,
                                         String reason, LocalDateTime now) {
        InventoryReservationEventEntity event = new InventoryReservationEventEntity();
        event.setCompanyId(audit.companyId());
        event.setAccountBookId(audit.accountBookId());
        event.setReservationId(reservation.getId());
        event.setWarehouseId(reservation.getWarehouseId());
        event.setProductId(reservation.getProductId());
        event.setSourceType(reservation.getSourceType());
        event.setSourceId(reservation.getSourceId());
        event.setSourceNo(reservation.getSourceNo());
        event.setSourceLineId(reservation.getSourceLineId());
        event.setEventType(eventType);
        event.setEventQty(ScalePrecision.quantity(eventQty));
        event.setRemainingQtyBefore(ScalePrecision.quantity(remainingQtyBefore));
        event.setRemainingQtyAfter(ScalePrecision.quantity(remainingQtyAfter));
        event.setReason(reason);
        event.setCreatedBy(audit.userId());
        event.setCreatedTime(now);
        inventoryReservationEventMapper.insert(event);
    }
}

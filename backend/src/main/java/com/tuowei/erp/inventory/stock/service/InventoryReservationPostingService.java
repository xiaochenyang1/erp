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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InventoryReservationPostingService {

    private static final int MAX_ATTEMPTS = 8;

    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final InventoryReservationMapper inventoryReservationMapper;
    private final InventoryReservationEventMapper inventoryReservationEventMapper;

    public InventoryReservationPostingService(
            InventoryBalanceMapper inventoryBalanceMapper,
            InventoryReservationMapper inventoryReservationMapper,
            InventoryReservationEventMapper inventoryReservationEventMapper
    ) {
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.inventoryReservationMapper = inventoryReservationMapper;
        this.inventoryReservationEventMapper = inventoryReservationEventMapper;
    }

    @Transactional
    public void reserve(InventoryReservationCommand command, AuditMetadata audit, String shortageMessage) {
        BigDecimal scaledQty = requirePositiveQuantity(command.qty());
        if (findReservation(command.sourceType(), command.sourceLineId(), audit.companyId(), audit.accountBookId()) != null) {
            return;
        }
        LocalDateTime now = audit.now();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            List<InventoryBalanceEntity> balances = listBalances(
                    audit.companyId(), audit.accountBookId(), command.warehouseId(), command.productId());
            if (totalAvailable(balances).compareTo(scaledQty) < 0) {
                throw new IllegalArgumentException(shortageMessage);
            }
            if (!allocateReserved(balances, scaledQty, audit, now)) {
                continue;
            }

            InventoryReservationEntity reservation = new InventoryReservationEntity();
            reservation.setCompanyId(audit.companyId());
            reservation.setAccountBookId(audit.accountBookId());
            reservation.setWarehouseId(command.warehouseId());
            reservation.setProductId(command.productId());
            reservation.setSourceType(command.sourceType());
            reservation.setSourceId(command.sourceId());
            reservation.setSourceNo(command.sourceNo());
            reservation.setSourceLineId(command.sourceLineId());
            reservation.setReservedQty(scaledQty);
            reservation.setReleasedQty(ScalePrecision.quantity(BigDecimal.ZERO));
            reservation.setRemainingQty(scaledQty);
            reservation.setStatus("ACTIVE");
            reservation.setRemark(command.remark());
            reservation.setCreatedBy(audit.userId());
            reservation.setCreatedTime(now);
            reservation.setUpdatedBy(audit.userId());
            reservation.setUpdatedTime(now);
            reservation.setVersion(0);
            try {
                inventoryReservationMapper.insert(reservation);
            } catch (DuplicateKeyException ex) {
                throw new BusinessConflictException("库存预占已存在，请刷新后重试");
            }
            insertReservationEvent(
                    reservation,
                    "RESERVE",
                    scaledQty,
                    ScalePrecision.quantity(BigDecimal.ZERO),
                    scaledQty,
                    audit,
                    command.remark(),
                    now
            );
            return;
        }

        throw new BusinessConflictException("库存余额已被其他操作修改，请重试");
    }

    @Transactional
    public void releaseReservation(String sourceType, Long sourceLineId, BigDecimal qty, AuditMetadata audit) {
        BigDecimal scaledQty = requirePositiveQuantity(qty);
        LocalDateTime now = audit.now();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            InventoryReservationEntity reservation = findReservation(sourceType, sourceLineId, audit.companyId(), audit.accountBookId());
            if (reservation == null) {
                throw new IllegalArgumentException("销售订单预占不存在，不能出库");
            }
            if (ScalePrecision.zeroDefault(reservation.getRemainingQty()).compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("销售出库数量超过销售订单预占数量");
            }
            if (ScalePrecision.quantity(reservation.getRemainingQty()).compareTo(scaledQty) < 0) {
                throw new IllegalArgumentException("销售出库数量超过销售订单预占数量");
            }

            BigDecimal remainingQtyBefore = ScalePrecision.quantity(reservation.getRemainingQty());
            BigDecimal newRemainingQty = ScalePrecision.quantity(remainingQtyBefore.subtract(scaledQty));
            reservation.setReleasedQty(ScalePrecision.quantity(ScalePrecision.zeroDefault(reservation.getReleasedQty()).add(scaledQty)));
            reservation.setRemainingQty(newRemainingQty);
            reservation.setStatus(newRemainingQty.compareTo(BigDecimal.ZERO) == 0 ? "RELEASED" : "ACTIVE");
            reservation.setUpdatedBy(audit.userId());
            reservation.setUpdatedTime(now);
            if (inventoryReservationMapper.updateById(reservation) != 1) {
                continue;
            }

            List<InventoryBalanceEntity> balances = listBalances(
                    audit.companyId(), audit.accountBookId(), reservation.getWarehouseId(), reservation.getProductId());
            if (!releaseReserved(balances, scaledQty, audit, now)) {
                throw new IllegalArgumentException("库存预占余额不足，不能释放预占");
            }
            insertReservationEvent(
                    reservation,
                    "RELEASE",
                    scaledQty,
                    remainingQtyBefore,
                    newRemainingQty,
                    audit,
                    null,
                    now
            );
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
            if (reservation == null || !audit.companyId().equals(reservation.getCompanyId())
                    || !audit.accountBookId().equals(reservation.getAccountBookId())) {
                throw new IllegalArgumentException("库存预占不存在");
            }
            BigDecimal remainingQtyBefore = ScalePrecision.quantity(ScalePrecision.zeroDefault(reservation.getRemainingQty()));
            if (remainingQtyBefore.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("库存预占已全部释放");
            }
            if (remainingQtyBefore.compareTo(scaledQty) < 0) {
                throw new IllegalArgumentException("人工释放数量超过库存预占剩余数量");
            }

            List<InventoryBalanceEntity> balances = listBalances(
                    audit.companyId(), audit.accountBookId(), reservation.getWarehouseId(), reservation.getProductId());
            if (balances.isEmpty()) {
                throw new IllegalArgumentException("库存预占对应的库存余额不存在");
            }

            BigDecimal newRemainingQty = ScalePrecision.quantity(remainingQtyBefore.subtract(scaledQty));
            reservation.setReleasedQty(ScalePrecision.quantity(ScalePrecision.zeroDefault(reservation.getReleasedQty()).add(scaledQty)));
            reservation.setRemainingQty(newRemainingQty);
            reservation.setStatus(newRemainingQty.compareTo(BigDecimal.ZERO) == 0 ? "RELEASED" : "ACTIVE");
            reservation.setUpdatedBy(audit.userId());
            reservation.setUpdatedTime(now);
            if (inventoryReservationMapper.updateById(reservation) != 1) {
                continue;
            }

            if (!releaseReserved(balances, scaledQty, audit, now)) {
                throw new IllegalArgumentException("库存预占余额不足，不能释放预占");
            }
            insertReservationEvent(
                    reservation,
                    "MANUAL_RELEASE",
                    scaledQty,
                    remainingQtyBefore,
                    newRemainingQty,
                    audit,
                    reason,
                    now
            );
            return;
        }

        throw new BusinessConflictException("库存预占已被其他操作修改，请刷新后重试");
    }

    @Transactional
    public void releaseAllReservations(String sourceType, Long sourceId, AuditMetadata audit) {
        List<InventoryReservationEntity> reservations = inventoryReservationMapper.selectList(
                new LambdaQueryWrapper<InventoryReservationEntity>()
                        .eq(InventoryReservationEntity::getCompanyId, audit.companyId())
                        .eq(InventoryReservationEntity::getAccountBookId, audit.accountBookId())
                        .eq(InventoryReservationEntity::getSourceType, sourceType)
                        .eq(InventoryReservationEntity::getSourceId, sourceId)
                        .gt(InventoryReservationEntity::getRemainingQty, BigDecimal.ZERO)
                        .orderByAsc(InventoryReservationEntity::getId)
        );
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
            if (reservation == null) {
                throw new IllegalArgumentException("生产工单预占不存在，不能退料恢复预占");
            }
            BigDecimal releasedQty = ScalePrecision.quantity(ScalePrecision.zeroDefault(reservation.getReleasedQty()));
            if (releasedQty.compareTo(scaledQty) < 0) {
                throw new IllegalArgumentException("生产退料数量超过已领料数量");
            }

            List<InventoryBalanceEntity> balances = listBalances(
                    audit.companyId(), audit.accountBookId(), reservation.getWarehouseId(), reservation.getProductId());
            if (balances.isEmpty()) {
                throw new IllegalArgumentException("库存预占对应的库存余额不存在");
            }

            BigDecimal remainingQtyBefore = ScalePrecision.quantity(ScalePrecision.zeroDefault(reservation.getRemainingQty()));
            BigDecimal newRemainingQty = ScalePrecision.quantity(remainingQtyBefore.add(scaledQty));
            reservation.setReleasedQty(ScalePrecision.quantity(releasedQty.subtract(scaledQty)));
            reservation.setRemainingQty(newRemainingQty);
            reservation.setStatus("ACTIVE");
            reservation.setUpdatedBy(audit.userId());
            reservation.setUpdatedTime(now);
            if (inventoryReservationMapper.updateById(reservation) != 1) {
                continue;
            }

            if (!allocateReserved(balances, scaledQty, audit, now)) {
                throw new BusinessConflictException("库存余额已被其他操作修改，请重试");
            }
            insertReservationEvent(
                    reservation,
                    "RESTORE",
                    scaledQty,
                    remainingQtyBefore,
                    newRemainingQty,
                    audit,
                    reason,
                    now
            );
            return;
        }

        throw new BusinessConflictException("库存预占已被其他操作修改，请重试");
    }

    private BigDecimal requirePositiveQuantity(BigDecimal qty) {
        BigDecimal scaledQty = ScalePrecision.quantity(ScalePrecision.zeroDefault(qty));
        if (scaledQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("库存过账数量必须大于0");
        }
        return scaledQty;
    }

    private BigDecimal qtyAvailable(InventoryBalanceEntity balance) {
        return ScalePrecision.quantity(
                ScalePrecision.zeroDefault(balance.getQtyOnHand())
                        .subtract(ScalePrecision.zeroDefault(balance.getQtyReserved()))
        );
    }

    private BigDecimal totalAvailable(List<InventoryBalanceEntity> balances) {
        BigDecimal total = ScalePrecision.quantity(BigDecimal.ZERO);
        for (InventoryBalanceEntity balance : balances) {
            total = ScalePrecision.quantity(total.add(qtyAvailable(balance)));
        }
        return total;
    }

    private List<InventoryBalanceEntity> listBalances(Long companyId, Long accountBookId, Long warehouseId, Long productId) {
        // 仓库级预占：跨库位余额汇总可用量，并按行 id 顺序分摊 reserved，避免只命中第一行导致假缺货。
        return inventoryBalanceMapper.selectList(new LambdaQueryWrapper<InventoryBalanceEntity>()
                .eq(InventoryBalanceEntity::getCompanyId, companyId)
                .eq(InventoryBalanceEntity::getAccountBookId, accountBookId)
                .eq(InventoryBalanceEntity::getWarehouseId, warehouseId)
                .eq(InventoryBalanceEntity::getProductId, productId)
                .orderByAsc(InventoryBalanceEntity::getId));
    }

    private boolean allocateReserved(
            List<InventoryBalanceEntity> balances,
            BigDecimal qty,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        BigDecimal remaining = ScalePrecision.quantity(qty);
        for (InventoryBalanceEntity balance : balances) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal available = qtyAvailable(balance);
            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal take = available.min(remaining);
            balance.setQtyReserved(ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyReserved()).add(take)));
            balance.setUpdatedBy(audit.userId());
            balance.setUpdatedTime(now);
            if (inventoryBalanceMapper.updateById(balance) != 1) {
                return false;
            }
            remaining = ScalePrecision.quantity(remaining.subtract(take));
        }
        return remaining.compareTo(BigDecimal.ZERO) <= 0;
    }

    private boolean releaseReserved(
            List<InventoryBalanceEntity> balances,
            BigDecimal qty,
            AuditMetadata audit,
            LocalDateTime now
    ) {
        BigDecimal remaining = ScalePrecision.quantity(qty);
        // 释放时从后往前扣减 reserved，尽量先回冲后分配的库位行。
        for (int i = balances.size() - 1; i >= 0; i--) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            InventoryBalanceEntity balance = balances.get(i);
            BigDecimal reserved = ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyReserved()));
            if (reserved.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal take = reserved.min(remaining);
            balance.setQtyReserved(ScalePrecision.quantity(reserved.subtract(take)));
            balance.setUpdatedBy(audit.userId());
            balance.setUpdatedTime(now);
            if (inventoryBalanceMapper.updateById(balance) != 1) {
                return false;
            }
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

    private void insertReservationEvent(
            InventoryReservationEntity reservation,
            String eventType,
            BigDecimal eventQty,
            BigDecimal remainingQtyBefore,
            BigDecimal remainingQtyAfter,
            AuditMetadata audit,
            String reason,
            LocalDateTime now
    ) {
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

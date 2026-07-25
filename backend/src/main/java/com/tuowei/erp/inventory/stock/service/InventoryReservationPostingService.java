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
            InventoryBalanceEntity balance = selectBalance(audit.companyId(), audit.accountBookId(), command.warehouseId(), command.productId());
            if (balance == null || qtyAvailable(balance).compareTo(scaledQty) < 0) {
                throw new IllegalArgumentException(shortageMessage);
            }

            balance.setQtyReserved(ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyReserved()).add(scaledQty)));
            balance.setUpdatedBy(audit.userId());
            balance.setUpdatedTime(now);
            if (inventoryBalanceMapper.updateById(balance) != 1) {
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

            InventoryBalanceEntity balance = selectBalance(audit.companyId(), audit.accountBookId(), reservation.getWarehouseId(), reservation.getProductId());
            if (balance == null) {
                throw new IllegalArgumentException("库存预占对应的库存余额不存在");
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

            BigDecimal currentReserved = ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyReserved()));
            if (currentReserved.compareTo(scaledQty) < 0) {
                throw new IllegalArgumentException("库存预占余额不足，不能释放预占");
            }
            balance.setQtyReserved(ScalePrecision.quantity(currentReserved.subtract(scaledQty)));
            balance.setUpdatedBy(audit.userId());
            balance.setUpdatedTime(now);
            if (inventoryBalanceMapper.updateById(balance) == 1) {
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
            throw new BusinessConflictException("库存余额已被其他操作修改，请重试");
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

            InventoryBalanceEntity balance = selectBalance(audit.companyId(), audit.accountBookId(), reservation.getWarehouseId(), reservation.getProductId());
            if (balance == null) {
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

            BigDecimal currentReserved = ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyReserved()));
            if (currentReserved.compareTo(scaledQty) < 0) {
                throw new IllegalArgumentException("库存预占余额不足，不能释放预占");
            }
            balance.setQtyReserved(ScalePrecision.quantity(currentReserved.subtract(scaledQty)));
            balance.setUpdatedBy(audit.userId());
            balance.setUpdatedTime(now);
            if (inventoryBalanceMapper.updateById(balance) == 1) {
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
            throw new BusinessConflictException("库存余额已被其他操作修改，请重试");
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

            InventoryBalanceEntity balance = selectBalance(audit.companyId(), audit.accountBookId(), reservation.getWarehouseId(), reservation.getProductId());
            if (balance == null) {
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

            balance.setQtyReserved(ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyReserved()).add(scaledQty)));
            balance.setUpdatedBy(audit.userId());
            balance.setUpdatedTime(now);
            if (inventoryBalanceMapper.updateById(balance) == 1) {
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
            throw new BusinessConflictException("库存余额已被其他操作修改，请重试");
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

    private InventoryBalanceEntity selectBalance(Long companyId, Long accountBookId, Long warehouseId, Long productId) {
        // 预留仍按仓库汇总维度操作：优先默认库位行，兼容未拆分前的单行余额。
        InventoryBalanceEntity preferred = inventoryBalanceMapper.selectOne(new LambdaQueryWrapper<InventoryBalanceEntity>()
                .eq(InventoryBalanceEntity::getCompanyId, companyId)
                .eq(InventoryBalanceEntity::getAccountBookId, accountBookId)
                .eq(InventoryBalanceEntity::getWarehouseId, warehouseId)
                .eq(InventoryBalanceEntity::getProductId, productId)
                .orderByAsc(InventoryBalanceEntity::getId)
                .last("limit 1"));
        return preferred;
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

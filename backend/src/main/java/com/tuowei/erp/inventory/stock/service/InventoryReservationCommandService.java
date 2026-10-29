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

/** Creates inventory reservations and allocates reserved quantity across warehouse locations. */
@Service
public class InventoryReservationCommandService {
    private static final int MAX_ATTEMPTS = 8;
    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final InventoryReservationMapper inventoryReservationMapper;
    private final InventoryReservationEventMapper inventoryReservationEventMapper;

    public InventoryReservationCommandService(InventoryBalanceMapper inventoryBalanceMapper,
                                               InventoryReservationMapper inventoryReservationMapper,
                                               InventoryReservationEventMapper inventoryReservationEventMapper) {
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
            List<InventoryBalanceEntity> balances = listBalances(audit.companyId(), audit.accountBookId(), command.warehouseId(), command.productId());
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
            insertReservationEvent(reservation, "RESERVE", scaledQty,
                    ScalePrecision.quantity(BigDecimal.ZERO), scaledQty, audit, command.remark(), now);
            return;
        }
        throw new BusinessConflictException("库存余额已被其他操作修改，请重试");
    }

    private BigDecimal requirePositiveQuantity(BigDecimal qty) {
        BigDecimal scaledQty = ScalePrecision.quantity(ScalePrecision.zeroDefault(qty));
        if (scaledQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("库存过账数量必须大于0");
        }
        return scaledQty;
    }
    private BigDecimal qtyAvailable(InventoryBalanceEntity balance) {
        return ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyOnHand())
                .subtract(ScalePrecision.zeroDefault(balance.getQtyReserved())));
    }
    private BigDecimal totalAvailable(List<InventoryBalanceEntity> balances) {
        BigDecimal total = ScalePrecision.quantity(BigDecimal.ZERO);
        for (InventoryBalanceEntity balance : balances) {
            total = ScalePrecision.quantity(total.add(qtyAvailable(balance)));
        }
        return total;
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
            BigDecimal available = qtyAvailable(balance);
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

package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryLotBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryLotBalanceEntity;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Owns batch/expiry validation, lot allocation and synchronized lot/aggregate balance mutation.
 * Transaction boundaries intentionally remain on {@link InventoryPostingService}.
 */
@Service
public class InventoryLotPostingService {

    private static final int MAX_ATTEMPTS = 8;

    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final InventoryLotBalanceMapper inventoryLotBalanceMapper;
    private final InventoryTransactionWriter inventoryTransactionWriter;

    public InventoryLotPostingService(
            InventoryBalanceMapper inventoryBalanceMapper,
            InventoryLotBalanceMapper inventoryLotBalanceMapper,
            InventoryTransactionWriter inventoryTransactionWriter
    ) {
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.inventoryLotBalanceMapper = inventoryLotBalanceMapper;
        this.inventoryTransactionWriter = inventoryTransactionWriter;
    }

    boolean isLotControlled(ProductEntity product) {
        return Integer.valueOf(1).equals(product.getLotControlled());
    }

    String normalizeLotNo(String lotNo) {
        if (lotNo == null) {
            return null;
        }
        String normalized = lotNo.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    String lotKey(String normalizedLotNo) {
        return normalizedLotNo == null ? "" : normalizedLotNo;
    }

    void validateInboundCommand(ProductEntity product, InventoryPostingCommand command) {
        boolean hasLotMetadata = normalizeLotNo(command.lotNo()) != null
                || command.productionDate() != null
                || command.expiryDate() != null;
        if (!isLotControlled(product)) {
            if (hasLotMetadata) {
                throw new IllegalArgumentException("未启用批次管理的商品不能填写批次信息");
            }
            return;
        }
        if (normalizeLotNo(command.lotNo()) == null) {
            throw new IllegalArgumentException("启用批次管理的商品必须填写批次号");
        }
        if (isShelfLifeControlled(product) && command.expiryDate() == null) {
            throw new IllegalArgumentException("启用效期管理的商品必须填写有效期");
        }
    }

    void validateOutboundCommand(ProductEntity product, InventoryPostingCommand command) {
        String normalizedLotNo = normalizeLotNo(command.lotNo());
        boolean hasLotMetadata = normalizedLotNo != null
                || command.productionDate() != null
                || command.expiryDate() != null;
        if (!isLotControlled(product)) {
            if (hasLotMetadata) {
                throw new IllegalArgumentException("未启用批次管理的商品不能填写批次信息");
            }
            return;
        }
        if (normalizedLotNo == null && (command.productionDate() != null || command.expiryDate() != null)) {
            throw new IllegalArgumentException("出库填写批次生产日期或有效期时必须指定批号");
        }
    }

    boolean upsertInboundBalance(
            InventoryPostingCommand command,
            AuditMetadata audit,
            LocalDateTime now,
            String normalizedLotNo,
            BigDecimal scaledQty,
            BigDecimal scaledAmount
    ) {
        InventoryLotBalanceEntity lotBalance = selectLotBalance(
                audit.companyId(),
                audit.accountBookId(),
                command.warehouseId(),
                command.productId(),
                command.locationId(),
                normalizedLotNo
        );
        if (lotBalance == null) {
            InventoryLotBalanceEntity newLotBalance = new InventoryLotBalanceEntity();
            newLotBalance.setCompanyId(audit.companyId());
            newLotBalance.setAccountBookId(audit.accountBookId());
            newLotBalance.setWarehouseId(command.warehouseId());
            newLotBalance.setProductId(command.productId());
            newLotBalance.setLocationId(command.locationId());
            newLotBalance.setLotNo(normalizedLotNo);
            newLotBalance.setProductionDate(command.productionDate());
            newLotBalance.setExpiryDate(command.expiryDate());
            newLotBalance.setFirstInboundTime(now);
            newLotBalance.setQtyOnHand(scaledQty);
            newLotBalance.setQtyReserved(ScalePrecision.quantity(BigDecimal.ZERO));
            newLotBalance.setAmountOnHand(scaledAmount);
            newLotBalance.setCreatedBy(audit.userId());
            newLotBalance.setCreatedTime(now);
            newLotBalance.setUpdatedBy(audit.userId());
            newLotBalance.setUpdatedTime(now);
            newLotBalance.setVersion(0);
            try {
                inventoryLotBalanceMapper.insert(newLotBalance);
                return true;
            } catch (DuplicateKeyException ex) {
                return false;
            }
        }

        validateExistingLotMetadata(lotBalance, command);
        lotBalance.setQtyOnHand(ScalePrecision.quantity(ScalePrecision.zeroDefault(lotBalance.getQtyOnHand()).add(scaledQty)));
        lotBalance.setAmountOnHand(ScalePrecision.amount(ScalePrecision.zeroDefault(lotBalance.getAmountOnHand()).add(scaledAmount)));
        lotBalance.setUpdatedBy(audit.userId());
        lotBalance.setUpdatedTime(now);
        return inventoryLotBalanceMapper.updateById(lotBalance) == 1;
    }

    List<LotAllocation> postOutbound(
            InventoryPostingCommand command,
            AuditMetadata audit,
            String shortageMessage,
            ProductEntity product,
            String normalizedLotNo,
            BigDecimal scaledQty
    ) {
        LocalDateTime now = audit.now();
        attemptLoop:
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            List<LotAllocation> postedAllocations = inventoryTransactionWriter.postedAllocations(
                    command,
                    audit.companyId(),
                    audit.accountBookId(),
                    "OUT"
            );
            if (!postedAllocations.isEmpty()) {
                return postedAllocations;
            }
            List<LotAllocation> allocations = normalizedLotNo == null
                    ? allocateAutomaticLots(command, audit, product, scaledQty, shortageMessage)
                    : allocateExplicitLot(command, audit, normalizedLotNo, scaledQty, shortageMessage);
            BigDecimal allocationAmount = ScalePrecision.amount(allocations.stream()
                    .map(LotAllocation::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

            for (LotAllocation allocation : allocations) {
                InventoryLotBalanceEntity lot = allocation.lot();
                lot.setQtyOnHand(ScalePrecision.quantity(ScalePrecision.zeroDefault(lot.getQtyOnHand()).subtract(allocation.qty())));
                lot.setAmountOnHand(ScalePrecision.amount(ScalePrecision.zeroDefault(lot.getAmountOnHand()).subtract(allocation.amount())));
                lot.setUpdatedBy(audit.userId());
                lot.setUpdatedTime(now);
                if (inventoryLotBalanceMapper.updateById(lot) != 1) {
                    if (allocation == allocations.get(0)) {
                        continue attemptLoop;
                    }
                    throw concurrentBalanceMutation();
                }
            }

            InventoryBalanceEntity balance = selectBalance(
                    audit.companyId(),
                    audit.accountBookId(),
                    command.warehouseId(),
                    command.productId(),
                    command.locationId()
            );
            if (balance == null || qtyAvailable(balance).compareTo(scaledQty) < 0) {
                throw new IllegalArgumentException(shortageMessage);
            }
            balance.setQtyOnHand(ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyOnHand()).subtract(scaledQty)));
            balance.setAmountOnHand(ScalePrecision.amount(ScalePrecision.zeroDefault(balance.getAmountOnHand()).subtract(allocationAmount)));
            balance.setUpdatedBy(audit.userId());
            balance.setUpdatedTime(now);
            if (inventoryBalanceMapper.updateById(balance) != 1) {
                throw concurrentBalanceMutation();
            }

            for (LotAllocation allocation : allocations) {
                InventoryLotBalanceEntity lot = allocation.lot();
                inventoryTransactionWriter.insert(
                        command,
                        "OUT",
                        audit,
                        now,
                        allocation.qty(),
                        allocation.amount(),
                        lot.getLotNo(),
                        lot.getProductionDate(),
                        lot.getExpiryDate(),
                        lotKey(lot.getLotNo())
                );
            }
            return allocations;
        }
        throw concurrentBalanceMutation();
    }

    private void validateExistingLotMetadata(InventoryLotBalanceEntity existing, InventoryPostingCommand command) {
        if (!Objects.equals(existing.getExpiryDate(), command.expiryDate())) {
            throw new IllegalArgumentException("批次有效期与已有批次不一致");
        }
        if (!Objects.equals(existing.getProductionDate(), command.productionDate())) {
            throw new IllegalArgumentException("批次生产日期与已有批次不一致");
        }
    }

    private InventoryLotBalanceEntity selectLotBalance(
            Long companyId,
            Long accountBookId,
            Long warehouseId,
            Long productId,
            Long locationId,
            String lotNo
    ) {
        LambdaQueryWrapper<InventoryLotBalanceEntity> wrapper = new LambdaQueryWrapper<InventoryLotBalanceEntity>()
                .eq(InventoryLotBalanceEntity::getCompanyId, companyId)
                .eq(InventoryLotBalanceEntity::getAccountBookId, accountBookId)
                .eq(InventoryLotBalanceEntity::getWarehouseId, warehouseId)
                .eq(InventoryLotBalanceEntity::getProductId, productId)
                .eq(InventoryLotBalanceEntity::getLotNo, lotNo);
        if (locationId != null) {
            wrapper.eq(InventoryLotBalanceEntity::getLocationId, locationId);
        }
        return inventoryLotBalanceMapper.selectOne(wrapper.last("limit 1"));
    }

    private List<LotAllocation> allocateExplicitLot(
            InventoryPostingCommand command,
            AuditMetadata audit,
            String normalizedLotNo,
            BigDecimal scaledQty,
            String shortageMessage
    ) {
        InventoryLotBalanceEntity lot = selectLotBalance(
                audit.companyId(),
                audit.accountBookId(),
                command.warehouseId(),
                command.productId(),
                command.locationId(),
                normalizedLotNo
        );
        if (lot == null || lotAvailable(lot).compareTo(scaledQty) < 0) {
            throw new IllegalArgumentException(shortageMessage);
        }
        if (isExpired(lot, outboundReferenceDate(command, audit))) {
            throw new IllegalArgumentException("批次已过期，不能出库");
        }
        return List.of(new LotAllocation(lot, scaledQty, lotOutboundAmount(lot, scaledQty)));
    }

    private List<LotAllocation> allocateAutomaticLots(
            InventoryPostingCommand command,
            AuditMetadata audit,
            ProductEntity product,
            BigDecimal scaledQty,
            String shortageMessage
    ) {
        List<InventoryLotBalanceEntity> lots = inventoryLotBalanceMapper.selectList(
                candidateLotWrapper(command, audit, product)
        );
        BigDecimal totalAvailable = lots.stream()
                .map(this::lotAvailable)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (ScalePrecision.quantity(totalAvailable).compareTo(scaledQty) < 0) {
            throw new IllegalArgumentException(shortageMessage);
        }

        List<LotAllocation> allocations = new ArrayList<>();
        BigDecimal remainingQty = scaledQty;
        for (InventoryLotBalanceEntity lot : lots) {
            if (remainingQty.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal available = lotAvailable(lot);
            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal allocatedQty = ScalePrecision.quantity(available.min(remainingQty));
            allocations.add(new LotAllocation(lot, allocatedQty, lotOutboundAmount(lot, allocatedQty)));
            remainingQty = ScalePrecision.quantity(remainingQty.subtract(allocatedQty));
        }
        return allocations;
    }

    private LambdaQueryWrapper<InventoryLotBalanceEntity> candidateLotWrapper(
            InventoryPostingCommand command,
            AuditMetadata audit,
            ProductEntity product
    ) {
        LambdaQueryWrapper<InventoryLotBalanceEntity> wrapper = new LambdaQueryWrapper<InventoryLotBalanceEntity>()
                .eq(InventoryLotBalanceEntity::getCompanyId, audit.companyId())
                .eq(InventoryLotBalanceEntity::getAccountBookId, audit.accountBookId())
                .eq(InventoryLotBalanceEntity::getWarehouseId, command.warehouseId())
                .eq(InventoryLotBalanceEntity::getProductId, command.productId())
                .eq(command.locationId() != null, InventoryLotBalanceEntity::getLocationId, command.locationId())
                .apply("qty_on_hand - qty_reserved > 0");
        LocalDate referenceDate = outboundReferenceDate(command, audit);
        wrapper.and(query -> query
                .isNull(InventoryLotBalanceEntity::getExpiryDate)
                .or()
                .ge(InventoryLotBalanceEntity::getExpiryDate, referenceDate));
        if (isShelfLifeControlled(product)) {
            wrapper.last("order by case when expiry_date is null then 1 else 0 end, expiry_date asc, first_inbound_time asc, id asc");
        } else {
            wrapper.orderByAsc(InventoryLotBalanceEntity::getFirstInboundTime)
                    .orderByAsc(InventoryLotBalanceEntity::getId);
        }
        return wrapper;
    }

    private InventoryBalanceEntity selectBalance(Long companyId, Long accountBookId, Long warehouseId, Long productId, Long locationId) {
        LambdaQueryWrapper<InventoryBalanceEntity> wrapper = new LambdaQueryWrapper<InventoryBalanceEntity>()
                .eq(InventoryBalanceEntity::getCompanyId, companyId)
                .eq(InventoryBalanceEntity::getAccountBookId, accountBookId)
                .eq(InventoryBalanceEntity::getWarehouseId, warehouseId)
                .eq(InventoryBalanceEntity::getProductId, productId);
        if (locationId != null) {
            wrapper.eq(InventoryBalanceEntity::getLocationId, locationId);
        }
        return inventoryBalanceMapper.selectOne(wrapper.last("limit 1"));
    }

    private boolean isShelfLifeControlled(ProductEntity product) {
        return Integer.valueOf(1).equals(product.getShelfLifeControlled());
    }

    private LocalDate outboundReferenceDate(InventoryPostingCommand command, AuditMetadata audit) {
        return command.bizDate() == null ? audit.now().toLocalDate() : command.bizDate();
    }

    private boolean isExpired(InventoryLotBalanceEntity lot, LocalDate referenceDate) {
        return lot.getExpiryDate() != null && lot.getExpiryDate().isBefore(referenceDate);
    }

    private BigDecimal qtyAvailable(InventoryBalanceEntity balance) {
        return ScalePrecision.quantity(
                ScalePrecision.zeroDefault(balance.getQtyOnHand())
                        .subtract(ScalePrecision.zeroDefault(balance.getQtyReserved()))
        );
    }

    private BigDecimal lotAvailable(InventoryLotBalanceEntity lot) {
        return ScalePrecision.quantity(
                ScalePrecision.zeroDefault(lot.getQtyOnHand())
                        .subtract(ScalePrecision.zeroDefault(lot.getQtyReserved()))
        );
    }

    private BigDecimal lotOutboundAmount(InventoryLotBalanceEntity lot, BigDecimal outboundQty) {
        BigDecimal qtyOnHand = ScalePrecision.quantity(ScalePrecision.zeroDefault(lot.getQtyOnHand()));
        BigDecimal amountOnHand = ScalePrecision.amount(ScalePrecision.zeroDefault(lot.getAmountOnHand()));
        if (qtyOnHand.compareTo(outboundQty) == 0) {
            return amountOnHand;
        }
        BigDecimal unitCost = ScalePrecision.unitCost(amountOnHand, qtyOnHand);
        return ScalePrecision.amount(unitCost.multiply(outboundQty));
    }

    private BusinessConflictException concurrentBalanceMutation() {
        return new BusinessConflictException("库存余额已被其他操作修改，请重试");
    }
}

package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.stock.mapper.InventoryLotBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryLotBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class InventoryPostingService {

    private static final int MAX_ATTEMPTS = 8;

    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final InventoryTransactionWriter inventoryTransactionWriter;
    private final InventoryReservationPostingService inventoryReservationPostingService;
    private final ProductMapper productMapper;
    private final InventoryLotBalanceMapper inventoryLotBalanceMapper;

    public InventoryPostingService(
            InventoryBalanceMapper inventoryBalanceMapper,
            InventoryTransactionWriter inventoryTransactionWriter,
            InventoryReservationPostingService inventoryReservationPostingService,
            ProductMapper productMapper,
            InventoryLotBalanceMapper inventoryLotBalanceMapper
    ) {
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.inventoryTransactionWriter = inventoryTransactionWriter;
        this.inventoryReservationPostingService = inventoryReservationPostingService;
        this.productMapper = productMapper;
        this.inventoryLotBalanceMapper = inventoryLotBalanceMapper;
    }

    @Transactional
    public void postInbound(InventoryPostingCommand command, AuditMetadata audit) {
        BigDecimal scaledQty = requirePositiveQuantity(command);
        BigDecimal scaledAmount = ScalePrecision.amount(command.amount());
        ProductEntity product = requireProduct(audit.companyId(), audit.accountBookId(), command.productId());
        validateLotCommandForInbound(product, command);
        String normalizedLotNo = normalizeLotNo(command.lotNo());
        String lotKey = lotControlled(product) ? lotKey(normalizedLotNo) : lotKey(null);
        if (inventoryTransactionWriter.findPostedTransaction(command, audit.companyId(), audit.accountBookId(), "IN", lotKey) != null) {
            return;
        }
        LocalDateTime now = audit.now();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            boolean lotBalanceMutated = false;
            if (lotControlled(product)) {
                lotBalanceMutated = upsertInboundLotBalance(command, audit, now, normalizedLotNo, scaledQty, scaledAmount, product);
                if (!lotBalanceMutated) {
                    continue;
                }
            }
            InventoryBalanceEntity balance = selectBalance(audit.companyId(), audit.accountBookId(), command.warehouseId(), command.productId());
            if (balance == null) {
                InventoryBalanceEntity newBalance = new InventoryBalanceEntity();
                newBalance.setCompanyId(audit.companyId());
                newBalance.setAccountBookId(audit.accountBookId());
                newBalance.setWarehouseId(command.warehouseId());
                newBalance.setProductId(command.productId());
                newBalance.setQtyOnHand(scaledQty);
                newBalance.setQtyReserved(ScalePrecision.quantity(BigDecimal.ZERO));
                newBalance.setAmountOnHand(scaledAmount);
                newBalance.setCreatedBy(audit.userId());
                newBalance.setCreatedTime(now);
                newBalance.setUpdatedBy(audit.userId());
                newBalance.setUpdatedTime(now);
                newBalance.setVersion(0);
                try {
                    inventoryBalanceMapper.insert(newBalance);
                } catch (DuplicateKeyException ex) {
                    if (lotBalanceMutated) {
                        throw new BusinessConflictException("库存余额已被其他操作修改，请重试");
                    }
                    continue;
                }
                inventoryTransactionWriter.insert(command, "IN", audit, now, command.qty(), scaledAmount,
                        normalizedLotNo, command.productionDate(), command.expiryDate(), lotKey);
                return;
            }

            balance.setQtyOnHand(ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyOnHand()).add(scaledQty)));
            balance.setAmountOnHand(ScalePrecision.amount(ScalePrecision.zeroDefault(balance.getAmountOnHand()).add(scaledAmount)));
            balance.setUpdatedBy(audit.userId());
            balance.setUpdatedTime(now);
            if (inventoryBalanceMapper.updateById(balance) == 1) {
                inventoryTransactionWriter.insert(command, "IN", audit, now, command.qty(), scaledAmount,
                        normalizedLotNo, command.productionDate(), command.expiryDate(), lotKey);
                return;
            }
            if (lotControlled(product)) {
                throw new BusinessConflictException("库存余额已被其他操作修改，请重试");
            }
        }

        throw new BusinessConflictException("库存余额已被其他操作修改，请重试");
    }

    @Transactional
    public BigDecimal postOutbound(InventoryPostingCommand command, AuditMetadata audit, String shortageMessage) {
        return ScalePrecision.amount(postOutboundWithAllocations(command, audit, shortageMessage).stream()
                .map(LotAllocation::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Transactional
    public List<LotAllocation> postOutboundWithAllocations(InventoryPostingCommand command, AuditMetadata audit, String shortageMessage) {
        BigDecimal scaledQty = requirePositiveQuantity(command);
        ProductEntity product = requireProduct(audit.companyId(), audit.accountBookId(), command.productId());
        validateLotCommandForOutbound(product, command);
        String normalizedLotNo = normalizeLotNo(command.lotNo());
        List<LotAllocation> postedAllocations = inventoryTransactionWriter.postedAllocations(command, audit.companyId(), audit.accountBookId(), "OUT");
        if (!postedAllocations.isEmpty()) {
            return postedAllocations;
        }
        if (lotControlled(product)) {
            return postLotOutbound(command, audit, shortageMessage, product, normalizedLotNo, scaledQty);
        }

        LocalDateTime now = audit.now();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            InventoryBalanceEntity balance = selectBalance(audit.companyId(), audit.accountBookId(), command.warehouseId(), command.productId());
            if (balance == null) {
                throw new IllegalArgumentException(shortageMessage);
            }
            if (qtyAvailable(balance).compareTo(scaledQty) < 0) {
                throw new IllegalArgumentException(shortageMessage);
            }

            balance.setQtyOnHand(ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyOnHand()).subtract(scaledQty)));
            BigDecimal outboundCostAmount = outboundCostAmount(balance, scaledQty);
            balance.setAmountOnHand(ScalePrecision.amount(ScalePrecision.zeroDefault(balance.getAmountOnHand()).subtract(outboundCostAmount)));
            balance.setUpdatedBy(audit.userId());
            balance.setUpdatedTime(now);
            if (inventoryBalanceMapper.updateById(balance) == 1) {
                inventoryTransactionWriter.insert(command, "OUT", audit, now, command.qty(), outboundCostAmount,
                        null, null, null, lotKey(null));
                return List.of(new LotAllocation(null, scaledQty, outboundCostAmount));
            }
        }

        throw new BusinessConflictException("库存余额已被其他操作修改，请重试");
    }

    @Transactional(readOnly = true)
    public BigDecimal getQtyOnHand(Long warehouseId, Long productId, Long companyId, Long accountBookId) {
        InventoryBalanceEntity balance = selectBalance(companyId, accountBookId, warehouseId, productId);
        if (balance == null) {
            return ScalePrecision.quantity(BigDecimal.ZERO);
        }
        return ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyOnHand()));
    }

    @Transactional(readOnly = true)
    public BigDecimal getQtyAvailable(Long warehouseId, Long productId, Long companyId, Long accountBookId) {
        InventoryBalanceEntity balance = selectBalance(companyId, accountBookId, warehouseId, productId);
        if (balance == null) {
            return ScalePrecision.quantity(BigDecimal.ZERO);
        }
        return qtyAvailable(balance);
    }

    @Transactional
    public void reserve(InventoryReservationCommand command, AuditMetadata audit, String shortageMessage) {
        inventoryReservationPostingService.reserve(command, audit, shortageMessage);
    }

    @Transactional
    public void releaseReservation(String sourceType, Long sourceLineId, BigDecimal qty, AuditMetadata audit) {
        inventoryReservationPostingService.releaseReservation(sourceType, sourceLineId, qty, audit);
    }

    @Transactional
    public void manualReleaseReservation(Long reservationId, BigDecimal qty, AuditMetadata audit, String reason) {
        inventoryReservationPostingService.manualReleaseReservation(reservationId, qty, audit, reason);
    }

    @Transactional
    public void releaseAllReservations(String sourceType, Long sourceId, AuditMetadata audit) {
        inventoryReservationPostingService.releaseAllReservations(sourceType, sourceId, audit);
    }

    @Transactional
    public void restoreReservation(String sourceType, Long sourceLineId, BigDecimal qty, AuditMetadata audit, String reason) {
        inventoryReservationPostingService.restoreReservation(sourceType, sourceLineId, qty, audit, reason);
    }

    private BigDecimal requirePositiveQuantity(InventoryPostingCommand command) {
        return requirePositiveQuantity(command.qty());
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
        return inventoryBalanceMapper.selectOne(new LambdaQueryWrapper<InventoryBalanceEntity>()
                .eq(InventoryBalanceEntity::getCompanyId, companyId)
                .eq(InventoryBalanceEntity::getAccountBookId, accountBookId)
                .eq(InventoryBalanceEntity::getWarehouseId, warehouseId)
                .eq(InventoryBalanceEntity::getProductId, productId));
    }

    private ProductEntity requireProduct(Long companyId, Long accountBookId, Long productId) {
        ProductEntity product = productMapper.selectOne(new LambdaQueryWrapper<ProductEntity>()
                .eq(ProductEntity::getCompanyId, companyId)
                .eq(ProductEntity::getAccountBookId, accountBookId)
                .eq(ProductEntity::getId, productId)
                .eq(ProductEntity::getDeletedFlag, 0)
                .last("limit 1"));
        if (product == null) {
            throw new IllegalArgumentException("商品不存在");
        }
        return product;
    }

    private boolean lotControlled(ProductEntity product) {
        return Integer.valueOf(1).equals(product.getLotControlled());
    }

    private boolean shelfLifeControlled(ProductEntity product) {
        return Integer.valueOf(1).equals(product.getShelfLifeControlled());
    }

    private String normalizeLotNo(String lotNo) {
        if (lotNo == null) {
            return null;
        }
        String normalized = lotNo.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String lotKey(String normalizedLotNo) {
        return normalizedLotNo == null ? "" : normalizedLotNo;
    }

    private void validateLotCommandForInbound(ProductEntity product, InventoryPostingCommand command) {
        boolean hasLotMetadata = normalizeLotNo(command.lotNo()) != null
                || command.productionDate() != null
                || command.expiryDate() != null;
        if (!lotControlled(product)) {
            if (hasLotMetadata) {
                throw new IllegalArgumentException("未启用批次管理的商品不能填写批次信息");
            }
            return;
        }
        if (normalizeLotNo(command.lotNo()) == null) {
            throw new IllegalArgumentException("启用批次管理的商品必须填写批次号");
        }
        if (shelfLifeControlled(product) && command.expiryDate() == null) {
            throw new IllegalArgumentException("启用效期管理的商品必须填写有效期");
        }
    }

    private void validateLotCommandForOutbound(ProductEntity product, InventoryPostingCommand command) {
        String normalizedLotNo = normalizeLotNo(command.lotNo());
        boolean hasLotMetadata = normalizedLotNo != null
                || command.productionDate() != null
                || command.expiryDate() != null;
        if (!lotControlled(product)) {
            if (hasLotMetadata) {
                throw new IllegalArgumentException("未启用批次管理的商品不能填写批次信息");
            }
            return;
        }
        if (normalizedLotNo == null && (command.productionDate() != null || command.expiryDate() != null)) {
            throw new IllegalArgumentException("出库填写批次生产日期或有效期时必须指定批号");
        }
    }

    private boolean upsertInboundLotBalance(
            InventoryPostingCommand command,
            AuditMetadata audit,
            LocalDateTime now,
            String normalizedLotNo,
            BigDecimal scaledQty,
            BigDecimal scaledAmount,
            ProductEntity product
    ) {
        InventoryLotBalanceEntity lotBalance = selectLotBalance(
                audit.companyId(),
                audit.accountBookId(),
                command.warehouseId(),
                command.productId(),
                normalizedLotNo
        );
        if (lotBalance == null) {
            InventoryLotBalanceEntity newLotBalance = new InventoryLotBalanceEntity();
            newLotBalance.setCompanyId(audit.companyId());
            newLotBalance.setAccountBookId(audit.accountBookId());
            newLotBalance.setWarehouseId(command.warehouseId());
            newLotBalance.setProductId(command.productId());
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
            String lotNo
    ) {
        return inventoryLotBalanceMapper.selectOne(new LambdaQueryWrapper<InventoryLotBalanceEntity>()
                .eq(InventoryLotBalanceEntity::getCompanyId, companyId)
                .eq(InventoryLotBalanceEntity::getAccountBookId, accountBookId)
                .eq(InventoryLotBalanceEntity::getWarehouseId, warehouseId)
                .eq(InventoryLotBalanceEntity::getProductId, productId)
                .eq(InventoryLotBalanceEntity::getLotNo, lotNo)
                .last("limit 1"));
    }

    private List<LotAllocation> postLotOutbound(
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
            List<LotAllocation> postedAllocations = inventoryTransactionWriter.postedAllocations(command, audit.companyId(), audit.accountBookId(), "OUT");
            if (!postedAllocations.isEmpty()) {
                return postedAllocations;
            }
            List<LotAllocation> allocations = normalizedLotNo == null
                    ? allocateAutomaticLots(command, audit, product, scaledQty, shortageMessage)
                    : allocateExplicitLot(command, audit, normalizedLotNo, scaledQty, shortageMessage);
            BigDecimal allocationAmount = allocations.stream()
                    .map(LotAllocation::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            allocationAmount = ScalePrecision.amount(allocationAmount);

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
                    throw new BusinessConflictException("库存余额已被其他操作修改，请重试");
                }
            }

            InventoryBalanceEntity balance = selectBalance(audit.companyId(), audit.accountBookId(), command.warehouseId(), command.productId());
            if (balance == null || qtyAvailable(balance).compareTo(scaledQty) < 0) {
                throw new IllegalArgumentException(shortageMessage);
            }
            balance.setQtyOnHand(ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyOnHand()).subtract(scaledQty)));
            balance.setAmountOnHand(ScalePrecision.amount(ScalePrecision.zeroDefault(balance.getAmountOnHand()).subtract(allocationAmount)));
            balance.setUpdatedBy(audit.userId());
            balance.setUpdatedTime(now);
            if (inventoryBalanceMapper.updateById(balance) != 1) {
                throw new BusinessConflictException("库存余额已被其他操作修改，请重试");
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
        throw new BusinessConflictException("库存余额已被其他操作修改，请重试");
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
                normalizedLotNo
        );
        if (lot == null || lotAvailable(lot).compareTo(scaledQty) < 0) {
            throw new IllegalArgumentException(shortageMessage);
        }
        if (expired(lot, outboundReferenceDate(command, audit))) {
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
        List<InventoryLotBalanceEntity> lots = inventoryLotBalanceMapper.selectList(candidateLotWrapper(command, audit, product));
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
            BigDecimal allocatedQty = available.min(remainingQty);
            allocatedQty = ScalePrecision.quantity(allocatedQty);
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
                .apply("qty_on_hand - qty_reserved > 0");
        LocalDate referenceDate = outboundReferenceDate(command, audit);
        wrapper.and(query -> query
                .isNull(InventoryLotBalanceEntity::getExpiryDate)
                .or()
                .ge(InventoryLotBalanceEntity::getExpiryDate, referenceDate));
        if (shelfLifeControlled(product)) {
            wrapper.last("order by case when expiry_date is null then 1 else 0 end, expiry_date asc, first_inbound_time asc, id asc");
        } else {
            wrapper.orderByAsc(InventoryLotBalanceEntity::getFirstInboundTime)
                    .orderByAsc(InventoryLotBalanceEntity::getId);
        }
        return wrapper;
    }

    private LocalDate outboundReferenceDate(InventoryPostingCommand command, AuditMetadata audit) {
        return command.bizDate() == null ? audit.now().toLocalDate() : command.bizDate();
    }

    private boolean expired(InventoryLotBalanceEntity lot, LocalDate referenceDate) {
        return lot.getExpiryDate() != null && lot.getExpiryDate().isBefore(referenceDate);
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

    private BigDecimal outboundCostAmount(InventoryBalanceEntity balance, BigDecimal outboundQty) {
        BigDecimal qtyOnHand = ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyOnHand()).add(outboundQty));
        BigDecimal amountOnHand = ScalePrecision.amount(ScalePrecision.zeroDefault(balance.getAmountOnHand()));
        if (qtyOnHand.compareTo(outboundQty) == 0) {
            return amountOnHand;
        }
        BigDecimal unitCost = ScalePrecision.unitCost(amountOnHand, qtyOnHand);
        return ScalePrecision.amount(unitCost.multiply(outboundQty));
    }

}

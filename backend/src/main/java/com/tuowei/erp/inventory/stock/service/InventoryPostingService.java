package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryLotBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InventoryPostingService {

    private static final int MAX_ATTEMPTS = 8;

    private final InventoryBalanceMapper inventoryBalanceMapper;
    private final InventoryTransactionWriter inventoryTransactionWriter;
    private final InventoryReservationPostingService inventoryReservationPostingService;
    private final ProductMapper productMapper;
    private final InventoryLotPostingService inventoryLotPostingService;

    @Autowired
    public InventoryPostingService(
            InventoryBalanceMapper inventoryBalanceMapper,
            InventoryTransactionWriter inventoryTransactionWriter,
            InventoryReservationPostingService inventoryReservationPostingService,
            ProductMapper productMapper,
            InventoryLotPostingService inventoryLotPostingService
    ) {
        this.inventoryBalanceMapper = inventoryBalanceMapper;
        this.inventoryTransactionWriter = inventoryTransactionWriter;
        this.inventoryReservationPostingService = inventoryReservationPostingService;
        this.productMapper = productMapper;
        this.inventoryLotPostingService = inventoryLotPostingService;
    }

    /**
     * Compatibility constructor for focused unit tests and downstream modules that build the facade directly.
     * Spring uses the collaborator-based constructor above.
     */
    public InventoryPostingService(
            InventoryBalanceMapper inventoryBalanceMapper,
            InventoryTransactionWriter inventoryTransactionWriter,
            InventoryReservationPostingService inventoryReservationPostingService,
            ProductMapper productMapper,
            InventoryLotBalanceMapper inventoryLotBalanceMapper
    ) {
        this(
                inventoryBalanceMapper,
                inventoryTransactionWriter,
                inventoryReservationPostingService,
                productMapper,
                new InventoryLotPostingService(
                        inventoryBalanceMapper,
                        inventoryLotBalanceMapper,
                        inventoryTransactionWriter
                )
        );
    }

    @Transactional
    public void postInbound(InventoryPostingCommand command, AuditMetadata audit) {
        BigDecimal scaledQty = requirePositiveQuantity(command);
        BigDecimal scaledAmount = ScalePrecision.amount(command.amount());
        ProductEntity product = requireProduct(audit.companyId(), audit.accountBookId(), command.productId());
        inventoryLotPostingService.validateInboundCommand(product, command);
        String normalizedLotNo = inventoryLotPostingService.normalizeLotNo(command.lotNo());
        String lotKey = inventoryLotPostingService.lotKey(
                inventoryLotPostingService.isLotControlled(product) ? normalizedLotNo : null
        );
        if (inventoryTransactionWriter.findPostedTransaction(command, audit.companyId(), audit.accountBookId(), "IN", lotKey) != null) {
            return;
        }
        LocalDateTime now = audit.now();

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            boolean lotBalanceMutated = false;
            if (inventoryLotPostingService.isLotControlled(product)) {
                lotBalanceMutated = inventoryLotPostingService.upsertInboundBalance(
                        command,
                        audit,
                        now,
                        normalizedLotNo,
                        scaledQty,
                        scaledAmount
                );
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
            if (inventoryLotPostingService.isLotControlled(product)) {
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
        inventoryLotPostingService.validateOutboundCommand(product, command);
        String normalizedLotNo = inventoryLotPostingService.normalizeLotNo(command.lotNo());
        List<LotAllocation> postedAllocations = inventoryTransactionWriter.postedAllocations(command, audit.companyId(), audit.accountBookId(), "OUT");
        if (!postedAllocations.isEmpty()) {
            return postedAllocations;
        }
        if (inventoryLotPostingService.isLotControlled(product)) {
            return inventoryLotPostingService.postOutbound(
                    command,
                    audit,
                    shortageMessage,
                    product,
                    normalizedLotNo,
                    scaledQty
            );
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
                        null, null, null, inventoryLotPostingService.lotKey(null));
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

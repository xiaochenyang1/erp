package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryLotBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InventoryTransactionWriter {

    private final InventoryTransactionMapper inventoryTransactionMapper;

    public InventoryTransactionWriter(InventoryTransactionMapper inventoryTransactionMapper) {
        this.inventoryTransactionMapper = inventoryTransactionMapper;
    }

    public InventoryTransactionEntity findPostedTransaction(
            InventoryPostingCommand command,
            Long companyId,
            Long accountBookId,
            String direction,
            String lotKey
    ) {
        return inventoryTransactionMapper.selectOne(new LambdaQueryWrapper<InventoryTransactionEntity>()
                .eq(InventoryTransactionEntity::getCompanyId, companyId)
                .eq(InventoryTransactionEntity::getAccountBookId, accountBookId)
                .eq(InventoryTransactionEntity::getBizType, command.bizType())
                .eq(InventoryTransactionEntity::getBizLineId, command.bizLineId())
                .eq(InventoryTransactionEntity::getDirection, direction)
                .eq(InventoryTransactionEntity::getLotKey, lotKey)
                .last("limit 1"));
    }

    public List<LotAllocation> postedAllocations(
            InventoryPostingCommand command,
            Long companyId,
            Long accountBookId,
            String direction
    ) {
        List<InventoryTransactionEntity> transactions = inventoryTransactionMapper.selectList(new LambdaQueryWrapper<InventoryTransactionEntity>()
                .eq(InventoryTransactionEntity::getCompanyId, companyId)
                .eq(InventoryTransactionEntity::getAccountBookId, accountBookId)
                .eq(InventoryTransactionEntity::getBizType, command.bizType())
                .eq(InventoryTransactionEntity::getBizLineId, command.bizLineId())
                .eq(InventoryTransactionEntity::getDirection, direction)
                .orderByAsc(InventoryTransactionEntity::getId));
        if (transactions.isEmpty()) {
            return List.of();
        }
        return transactions.stream()
                .map(this::toPostedAllocation)
                .toList();
    }

    public void insert(
            InventoryPostingCommand command,
            String direction,
            AuditMetadata audit,
            LocalDateTime now,
            BigDecimal transactionQty,
            BigDecimal transactionAmount,
            String lotNo,
            LocalDate productionDate,
            LocalDate expiryDate,
            String lotKey
    ) {
        LocalDateTime occurredTime = command.bizDate() == null ? now : command.bizDate().atStartOfDay();
        BigDecimal scaledQty = ScalePrecision.quantity(transactionQty);
        BigDecimal scaledAmount = ScalePrecision.amount(transactionAmount);
        InventoryTransactionEntity transaction = new InventoryTransactionEntity();
        transaction.setCompanyId(audit.companyId());
        transaction.setAccountBookId(audit.accountBookId());
        transaction.setWarehouseId(command.warehouseId());
        transaction.setProductId(command.productId());
        transaction.setBizType(command.bizType());
        transaction.setBizNo(command.bizNo());
        transaction.setBizLineId(command.bizLineId());
        transaction.setDirection(direction);
        transaction.setQty(scaledQty);
        transaction.setAmount(scaledAmount);
        transaction.setUnitCost(ScalePrecision.unitCost(scaledAmount, scaledQty));
        transaction.setOccurredTime(occurredTime);
        transaction.setLotNo(lotNo);
        transaction.setProductionDate(productionDate);
        transaction.setExpiryDate(expiryDate);
        transaction.setLotKey(lotKey);
        transaction.setRemark(command.remark());
        transaction.setCreatedBy(audit.userId());
        transaction.setCreatedTime(now);
        transaction.setUpdatedBy(audit.userId());
        transaction.setUpdatedTime(now);
        transaction.setVersion(0);
        inventoryTransactionMapper.insert(transaction);
    }

    private LotAllocation toPostedAllocation(InventoryTransactionEntity transaction) {
        InventoryLotBalanceEntity lot = null;
        if (normalizeLotNo(transaction.getLotNo()) != null) {
            lot = new InventoryLotBalanceEntity();
            lot.setWarehouseId(transaction.getWarehouseId());
            lot.setProductId(transaction.getProductId());
            lot.setLotNo(transaction.getLotNo());
            lot.setProductionDate(transaction.getProductionDate());
            lot.setExpiryDate(transaction.getExpiryDate());
        }
        return new LotAllocation(
                lot,
                ScalePrecision.quantity(transaction.getQty()),
                ScalePrecision.amount(transaction.getAmount())
        );
    }

    private String normalizeLotNo(String lotNo) {
        if (lotNo == null) {
            return null;
        }
        String normalized = lotNo.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}

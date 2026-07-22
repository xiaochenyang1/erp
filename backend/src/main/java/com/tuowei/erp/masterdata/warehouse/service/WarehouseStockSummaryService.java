package com.tuowei.erp.masterdata.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.masterdata.warehouse.web.WarehouseStockSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class WarehouseStockSummaryService {

    private final WarehouseMapper warehouseMapper;
    private final InventoryBalanceMapper balanceMapper;
    private final AuditMetadataFactory auditMetadataFactory;

    public WarehouseStockSummaryService(WarehouseMapper warehouseMapper, InventoryBalanceMapper balanceMapper,
            AuditMetadataFactory auditMetadataFactory) {
        this.warehouseMapper = warehouseMapper;
        this.balanceMapper = balanceMapper;
        this.auditMetadataFactory = auditMetadataFactory;
    }

    @Transactional(readOnly = true)
    public WarehouseStockSummaryResponse summary(Long warehouseId) {
        AuditMetadata audit = auditMetadataFactory.current();
        WarehouseEntity warehouse = warehouseMapper.selectOne(new LambdaQueryWrapper<WarehouseEntity>()
                .eq(WarehouseEntity::getId, warehouseId)
                .eq(WarehouseEntity::getCompanyId, audit.companyId())
                .eq(WarehouseEntity::getAccountBookId, audit.accountBookId())
                .eq(WarehouseEntity::getDeletedFlag, 0)
                .last("limit 1"));
        if (warehouse == null) {
            throw new IllegalArgumentException("仓库不存在");
        }

        List<InventoryBalanceEntity> balances = balanceMapper.selectList(new LambdaQueryWrapper<InventoryBalanceEntity>()
                .eq(InventoryBalanceEntity::getCompanyId, audit.companyId())
                .eq(InventoryBalanceEntity::getAccountBookId, audit.accountBookId())
                .eq(InventoryBalanceEntity::getWarehouseId, warehouseId)
                .gt(InventoryBalanceEntity::getQtyOnHand, BigDecimal.ZERO));
        BigDecimal onHand = total(balances, true, false);
        BigDecimal reserved = total(balances, false, false);
        BigDecimal amount = total(balances, false, true);
        return new WarehouseStockSummaryResponse(warehouseId, balances.size(),
                ScalePrecision.quantity(onHand), ScalePrecision.quantity(reserved),
                ScalePrecision.quantity(onHand.subtract(reserved)), ScalePrecision.amount(amount));
    }

    private BigDecimal total(List<InventoryBalanceEntity> balances, boolean onHand, boolean amount) {
        return balances.stream().map(balance -> {
            if (amount) {
                return ScalePrecision.zeroDefault(balance.getAmountOnHand());
            }
            return ScalePrecision.zeroDefault(onHand ? balance.getQtyOnHand() : balance.getQtyReserved());
        }).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

package com.tuowei.erp.inventory.stock.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.math.ScalePrecision;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Read-side inventory balance lookup scoped by company, account book and location. */
@Service
public class InventoryPostingQueryService {
    private final InventoryBalanceMapper inventoryBalanceMapper;

    public InventoryPostingQueryService(InventoryBalanceMapper inventoryBalanceMapper) {
        this.inventoryBalanceMapper = inventoryBalanceMapper;
    }

    @Transactional(readOnly = true)
    public BigDecimal getQtyOnHand(Long warehouseId, Long productId, Long companyId, Long accountBookId) {
        return getQtyOnHand(warehouseId, productId, null, companyId, accountBookId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getQtyOnHand(Long warehouseId, Long productId, Long locationId, Long companyId, Long accountBookId) {
        InventoryBalanceEntity balance = selectBalance(companyId, accountBookId, warehouseId, productId, locationId);
        return balance == null
                ? ScalePrecision.quantity(BigDecimal.ZERO)
                : ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyOnHand()));
    }

    @Transactional(readOnly = true)
    public BigDecimal getQtyAvailable(Long warehouseId, Long productId, Long companyId, Long accountBookId) {
        return getQtyAvailable(warehouseId, productId, null, companyId, accountBookId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getQtyAvailable(Long warehouseId, Long productId, Long locationId, Long companyId, Long accountBookId) {
        InventoryBalanceEntity balance = selectBalance(companyId, accountBookId, warehouseId, productId, locationId);
        if (balance == null) {
            return ScalePrecision.quantity(BigDecimal.ZERO);
        }
        return ScalePrecision.quantity(ScalePrecision.zeroDefault(balance.getQtyOnHand())
                .subtract(ScalePrecision.zeroDefault(balance.getQtyReserved())));
    }

    InventoryBalanceEntity selectBalance(
            Long companyId, Long accountBookId, Long warehouseId, Long productId, Long locationId
    ) {
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
}

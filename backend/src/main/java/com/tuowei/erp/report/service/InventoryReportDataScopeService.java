package com.tuowei.erp.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import org.springframework.stereotype.Service;

@Service
public class InventoryReportDataScopeService {

    private final DataScopeService dataScopeService;

    public InventoryReportDataScopeService(DataScopeService dataScopeService) {
        this.dataScopeService = dataScopeService;
    }

    public LambdaQueryWrapper<InventoryBalanceEntity> applyInventoryBalanceScope(
            LambdaQueryWrapper<InventoryBalanceEntity> wrapper,
            DataScopeSnapshot snapshot
    ) {
        return dataScopeService.applyInventoryBalanceScope(wrapper, snapshot);
    }

    public LambdaQueryWrapper<InventoryTransactionEntity> applyInventoryTransactionScope(
            LambdaQueryWrapper<InventoryTransactionEntity> wrapper,
            DataScopeSnapshot snapshot
    ) {
        return dataScopeService.applyInventoryTransactionScope(wrapper, snapshot);
    }
}

package com.tuowei.erp.purchase.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PurchaseOrderLookupService {

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderLineMapper purchaseOrderLineMapper;

    public PurchaseOrderLookupService(
            PurchaseOrderMapper purchaseOrderMapper,
            PurchaseOrderLineMapper purchaseOrderLineMapper
    ) {
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.purchaseOrderLineMapper = purchaseOrderLineMapper;
    }

    public PurchaseOrderEntity requireOrder(Long orderId) {
        PurchaseOrderEntity entity = purchaseOrderMapper.selectById(orderId);
        if (entity == null || entity.getDeletedFlag() == null || entity.getDeletedFlag() != 0) {
            throw new IllegalArgumentException("采购订单不存在");
        }
        return entity;
    }

    public Map<Long, PurchaseOrderLineEntity> loadOrderLinesAsMap(PurchaseOrderEntity order) {
        return purchaseOrderLineMapper.selectList(
                new LambdaQueryWrapper<PurchaseOrderLineEntity>()
                        .eq(PurchaseOrderLineEntity::getCompanyId, order.getCompanyId())
                        .eq(PurchaseOrderLineEntity::getAccountBookId, order.getAccountBookId())
                        .eq(PurchaseOrderLineEntity::getOrderId, order.getId())
                        .orderByAsc(PurchaseOrderLineEntity::getLineNo)
        ).stream().collect(Collectors.toMap(PurchaseOrderLineEntity::getId, Function.identity()));
    }

    public PurchaseOrderLineEntity requireOrderLine(Map<Long, PurchaseOrderLineEntity> orderLines, Long orderLineId) {
        PurchaseOrderLineEntity entity = orderLines.get(orderLineId);
        if (entity == null) {
            throw new IllegalArgumentException("采购订单明细不存在");
        }
        return entity;
    }
}

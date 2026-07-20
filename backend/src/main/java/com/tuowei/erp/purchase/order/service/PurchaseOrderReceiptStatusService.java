package com.tuowei.erp.purchase.order.service;

import com.tuowei.erp.common.exception.BusinessConflictException;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import com.tuowei.erp.purchase.support.PurchaseReceiptQuantities;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
public class PurchaseOrderReceiptStatusService {

    private static final int MAX_ATTEMPTS = 8;

    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderLookupService purchaseOrderLookupService;

    public PurchaseOrderReceiptStatusService(
            PurchaseOrderMapper purchaseOrderMapper,
            PurchaseOrderLookupService purchaseOrderLookupService
    ) {
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.purchaseOrderLookupService = purchaseOrderLookupService;
    }

    public void refreshReceiptStatus(Long orderId, AuditMetadata audit, LocalDateTime now) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            PurchaseOrderEntity order = purchaseOrderLookupService.requireOrder(orderId);
            requireSameTenant(order, audit);
            List<PurchaseOrderLineEntity> orderLines = purchaseOrderLookupService.loadOrderLinesAsMap(order).values().stream().toList();
            order.setReceiptStatus(resolveReceiptStatus(orderLines));
            order.setUpdatedBy(audit.userId());
            order.setUpdatedTime(now);
            if (purchaseOrderMapper.updateById(order) == 1) {
                return;
            }
        }
        throw new BusinessConflictException("采购订单已被其他操作修改，请刷新后重试");
    }

    private void requireSameTenant(PurchaseOrderEntity order, AuditMetadata audit) {
        if (!Objects.equals(order.getCompanyId(), audit.companyId())
                || !Objects.equals(order.getAccountBookId(), audit.accountBookId())) {
            throw new IllegalArgumentException("采购订单不存在");
        }
    }

    private String resolveReceiptStatus(Iterable<PurchaseOrderLineEntity> orderLines) {
        boolean anyReceived = false;
        boolean allReceived = true;
        for (PurchaseOrderLineEntity orderLine : orderLines) {
            PurchaseReceiptQuantities.OrderLineQuantities quantities = PurchaseReceiptQuantities.from(
                    orderLine.getQty(),
                    orderLine.getReceivedQty()
            );
            if (quantities.hasReceived()) {
                anyReceived = true;
            }
            if (!quantities.fullyReceived()) {
                allReceived = false;
            }
        }
        if (allReceived) {
            return "RECEIVED";
        }
        if (anyReceived) {
            return "PARTIAL_RECEIVED";
        }
        return "NOT_RECEIVED";
    }
}

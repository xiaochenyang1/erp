package com.tuowei.erp.purchase;

import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderLookupService;
import com.tuowei.erp.purchase.order.service.PurchaseOrderReceiptStatusService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseOrderReceiptStatusServiceTenantBoundaryTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9701L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 8, 17, 30)
    );

    @Test
    void refreshReceiptStatusRejectsDifferentAccountBookWithinSameCompany() {
        PurchaseOrderMapper purchaseOrderMapper = mock(PurchaseOrderMapper.class);
        PurchaseOrderLookupService lookupService = mock(PurchaseOrderLookupService.class);
        PurchaseOrderReceiptStatusService service = new PurchaseOrderReceiptStatusService(
                purchaseOrderMapper,
                lookupService
        );
        when(lookupService.requireOrder(6001L)).thenReturn(order(9999L));
        when(lookupService.loadOrderLinesAsMap(any(PurchaseOrderEntity.class))).thenReturn(Map.of());
        when(purchaseOrderMapper.updateById(any(PurchaseOrderEntity.class))).thenReturn(1);

        assertThatThrownBy(() -> service.refreshReceiptStatus(6001L, AUDIT, AUDIT.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("采购订单不存在");

        verify(purchaseOrderMapper, never()).updateById(any(PurchaseOrderEntity.class));
    }

    private PurchaseOrderEntity order(Long accountBookId) {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setId(6001L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setOrderNo("PO-6001");
        entity.setDeletedFlag(0);
        entity.setVersion(0);
        return entity;
    }
}

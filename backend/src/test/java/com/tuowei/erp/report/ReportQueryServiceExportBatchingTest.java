package com.tuowei.erp.report;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.config.ReportProperties;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.report.mapper.FinanceSettlementReportMapper;
import com.tuowei.erp.report.service.ReportQueryService;
import com.tuowei.erp.report.web.OrderReportResponse;
import com.tuowei.erp.report.web.PurchaseOrderReportQuery;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportQueryServiceExportBatchingTest {

    @Test
    void streamsPurchaseOrderExportRowsInConfiguredBatches() {
        PurchaseOrderMapper purchaseOrderMapper = mock(PurchaseOrderMapper.class);
        CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
        DataScopeService dataScopeService = mock(DataScopeService.class);
        ScopedUserResolver scopedUserResolver = mock(ScopedUserResolver.class);
        ReportQueryService service = new ReportQueryService(
                purchaseOrderMapper,
                mock(SalesOrderMapper.class),
                mock(InventoryBalanceMapper.class),
                mock(InventoryTransactionMapper.class),
                mock(PayableMapper.class),
                mock(ReceivableMapper.class),
                mock(FinanceSettlementReportMapper.class),
                currentUserContext,
                dataScopeService,
                scopedUserResolver,
                mock(FinanceSettlementScopeSupport.class),
                new ReportProperties(5_000, 2)
        );
        CurrentUser currentUser = new CurrentUser(91L, 1L, 1L, 11L, 12L, "report_user", "报表用户");
        ErpPrincipal principal = new ErpPrincipal(
                currentUser.userId(),
                currentUser.companyId(),
                currentUser.accountBookId(),
                currentUser.deptId(),
                currentUser.postId(),
                currentUser.username(),
                currentUser.realName(),
                "N/A",
                Set.of(),
                DataScopeSnapshot.all()
        );
        when(currentUserContext.requireCurrentUser()).thenReturn(currentUser);
        when(currentUserContext.requirePrincipal()).thenReturn(principal);
        when(scopedUserResolver.resolve(eq(currentUser), eq(principal.dataScopeSnapshot())))
                .thenReturn(new ScopedUserResolver.ScopedUserIds(Set.of(), Set.of()));
        when(dataScopeService.applyPurchaseOrderScope(any(), eq(currentUser), eq(principal.dataScopeSnapshot()), eq(Set.of()), eq(Set.of())))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseOrderMapper.selectCount(any())).thenReturn(3L);
        when(purchaseOrderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<PurchaseOrderEntity> page = invocation.getArgument(0);
            if (page.getCurrent() == 1) {
                page.setRecords(List.of(purchaseOrder(1L, "PO-BATCH-001"), purchaseOrder(2L, "PO-BATCH-002")));
            } else if (page.getCurrent() == 2) {
                page.setRecords(List.of(purchaseOrder(3L, "PO-BATCH-003")));
            } else {
                page.setRecords(List.of());
            }
            return page;
        });

        service.assertPurchaseOrderExportWithinLimit(new PurchaseOrderReportQuery());
        List<OrderReportResponse> rows = new ArrayList<>();
        service.streamPurchaseOrders(new PurchaseOrderReportQuery(), rows::add);

        assertThat(rows).extracting(OrderReportResponse::bizNo)
                .containsExactly("PO-BATCH-001", "PO-BATCH-002", "PO-BATCH-003");
        ArgumentCaptor<Page<PurchaseOrderEntity>> pageCaptor = purchaseOrderPageCaptor();
        verify(purchaseOrderMapper, org.mockito.Mockito.times(2))
                .selectPage(pageCaptor.capture(), anyPurchaseOrderWrapper());
        assertThat(pageCaptor.getAllValues()).extracting(Page::getSize).containsExactly(2L, 2L);
        assertThat(pageCaptor.getAllValues()).extracting(Page::getCurrent).containsExactly(1L, 2L);
    }

    private PurchaseOrderEntity purchaseOrder(long id, String orderNo) {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setId(id);
        entity.setOrderNo(orderNo);
        entity.setSupplierId(101L);
        entity.setOrderDate(LocalDate.of(2026, 6, 2));
        entity.setStatus("APPROVED");
        entity.setApprovalStatus("APPROVED");
        entity.setReceiptStatus("NOT_RECEIVED");
        entity.setTotalQuantity(new BigDecimal("1.0000"));
        entity.setTotalAmount(new BigDecimal("10.00"));
        entity.setTotalTaxAmount(new BigDecimal("1.30"));
        return entity;
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<Page<PurchaseOrderEntity>> purchaseOrderPageCaptor() {
        return ArgumentCaptor.forClass(Page.class);
    }

    @SuppressWarnings("unchecked")
    private static LambdaQueryWrapper<PurchaseOrderEntity> anyPurchaseOrderWrapper() {
        return any(LambdaQueryWrapper.class);
    }
}

package com.tuowei.erp.report;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.report.service.BusinessTraceDataScopeService;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.system.log.model.OperationLogEntity;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessTraceDataScopeServiceTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9001L,
            1001L,
            2001L,
            11L,
            12L,
            "scope_user",
            "Scope User"
    );
    private static final ScopedUserResolver.ScopedUserIds SCOPED_USER_IDS =
            new ScopedUserResolver.ScopedUserIds(Set.of(21L), Set.of(31L));

    private final BusinessTraceDataScopeService service =
            new BusinessTraceDataScopeService(new DataScopeService(null, null, null, null));

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(SalesOrderEntity.class);
        initTableInfo(PurchaseOrderEntity.class);
        initTableInfo(SalesDeliveryEntity.class);
        initTableInfo(PurchaseReceiptEntity.class);
        initTableInfo(InventoryTransactionEntity.class);
        initTableInfo(ReceivableEntity.class);
        initTableInfo(PayableEntity.class);
        initTableInfo(WorkflowTaskEntity.class);
        initTableInfo(OperationLogEntity.class);
        initTableInfo(ExceptionTicketEntity.class);
    }

    @Test
    void delegatesDocumentAndInventoryScopesToCompatibilityPolicy() {
        DataScopeService dataScopeService = mock(DataScopeService.class);
        BusinessTraceDataScopeService delegatedService = new BusinessTraceDataScopeService(dataScopeService);
        DataScopeSnapshot snapshot = new DataScopeSnapshot(false, true, true, true, Set.of(801L));
        when(dataScopeService.applySalesOrderScope(any(), eq(CURRENT_USER), eq(snapshot), eq(Set.of(21L)), eq(Set.of(31L))))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(dataScopeService.applyPurchaseOrderScope(any(), eq(CURRENT_USER), eq(snapshot), eq(Set.of(21L)), eq(Set.of(31L))))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(dataScopeService.applySalesDeliveryScope(any(), eq(CURRENT_USER), eq(snapshot), eq(Set.of(21L)), eq(Set.of(31L))))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(dataScopeService.applyPurchaseReceiptScope(any(), eq(CURRENT_USER), eq(snapshot), eq(Set.of(21L)), eq(Set.of(31L))))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(dataScopeService.applyInventoryTransactionScope(any(), eq(snapshot)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        delegatedService.salesOrderScope(CURRENT_USER, snapshot, SCOPED_USER_IDS);
        delegatedService.purchaseOrderScope(CURRENT_USER, snapshot, SCOPED_USER_IDS);
        delegatedService.salesDeliveryScope(CURRENT_USER, snapshot, SCOPED_USER_IDS);
        delegatedService.purchaseReceiptScope(CURRENT_USER, snapshot, SCOPED_USER_IDS);
        delegatedService.inventoryTransactionScope(CURRENT_USER, snapshot);

        verify(dataScopeService).applySalesOrderScope(
                any(), eq(CURRENT_USER), eq(snapshot), eq(Set.of(21L)), eq(Set.of(31L)));
        verify(dataScopeService).applyPurchaseOrderScope(
                any(), eq(CURRENT_USER), eq(snapshot), eq(Set.of(21L)), eq(Set.of(31L)));
        verify(dataScopeService).applySalesDeliveryScope(
                any(), eq(CURRENT_USER), eq(snapshot), eq(Set.of(21L)), eq(Set.of(31L)));
        verify(dataScopeService).applyPurchaseReceiptScope(
                any(), eq(CURRENT_USER), eq(snapshot), eq(Set.of(21L)), eq(Set.of(31L)));
        verify(dataScopeService).applyInventoryTransactionScope(any(), eq(snapshot));
    }

    @Test
    void allScopeAllowsDirectSecondaryLookup() {
        String keyword = "HIDDEN-999";

        assertDirectLookup(service.receivableQuery(
                CURRENT_USER, DataScopeSnapshot.all(), keyword, Set.of()).orElseThrow(), "receivable_no", keyword);
        assertDirectLookup(service.payableQuery(
                CURRENT_USER, DataScopeSnapshot.all(), keyword, Set.of()).orElseThrow(), "payable_no", keyword);
        assertDirectLookup(service.workflowTaskQuery(
                CURRENT_USER, DataScopeSnapshot.all(), keyword, Set.of()).orElseThrow(), "business_no", keyword);
        assertDirectLookup(service.operationLogQuery(
                CURRENT_USER, DataScopeSnapshot.all(), keyword, Set.of()).orElseThrow(), "biz_no", keyword);
        assertDirectLookup(service.exceptionTicketQuery(
                CURRENT_USER, DataScopeSnapshot.all(), keyword, Set.of()).orElseThrow(), "ticket_no", keyword);
    }

    @Test
    void restrictedScopeFollowsVisibleSourcesWithoutUsingDirectKeyword() {
        DataScopeSnapshot snapshot = new DataScopeSnapshot(false, false, false, false, Set.of(801L));
        String keyword = "HIDDEN-999";
        Set<String> knownBizNos = Set.of("SO-001", "SD-001");

        assertVisibleSourceOnly(service.receivableQuery(
                CURRENT_USER, snapshot, keyword, knownBizNos).orElseThrow(), "source_no", keyword, knownBizNos);
        assertVisibleSourceOnly(service.payableQuery(
                CURRENT_USER, snapshot, keyword, knownBizNos).orElseThrow(), "source_no", keyword, knownBizNos);
        assertVisibleSourceOnly(service.workflowTaskQuery(
                CURRENT_USER, snapshot, keyword, knownBizNos).orElseThrow(), "business_no", keyword, knownBizNos);
        assertVisibleSourceOnly(service.operationLogQuery(
                CURRENT_USER, snapshot, keyword, knownBizNos).orElseThrow(), "biz_no", keyword, knownBizNos);
        assertVisibleSourceOnly(service.exceptionTicketQuery(
                CURRENT_USER, snapshot, keyword, knownBizNos).orElseThrow(), "source_no", keyword, knownBizNos);
    }

    @Test
    void restrictedScopeWithNoVisibleSourceSkipsEverySecondaryQuery() {
        DataScopeSnapshot snapshot = new DataScopeSnapshot(false, false, false, false, Set.of(801L));

        assertThat(service.receivableQuery(CURRENT_USER, snapshot, "HIDDEN-999", Set.of())).isEmpty();
        assertThat(service.payableQuery(CURRENT_USER, snapshot, "HIDDEN-999", Set.of())).isEmpty();
        assertThat(service.workflowTaskQuery(CURRENT_USER, snapshot, "HIDDEN-999", Set.of())).isEmpty();
        assertThat(service.operationLogQuery(CURRENT_USER, snapshot, "HIDDEN-999", Set.of())).isEmpty();
        assertThat(service.exceptionTicketQuery(CURRENT_USER, snapshot, "HIDDEN-999", Set.of())).isEmpty();
    }

    @Test
    void everyTraceSourceQueryKeepsCompanyAndAccountBookPredicates() {
        DataScopeSnapshot snapshot = DataScopeSnapshot.all();
        List<LambdaQueryWrapper<?>> wrappers = List.of(
                service.salesOrderScope(CURRENT_USER, snapshot, SCOPED_USER_IDS),
                service.purchaseOrderScope(CURRENT_USER, snapshot, SCOPED_USER_IDS),
                service.salesDeliveryScope(CURRENT_USER, snapshot, SCOPED_USER_IDS),
                service.purchaseReceiptScope(CURRENT_USER, snapshot, SCOPED_USER_IDS),
                service.inventoryTransactionScope(CURRENT_USER, snapshot),
                service.receivableQuery(CURRENT_USER, snapshot, "SO-001", Set.of()).orElseThrow(),
                service.payableQuery(CURRENT_USER, snapshot, "PO-001", Set.of()).orElseThrow(),
                service.workflowTaskQuery(CURRENT_USER, snapshot, "SO-001", Set.of()).orElseThrow(),
                service.operationLogQuery(CURRENT_USER, snapshot, "SO-001", Set.of()).orElseThrow(),
                service.exceptionTicketQuery(CURRENT_USER, snapshot, "SO-001", Set.of()).orElseThrow()
        );

        assertThat(wrappers).allSatisfy(wrapper -> assertTenantScoped(wrapper.getSqlSegment()));
    }

    private static void assertDirectLookup(LambdaQueryWrapper<?> wrapper, String directColumn, String keyword) {
        assertTenantScoped(wrapper.getSqlSegment());
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT)).contains(directColumn);
        assertThat(wrapper.getParamNameValuePairs().values()).contains("%" + keyword + "%");
    }

    private static void assertVisibleSourceOnly(
            LambdaQueryWrapper<?> wrapper,
            String sourceColumn,
            String keyword,
            Set<String> knownBizNos
    ) {
        assertTenantScoped(wrapper.getSqlSegment());
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT)).contains(sourceColumn);
        assertThat(wrapper.getParamNameValuePairs().values())
                .containsAll(knownBizNos)
                .doesNotContain(keyword);
    }

    private static void assertTenantScoped(String sqlSegment) {
        assertThat(sqlSegment.toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(), entityType.getName());
        assistant.setCurrentNamespace(entityType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}

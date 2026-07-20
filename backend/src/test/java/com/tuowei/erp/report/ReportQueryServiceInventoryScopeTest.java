package com.tuowei.erp.report;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.report.mapper.FinanceSettlementReportMapper;
import com.tuowei.erp.report.service.ReportQueryService;
import com.tuowei.erp.report.web.InventoryBalanceReportQuery;
import com.tuowei.erp.report.web.InventoryTransactionReportQuery;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportQueryServiceInventoryScopeTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9201L,
            101L,
            202L,
            11L,
            12L,
            "inventory_report_user",
            "库存报表用户"
    );
    private static final ErpPrincipal PRINCIPAL = new ErpPrincipal(
            CURRENT_USER.userId(),
            CURRENT_USER.companyId(),
            CURRENT_USER.accountBookId(),
            CURRENT_USER.deptId(),
            CURRENT_USER.postId(),
            CURRENT_USER.username(),
            CURRENT_USER.realName(),
            "N/A",
            Set.of(),
            DataScopeSnapshot.all()
    );

    private final InventoryBalanceMapper inventoryBalanceMapper = mock(InventoryBalanceMapper.class);
    private final InventoryTransactionMapper inventoryTransactionMapper = mock(InventoryTransactionMapper.class);
    private final CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
    private final DataScopeService dataScopeService = mock(DataScopeService.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryBalanceEntity.class);
        initTableInfo(InventoryTransactionEntity.class);
    }

    @Test
    void inventoryBalanceReportScopesQueryByCompanyAndAccountBook() {
        stubCurrentUser();
        when(dataScopeService.applyInventoryBalanceScope(any(), eq(PRINCIPAL.dataScopeSnapshot())))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryBalanceMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<InventoryBalanceEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            return page;
        });

        service().listInventoryBalances(new InventoryBalanceReportQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryBalanceEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryBalanceMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    @Test
    void inventoryTransactionReportScopesQueryByCompanyAndAccountBook() {
        stubCurrentUser();
        when(dataScopeService.applyInventoryTransactionScope(any(), eq(PRINCIPAL.dataScopeSnapshot())))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryTransactionMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<InventoryTransactionEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            return page;
        });

        service().listInventoryTransactions(new InventoryTransactionReportQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryTransactionEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryTransactionMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    private void stubCurrentUser() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
    }

    private ReportQueryService service() {
        return new ReportQueryService(
                mock(PurchaseOrderMapper.class),
                mock(SalesOrderMapper.class),
                inventoryBalanceMapper,
                inventoryTransactionMapper,
                mock(PayableMapper.class),
                mock(ReceivableMapper.class),
                mock(FinanceSettlementReportMapper.class),
                currentUserContext,
                dataScopeService,
                mock(ScopedUserResolver.class),
                mock(FinanceSettlementScopeSupport.class),
                new ReportProperties(5_000, 500)
        );
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}

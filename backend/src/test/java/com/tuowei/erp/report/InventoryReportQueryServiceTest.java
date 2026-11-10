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
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.report.service.InventoryReportQueryService;
import com.tuowei.erp.report.web.InventoryBalanceReportQuery;
import com.tuowei.erp.report.web.InventoryBalanceReportResponse;
import com.tuowei.erp.report.web.InventoryTransactionReportQuery;
import com.tuowei.erp.report.web.InventoryTransactionReportResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryReportQueryServiceTest {

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

    @Mock
    private InventoryBalanceMapper inventoryBalanceMapper;
    @Mock
    private InventoryTransactionMapper inventoryTransactionMapper;
    @Mock
    private CurrentUserContext currentUserContext;
    @Mock
    private DataScopeService dataScopeService;

    private InventoryReportQueryService service;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryBalanceEntity.class);
        initTableInfo(InventoryTransactionEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new InventoryReportQueryService(
                inventoryBalanceMapper,
                inventoryTransactionMapper,
                currentUserContext,
                dataScopeService,
                new ReportProperties(3, 2)
        );
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
    }

    @Test
    void listsBalancesWithTenantFiltersDataScopePaginationAndQuantityMapping() {
        InventoryBalanceReportQuery query = new InventoryBalanceReportQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setWarehouseId(301L);
        query.setProductId(401L);
        when(dataScopeService.applyInventoryBalanceScope(any(), eq(PRINCIPAL.dataScopeSnapshot())))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryBalanceMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<InventoryBalanceEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(balance()));
            return page;
        });

        var response = service.listInventoryBalances(query);

        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(200);
        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records()).singleElement().satisfies(record -> {
            assertThat(record.qtyReserved()).isEqualByComparingTo("2.0000");
            assertThat(record.qtyAvailable()).isEqualByComparingTo("8.0000");
        });
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryBalanceEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryBalanceMapper).selectPage(any(), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("company_id")
                .contains("account_book_id")
                .contains("warehouse_id")
                .contains("product_id");
        verify(dataScopeService).applyInventoryBalanceScope(any(), eq(PRINCIPAL.dataScopeSnapshot()));
    }

    @Test
    void listsTransactionsWithNormalizedFiltersAndTenantScope() {
        InventoryTransactionReportQuery query = new InventoryTransactionReportQuery();
        query.setWarehouseId(301L);
        query.setProductId(401L);
        query.setBizType(" sales_delivery ");
        query.setBizNo(" SO-001 ");
        query.setDirection(" out ");
        query.setOccurredTimeFrom(LocalDateTime.of(2026, 8, 1, 0, 0));
        query.setOccurredTimeTo(LocalDateTime.of(2026, 8, 31, 23, 59));
        when(dataScopeService.applyInventoryTransactionScope(any(), eq(PRINCIPAL.dataScopeSnapshot())))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryTransactionMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<InventoryTransactionEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(transaction()));
            page.setTotal(1);
            return page;
        });

        var response = service.listInventoryTransactions(query);

        assertThat(response.records()).extracting(InventoryTransactionReportResponse::bizNo)
                .containsExactly("SO-001");
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryTransactionEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryTransactionMapper).selectPage(any(), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("company_id")
                .contains("account_book_id")
                .contains("warehouse_id")
                .contains("product_id")
                .contains("biz_type")
                .contains("biz_no")
                .contains("direction")
                .contains("occurred_time");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains("SALES_DELIVERY", "%SO-001%", "OUT");
        verify(dataScopeService).applyInventoryTransactionScope(any(), eq(PRINCIPAL.dataScopeSnapshot()));
    }

    @Test
    void enforcesExportLimitBeforeStreaming() {
        when(dataScopeService.applyInventoryBalanceScope(any(), eq(PRINCIPAL.dataScopeSnapshot())))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryBalanceMapper.selectCount(any())).thenReturn(4L);

        assertThatThrownBy(() -> service.assertInventoryBalanceExportWithinLimit(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("导出结果超过3行，请缩小筛选范围后重试");
        verify(inventoryBalanceMapper, never()).selectPage(any(), any());
    }

    @Test
    void streamsTransactionExportRowsInConfiguredBatches() {
        when(dataScopeService.applyInventoryTransactionScope(any(), eq(PRINCIPAL.dataScopeSnapshot())))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryTransactionMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<InventoryTransactionEntity> page = invocation.getArgument(0);
            if (page.getCurrent() == 1) {
                page.setRecords(List.of(transaction(1L, "SO-001"), transaction(2L, "SO-002")));
            } else {
                page.setRecords(List.of(transaction(3L, "SO-003")));
            }
            return page;
        });

        List<InventoryTransactionReportResponse> records = new ArrayList<>();
        service.streamInventoryTransactions(null, records::add);

        assertThat(records).extracting(InventoryTransactionReportResponse::bizNo)
                .containsExactly("SO-001", "SO-002", "SO-003");
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Page<InventoryTransactionEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(inventoryTransactionMapper, org.mockito.Mockito.times(2)).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getAllValues()).extracting(Page::getCurrent).containsExactly(1L, 2L);
        assertThat(pageCaptor.getAllValues()).extracting(Page::getSize).containsExactly(2L, 2L);
    }

    private InventoryBalanceEntity balance() {
        InventoryBalanceEntity entity = new InventoryBalanceEntity();
        entity.setId(1L);
        entity.setWarehouseId(301L);
        entity.setProductId(401L);
        entity.setQtyOnHand(new BigDecimal("10.0000"));
        entity.setQtyReserved(new BigDecimal("2.0000"));
        entity.setAmountOnHand(new BigDecimal("50.00"));
        entity.setUpdatedTime(LocalDateTime.of(2026, 8, 13, 12, 0));
        return entity;
    }

    private InventoryTransactionEntity transaction() {
        return transaction(1L, "SO-001");
    }

    private InventoryTransactionEntity transaction(long id, String bizNo) {
        InventoryTransactionEntity entity = new InventoryTransactionEntity();
        entity.setId(id);
        entity.setWarehouseId(301L);
        entity.setProductId(401L);
        entity.setBizType("SALES_DELIVERY");
        entity.setBizNo(bizNo);
        entity.setBizLineId(501L);
        entity.setDirection("OUT");
        entity.setQty(new BigDecimal("1.0000"));
        entity.setAmount(new BigDecimal("5.00"));
        entity.setUnitCost(new BigDecimal("5.00"));
        entity.setOccurredTime(LocalDateTime.of(2026, 8, 13, 12, 0));
        entity.setRemark("test");
        return entity;
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

package com.tuowei.erp.inventory.stock;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.stock.service.InventoryTransactionQueryService;
import com.tuowei.erp.inventory.stock.web.InventoryTransactionPageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryTransactionResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryTransactionQueryServiceTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9401L, 101L, 202L, 11L, 12L, "txn_query_user", "流水查询用户"
    );
    private static final ErpPrincipal PRINCIPAL = new ErpPrincipal(
            CURRENT_USER.userId(), CURRENT_USER.companyId(), CURRENT_USER.accountBookId(),
            CURRENT_USER.deptId(), CURRENT_USER.postId(), CURRENT_USER.username(),
            CURRENT_USER.realName(), "N/A", Set.of(), DataScopeSnapshot.all()
    );

    @Mock
    private InventoryTransactionMapper mapper;

    @Mock
    private CurrentUserContext currentUserContext;

    @Mock
    private DataScopeService dataScopeService;

    @BeforeAll
    static void initTableInfo() {
        if (TableInfoHelper.getTableInfo(InventoryTransactionEntity.class) == null) {
            MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                    new MybatisConfiguration(), InventoryTransactionEntity.class.getName()
            );
            assistant.setCurrentNamespace(InventoryTransactionEntity.class.getName());
            TableInfoHelper.initTableInfo(assistant, InventoryTransactionEntity.class);
        }
    }

    @Test
    void listNormalizesFiltersAndKeepsTenantAndScopePredicates() {
        stubPrincipal();
        when(dataScopeService.applyInventoryTransactionScope(any(), eq(PRINCIPAL.dataScopeSnapshot())))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(mapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<InventoryTransactionEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(transaction()));
            return page;
        });

        InventoryTransactionPageQuery query = new InventoryTransactionPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setWarehouseId(3001L);
        query.setProductId(4001L);
        query.setBizType(" sales_delivery ");
        query.setBizNo(" SD-7001 ");
        query.setDirection(" out ");
        query.setOccurredTimeFrom(LocalDateTime.of(2026, 6, 1, 0, 0));
        query.setOccurredTimeTo(LocalDateTime.of(2026, 6, 30, 23, 59));

        var response = service().listTransactions(query);

        assertThat(response.records()).singleElement().extracting(InventoryTransactionResponse::bizType)
                .isEqualTo("SALES_DELIVERY");
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Page<InventoryTransactionEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryTransactionEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200);
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
    }

    @Test
    void detailRejectsCrossTenantRecordBeforeDataScopeCheck() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(mapper.selectById(7001L)).thenReturn(transactionWithAccountBook(9999L));

        assertThatThrownBy(() -> service().getTransactionById(7001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存流水不存在");
    }

    @Test
    void detailAppliesDataScopeToTenantMatchingRecord() {
        stubPrincipal();
        when(mapper.selectById(7001L)).thenReturn(transaction());

        InventoryTransactionResponse response = service().getTransactionById(7001L);

        assertThat(response.id()).isEqualTo(7001L);
        verify(dataScopeService).assertCanViewInventoryTransaction(
                any(InventoryTransactionEntity.class), eq(PRINCIPAL.dataScopeSnapshot())
        );
    }

    private void stubPrincipal() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
    }

    private InventoryTransactionQueryService service() {
        return new InventoryTransactionQueryService(mapper, currentUserContext, dataScopeService);
    }

    private InventoryTransactionEntity transaction() {
        return transactionWithAccountBook(CURRENT_USER.accountBookId());
    }

    private InventoryTransactionEntity transactionWithAccountBook(Long accountBookId) {
        InventoryTransactionEntity entity = new InventoryTransactionEntity();
        entity.setId(7001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setWarehouseId(3001L);
        entity.setLocationId(3002L);
        entity.setProductId(4001L);
        entity.setBizType("SALES_DELIVERY");
        entity.setBizNo("SD-7001");
        entity.setBizLineId(8001L);
        entity.setDirection("OUT");
        entity.setQty(new BigDecimal("3.0000"));
        entity.setAmount(new BigDecimal("30.00"));
        entity.setUnitCost(new BigDecimal("10.000000"));
        entity.setOccurredTime(LocalDateTime.of(2026, 6, 8, 14, 45));
        entity.setRemark("test transaction");
        return entity;
    }
}

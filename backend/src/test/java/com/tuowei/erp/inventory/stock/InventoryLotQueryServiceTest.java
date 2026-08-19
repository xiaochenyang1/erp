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
import com.tuowei.erp.inventory.stock.mapper.InventoryLotBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryLotBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.stock.service.InventoryLotQueryService;
import com.tuowei.erp.inventory.stock.service.InventoryDocumentLinkResolver;
import com.tuowei.erp.inventory.stock.web.InventoryLotBalancePageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotExpiryAlertQuery;
import com.tuowei.erp.inventory.stock.web.InventoryLotTraceQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryLotQueryServiceTest {

    private static final CurrentUser USER = new CurrentUser(
            9401L,
            101L,
            202L,
            11L,
            12L,
            "lot_query_user",
            "批次查询用户"
    );
    private static final DataScopeSnapshot SNAPSHOT = DataScopeSnapshot.all();
    private static final ErpPrincipal PRINCIPAL = new ErpPrincipal(
            USER.userId(),
            USER.companyId(),
            USER.accountBookId(),
            USER.deptId(),
            USER.postId(),
            USER.username(),
            USER.realName(),
            "N/A",
            Set.of(),
            SNAPSHOT
    );

    @Mock
    private InventoryLotBalanceMapper lotBalanceMapper;

    @Mock
    private InventoryTransactionMapper transactionMapper;

    @Mock
    private CurrentUserContext currentUserContext;

    @Mock
    private DataScopeService dataScopeService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryLotBalanceEntity.class);
        initTableInfo(InventoryTransactionEntity.class);
    }

    @Test
    void listLotBalancesNormalizesFiltersScopesTenantAndMapsQuantities() {
        stubCurrentUser();
        when(dataScopeService.applyInventoryLotBalanceScope(any(), eq(SNAPSHOT)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lotBalanceMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<InventoryLotBalanceEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(lotBalance(USER.accountBookId())));
            return page;
        });
        InventoryLotBalancePageQuery query = new InventoryLotBalancePageQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setWarehouseId(3001L);
        query.setProductId(4001L);
        query.setLotNo(" LOT_% ");
        query.setExpiryDateFrom(LocalDate.of(2026, 6, 1));
        query.setExpiryDateTo(LocalDate.of(2026, 12, 31));
        query.setExpiringWithinDays(7);

        var response = service().listLotBalances(query);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Page<InventoryLotBalanceEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryLotBalanceEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(lotBalanceMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200);
        assertTenantScoped(wrapperCaptor.getValue());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("warehouse_id")
                .contains("product_id")
                .contains("lot_no")
                .contains("expiry_date");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .anyMatch(value -> String.valueOf(value).contains("LOT\\_\\%"));
        assertThat(response.records()).singleElement().satisfies(record -> {
            assertThat(record.qtyOnHand()).isEqualByComparingTo("10.0000");
            assertThat(record.qtyReserved()).isEqualByComparingTo("2.0000");
            assertThat(record.qtyAvailable()).isEqualByComparingTo("8.0000");
        });
    }

    @Test
    void getLotBalanceRejectsOtherAccountBookWithinSameCompany() {
        when(currentUserContext.requireCurrentUser()).thenReturn(USER);
        when(lotBalanceMapper.selectById(6001L)).thenReturn(lotBalance(9999L));

        assertThatThrownBy(() -> service().getLotBalanceById(6001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("批次库存余额不存在");
    }

    @Test
    void traceLotValidatesRequiredFieldsBeforeQuerying() {
        assertThatThrownBy(() -> service().traceLot(new InventoryLotTraceQuery()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("批次追溯必须指定商品");

        InventoryLotTraceQuery blankLot = new InventoryLotTraceQuery();
        blankLot.setProductId(4001L);
        blankLot.setLotNo("  ");
        assertThatThrownBy(() -> service().traceLot(blankLot))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("批次追溯必须指定批次号");
        verifyNoInteractions(lotBalanceMapper, transactionMapper, currentUserContext, dataScopeService);
    }

    @Test
    void traceLotScopesFiltersAndMapsDocumentLinkWithCalculatedUnitCost() {
        stubCurrentUser();
        when(dataScopeService.applyInventoryTransactionScope(any(), eq(SNAPSHOT)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<InventoryTransactionEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(transaction()));
            return page;
        });
        InventoryLotTraceQuery query = new InventoryLotTraceQuery();
        query.setProductId(4001L);
        query.setLotNo(" LOT-A ");
        query.setWarehouseId(3001L);
        query.setDirection(" out ");
        query.setOccurredTimeFrom(LocalDateTime.of(2026, 6, 1, 0, 0));
        query.setOccurredTimeTo(LocalDateTime.of(2026, 6, 30, 23, 59));

        var response = service().traceLot(query);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryTransactionEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(transactionMapper).selectPage(any(), wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains("LOT-A", "OUT");
        assertThat(response.records()).singleElement().satisfies(record -> {
            assertThat(record.qty()).isEqualByComparingTo("2.0000");
            assertThat(record.amount()).isEqualByComparingTo("30.00");
            assertThat(record.unitCost()).isEqualByComparingTo("15.000000");
            assertThat(record.documentRoute()).isEqualTo("/sales/deliveries?keyword=SD+%2F001");
            assertThat(record.documentLabel()).isEqualTo("销售发货");
        });
    }

    @Test
    void expiryAlertsClampWarningDaysNormalizeStatusAndMapExpirySummary() {
        stubCurrentUser();
        when(dataScopeService.applyInventoryLotBalanceScope(any(), eq(SNAPSHOT)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        InventoryLotBalanceEntity entity = lotBalance(USER.accountBookId());
        entity.setExpiryDate(LocalDate.of(2026, 7, 1));
        entity.setQtyOnHand(null);
        entity.setQtyReserved(null);
        entity.setAmountOnHand(null);
        when(lotBalanceMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<InventoryLotBalanceEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(entity));
            return page;
        });
        InventoryLotExpiryAlertQuery query = new InventoryLotExpiryAlertQuery();
        query.setWarningDays(999);
        query.setStatus(" expiring ");
        query.setLotNo(" LOT-A ");

        var response = service().listLotExpiryAlerts(query);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryLotBalanceEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(lotBalanceMapper).selectPage(any(), wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("qty_on_hand - qty_reserved > 0")
                .contains("expiry_date");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(LocalDate.of(2026, 6, 29), LocalDate.of(2027, 6, 29));
        assertThat(response.records()).singleElement().satisfies(record -> {
            assertThat(record.qtyOnHand()).isEqualByComparingTo("0.0000");
            assertThat(record.qtyReserved()).isEqualByComparingTo("0.0000");
            assertThat(record.qtyAvailable()).isEqualByComparingTo("0.0000");
            assertThat(record.amountOnHand()).isEqualByComparingTo("0.00");
            assertThat(record.expiryStatus()).isEqualTo("EXPIRING");
            assertThat(record.daysToExpiry()).isEqualTo(2L);
        });
    }

    private InventoryLotQueryService service() {
        return new InventoryLotQueryService(
                lotBalanceMapper,
                transactionMapper,
                currentUserContext,
                dataScopeService,
                new InventoryDocumentLinkResolver(),
                Clock.fixed(Instant.parse("2026-06-29T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private void stubCurrentUser() {
        when(currentUserContext.requireCurrentUser()).thenReturn(USER);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
    }

    private InventoryLotBalanceEntity lotBalance(Long accountBookId) {
        InventoryLotBalanceEntity entity = new InventoryLotBalanceEntity();
        entity.setId(6001L);
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setWarehouseId(3001L);
        entity.setLocationId(3002L);
        entity.setProductId(4001L);
        entity.setLotNo("LOT-A");
        entity.setProductionDate(LocalDate.of(2026, 1, 1));
        entity.setExpiryDate(LocalDate.of(2026, 7, 31));
        entity.setFirstInboundTime(LocalDateTime.of(2026, 1, 2, 8, 0));
        entity.setQtyOnHand(new BigDecimal("10.0000"));
        entity.setQtyReserved(new BigDecimal("2.0000"));
        entity.setAmountOnHand(new BigDecimal("100.00"));
        entity.setUpdatedTime(LocalDateTime.of(2026, 6, 28, 9, 0));
        return entity;
    }

    private InventoryTransactionEntity transaction() {
        InventoryTransactionEntity entity = new InventoryTransactionEntity();
        entity.setId(7001L);
        entity.setCompanyId(USER.companyId());
        entity.setAccountBookId(USER.accountBookId());
        entity.setWarehouseId(3001L);
        entity.setLocationId(3002L);
        entity.setProductId(4001L);
        entity.setLotNo("LOT-A");
        entity.setProductionDate(LocalDate.of(2026, 1, 1));
        entity.setExpiryDate(LocalDate.of(2026, 12, 31));
        entity.setBizType("SALES_DELIVERY");
        entity.setBizNo("SD /001");
        entity.setBizLineId(8001L);
        entity.setDirection("OUT");
        entity.setQty(new BigDecimal("2.0000"));
        entity.setAmount(new BigDecimal("30.00"));
        entity.setOccurredTime(LocalDateTime.of(2026, 6, 20, 9, 0));
        entity.setRemark("批次出库");
        return entity;
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
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

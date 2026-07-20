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
import com.tuowei.erp.inventory.stock.mapper.InventoryBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryLotBalanceMapper;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryBalanceEntity;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.stock.service.InventoryStockQueryService;
import com.tuowei.erp.inventory.stock.web.InventoryBalancePageQuery;
import com.tuowei.erp.inventory.stock.web.InventoryTransactionPageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
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
class InventoryStockQueryServiceTenantBoundaryTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9401L,
            101L,
            202L,
            11L,
            12L,
            "stock_scope_user",
            "库存查询用户"
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
    private InventoryLotBalanceMapper inventoryLotBalanceMapper;

    @Mock
    private InventoryTransactionMapper inventoryTransactionMapper;

    @Mock
    private CurrentUserContext currentUserContext;

    @Mock
    private DataScopeService dataScopeService;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryBalanceEntity.class);
        initTableInfo(InventoryTransactionEntity.class);
    }

    @Test
    void listBalancesScopesQueryByCompanyAndAccountBook() {
        stubCurrentUser();
        when(dataScopeService.applyInventoryBalanceScope(any(), eq(PRINCIPAL.dataScopeSnapshot())))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryBalanceMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<InventoryBalanceEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            return page;
        });

        service().listBalances(new InventoryBalancePageQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryBalanceEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryBalanceMapper).selectPage(any(), wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void exportBalancesScopesQueryAndCapsPageSize() throws Exception {
        stubCurrentUser();
        when(dataScopeService.applyInventoryBalanceScope(any(), eq(PRINCIPAL.dataScopeSnapshot())))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryBalanceMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<InventoryBalanceEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(balance(CURRENT_USER.accountBookId())));
            return page;
        });

        InventoryBalancePageQuery query = new InventoryBalancePageQuery();
        query.setPageNo(9);
        query.setPageSize(1);
        query.setWarehouseId(3001L);
        query.setProductId(4001L);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        service().exportBalances(query).writeTo(outputStream);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Page<InventoryBalanceEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryBalanceEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryBalanceMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(5000);
        assertTenantScoped(wrapperCaptor.getValue());

        String csv = outputStream.toString(StandardCharsets.UTF_8);
        assertThat(csv)
                .startsWith("\uFEFFwarehouseId,productId,qtyOnHand,qtyReserved,qtyAvailable,amountOnHand,updatedTime\r\n")
                .contains("3001,4001,10.0000,2.0000,8.0000,100.00,2026-06-08T14:45\r\n");
    }

    @Test
    void getBalanceByIdRejectsDifferentAccountBookWithinSameCompany() {
        stubCurrentUserForDetailCheck();
        when(inventoryBalanceMapper.selectById(6001L)).thenReturn(balance(9999L));

        assertThatThrownBy(() -> service().getBalanceById(6001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存余额不存在");
    }

    @Test
    void listTransactionsScopesQueryByCompanyAndAccountBook() {
        stubCurrentUser();
        when(dataScopeService.applyInventoryTransactionScope(any(), eq(PRINCIPAL.dataScopeSnapshot())))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryTransactionMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<InventoryTransactionEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            return page;
        });

        service().listTransactions(new InventoryTransactionPageQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryTransactionEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryTransactionMapper).selectPage(any(), wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    @Test
    void getTransactionByIdRejectsDifferentAccountBookWithinSameCompany() {
        stubCurrentUserForDetailCheck();
        when(inventoryTransactionMapper.selectById(7001L)).thenReturn(transaction(9999L));

        assertThatThrownBy(() -> service().getTransactionById(7001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("库存流水不存在");
    }

    private void stubCurrentUser() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
    }

    private void stubCurrentUserForDetailCheck() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        lenient().when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    private InventoryBalanceEntity balance(Long accountBookId) {
        InventoryBalanceEntity entity = new InventoryBalanceEntity();
        entity.setId(6001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setWarehouseId(3001L);
        entity.setProductId(4001L);
        entity.setQtyOnHand(new BigDecimal("10.0000"));
        entity.setQtyReserved(new BigDecimal("2.0000"));
        entity.setAmountOnHand(new BigDecimal("100.00"));
        entity.setUpdatedTime(LocalDateTime.of(2026, 6, 8, 14, 45));
        return entity;
    }

    private InventoryTransactionEntity transaction(Long accountBookId) {
        InventoryTransactionEntity entity = new InventoryTransactionEntity();
        entity.setId(7001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setWarehouseId(3001L);
        entity.setProductId(4001L);
        entity.setBizType("SALES_DELIVERY");
        entity.setBizNo("SD-7001");
        entity.setBizLineId(8001L);
        entity.setDirection("OUT");
        entity.setQty(new BigDecimal("3.0000"));
        entity.setAmount(new BigDecimal("30.00"));
        entity.setUnitCost(new BigDecimal("10.000000"));
        entity.setOccurredTime(LocalDateTime.of(2026, 6, 8, 14, 45));
        return entity;
    }

    private InventoryStockQueryService service() {
        return new InventoryStockQueryService(
                inventoryBalanceMapper,
                inventoryLotBalanceMapper,
                inventoryTransactionMapper,
                currentUserContext,
                dataScopeService,
                Clock.systemUTC()
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

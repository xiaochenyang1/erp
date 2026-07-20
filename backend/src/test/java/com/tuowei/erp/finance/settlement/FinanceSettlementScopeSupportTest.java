package com.tuowei.erp.finance.settlement;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.finance.settlement.service.FinanceSettlementScopeSupport;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnMapper;
import com.tuowei.erp.testsupport.TestSecurityContexts;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FinanceSettlementScopeSupportTest {

    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private ScopedUserResolver scopedUserResolver;
    @Mock
    private PurchaseReceiptMapper purchaseReceiptMapper;
    @Mock
    private PurchaseReturnMapper purchaseReturnMapper;
    @Mock
    private SalesDeliveryMapper salesDeliveryMapper;
    @Mock
    private SalesReturnMapper salesReturnMapper;

    private FinanceSettlementScopeSupport scopeSupport;

    @BeforeEach
    void setUp() {
        initTableInfo(PayableEntity.class);
        initTableInfo(ReceivableEntity.class);
        scopeSupport = new FinanceSettlementScopeSupport(
                new CurrentUserContext(),
                dataScopeService,
                scopedUserResolver,
                purchaseReceiptMapper,
                purchaseReturnMapper,
                salesDeliveryMapper,
                salesReturnMapper
        );
        lenient().when(scopedUserResolver.resolve(any(), any()))
                .thenReturn(new ScopedUserResolver.ScopedUserIds(Set.of(), Set.of()));
        lenient().when(dataScopeService.applySalesDeliveryScope(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(dataScopeService.applySalesReturnScope(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(dataScopeService.applyPurchaseReceiptScope(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(dataScopeService.applyPurchaseReturnScope(any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(salesDeliveryMapper.selectList(any())).thenReturn(List.of());
        lenient().when(salesReturnMapper.selectList(any())).thenReturn(List.of());
        lenient().when(purchaseReceiptMapper.selectList(any())).thenReturn(List.of());
        lenient().when(purchaseReturnMapper.selectList(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void receivableScopeUsesExistsInsteadOfLoadingVisibleSourceIds() {
        useScopedUser();

        LambdaQueryWrapper<ReceivableEntity> wrapper =
                scopeSupport.applyReceivableScope(new LambdaQueryWrapper<>(ReceivableEntity.class));

        assertThat(normalizedSql(wrapper))
                .contains("exists")
                .contains("account_book_id")
                .contains("sal_delivery")
                .contains("sal_return");
        verify(salesDeliveryMapper, never()).selectList(any());
        verify(salesReturnMapper, never()).selectList(any());
    }

    @Test
    void payableScopeUsesExistsInsteadOfLoadingVisibleSourceIds() {
        useScopedUser();

        LambdaQueryWrapper<PayableEntity> wrapper =
                scopeSupport.applyPayableScope(new LambdaQueryWrapper<>(PayableEntity.class));

        assertThat(normalizedSql(wrapper))
                .contains("exists")
                .contains("account_book_id")
                .contains("pur_receipt")
                .contains("pur_return");
        verify(purchaseReceiptMapper, never()).selectList(any());
        verify(purchaseReturnMapper, never()).selectList(any());
    }

    @Test
    void allScopeReceivableDetailStillRejectsDifferentAccountBook() {
        useAllScopeUser();
        ReceivableEntity receivable = receivable(2L);

        assertThatThrownBy(() -> scopeSupport.assertCanViewReceivable(receivable))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("无权访问该应收记录");
    }

    @Test
    void allScopePayableDetailStillRejectsDifferentAccountBook() {
        useAllScopeUser();
        PayableEntity payable = payable(2L);

        assertThatThrownBy(() -> scopeSupport.assertCanViewPayable(payable))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("无权访问该应付记录");
    }

    private void useScopedUser() {
        TestSecurityContexts.useUser(
                91001L,
                1L,
                1L,
                11L,
                12L,
                "scoped",
                "scoped",
                Set.of("report:view"),
                new DataScopeSnapshot(false, false, false, true, Set.of(92001L))
        );
    }

    private void useAllScopeUser() {
        TestSecurityContexts.useUser(
                91001L,
                1L,
                1L,
                11L,
                12L,
                "all-scope",
                "all-scope",
                Set.of("report:view"),
                DataScopeSnapshot.all()
        );
    }

    private ReceivableEntity receivable(Long accountBookId) {
        ReceivableEntity entity = new ReceivableEntity();
        entity.setId(93001L);
        entity.setCompanyId(1L);
        entity.setAccountBookId(accountBookId);
        entity.setCustomerId(94001L);
        entity.setSourceType("MANUAL");
        entity.setDirection("INCREASE");
        entity.setOriginalAmount(BigDecimal.TEN);
        entity.setSettledAmount(BigDecimal.ZERO);
        entity.setStatus("UNSETTLED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PayableEntity payable(Long accountBookId) {
        PayableEntity entity = new PayableEntity();
        entity.setId(95001L);
        entity.setCompanyId(1L);
        entity.setAccountBookId(accountBookId);
        entity.setSupplierId(96001L);
        entity.setSourceType("MANUAL");
        entity.setDirection("INCREASE");
        entity.setOriginalAmount(BigDecimal.TEN);
        entity.setSettledAmount(BigDecimal.ZERO);
        entity.setStatus("UNSETTLED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private String normalizedSql(LambdaQueryWrapper<?> wrapper) {
        return wrapper.getSqlSegment().toLowerCase(Locale.ROOT);
    }

    private void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityClass.getName());
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}

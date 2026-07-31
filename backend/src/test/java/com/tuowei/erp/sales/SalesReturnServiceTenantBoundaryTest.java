package com.tuowei.erp.sales;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.finance.period.service.AccountPeriodGuard;
import com.tuowei.erp.finance.posting.FinancePostingService;
import com.tuowei.erp.inventory.stock.mapper.InventoryTransactionMapper;
import com.tuowei.erp.inventory.stock.model.InventoryTransactionEntity;
import com.tuowei.erp.inventory.stock.service.InventoryPostingCommand;
import com.tuowei.erp.inventory.serial.service.InventorySerialNumberService;
import com.tuowei.erp.inventory.stock.service.InventoryPostingService;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryMapper;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryEntity;
import com.tuowei.erp.sales.delivery.model.SalesDeliveryLineEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnLineMapper;
import com.tuowei.erp.sales.returnorder.mapper.SalesReturnMapper;
import com.tuowei.erp.sales.returnorder.model.SalesReturnEntity;
import com.tuowei.erp.sales.returnorder.model.SalesReturnLineEntity;
import com.tuowei.erp.sales.returnorder.service.SalesReturnNumberService;
import com.tuowei.erp.sales.returnorder.service.SalesReturnQueryService;
import com.tuowei.erp.sales.returnorder.service.SalesReturnService;
import com.tuowei.erp.sales.returnorder.web.SalesReturnCreateRequest;
import com.tuowei.erp.sales.returnorder.web.SalesReturnLineRequest;
import com.tuowei.erp.system.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalesReturnServiceTenantBoundaryTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9601L,
            101L,
            202L,
            11L,
            12L,
            "sales_return_scope_user",
            "销售退货用户"
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
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 8, 15, 30);

    @Mock
    private SalesReturnMapper salesReturnMapper;

    @Mock
    private SalesReturnLineMapper salesReturnLineMapper;

    @Mock
    private SalesDeliveryMapper salesDeliveryMapper;

    @Mock
    private SalesDeliveryLineMapper salesDeliveryLineMapper;

    @Mock
    private SalesOrderMapper salesOrderMapper;

    @Mock
    private SalesOrderLineMapper salesOrderLineMapper;

    @Mock
    private ProductValidator productValidator;

    @Mock
    private InventoryTransactionMapper inventoryTransactionMapper;

    @Mock
    private InventoryPostingService inventoryPostingService;

    @Mock
    private InventorySerialNumberService inventorySerialNumberService;

    @Mock
    private SalesReturnNumberService salesReturnNumberService;

    @Mock
    private FinancePostingService financePostingService;

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private CurrentUserContext currentUserContext;

    @Mock
    private DataScopeService dataScopeService;

    @Mock
    private ScopedUserResolver scopedUserResolver;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AccountPeriodGuard accountPeriodGuard;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(InventoryTransactionEntity.class);
        initTableInfo(SalesReturnLineEntity.class);
        initTableInfo(SalesDeliveryLineEntity.class);
        initTableInfo(SalesOrderLineEntity.class);
    }

    @Test
    void postScopesDeliveryLotTransactionLookupByCompanyAndAccountBook() {
        stubCurrentUser();
        when(auditMetadataFactory.current()).thenReturn(new AuditMetadata(
                CURRENT_USER.userId(),
                CURRENT_USER.companyId(),
                CURRENT_USER.accountBookId(),
                NOW
        ));
        when(salesReturnMapper.selectById(9001L)).thenReturn(salesReturn());
        when(salesDeliveryMapper.selectById(7001L)).thenReturn(delivery());
        when(salesOrderMapper.selectById(6001L)).thenReturn(order());
        when(salesReturnLineMapper.selectList(any())).thenReturn(List.of(returnLine()));
        when(salesDeliveryLineMapper.selectList(any())).thenReturn(List.of(deliveryLine()));
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine()));
        when(inventoryTransactionMapper.selectList(any())).thenReturn(List.of(deliveryTxn(null, "2.0000", "20.00")));
        when(salesReturnMapper.updateById(any(SalesReturnEntity.class))).thenReturn(1);
        when(salesDeliveryLineMapper.updateById(any(SalesDeliveryLineEntity.class))).thenReturn(1);
        when(salesOrderLineMapper.updateById(any(SalesOrderLineEntity.class))).thenReturn(1);
        when(salesOrderMapper.updateById(any(SalesOrderEntity.class))).thenReturn(1);

        service().post(9001L);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesReturnLineEntity>> returnLineWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesReturnLineMapper, atLeastOnce()).selectList(returnLineWrapperCaptor.capture());
        assertThat(returnLineWrapperCaptor.getAllValues())
                .allSatisfy(wrapper -> assertTenantScoped(wrapper));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesDeliveryLineEntity>> deliveryLineWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesDeliveryLineMapper).selectList(deliveryLineWrapperCaptor.capture());
        assertTenantScoped(deliveryLineWrapperCaptor.getValue());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesOrderLineEntity>> orderLineWrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesOrderLineMapper, atLeastOnce()).selectList(orderLineWrapperCaptor.capture());
        assertThat(orderLineWrapperCaptor.getAllValues())
                .allSatisfy(wrapper -> assertTenantScoped(wrapper));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<InventoryTransactionEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(inventoryTransactionMapper, atLeastOnce()).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getAllValues())
                .allSatisfy(this::assertTenantScoped);
    }

    @Test
    void postUsesOriginalDeliveryInventoryCostInsteadOfSalesAmount() {
        stubCurrentUser();
        AuditMetadata audit = new AuditMetadata(
                CURRENT_USER.userId(),
                CURRENT_USER.companyId(),
                CURRENT_USER.accountBookId(),
                NOW
        );
        SalesReturnEntity salesReturn = salesReturn();
        SalesDeliveryEntity delivery = delivery();
        SalesOrderEntity order = order();
        SalesReturnLineEntity returnLine = returnLine();
        SalesDeliveryLineEntity deliveryLine = deliveryLine();
        SalesOrderLineEntity orderLine = orderLine();
        InventoryTransactionEntity deliveryTxn = deliveryTxn("LOT-A", "1.0000", "10.00");

        returnLine.setQty(new BigDecimal("1.0000"));
        returnLine.setAmount(new BigDecimal("20.00"));
        returnLine.setLotNo("LOT-A");
        deliveryLine.setQty(new BigDecimal("1.0000"));
        deliveryLine.setPrice(new BigDecimal("20.00"));
        deliveryLine.setAmount(new BigDecimal("20.00"));
        orderLine.setQty(new BigDecimal("1.0000"));
        orderLine.setDeliveredQty(new BigDecimal("1.0000"));

        when(auditMetadataFactory.current()).thenReturn(audit);
        when(salesReturnMapper.selectById(9001L)).thenReturn(salesReturn);
        when(salesDeliveryMapper.selectById(7001L)).thenReturn(delivery);
        when(salesOrderMapper.selectById(6001L)).thenReturn(order);
        when(salesReturnLineMapper.selectList(any())).thenReturn(List.of(returnLine));
        when(salesDeliveryLineMapper.selectList(any())).thenReturn(List.of(deliveryLine));
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine));
        when(inventoryTransactionMapper.selectList(any())).thenReturn(List.of(deliveryTxn));
        when(salesReturnMapper.updateById(any(SalesReturnEntity.class))).thenReturn(1);
        when(salesDeliveryLineMapper.updateById(any(SalesDeliveryLineEntity.class))).thenReturn(1);
        when(salesOrderLineMapper.updateById(any(SalesOrderLineEntity.class))).thenReturn(1);
        when(salesOrderMapper.updateById(any(SalesOrderEntity.class))).thenReturn(1);

        service().post(9001L);

        ArgumentCaptor<InventoryPostingCommand> postingCaptor = ArgumentCaptor.forClass(InventoryPostingCommand.class);
        verify(inventoryPostingService).postInbound(postingCaptor.capture(), any(AuditMetadata.class));
        assertThat(postingCaptor.getValue().amount()).isEqualByComparingTo("10.00");

        ArgumentCaptor<BigDecimal> costAmountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(financePostingService).recordSalesReturn(
                any(SalesReturnEntity.class),
                any(SalesOrderEntity.class),
                costAmountCaptor.capture(),
                any(AuditMetadata.class)
        );
        assertThat(costAmountCaptor.getValue()).isEqualByComparingTo("10.00");
    }

    private void stubCurrentUser() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
    }

    private void assertTenantScoped(LambdaQueryWrapper<?> wrapper) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id");
    }

    @Test
    void createRejectsDeliveryLineProductFromDifferentAccountBookWithinSameCompany() {
        stubCurrentUser();
        when(auditMetadataFactory.current()).thenReturn(new AuditMetadata(
                CURRENT_USER.userId(),
                CURRENT_USER.companyId(),
                CURRENT_USER.accountBookId(),
                NOW
        ));
        when(salesDeliveryMapper.selectById(7001L)).thenReturn(delivery());
        when(salesDeliveryLineMapper.selectList(any())).thenReturn(List.of(deliveryLine()));
        when(productValidator.requireProducts(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("商品不存在或已停用"));

        assertThatThrownBy(() -> service().create(new SalesReturnCreateRequest(
                7001L,
                LocalDate.of(2026, 6, 8),
                "tenant boundary",
                List.of(new SalesReturnLineRequest(
                        7101L,
                        new BigDecimal("2.0000"),
                        "line"
                ))
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("商品不存在或已停用");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesDeliveryLineEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesDeliveryLineMapper).selectList(wrapperCaptor.capture());
        assertTenantScoped(wrapperCaptor.getValue());
    }

    private SalesReturnEntity salesReturn() {
        SalesReturnEntity entity = new SalesReturnEntity();
        entity.setId(9001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setReturnNo("SR-9001");
        entity.setDeliveryId(7001L);
        entity.setWarehouseId(3001L);
        entity.setReturnDate(LocalDate.of(2026, 6, 8));
        entity.setStatus("DRAFT");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesDeliveryEntity delivery() {
        SalesDeliveryEntity entity = new SalesDeliveryEntity();
        entity.setId(7001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setOrderId(6001L);
        entity.setWarehouseId(3001L);
        entity.setStatus("POSTED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesOrderEntity order() {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setId(6001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setStatus("APPROVED");
        entity.setApprovalStatus("APPROVED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private SalesReturnLineEntity returnLine() {
        SalesReturnLineEntity entity = new SalesReturnLineEntity();
        entity.setId(9101L);
        entity.setReturnId(9001L);
        entity.setLineNo(1);
        entity.setDeliveryLineId(7101L);
        entity.setOrderLineId(6101L);
        entity.setProductId(4001L);
        entity.setQty(new BigDecimal("2.0000"));
        entity.setPrice(new BigDecimal("10.00"));
        entity.setTaxRate(new BigDecimal("0.0000"));
        entity.setAmount(new BigDecimal("20.00"));
        entity.setTaxAmount(new BigDecimal("0.00"));
        return entity;
    }

    private SalesDeliveryLineEntity deliveryLine() {
        SalesDeliveryLineEntity entity = new SalesDeliveryLineEntity();
        entity.setId(7101L);
        entity.setDeliveryId(7001L);
        entity.setLineNo(1);
        entity.setOrderLineId(6101L);
        entity.setProductId(4001L);
        entity.setQty(new BigDecimal("5.0000"));
        entity.setPrice(new BigDecimal("10.00"));
        entity.setTaxRate(new BigDecimal("0.0000"));
        entity.setAmount(new BigDecimal("50.00"));
        entity.setTaxAmount(new BigDecimal("0.00"));
        entity.setReturnedQty(new BigDecimal("0.0000"));
        return entity;
    }

    private SalesOrderLineEntity orderLine() {
        SalesOrderLineEntity entity = new SalesOrderLineEntity();
        entity.setId(6101L);
        entity.setOrderId(6001L);
        entity.setLineNo(1);
        entity.setProductId(4001L);
        entity.setQty(new BigDecimal("5.0000"));
        entity.setDeliveredQty(new BigDecimal("5.0000"));
        return entity;
    }

    private InventoryTransactionEntity deliveryTxn(String lotNo, String qty, String amount) {
        InventoryTransactionEntity entity = new InventoryTransactionEntity();
        entity.setId(7201L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setWarehouseId(3001L);
        entity.setProductId(4001L);
        entity.setBizType("SALES_DELIVERY");
        entity.setBizNo("SD-7001");
        entity.setBizLineId(7101L);
        entity.setDirection("OUT");
        entity.setQty(new BigDecimal(qty));
        entity.setAmount(new BigDecimal(amount));
        entity.setLotNo(lotNo);
        return entity;
    }

    private SalesReturnService service() {
        return new SalesReturnService(
                salesReturnMapper,
                salesReturnLineMapper,
                salesDeliveryMapper,
                salesDeliveryLineMapper,
                salesOrderMapper,
                salesOrderLineMapper,
                productValidator,
                inventoryTransactionMapper,
                inventoryPostingService,
                inventorySerialNumberService,
                salesReturnNumberService,
                financePostingService,
                auditMetadataFactory,
                new SalesReturnQueryService(
                        salesReturnMapper,
                        salesReturnLineMapper,
                        currentUserContext,
                        dataScopeService,
                        scopedUserResolver,
                        userMapper
                ),
                accountPeriodGuard
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

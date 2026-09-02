package com.tuowei.erp.commercial.contract;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.commercial.contract.mapper.ContractLineMapper;
import com.tuowei.erp.commercial.contract.mapper.ContractMapper;
import com.tuowei.erp.commercial.contract.model.ContractEntity;
import com.tuowei.erp.commercial.contract.model.ContractLineEntity;
import com.tuowei.erp.commercial.contract.service.ContractQueryService;
import com.tuowei.erp.commercial.contract.web.ContractPageQuery;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.masterdata.customer.mapper.CustomerMapper;
import com.tuowei.erp.masterdata.customer.model.CustomerEntity;
import com.tuowei.erp.masterdata.product.mapper.ProductMapper;
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.order.model.SalesOrderLineEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ContractQueryServiceTest {

    private static final CurrentUser USER = new CurrentUser(9L, 1L, 2L, 31L, 41L, "scope", "范围用户");
    private final ContractMapper contractMapper = mock(ContractMapper.class);
    private final ContractLineMapper contractLineMapper = mock(ContractLineMapper.class);
    private final CustomerMapper customerMapper = mock(CustomerMapper.class);
    private final SupplierMapper supplierMapper = mock(SupplierMapper.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
    private final ScopedUserResolver scopedUserResolver = mock(ScopedUserResolver.class);
    private final com.tuowei.erp.sales.order.mapper.SalesOrderMapper salesOrderMapper = mock(com.tuowei.erp.sales.order.mapper.SalesOrderMapper.class);
    private final com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper salesOrderLineMapper = mock(com.tuowei.erp.sales.order.mapper.SalesOrderLineMapper.class);
    private final com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper purchaseOrderMapper = mock(com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper.class);
    private final com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper purchaseOrderLineMapper = mock(com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ContractEntity.class);
        initTableInfo(ContractLineEntity.class);
    }

    @Test
    void listNormalizesFiltersAppliesTenantAndCreatorScope() {
        DataScopeSnapshot snapshot = new DataScopeSnapshot(false, true, false, true, Set.of());
        when(currentUserContext.requireCurrentUser()).thenReturn(USER);
        when(currentUserContext.requirePrincipal()).thenReturn(principal(snapshot));
        when(scopedUserResolver.resolve(USER, snapshot)).thenReturn(new ScopedUserResolver.ScopedUserIds(Set.of(12L, 13L), Set.of()));
        when(contractMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<ContractEntity> page = invocation.getArgument(0);
            page.setTotal(0L); page.setRecords(List.of()); return page;
        });

        ContractPageQuery query = new ContractPageQuery();
        query.setPageNo(0L); query.setPageSize(999L); query.setKeyword("  CT-001  ");
        query.setContractType(" sales "); query.setStatus(" active ");
        query.setEffectiveFrom(LocalDate.of(2026, 8, 1));
        query.setEffectiveTo(LocalDate.of(2026, 8, 31));
        service().list(query);

        ArgumentCaptor<Page<ContractEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<ContractEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(contractMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
        String sql = wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql).contains("company_id", "account_book_id", "contract_type", "status", "effective_to", "effective_from", "created_by");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values()).contains(1L, 2L, "SALES", "ACTIVE", 9L, 12L, 13L);
    }

    @Test
    void listHydratesSalesCustomerWithoutLookingUpNullSupplier() {
        DataScopeSnapshot snapshot = DataScopeSnapshot.all();
        when(currentUserContext.requireCurrentUser()).thenReturn(USER);
        when(currentUserContext.requirePrincipal()).thenReturn(principal(snapshot));
        ContractEntity entity = contract("ACTIVE");
        when(contractMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<ContractEntity> page = invocation.getArgument(0);
            page.setTotal(1L); page.setRecords(List.of(entity)); return page;
        });
        CustomerEntity customer = new CustomerEntity(); customer.setId(101L); customer.setCompanyId(1L); customer.setAccountBookId(2L);
        customer.setDeletedFlag(0); customer.setCustomerName("客户A");
        when(customerMapper.selectBatchIds(any())).thenReturn(List.of(customer));

        var result = service().list(new ContractPageQuery());

        assertThat(result.records()).singleElement().satisfies(item -> {
            assertThat(item.customerName()).isEqualTo("客户A");
            assertThat(item.supplierName()).isNull();
        });
        verifyNoInteractions(supplierMapper);
    }

    @Test
    void detailRejectsCrossAccountBookBeforeLoadingLines() {
        when(currentUserContext.requireCurrentUser()).thenReturn(USER);
        ContractEntity entity = contract("ACTIVE"); entity.setAccountBookId(999L);
        when(contractMapper.selectById(1001L)).thenReturn(entity);

        assertThatThrownBy(() -> service().detail(1001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("合同不存在");
        verify(contractLineMapper, never()).selectList(any());
    }

    @Test
    void detailHydratesLinesAndProductNamesForSameTenant() {
        DataScopeSnapshot snapshot = DataScopeSnapshot.all();
        when(currentUserContext.requireCurrentUser()).thenReturn(USER);
        when(currentUserContext.requirePrincipal()).thenReturn(principal(snapshot));
        ContractEntity entity = contract("ACTIVE");
        when(contractMapper.selectById(1001L)).thenReturn(entity);
        ContractLineEntity line = new ContractLineEntity();
        line.setId(2001L); line.setCompanyId(1L); line.setAccountBookId(2L); line.setContractId(1001L);
        line.setLineNo(1); line.setProductId(7001L); line.setQuantity(new BigDecimal("2.0000"));
        line.setFulfilledQuantity(new BigDecimal("1.0000")); line.setUnitPrice(new BigDecimal("10.00"));
        line.setAmount(new BigDecimal("20.00")); line.setDeletedFlag(0);
        when(contractLineMapper.selectList(any())).thenReturn(List.of(line));
        ProductEntity product = new ProductEntity(); product.setId(7001L); product.setCompanyId(1L); product.setAccountBookId(2L); product.setDeletedFlag(0);
        product.setProductCode("P-7001"); product.setProductName("商品A");
        when(productMapper.selectBatchIds(any())).thenReturn(List.of(product));
        CustomerEntity customer = new CustomerEntity(); customer.setId(101L); customer.setCompanyId(1L); customer.setAccountBookId(2L); customer.setDeletedFlag(0); customer.setCustomerName("客户A");
        when(customerMapper.selectById(101L)).thenReturn(customer);

        var result = service().detail(1001L);

        assertThat(result.customerName()).isEqualTo("客户A");
        assertThat(result.lines()).singleElement().satisfies(item -> {
            assertThat(item.productCode()).isEqualTo("P-7001");
            assertThat(item.productName()).isEqualTo("商品A");
            assertThat(item.fulfilledQuantity()).isEqualByComparingTo("1.0000");
        });
        ArgumentCaptor<LambdaQueryWrapper<ContractLineEntity>> lineCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(contractLineMapper).selectList(lineCaptor.capture());
        assertThat(lineCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT)).contains("company_id", "account_book_id", "contract_id", "deleted_flag", "line_no");
    }

    @Test
    void detailReportsCommittedQuantityFromActiveOrders() {
        DataScopeSnapshot snapshot = DataScopeSnapshot.all();
        when(currentUserContext.requireCurrentUser()).thenReturn(USER);
        when(currentUserContext.requirePrincipal()).thenReturn(principal(snapshot));
        ContractEntity entity = contract("ACTIVE");
        when(contractMapper.selectById(1001L)).thenReturn(entity);
        ContractLineEntity line = new ContractLineEntity();
        line.setId(2001L); line.setCompanyId(1L); line.setAccountBookId(2L); line.setContractId(1001L);
        line.setLineNo(1); line.setProductId(7001L); line.setQuantity(new BigDecimal("10.0000"));
        line.setFulfilledQuantity(new BigDecimal("2.0000")); line.setUnitPrice(new BigDecimal("10.00")); line.setAmount(new BigDecimal("100.00")); line.setDeletedFlag(0);
        when(contractLineMapper.selectList(any())).thenReturn(List.of(line));
        when(productMapper.selectBatchIds(any())).thenReturn(List.of());
        SalesOrderLineEntity orderLine = new SalesOrderLineEntity(); orderLine.setOrderId(3001L); orderLine.setContractLineId(2001L); orderLine.setQty(new BigDecimal("3.0000"));
        when(salesOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine));
        SalesOrderEntity order = new SalesOrderEntity(); order.setId(3001L); order.setContractId(1001L); order.setCompanyId(1L); order.setAccountBookId(2L); order.setDeletedFlag(0); order.setStatus("APPROVED");
        when(salesOrderMapper.selectBatchIds(any())).thenReturn(List.of(order));

        var result = service().detail(1001L);
        assertThat(result.lines()).singleElement().satisfies(item -> assertThat(item.committedQuantity()).isEqualByComparingTo("3.0000"));
    }

    private ContractQueryService service() {
        return new ContractQueryService(contractMapper, contractLineMapper, customerMapper, supplierMapper,
                productMapper, userMapper, currentUserContext, scopedUserResolver,
                salesOrderMapper, salesOrderLineMapper, purchaseOrderMapper, purchaseOrderLineMapper);
    }

    private ErpPrincipal principal(DataScopeSnapshot snapshot) {
        return new ErpPrincipal(USER.userId(), USER.companyId(), USER.accountBookId(), USER.deptId(), USER.postId(),
                USER.username(), USER.realName(), "N/A", Set.of(), snapshot);
    }

    private ContractEntity contract(String status) {
        ContractEntity entity = new ContractEntity();
        entity.setId(1001L); entity.setCompanyId(1L); entity.setAccountBookId(2L); entity.setContractType("SALES");
        entity.setCustomerId(101L); entity.setContractName("测试合同"); entity.setContractNo("CT-001");
        entity.setSignedDate(LocalDate.of(2026, 8, 26)); entity.setEffectiveFrom(LocalDate.of(2026, 8, 1));
        entity.setStatus(status); entity.setTotalAmount(new BigDecimal("20.00")); entity.setDeletedFlag(0); entity.setCreatedBy(9L);
        return entity;
    }

    private static void initTableInfo(Class<?> entityType) {
        if (TableInfoHelper.getTableInfo(entityType) != null) return;
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), entityType.getName());
        assistant.setCurrentNamespace(entityType.getName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}

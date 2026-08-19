package com.tuowei.erp.purchase;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.CurrentUser;
import com.tuowei.erp.common.security.CurrentUserContext;
import com.tuowei.erp.common.security.DataScopeService;
import com.tuowei.erp.common.security.DataScopeSnapshot;
import com.tuowei.erp.common.security.ErpPrincipal;
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.masterdata.supplier.mapper.SupplierMapper;
import com.tuowei.erp.masterdata.supplier.model.SupplierEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderLineMapper;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.order.model.PurchaseOrderLineEntity;
import com.tuowei.erp.purchase.order.service.PurchaseOrderQueryService;
import com.tuowei.erp.purchase.order.web.PurchaseOrderPageQuery;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseOrderQueryServiceTest {

    private static final Long SUPPLIER_ID = 4101L;
    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9932L,
            101L,
            202L,
            11L,
            12L,
            "purchase_order_scope_user",
            "采购订单用户"
    );

    private final PurchaseOrderMapper purchaseOrderMapper = mock(PurchaseOrderMapper.class);
    private final PurchaseOrderLineMapper purchaseOrderLineMapper = mock(PurchaseOrderLineMapper.class);
    private final SupplierMapper supplierMapper = mock(SupplierMapper.class);
    private final CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
    private final ScopedUserResolver scopedUserResolver = mock(ScopedUserResolver.class);
    private final UserMapper userMapper = mock(UserMapper.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PurchaseOrderEntity.class);
        initTableInfo(PurchaseOrderLineEntity.class);
    }

    @ParameterizedTest(name = "{0} scope is shared by list and export")
    @MethodSource("scopedQueryCases")
    void listAndExportShareNormalizedScopedQuery(
            String scopeName,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds,
            Set<Long> expectedCreatorIds
    ) throws Exception {
        stubScope(snapshot, deptUserIds, postUserIds);
        when(purchaseOrderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<PurchaseOrderEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0L);
            return page;
        });
        when(purchaseOrderMapper.selectList(any())).thenReturn(List.of());

        PurchaseOrderPageQuery query = new PurchaseOrderPageQuery();
        query.setKeyword("  PO-SCOPE  ");
        query.setStatus("  approved  ");
        query.setApprovalStatus("  in_approval  ");
        query.setSupplierId(SUPPLIER_ID);

        PurchaseOrderQueryService service = service(new DataScopeService(null, null, null, null));
        service.list(query);
        service.exportOrders(query).writeTo(new ByteArrayOutputStream());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseOrderEntity>> listQuery =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseOrderMapper).selectPage(any(), listQuery.capture());
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseOrderEntity>> exportQuery =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseOrderMapper).selectList(exportQuery.capture());

        assertNormalizedScope(listQuery.getValue(), expectedCreatorIds);
        assertNormalizedScope(exportQuery.getValue(), expectedCreatorIds);
    }

    @Test
    void listClampsPaginationAndHydratesSupplierSummary() {
        DataScopeSnapshot snapshot = DataScopeSnapshot.all();
        stubScope(snapshot, Set.of(), Set.of());
        PurchaseOrderEntity order = order();
        when(purchaseOrderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<PurchaseOrderEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(order));
            page.setTotal(1L);
            return page;
        });
        when(supplierMapper.selectBatchIds(any())).thenReturn(List.of(supplier()));
        PurchaseOrderPageQuery query = new PurchaseOrderPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);

        var result = service(new DataScopeService(null, null, null, null)).list(query);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(200L);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.records()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(order.getId());
            assertThat(summary.supplierName()).isEqualTo("Scoped Supplier");
            assertThat(summary.lines()).isEmpty();
        });

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Page<PurchaseOrderEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(purchaseOrderMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
    }

    @Test
    void listAndExportDoNotHydrateNamesAcrossTenantBoundaries() throws Exception {
        DataScopeSnapshot snapshot = DataScopeSnapshot.all();
        stubScope(snapshot, Set.of(), Set.of());
        PurchaseOrderEntity order = order();
        order.setCreatedBy(7201L);
        SupplierEntity crossTenantSupplier = supplier();
        crossTenantSupplier.setAccountBookId(999L);
        UserEntity crossTenantCreator = user(7201L, "cross.tenant.creator");
        crossTenantCreator.setAccountBookId(999L);
        when(purchaseOrderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<PurchaseOrderEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(order));
            page.setTotal(1L);
            return page;
        });
        when(purchaseOrderMapper.selectList(any())).thenReturn(List.of(order));
        when(supplierMapper.selectBatchIds(any())).thenReturn(List.of(crossTenantSupplier));
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(crossTenantCreator));

        PurchaseOrderQueryService service = service(new DataScopeService(null, null, null, null));
        var result = service.list(new PurchaseOrderPageQuery());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.exportOrders(new PurchaseOrderPageQuery()).writeTo(output);

        assertThat(result.records()).singleElement()
                .satisfies(summary -> assertThat(summary.supplierName()).isNull());
        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("PO-4301,,2026-06-08,2026-06-10,100.00,APPROVED,,2026-06-08T21:30,query export");
    }

    @Test
    void assertCanViewPassesTheCreatorOrganizationToDataScope() {
        DataScopeSnapshot snapshot = new DataScopeSnapshot(false, true, true, false, Set.of());
        DataScopeService dataScopeService = mock(DataScopeService.class);
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(principal(snapshot));
        PurchaseOrderEntity order = order();
        order.setCreatedBy(7201L);
        UserEntity creator = user(7201L, "creator");
        creator.setDeptId(31L);
        creator.setPostId(32L);
        when(userMapper.selectById(7201L)).thenReturn(creator);

        service(dataScopeService).assertCanView(order);

        verify(dataScopeService).assertCanViewPurchaseOrder(
                order,
                CURRENT_USER,
                snapshot,
                31L,
                32L
        );
    }

    @Test
    void assertCanViewIgnoresCreatorOrganizationAcrossTenantBoundaries() {
        DataScopeSnapshot snapshot = new DataScopeSnapshot(false, true, true, false, Set.of());
        DataScopeService dataScopeService = mock(DataScopeService.class);
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(principal(snapshot));
        PurchaseOrderEntity order = order();
        order.setCreatedBy(7201L);
        UserEntity creator = user(7201L, "cross.tenant.creator");
        creator.setAccountBookId(999L);
        creator.setDeptId(31L);
        creator.setPostId(32L);
        when(userMapper.selectById(7201L)).thenReturn(creator);

        service(dataScopeService).assertCanView(order);

        verify(dataScopeService).assertCanViewPurchaseOrder(
                order,
                CURRENT_USER,
                snapshot,
                null,
                null
        );
    }

    @Test
    void getByIdChecksDataScopeScopesLinesAndMapsFullDetail() {
        DataScopeSnapshot snapshot = DataScopeSnapshot.all();
        DataScopeService dataScopeService = mock(DataScopeService.class);
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(principal(snapshot));
        PurchaseOrderEntity order = order();
        order.setSourceInquiryId(5101L);
        order.setSourceInquiryNo("RFQ-5101");
        order.setSourceQuoteId(5201L);
        when(purchaseOrderMapper.selectById(order.getId())).thenReturn(order);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(supplier());
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine()));

        var detail = service(dataScopeService).getById(order.getId());

        verify(dataScopeService).assertCanViewPurchaseOrder(
                order,
                CURRENT_USER,
                snapshot,
                null,
                null
        );
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseOrderLineEntity>> lineQuery =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseOrderLineMapper).selectList(lineQuery.capture());
        assertThat(lineQuery.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "order_id", "line_no");
        assertThat(lineQuery.getValue().getParamNameValuePairs().values())
                .contains(CURRENT_USER.companyId(), CURRENT_USER.accountBookId(), order.getId());
        assertThat(detail.supplierName()).isEqualTo("Scoped Supplier");
        assertThat(detail.sourceInquiryId()).isEqualTo(5101L);
        assertThat(detail.sourceInquiryNo()).isEqualTo("RFQ-5101");
        assertThat(detail.sourceQuoteId()).isEqualTo(5201L);
        assertThat(detail.lines()).singleElement().satisfies(line -> {
            assertThat(line.id()).isEqualTo(4401L);
            assertThat(line.auxQty()).isEqualByComparingTo("2.0000");
            assertThat(line.auxUnitName()).isEqualTo("箱");
            assertThat(line.conversionFactor()).isEqualByComparingTo("5.000000");
            assertThat(line.sourceInquiryId()).isEqualTo(5101L);
            assertThat(line.sourceInquiryLineId()).isEqualTo(5301L);
        });
    }

    @Test
    void getByIdStopsHydrationWhenDataScopeRejectsTheOrder() {
        DataScopeSnapshot snapshot = DataScopeSnapshot.all();
        DataScopeService dataScopeService = mock(DataScopeService.class);
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(principal(snapshot));
        PurchaseOrderEntity order = order();
        when(purchaseOrderMapper.selectById(order.getId())).thenReturn(order);
        doThrow(new AccessDeniedException("无权访问采购订单"))
                .when(dataScopeService)
                .assertCanViewPurchaseOrder(order, CURRENT_USER, snapshot, null, null);

        assertThatThrownBy(() -> service(dataScopeService).getById(order.getId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("无权访问采购订单");

        verify(supplierMapper, never()).selectById(any());
        verify(purchaseOrderLineMapper, never()).selectList(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"COMPANY", "ACCOUNT_BOOK"})
    void getByIdDoesNotHydrateSupplierNameAcrossTenantBoundaries(String invalidField) {
        DataScopeSnapshot snapshot = DataScopeSnapshot.all();
        DataScopeService dataScopeService = mock(DataScopeService.class);
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(principal(snapshot));
        PurchaseOrderEntity order = order();
        SupplierEntity supplier = supplier();
        if ("COMPANY".equals(invalidField)) {
            supplier.setCompanyId(999L);
        } else {
            supplier.setAccountBookId(999L);
        }
        when(purchaseOrderMapper.selectById(order.getId())).thenReturn(order);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(supplier);
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of());

        var detail = service(dataScopeService).getById(order.getId());

        assertThat(detail.supplierName()).isNull();
    }

    @Test
    void getBySourceInquiryKeepsTenantAndSourceContractAndMapsLines() {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        PurchaseOrderEntity order = order();
        order.setSourceInquiryId(5101L);
        order.setSourceInquiryNo("RFQ-5101");
        order.setSourceQuoteId(5201L);
        when(purchaseOrderMapper.selectById(order.getId())).thenReturn(order);
        when(supplierMapper.selectById(SUPPLIER_ID)).thenReturn(supplier());
        when(purchaseOrderLineMapper.selectList(any())).thenReturn(List.of(orderLine()));

        var detail = service(mock(DataScopeService.class)).getBySourceInquiry(order.getId(), 5101L);

        assertThat(detail.id()).isEqualTo(order.getId());
        assertThat(detail.supplierName()).isEqualTo("Scoped Supplier");
        assertThat(detail.lines()).singleElement()
                .satisfies(line -> assertThat(line.sourceInquiryLineId()).isEqualTo(5301L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DELETED", "COMPANY", "ACCOUNT_BOOK", "INQUIRY", "SOURCE_NULL", "REQUEST_NULL"})
    void getBySourceInquiryRejectsInvalidTenantOrSourceBeforeHydration(String invalidField) {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        PurchaseOrderEntity order = order();
        order.setSourceInquiryId(5101L);
        switch (invalidField) {
            case "DELETED" -> order.setDeletedFlag(1);
            case "COMPANY" -> order.setCompanyId(999L);
            case "ACCOUNT_BOOK" -> order.setAccountBookId(999L);
            case "INQUIRY" -> order.setSourceInquiryId(999L);
            case "SOURCE_NULL" -> order.setSourceInquiryId(null);
            case "REQUEST_NULL" -> {
            }
            default -> throw new IllegalArgumentException("unsupported field");
        }
        when(purchaseOrderMapper.selectById(order.getId())).thenReturn(order);
        Long inquiryId = "REQUEST_NULL".equals(invalidField) ? null : 5101L;

        PurchaseOrderQueryService service = service(mock(DataScopeService.class));
        assertThatThrownBy(() -> service.getBySourceInquiry(order.getId(), inquiryId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("询价单关联的采购订单不存在");

        verify(supplierMapper, never()).selectById(any());
        verify(purchaseOrderLineMapper, never()).selectList(any());
    }

    @Test
    void exportMapsNamesAndRestoresTheStreamingThreadAuthentication() throws Exception {
        DataScopeSnapshot snapshot = DataScopeSnapshot.all();
        stubScope(snapshot, Set.of(), Set.of());
        PurchaseOrderEntity order = order();
        order.setCreatedBy(7201L);
        Authentication capturedAuthentication = mock(Authentication.class);
        Authentication streamingThreadAuthentication = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(capturedAuthentication);
        try {
            var export = service(new DataScopeService(null, null, null, null))
                    .exportOrders(new PurchaseOrderPageQuery());
            SecurityContextHolder.getContext().setAuthentication(streamingThreadAuthentication);
            when(purchaseOrderMapper.selectList(any())).thenAnswer(invocation -> {
                assertThat(SecurityContextHolder.getContext().getAuthentication())
                        .isSameAs(capturedAuthentication);
                return List.of(order);
            });
            when(supplierMapper.selectBatchIds(any())).thenReturn(List.of(supplier()));
            when(userMapper.selectBatchIds(any())).thenReturn(List.of(user(7201L, "creator.user")));
            ByteArrayOutputStream output = new ByteArrayOutputStream();

            export.writeTo(output);

            String csv = output.toString(StandardCharsets.UTF_8);
            assertThat(csv)
                    .startsWith("\uFEFF订单编号,供应商,订单日期,交货日期,订单金额,状态,创建人,创建时间,备注\r\n")
                    .contains("PO-4301,Scoped Supplier,2026-06-08,2026-06-10,100.00,APPROVED,creator.user,2026-06-08T21:30,query export\r\n");
            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .isSameAs(streamingThreadAuthentication);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static Stream<Arguments> scopedQueryCases() {
        return Stream.of(
                Arguments.of(
                        "SELF",
                        new DataScopeSnapshot(false, false, false, true, Set.of()),
                        Set.of(),
                        Set.of(),
                        Set.of(CURRENT_USER.userId())
                ),
                Arguments.of(
                        "DEPT",
                        new DataScopeSnapshot(false, true, false, false, Set.of()),
                        Set.of(9301L, 9302L),
                        Set.of(),
                        Set.of(9301L, 9302L)
                ),
                Arguments.of(
                        "POST",
                        new DataScopeSnapshot(false, false, true, false, Set.of()),
                        Set.of(),
                        Set.of(9401L, 9402L),
                        Set.of(9401L, 9402L)
                )
        );
    }

    private PurchaseOrderQueryService service(DataScopeService dataScopeService) {
        return new PurchaseOrderQueryService(
                purchaseOrderMapper,
                purchaseOrderLineMapper,
                supplierMapper,
                currentUserContext,
                dataScopeService,
                scopedUserResolver,
                userMapper
        );
    }

    private void stubScope(
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds
    ) {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(principal(snapshot));
        when(scopedUserResolver.resolve(CURRENT_USER, snapshot))
                .thenReturn(new ScopedUserResolver.ScopedUserIds(deptUserIds, postUserIds));
    }

    private void assertNormalizedScope(
            LambdaQueryWrapper<PurchaseOrderEntity> wrapper,
            Set<Long> expectedCreatorIds
    ) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("deleted_flag")
                .contains("order_no")
                .contains("status")
                .contains("approval_status")
                .contains("supplier_id")
                .contains("company_id")
                .contains("account_book_id")
                .contains("created_by");
        Collection<Object> parameters = wrapper.getParamNameValuePairs().values();
        assertThat(parameters)
                .contains(
                        "%PO-SCOPE%",
                        "APPROVED",
                        "IN_APPROVAL",
                        SUPPLIER_ID,
                        CURRENT_USER.companyId(),
                        CURRENT_USER.accountBookId()
                )
                .containsAll(expectedCreatorIds)
                .doesNotContain("%  PO-SCOPE  %", "  approved  ", "  in_approval  ");
    }

    private ErpPrincipal principal(DataScopeSnapshot snapshot) {
        return new ErpPrincipal(
                CURRENT_USER.userId(),
                CURRENT_USER.companyId(),
                CURRENT_USER.accountBookId(),
                CURRENT_USER.deptId(),
                CURRENT_USER.postId(),
                CURRENT_USER.username(),
                CURRENT_USER.realName(),
                "N/A",
                Set.of(),
                snapshot
        );
    }

    private PurchaseOrderEntity order() {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setId(4301L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setOrderNo("PO-4301");
        entity.setSupplierId(SUPPLIER_ID);
        entity.setOrderDate(LocalDate.of(2026, 6, 8));
        entity.setDeliveryDate(LocalDate.of(2026, 6, 10));
        entity.setStatus("APPROVED");
        entity.setApprovalStatus("APPROVED");
        entity.setReceiptStatus("NOT_RECEIVED");
        entity.setTotalQuantity(BigDecimal.TEN);
        entity.setTotalAmount(new BigDecimal("100.00"));
        entity.setTotalTaxAmount(new BigDecimal("13.00"));
        entity.setRemark("query export");
        entity.setCreatedTime(LocalDateTime.of(2026, 6, 8, 21, 30));
        entity.setDeletedFlag(0);
        return entity;
    }

    private SupplierEntity supplier() {
        SupplierEntity entity = new SupplierEntity();
        entity.setId(SUPPLIER_ID);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setSupplierName("Scoped Supplier");
        return entity;
    }

    private PurchaseOrderLineEntity orderLine() {
        PurchaseOrderLineEntity entity = new PurchaseOrderLineEntity();
        entity.setId(4401L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setOrderId(4301L);
        entity.setLineNo(1);
        entity.setProductId(4201L);
        entity.setQty(BigDecimal.TEN);
        entity.setAuxQty(new BigDecimal("2.0000"));
        entity.setAuxUnitName("箱");
        entity.setConversionFactor(new BigDecimal("5.000000"));
        entity.setPrice(new BigDecimal("10.00"));
        entity.setTaxRate(new BigDecimal("0.1300"));
        entity.setAmount(new BigDecimal("100.00"));
        entity.setTaxAmount(new BigDecimal("13.00"));
        entity.setReceivedQty(BigDecimal.ONE);
        entity.setSourceInquiryId(5101L);
        entity.setSourceInquiryLineId(5301L);
        entity.setRemark("detail line");
        return entity;
    }

    private UserEntity user(Long id, String username) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setUsername(username);
        return entity;
    }

    private static void initTableInfo(Class<?> entityClass) {
        if (TableInfoHelper.getTableInfo(entityClass) != null) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(
                new MybatisConfiguration(),
                entityClass.getName()
        );
        assistant.setCurrentNamespace(entityClass.getName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}

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
import com.tuowei.erp.masterdata.product.model.ProductEntity;
import com.tuowei.erp.masterdata.product.service.ProductValidator;
import com.tuowei.erp.masterdata.warehouse.mapper.WarehouseMapper;
import com.tuowei.erp.masterdata.warehouse.model.WarehouseEntity;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptLineEntity;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnLineMapper;
import com.tuowei.erp.purchase.returnorder.mapper.PurchaseReturnMapper;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnEntity;
import com.tuowei.erp.purchase.returnorder.model.PurchaseReturnLineEntity;
import com.tuowei.erp.purchase.returnorder.service.PurchaseReturnQueryService;
import com.tuowei.erp.purchase.returnorder.web.PurchaseReturnPageQuery;
import com.tuowei.erp.system.user.mapper.UserMapper;
import com.tuowei.erp.system.user.model.UserEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchaseReturnQueryServiceTest {

    private static final Long RECEIPT_ID = 7101L;
    private static final Long WAREHOUSE_ID = 7201L;
    private static final LocalDate DATE_FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate DATE_TO = LocalDate.of(2026, 6, 30);
    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9935L,
            101L,
            202L,
            11L,
            12L,
            "purchase_return_scope_user",
            "采购退货用户"
    );

    private final PurchaseReturnMapper purchaseReturnMapper = mock(PurchaseReturnMapper.class);
    private final PurchaseReturnLineMapper purchaseReturnLineMapper = mock(PurchaseReturnLineMapper.class);
    private final PurchaseReceiptMapper purchaseReceiptMapper = mock(PurchaseReceiptMapper.class);
    private final PurchaseReceiptLineMapper purchaseReceiptLineMapper = mock(PurchaseReceiptLineMapper.class);
    private final PurchaseOrderMapper purchaseOrderMapper = mock(PurchaseOrderMapper.class);
    private final WarehouseMapper warehouseMapper = mock(WarehouseMapper.class);
    private final ProductValidator productValidator = mock(ProductValidator.class);
    private final CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
    private final ScopedUserResolver scopedUserResolver = mock(ScopedUserResolver.class);
    private final UserMapper userMapper = mock(UserMapper.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PurchaseReturnEntity.class);
        initTableInfo(PurchaseReturnLineEntity.class);
        initTableInfo(PurchaseReceiptLineEntity.class);
    }

    @ParameterizedTest(name = "{0} scope is applied to normalized purchase return filters")
    @MethodSource("scopedQueryCases")
    void listNormalizesFiltersAndAppliesSharedScope(
            String scopeName,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds,
            Set<Long> expectedCreatorIds
    ) {
        stubScope(snapshot, deptUserIds, postUserIds);
        when(purchaseReturnMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<PurchaseReturnEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0L);
            return page;
        });
        PurchaseReturnPageQuery query = fullQuery();

        service(new DataScopeService(null, null, null, null)).list(query);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseReturnEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseReturnMapper).selectPage(any(), queryCaptor.capture());
        assertNormalizedScope(queryCaptor.getValue(), expectedCreatorIds);
    }

    @Test
    void listClampsPaginationAndMapsSummaryContextWithoutLines() {
        stubScope(DataScopeSnapshot.all(), Set.of(), Set.of());
        PurchaseReturnEntity purchaseReturn = purchaseReturn();
        when(purchaseReturnMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<PurchaseReturnEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(purchaseReturn));
            page.setTotal(1L);
            return page;
        });
        stubDocumentContext();
        PurchaseReturnPageQuery query = new PurchaseReturnPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);

        var result = service(new DataScopeService(null, null, null, null)).list(query);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(200L);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.records()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(purchaseReturn.getId());
            assertThat(summary.returnNo()).isEqualTo("PR-7301");
            assertThat(summary.receiptId()).isEqualTo(RECEIPT_ID);
            assertThat(summary.receiptNo()).isEqualTo("GR-7101");
            assertThat(summary.orderNo()).isEqualTo("PO-7401");
            assertThat(summary.warehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(summary.warehouseName()).isEqualTo("主仓");
            assertThat(summary.totalQuantity()).isEqualByComparingTo("3.0000");
            assertThat(summary.totalAmount()).isEqualByComparingTo("60.00");
            assertThat(summary.totalTaxAmount()).isEqualByComparingTo("7.80");
            assertThat(summary.lines()).isEmpty();
        });

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Page<PurchaseReturnEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(purchaseReturnMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
    }

    @Test
    void listUsesDefaultPaginationForNullQuery() {
        stubScope(DataScopeSnapshot.all(), Set.of(), Set.of());
        when(purchaseReturnMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<PurchaseReturnEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0L);
            return page;
        });

        var result = service(new DataScopeService(null, null, null, null)).list(null);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(20L);
    }

    @Test
    void getByIdScopesLineQueriesAndMapsEnrichedDetail() {
        DataScopeSnapshot snapshot = DataScopeSnapshot.all();
        DataScopeService dataScopeService = mock(DataScopeService.class);
        stubCurrentUser(snapshot);
        PurchaseReturnEntity purchaseReturn = purchaseReturn();
        purchaseReturn.setCreatedBy(null);
        PurchaseReturnLineEntity returnLine = purchaseReturnLine();
        when(purchaseReturnMapper.selectById(purchaseReturn.getId())).thenReturn(purchaseReturn);
        when(purchaseReturnLineMapper.selectList(any())).thenReturn(List.of(returnLine));
        when(purchaseReceiptLineMapper.selectOne(any())).thenReturn(receiptLine());
        when(productValidator.requireProduct(7601L, CURRENT_USER.companyId(), CURRENT_USER.accountBookId()))
                .thenReturn(product());
        stubDocumentContext();

        var result = service(dataScopeService).getById(purchaseReturn.getId());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseReturnLineEntity>> returnLineQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseReturnLineMapper).selectList(returnLineQueryCaptor.capture());
        assertThat(returnLineQueryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "return_id", "line_no");
        assertThat(returnLineQueryCaptor.getValue().getParamNameValuePairs().values())
                .contains(CURRENT_USER.companyId(), CURRENT_USER.accountBookId(), purchaseReturn.getId());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseReceiptLineEntity>> receiptLineQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseReceiptLineMapper).selectOne(receiptLineQueryCaptor.capture());
        assertThat(receiptLineQueryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "id");
        assertThat(receiptLineQueryCaptor.getValue().getParamNameValuePairs().values())
                .contains(CURRENT_USER.companyId(), CURRENT_USER.accountBookId(), 7501L);
        verify(dataScopeService).assertCanViewPurchaseReturn(
                purchaseReturn,
                CURRENT_USER,
                snapshot,
                null,
                null
        );
        assertThat(result.receiptNo()).isEqualTo("GR-7101");
        assertThat(result.orderNo()).isEqualTo("PO-7401");
        assertThat(result.warehouseName()).isEqualTo("主仓");
        assertThat(result.lines()).singleElement().satisfies(line -> {
            assertThat(line.id()).isEqualTo(returnLine.getId());
            assertThat(line.receiptLineId()).isEqualTo(7501L);
            assertThat(line.orderLineId()).isEqualTo(7551L);
            assertThat(line.productId()).isEqualTo(7601L);
            assertThat(line.productName()).isEqualTo("查询商品");
            assertThat(line.qty()).isEqualByComparingTo("3.0000");
            assertThat(line.price()).isEqualByComparingTo("20.00");
            assertThat(line.taxRate()).isEqualByComparingTo("0.1300");
            assertThat(line.amount()).isEqualByComparingTo("60.00");
            assertThat(line.taxAmount()).isEqualByComparingTo("7.80");
            assertThat(line.receiptQty()).isEqualByComparingTo("5.0000");
            assertThat(line.returnedQty()).isEqualByComparingTo("1.0000");
            assertThat(line.availableReturnQty()).isEqualByComparingTo("4.0000");
            assertThat(line.lotNo()).isEqualTo("LOT-RETURN");
            assertThat(line.productionDate()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(line.expiryDate()).isEqualTo(LocalDate.of(2027, 5, 1));
            assertThat(line.locationId()).isEqualTo(7701L);
            assertThat(line.serialNos()).isEqualTo("SN-R1\nSN-R2\nSN-R3");
            assertThat(line.remark()).isEqualTo("return detail line");
        });
    }

    @Test
    void getByIdRejectsMissingReturnBeforeLoadingRelatedData() {
        when(purchaseReturnMapper.selectById(7301L)).thenReturn(null);

        assertThatThrownBy(() -> service(mock(DataScopeService.class)).getById(7301L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("采购退货单不存在");

        verify(purchaseReceiptMapper, never()).selectById(any());
        verify(purchaseReturnLineMapper, never()).selectList(any());
    }

    @Test
    void getByIdRejectsReceiptFromDifferentAccountBookBeforeLoadingLines() {
        DataScopeSnapshot snapshot = DataScopeSnapshot.all();
        DataScopeService dataScopeService = mock(DataScopeService.class);
        stubCurrentUser(snapshot);
        PurchaseReturnEntity purchaseReturn = purchaseReturn();
        purchaseReturn.setCreatedBy(null);
        when(purchaseReturnMapper.selectById(purchaseReturn.getId())).thenReturn(purchaseReturn);
        when(purchaseReceiptMapper.selectById(RECEIPT_ID)).thenReturn(receipt(9999L));

        assertThatThrownBy(() -> service(dataScopeService).getById(purchaseReturn.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("采购入库单未过账，不能创建采购退货单");

        verify(purchaseReturnLineMapper, never()).selectList(any());
    }

    @Test
    void assertCanViewReturnPassesCreatorOrganizationToDataScope() {
        DataScopeSnapshot snapshot = organizationScope();
        DataScopeService dataScopeService = mock(DataScopeService.class);
        stubCurrentUser(snapshot);
        PurchaseReturnEntity purchaseReturn = purchaseReturn();
        purchaseReturn.setCreatedBy(7801L);
        when(userMapper.selectById(7801L)).thenReturn(user(7801L, 31L, 32L));

        service(dataScopeService).assertCanView(purchaseReturn);

        verify(dataScopeService).assertCanViewPurchaseReturn(
                purchaseReturn,
                CURRENT_USER,
                snapshot,
                31L,
                32L
        );
    }

    @Test
    void assertCanViewReceiptPassesCreatorOrganizationToDataScope() {
        DataScopeSnapshot snapshot = organizationScope();
        DataScopeService dataScopeService = mock(DataScopeService.class);
        stubCurrentUser(snapshot);
        PurchaseReceiptEntity receipt = receipt(CURRENT_USER.accountBookId());
        receipt.setCreatedBy(7802L);
        when(userMapper.selectById(7802L)).thenReturn(user(7802L, 41L, 42L));

        service(dataScopeService).assertCanView(receipt);

        verify(dataScopeService).assertCanViewPurchaseReceipt(
                receipt,
                CURRENT_USER,
                snapshot,
                41L,
                42L
        );
    }

    @Test
    void assertCanViewOrderPassesCreatorOrganizationToDataScope() {
        DataScopeSnapshot snapshot = organizationScope();
        DataScopeService dataScopeService = mock(DataScopeService.class);
        stubCurrentUser(snapshot);
        PurchaseOrderEntity order = order();
        order.setCreatedBy(7803L);
        when(userMapper.selectById(7803L)).thenReturn(user(7803L, 51L, 52L));

        service(dataScopeService).assertCanView(order);

        verify(dataScopeService).assertCanViewPurchaseOrder(
                order,
                CURRENT_USER,
                snapshot,
                51L,
                52L
        );
    }

    @Test
    void exportUsesCapturedAuthenticationAndRestoresStreamingAuthentication() throws Exception {
        DataScopeSnapshot snapshot = DataScopeSnapshot.all();
        stubScope(snapshot, Set.of(), Set.of());
        DataScopeService dataScopeService = mock(DataScopeService.class);
        Authentication capturedAuthentication = mock(Authentication.class);
        Authentication streamingAuthentication = mock(Authentication.class);
        SecurityContext requestContext = SecurityContextHolder.createEmptyContext();
        requestContext.setAuthentication(capturedAuthentication);
        SecurityContextHolder.setContext(requestContext);
        try {
            var export = service(dataScopeService).exportReturns(fullQuery());
            SecurityContext streamingContext = SecurityContextHolder.createEmptyContext();
            streamingContext.setAuthentication(streamingAuthentication);
            SecurityContextHolder.setContext(streamingContext);
            when(dataScopeService.applyPurchaseReturnScope(any(), any(), any(), any(), any()))
                    .thenAnswer(invocation -> {
                        assertThat(SecurityContextHolder.getContext().getAuthentication())
                                .isSameAs(capturedAuthentication);
                        LambdaQueryWrapper<PurchaseReturnEntity> wrapper = invocation.getArgument(0);
                        return wrapper.eq(PurchaseReturnEntity::getCompanyId, CURRENT_USER.companyId())
                                .eq(PurchaseReturnEntity::getAccountBookId, CURRENT_USER.accountBookId());
                    });
            when(purchaseReturnMapper.selectList(any())).thenReturn(List.of());

            export.writeTo(new ByteArrayOutputStream());

            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .isSameAs(streamingAuthentication);
            @SuppressWarnings({"unchecked", "rawtypes"})
            ArgumentCaptor<LambdaQueryWrapper<PurchaseReturnEntity>> queryCaptor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(purchaseReturnMapper).selectList(queryCaptor.capture());
            assertNormalizedScope(queryCaptor.getValue(), Set.of());
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

    private PurchaseReturnQueryService service(DataScopeService dataScopeService) {
        return new PurchaseReturnQueryService(
                purchaseReturnMapper,
                purchaseReturnLineMapper,
                purchaseReceiptMapper,
                purchaseReceiptLineMapper,
                purchaseOrderMapper,
                warehouseMapper,
                productValidator,
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
        stubCurrentUser(snapshot);
        when(scopedUserResolver.resolve(CURRENT_USER, snapshot))
                .thenReturn(new ScopedUserResolver.ScopedUserIds(deptUserIds, postUserIds));
    }

    private void stubCurrentUser(DataScopeSnapshot snapshot) {
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(principal(snapshot));
    }

    private void stubDocumentContext() {
        when(purchaseReceiptMapper.selectById(RECEIPT_ID)).thenReturn(receipt(CURRENT_USER.accountBookId()));
        when(purchaseOrderMapper.selectById(7401L)).thenReturn(order());
        when(warehouseMapper.selectById(WAREHOUSE_ID)).thenReturn(warehouse());
    }

    private PurchaseReturnPageQuery fullQuery() {
        PurchaseReturnPageQuery query = new PurchaseReturnPageQuery();
        query.setKeyword("  PR-SCOPE  ");
        query.setReceiptId(RECEIPT_ID);
        query.setWarehouseId(WAREHOUSE_ID);
        query.setStatus("  posted  ");
        query.setReturnDateFrom(DATE_FROM);
        query.setReturnDateTo(DATE_TO);
        return query;
    }

    private void assertNormalizedScope(
            LambdaQueryWrapper<PurchaseReturnEntity> wrapper,
            Set<Long> expectedCreatorIds
    ) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains(
                        "deleted_flag",
                        "return_no",
                        "receipt_id",
                        "warehouse_id",
                        "status",
                        "return_date",
                        "company_id",
                        "account_book_id"
                );
        Collection<Object> parameters = wrapper.getParamNameValuePairs().values();
        assertThat(parameters)
                .contains(
                        "%PR-SCOPE%",
                        RECEIPT_ID,
                        WAREHOUSE_ID,
                        "POSTED",
                        DATE_FROM,
                        DATE_TO,
                        CURRENT_USER.companyId(),
                        CURRENT_USER.accountBookId()
                )
                .containsAll(expectedCreatorIds)
                .doesNotContain("%  PR-SCOPE  %", "  posted  ");
        if (!expectedCreatorIds.isEmpty()) {
            assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT)).contains("created_by");
        }
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

    private DataScopeSnapshot organizationScope() {
        return new DataScopeSnapshot(false, true, true, false, Set.of());
    }

    private PurchaseReturnEntity purchaseReturn() {
        PurchaseReturnEntity entity = new PurchaseReturnEntity();
        entity.setId(7301L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setReturnNo("PR-7301");
        entity.setReceiptId(RECEIPT_ID);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setReturnDate(LocalDate.of(2026, 6, 8));
        entity.setStatus("POSTED");
        entity.setTotalQuantity(new BigDecimal("3.0000"));
        entity.setTotalAmount(new BigDecimal("60.00"));
        entity.setTotalTaxAmount(new BigDecimal("7.80"));
        entity.setRemark("query detail");
        entity.setCreatedBy(7801L);
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseReturnLineEntity purchaseReturnLine() {
        PurchaseReturnLineEntity entity = new PurchaseReturnLineEntity();
        entity.setId(7351L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setReturnId(7301L);
        entity.setLineNo(1);
        entity.setReceiptLineId(7501L);
        entity.setOrderLineId(7551L);
        entity.setProductId(7601L);
        entity.setQty(new BigDecimal("3.0000"));
        entity.setPrice(new BigDecimal("20.00"));
        entity.setTaxRate(new BigDecimal("0.1300"));
        entity.setAmount(new BigDecimal("60.00"));
        entity.setTaxAmount(new BigDecimal("7.80"));
        entity.setLotNo("LOT-RETURN");
        entity.setProductionDate(LocalDate.of(2026, 5, 1));
        entity.setExpiryDate(LocalDate.of(2027, 5, 1));
        entity.setLocationId(7701L);
        entity.setSerialNos("SN-R1\nSN-R2\nSN-R3");
        entity.setRemark("return detail line");
        return entity;
    }

    private PurchaseReceiptEntity receipt(Long accountBookId) {
        PurchaseReceiptEntity entity = new PurchaseReceiptEntity();
        entity.setId(RECEIPT_ID);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(accountBookId);
        entity.setReceiptNo("GR-7101");
        entity.setOrderId(7401L);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setStatus("POSTED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseReceiptLineEntity receiptLine() {
        PurchaseReceiptLineEntity entity = new PurchaseReceiptLineEntity();
        entity.setId(7501L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setReceiptId(RECEIPT_ID);
        entity.setOrderLineId(7551L);
        entity.setProductId(7601L);
        entity.setQty(new BigDecimal("5.0000"));
        entity.setPrice(new BigDecimal("20.00"));
        entity.setTaxRate(new BigDecimal("0.1300"));
        entity.setReturnedQty(new BigDecimal("1.0000"));
        return entity;
    }

    private PurchaseOrderEntity order() {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setId(7401L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setOrderNo("PO-7401");
        entity.setDeletedFlag(0);
        return entity;
    }

    private WarehouseEntity warehouse() {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(WAREHOUSE_ID);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setWarehouseName("主仓");
        entity.setDeletedFlag(0);
        return entity;
    }

    private ProductEntity product() {
        ProductEntity entity = new ProductEntity();
        entity.setId(7601L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setProductName("查询商品");
        entity.setDeletedFlag(0);
        return entity;
    }

    private UserEntity user(Long id, Long deptId, Long postId) {
        UserEntity entity = new UserEntity();
        entity.setId(id);
        entity.setDeptId(deptId);
        entity.setPostId(postId);
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

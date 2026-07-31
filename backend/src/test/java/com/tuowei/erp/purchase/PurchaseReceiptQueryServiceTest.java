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
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptLineMapper;
import com.tuowei.erp.purchase.receipt.mapper.PurchaseReceiptMapper;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptEntity;
import com.tuowei.erp.purchase.receipt.model.PurchaseReceiptLineEntity;
import com.tuowei.erp.purchase.receipt.service.PurchaseReceiptQueryService;
import com.tuowei.erp.purchase.receipt.web.PurchaseReceiptPageQuery;
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
import java.nio.charset.StandardCharsets;
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

class PurchaseReceiptQueryServiceTest {

    private static final Long ORDER_ID = 6001L;
    private static final Long WAREHOUSE_ID = 3001L;
    private static final LocalDate DATE_FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate DATE_TO = LocalDate.of(2026, 6, 30);
    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9934L,
            101L,
            202L,
            11L,
            12L,
            "purchase_receipt_scope_user",
            "采购入库用户"
    );

    private final PurchaseReceiptMapper purchaseReceiptMapper = mock(PurchaseReceiptMapper.class);
    private final PurchaseReceiptLineMapper purchaseReceiptLineMapper = mock(PurchaseReceiptLineMapper.class);
    private final CurrentUserContext currentUserContext = mock(CurrentUserContext.class);
    private final ScopedUserResolver scopedUserResolver = mock(ScopedUserResolver.class);
    private final UserMapper userMapper = mock(UserMapper.class);

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PurchaseReceiptEntity.class);
        initTableInfo(PurchaseReceiptLineEntity.class);
    }

    @ParameterizedTest(name = "{0} scope is applied to normalized purchase receipt filters")
    @MethodSource("scopedQueryCases")
    void listNormalizesFiltersAndAppliesSharedScope(
            String scopeName,
            DataScopeSnapshot snapshot,
            Set<Long> deptUserIds,
            Set<Long> postUserIds,
            Set<Long> expectedCreatorIds
    ) {
        stubScope(snapshot, deptUserIds, postUserIds);
        when(purchaseReceiptMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<PurchaseReceiptEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0L);
            return page;
        });

        service(new DataScopeService(null, null, null, null)).list(fullQuery());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseReceiptEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseReceiptMapper).selectPage(any(), queryCaptor.capture());
        assertNormalizedScope(queryCaptor.getValue(), expectedCreatorIds);
    }

    @Test
    void listClampsPaginationAndMapsSummaryWithoutLines() {
        stubScope(DataScopeSnapshot.all(), Set.of(), Set.of());
        PurchaseReceiptEntity receipt = receipt();
        when(purchaseReceiptMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<PurchaseReceiptEntity> page = invocation.getArgument(0);
            page.setRecords(List.of(receipt));
            page.setTotal(1L);
            return page;
        });
        PurchaseReceiptPageQuery query = new PurchaseReceiptPageQuery();
        query.setPageNo(0);
        query.setPageSize(999);

        var result = service(new DataScopeService(null, null, null, null)).list(query);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(200L);
        assertThat(result.total()).isEqualTo(1L);
        assertThat(result.records()).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(receipt.getId());
            assertThat(summary.receiptNo()).isEqualTo("GR-7001");
            assertThat(summary.orderId()).isEqualTo(ORDER_ID);
            assertThat(summary.warehouseId()).isEqualTo(WAREHOUSE_ID);
            assertThat(summary.receiptDate()).isEqualTo(LocalDate.of(2026, 6, 8));
            assertThat(summary.status()).isEqualTo("POSTED");
            assertThat(summary.totalQuantity()).isEqualByComparingTo("3.0000");
            assertThat(summary.totalAmount()).isEqualByComparingTo("60.00");
            assertThat(summary.totalTaxAmount()).isEqualByComparingTo("7.80");
            assertThat(summary.remark()).isEqualTo("query detail");
            assertThat(summary.lines()).isEmpty();
        });

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<Page<PurchaseReceiptEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(purchaseReceiptMapper).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1L);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200L);
    }

    @Test
    void listUsesDefaultPaginationForNullQuery() {
        stubScope(DataScopeSnapshot.all(), Set.of(), Set.of());
        when(purchaseReceiptMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<PurchaseReceiptEntity> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0L);
            return page;
        });

        var result = service(new DataScopeService(null, null, null, null)).list(null);

        assertThat(result.pageNo()).isEqualTo(1L);
        assertThat(result.pageSize()).isEqualTo(20L);
    }

    @Test
    void getByIdScopesLineQueryAndMapsCompleteDetail() {
        DataScopeSnapshot snapshot = DataScopeSnapshot.all();
        DataScopeService dataScopeService = mock(DataScopeService.class);
        stubCurrentUser(snapshot);
        PurchaseReceiptEntity receipt = receipt();
        receipt.setCreatedBy(null);
        PurchaseReceiptLineEntity line = receiptLine();
        when(purchaseReceiptMapper.selectById(receipt.getId())).thenReturn(receipt);
        when(purchaseReceiptLineMapper.selectList(any())).thenReturn(List.of(line));

        var result = service(dataScopeService).getById(receipt.getId());

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseReceiptLineEntity>> lineQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseReceiptLineMapper).selectList(lineQueryCaptor.capture());
        assertThat(lineQueryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "receipt_id", "line_no");
        assertThat(lineQueryCaptor.getValue().getParamNameValuePairs().values())
                .contains(CURRENT_USER.companyId(), CURRENT_USER.accountBookId(), receipt.getId());
        verify(dataScopeService).assertCanViewPurchaseReceipt(receipt, CURRENT_USER, snapshot, null, null);
        assertThat(result.id()).isEqualTo(receipt.getId());
        assertThat(result.lines()).singleElement().satisfies(detail -> {
            assertThat(detail.id()).isEqualTo(line.getId());
            assertThat(detail.lineNo()).isEqualTo(1);
            assertThat(detail.orderLineId()).isEqualTo(6101L);
            assertThat(detail.productId()).isEqualTo(4001L);
            assertThat(detail.qty()).isEqualByComparingTo("3.0000");
            assertThat(detail.price()).isEqualByComparingTo("20.00");
            assertThat(detail.taxRate()).isEqualByComparingTo("0.1300");
            assertThat(detail.amount()).isEqualByComparingTo("60.00");
            assertThat(detail.taxAmount()).isEqualByComparingTo("7.80");
            assertThat(detail.lotNo()).isEqualTo("LOT-A");
            assertThat(detail.productionDate()).isEqualTo(LocalDate.of(2026, 5, 1));
            assertThat(detail.expiryDate()).isEqualTo(LocalDate.of(2027, 5, 1));
            assertThat(detail.locationId()).isEqualTo(3101L);
            assertThat(detail.serialNos()).isEqualTo("SN-1\nSN-2\nSN-3");
            assertThat(detail.remark()).isEqualTo("receipt detail line");
        });
    }

    @Test
    void getByIdRejectsMissingReceiptBeforeLoadingLines() {
        when(purchaseReceiptMapper.selectById(7001L)).thenReturn(null);

        assertThatThrownBy(() -> service(mock(DataScopeService.class)).getById(7001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("采购入库单不存在");

        verify(purchaseReceiptLineMapper, never()).selectList(any());
    }

    @Test
    void assertCanViewReceiptPassesCreatorOrganizationToDataScope() {
        DataScopeSnapshot snapshot = organizationScope();
        DataScopeService dataScopeService = mock(DataScopeService.class);
        stubCurrentUser(snapshot);
        PurchaseReceiptEntity receipt = receipt();
        receipt.setCreatedBy(7801L);
        when(userMapper.selectById(7801L)).thenReturn(user(7801L, 31L, 32L));

        service(dataScopeService).assertCanView(receipt);

        verify(dataScopeService).assertCanViewPurchaseReceipt(
                receipt,
                CURRENT_USER,
                snapshot,
                31L,
                32L
        );
    }

    @Test
    void assertCanViewOrderPassesCreatorOrganizationToDataScope() {
        DataScopeSnapshot snapshot = organizationScope();
        DataScopeService dataScopeService = mock(DataScopeService.class);
        stubCurrentUser(snapshot);
        PurchaseOrderEntity order = order();
        order.setCreatedBy(7802L);
        when(userMapper.selectById(7802L)).thenReturn(user(7802L, 41L, 42L));

        service(dataScopeService).assertCanView(order);

        verify(dataScopeService).assertCanViewPurchaseOrder(
                order,
                CURRENT_USER,
                snapshot,
                41L,
                42L
        );
    }

    @Test
    void exportUsesCapturedAuthenticationRestoresContextAndWritesNormalizedCsv() throws Exception {
        DataScopeSnapshot snapshot = DataScopeSnapshot.all();
        stubScope(snapshot, Set.of(), Set.of());
        DataScopeService dataScopeService = mock(DataScopeService.class);
        Authentication capturedAuthentication = mock(Authentication.class);
        Authentication streamingAuthentication = mock(Authentication.class);
        SecurityContext requestContext = SecurityContextHolder.createEmptyContext();
        requestContext.setAuthentication(capturedAuthentication);
        SecurityContextHolder.setContext(requestContext);
        try {
            var export = service(dataScopeService).exportReceipts(fullQuery());
            SecurityContext streamingContext = SecurityContextHolder.createEmptyContext();
            streamingContext.setAuthentication(streamingAuthentication);
            SecurityContextHolder.setContext(streamingContext);
            when(dataScopeService.applyPurchaseReceiptScope(any(), any(), any(), any(), any()))
                    .thenAnswer(invocation -> {
                        assertThat(SecurityContextHolder.getContext().getAuthentication())
                                .isSameAs(capturedAuthentication);
                        LambdaQueryWrapper<PurchaseReceiptEntity> wrapper = invocation.getArgument(0);
                        return wrapper.eq(PurchaseReceiptEntity::getCompanyId, CURRENT_USER.companyId())
                                .eq(PurchaseReceiptEntity::getAccountBookId, CURRENT_USER.accountBookId());
                    });
            when(purchaseReceiptMapper.selectList(any())).thenReturn(List.of(receipt()));
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            export.writeTo(outputStream);

            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .isSameAs(streamingAuthentication);
            assertThat(outputStream.toString(StandardCharsets.UTF_8))
                    .startsWith("\uFEFFreceiptNo,orderId,warehouseId,receiptDate,status,totalQuantity,totalAmount,totalTaxAmount,remark\r\n")
                    .contains("GR-7001,6001,3001,2026-06-08,POSTED,3.0000,60.00,7.80,query detail\r\n");
            @SuppressWarnings({"unchecked", "rawtypes"})
            ArgumentCaptor<LambdaQueryWrapper<PurchaseReceiptEntity>> queryCaptor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(purchaseReceiptMapper).selectList(queryCaptor.capture());
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

    private PurchaseReceiptQueryService service(DataScopeService dataScopeService) {
        return new PurchaseReceiptQueryService(
                purchaseReceiptMapper,
                purchaseReceiptLineMapper,
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

    private PurchaseReceiptPageQuery fullQuery() {
        PurchaseReceiptPageQuery query = new PurchaseReceiptPageQuery();
        query.setKeyword("  GR-SCOPE  ");
        query.setOrderId(ORDER_ID);
        query.setWarehouseId(WAREHOUSE_ID);
        query.setStatus("  posted  ");
        query.setReceiptDateFrom(DATE_FROM);
        query.setReceiptDateTo(DATE_TO);
        return query;
    }

    private void assertNormalizedScope(
            LambdaQueryWrapper<PurchaseReceiptEntity> wrapper,
            Set<Long> expectedCreatorIds
    ) {
        assertThat(wrapper.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains(
                        "deleted_flag",
                        "receipt_no",
                        "order_id",
                        "warehouse_id",
                        "status",
                        "receipt_date",
                        "company_id",
                        "account_book_id"
                );
        Collection<Object> parameters = wrapper.getParamNameValuePairs().values();
        assertThat(parameters)
                .contains(
                        "%GR-SCOPE%",
                        ORDER_ID,
                        WAREHOUSE_ID,
                        "POSTED",
                        DATE_FROM,
                        DATE_TO,
                        CURRENT_USER.companyId(),
                        CURRENT_USER.accountBookId()
                )
                .containsAll(expectedCreatorIds)
                .doesNotContain("%  GR-SCOPE  %", "  posted  ");
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

    private PurchaseReceiptEntity receipt() {
        PurchaseReceiptEntity entity = new PurchaseReceiptEntity();
        entity.setId(7001L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setReceiptNo("GR-7001");
        entity.setOrderId(ORDER_ID);
        entity.setWarehouseId(WAREHOUSE_ID);
        entity.setReceiptDate(LocalDate.of(2026, 6, 8));
        entity.setStatus("POSTED");
        entity.setTotalQuantity(new BigDecimal("3.0000"));
        entity.setTotalAmount(new BigDecimal("60.00"));
        entity.setTotalTaxAmount(new BigDecimal("7.80"));
        entity.setRemark("query detail");
        entity.setCreatedBy(7801L);
        entity.setDeletedFlag(0);
        return entity;
    }

    private PurchaseReceiptLineEntity receiptLine() {
        PurchaseReceiptLineEntity entity = new PurchaseReceiptLineEntity();
        entity.setId(7101L);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setReceiptId(7001L);
        entity.setLineNo(1);
        entity.setOrderLineId(6101L);
        entity.setProductId(4001L);
        entity.setQty(new BigDecimal("3.0000"));
        entity.setPrice(new BigDecimal("20.00"));
        entity.setTaxRate(new BigDecimal("0.1300"));
        entity.setAmount(new BigDecimal("60.00"));
        entity.setTaxAmount(new BigDecimal("7.80"));
        entity.setLotNo("LOT-A");
        entity.setProductionDate(LocalDate.of(2026, 5, 1));
        entity.setExpiryDate(LocalDate.of(2027, 5, 1));
        entity.setLocationId(3101L);
        entity.setSerialNos("SN-1\nSN-2\nSN-3");
        entity.setRemark("receipt detail line");
        return entity;
    }

    private PurchaseOrderEntity order() {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setId(ORDER_ID);
        entity.setCompanyId(CURRENT_USER.companyId());
        entity.setAccountBookId(CURRENT_USER.accountBookId());
        entity.setOrderNo("PO-6001");
        entity.setStatus("APPROVED");
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

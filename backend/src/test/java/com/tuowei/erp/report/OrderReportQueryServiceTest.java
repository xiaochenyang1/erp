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
import com.tuowei.erp.common.security.ScopedUserResolver;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.report.service.OrderReportQueryService;
import com.tuowei.erp.report.web.OrderReportResponse;
import com.tuowei.erp.report.web.PurchaseOrderReportQuery;
import com.tuowei.erp.report.web.SalesOrderReportQuery;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
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
class OrderReportQueryServiceTest {

    private static final CurrentUser CURRENT_USER = new CurrentUser(
            9201L,
            101L,
            202L,
            11L,
            12L,
            "order_report_user",
            "订单报表用户"
    );
    private static final DataScopeSnapshot SNAPSHOT =
            new DataScopeSnapshot(false, true, true, true, Set.of());
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
            SNAPSHOT
    );
    private static final Set<Long> DEPT_USERS = Set.of(9202L);
    private static final Set<Long> POST_USERS = Set.of(9203L);

    @Mock
    private PurchaseOrderMapper purchaseOrderMapper;
    @Mock
    private SalesOrderMapper salesOrderMapper;
    @Mock
    private CurrentUserContext currentUserContext;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private ScopedUserResolver scopedUserResolver;

    private OrderReportQueryService service;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(PurchaseOrderEntity.class);
        initTableInfo(SalesOrderEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new OrderReportQueryService(
                purchaseOrderMapper,
                salesOrderMapper,
                currentUserContext,
                dataScopeService,
                scopedUserResolver,
                new ReportProperties(3, 2)
        );
        when(currentUserContext.requireCurrentUser()).thenReturn(CURRENT_USER);
        when(currentUserContext.requirePrincipal()).thenReturn(PRINCIPAL);
        when(scopedUserResolver.resolve(CURRENT_USER, SNAPSHOT))
                .thenReturn(new ScopedUserResolver.ScopedUserIds(DEPT_USERS, POST_USERS));
    }

    @Test
    void listsPurchaseOrdersWithNormalizedFiltersScopePaginationAndMapping() {
        PurchaseOrderReportQuery query = new PurchaseOrderReportQuery();
        query.setPageNo(0);
        query.setPageSize(999);
        query.setSupplierId(301L);
        query.setOrderDateFrom(LocalDate.of(2026, 8, 1));
        query.setOrderDateTo(LocalDate.of(2026, 8, 31));
        query.setStatus(" approved ");
        query.setApprovalStatus(" pending ");
        query.setKeyword(" PO-001 ");
        when(dataScopeService.applyPurchaseOrderScope(
                any(), eq(CURRENT_USER), eq(SNAPSHOT), eq(DEPT_USERS), eq(POST_USERS)
        )).thenAnswer(invocation -> {
            LambdaQueryWrapper<PurchaseOrderEntity> wrapper = invocation.getArgument(0);
            return wrapper.eq(PurchaseOrderEntity::getCompanyId, CURRENT_USER.companyId())
                    .eq(PurchaseOrderEntity::getAccountBookId, CURRENT_USER.accountBookId())
                    .in(PurchaseOrderEntity::getCreatedBy, CURRENT_USER.userId(), 9202L, 9203L);
        });
        when(purchaseOrderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<PurchaseOrderEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(purchaseOrder(1L, "PO-001")));
            return page;
        });

        var response = service.listPurchaseOrders(query);

        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(200);
        assertThat(response.records()).singleElement().satisfies(record -> {
            assertThat(record.bizNo()).isEqualTo("PO-001");
            assertThat(record.partnerId()).isEqualTo(301L);
            assertThat(record.fulfillmentStatus()).isEqualTo("NOT_RECEIVED");
        });
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PurchaseOrderEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(purchaseOrderMapper).selectPage(any(), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("deleted_flag")
                .contains("supplier_id")
                .contains("order_date")
                .contains("status")
                .contains("approval_status")
                .contains("order_no")
                .contains("company_id")
                .contains("account_book_id")
                .contains("created_by");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains("APPROVED", "PENDING", "%PO-001%");
        verify(scopedUserResolver).resolve(CURRENT_USER, SNAPSHOT);
    }

    @Test
    void listsSalesOrdersWithNormalizedDeliveryStatusAndDataScope() {
        SalesOrderReportQuery query = new SalesOrderReportQuery();
        query.setCustomerId(401L);
        query.setStatus(" open ");
        query.setApprovalStatus(" approved ");
        query.setDeliveryStatus(" partial ");
        query.setKeyword(" SO-001 ");
        when(dataScopeService.applySalesOrderScope(
                any(), eq(CURRENT_USER), eq(SNAPSHOT), eq(DEPT_USERS), eq(POST_USERS)
        )).thenAnswer(invocation -> {
            LambdaQueryWrapper<SalesOrderEntity> wrapper = invocation.getArgument(0);
            return wrapper.eq(SalesOrderEntity::getCompanyId, CURRENT_USER.companyId())
                    .eq(SalesOrderEntity::getAccountBookId, CURRENT_USER.accountBookId());
        });
        when(salesOrderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<SalesOrderEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(salesOrder(2L, "SO-001")));
            return page;
        });

        var response = service.listSalesOrders(query);

        assertThat(response.records()).extracting(OrderReportResponse::bizNo).containsExactly("SO-001");
        assertThat(response.records()).extracting(OrderReportResponse::fulfillmentStatus).containsExactly("PARTIAL");
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<SalesOrderEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(salesOrderMapper).selectPage(any(), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT);
        assertThat(sql)
                .contains("customer_id")
                .contains("status")
                .contains("approval_status")
                .contains("delivery_status")
                .contains("order_no")
                .contains("company_id")
                .contains("account_book_id");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains("OPEN", "APPROVED", "PARTIAL", "%SO-001%");
        verify(dataScopeService).applySalesOrderScope(
                any(), eq(CURRENT_USER), eq(SNAPSHOT), eq(DEPT_USERS), eq(POST_USERS)
        );
    }

    @Test
    void enforcesSalesOrderExportLimitBeforeStreaming() {
        when(dataScopeService.applySalesOrderScope(
                any(), eq(CURRENT_USER), eq(SNAPSHOT), eq(DEPT_USERS), eq(POST_USERS)
        )).thenAnswer(invocation -> invocation.getArgument(0));
        when(salesOrderMapper.selectCount(any())).thenReturn(4L);

        assertThatThrownBy(() -> service.assertSalesOrderExportWithinLimit(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("导出结果超过3行，请缩小筛选范围后重试");
        verify(salesOrderMapper, never()).selectPage(any(), any());
    }

    @Test
    void streamsPurchaseOrderExportInConfiguredBatches() {
        when(dataScopeService.applyPurchaseOrderScope(
                any(), eq(CURRENT_USER), eq(SNAPSHOT), eq(DEPT_USERS), eq(POST_USERS)
        )).thenAnswer(invocation -> invocation.getArgument(0));
        when(purchaseOrderMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<PurchaseOrderEntity> page = invocation.getArgument(0);
            if (page.getCurrent() == 1) {
                page.setRecords(List.of(purchaseOrder(1L, "PO-001"), purchaseOrder(2L, "PO-002")));
            } else {
                page.setRecords(List.of(purchaseOrder(3L, "PO-003")));
            }
            return page;
        });

        List<OrderReportResponse> records = new ArrayList<>();
        service.streamPurchaseOrders(null, records::add);

        assertThat(records).extracting(OrderReportResponse::bizNo)
                .containsExactly("PO-001", "PO-002", "PO-003");
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Page<PurchaseOrderEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        verify(purchaseOrderMapper, org.mockito.Mockito.times(2)).selectPage(pageCaptor.capture(), any());
        assertThat(pageCaptor.getAllValues()).extracting(Page::getCurrent).containsExactly(1L, 2L);
        assertThat(pageCaptor.getAllValues()).extracting(Page::getSize).containsExactly(2L, 2L);
    }

    private PurchaseOrderEntity purchaseOrder(long id, String orderNo) {
        PurchaseOrderEntity entity = new PurchaseOrderEntity();
        entity.setId(id);
        entity.setOrderNo(orderNo);
        entity.setSupplierId(301L);
        entity.setOrderDate(LocalDate.of(2026, 8, 13));
        entity.setStatus("APPROVED");
        entity.setApprovalStatus("PENDING");
        entity.setReceiptStatus("NOT_RECEIVED");
        entity.setTotalQuantity(new BigDecimal("2.0000"));
        entity.setTotalAmount(new BigDecimal("20.00"));
        entity.setTotalTaxAmount(new BigDecimal("2.60"));
        return entity;
    }

    private SalesOrderEntity salesOrder(long id, String orderNo) {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setId(id);
        entity.setOrderNo(orderNo);
        entity.setCustomerId(401L);
        entity.setOrderDate(LocalDate.of(2026, 8, 13));
        entity.setStatus("OPEN");
        entity.setApprovalStatus("APPROVED");
        entity.setDeliveryStatus("PARTIAL");
        entity.setTotalQuantity(new BigDecimal("3.0000"));
        entity.setTotalAmount(new BigDecimal("30.00"));
        entity.setTotalTaxAmount(new BigDecimal("3.90"));
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

package com.tuowei.erp.dashboard;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.dashboard.service.OperationsDashboardPresentationService;
import com.tuowei.erp.dashboard.service.OperationsDashboardQueryService;
import com.tuowei.erp.dashboard.service.OperationsDashboardService;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.inventory.alert.service.InventoryAlertService;
import com.tuowei.erp.inventory.alert.web.InventoryLowStockResponse;
import com.tuowei.erp.purchase.order.mapper.PurchaseOrderMapper;
import com.tuowei.erp.purchase.order.model.PurchaseOrderEntity;
import com.tuowei.erp.sales.order.mapper.SalesOrderMapper;
import com.tuowei.erp.sales.order.model.SalesOrderEntity;
import com.tuowei.erp.sales.delivery.mapper.SalesDeliveryLineMapper;
import com.tuowei.erp.dashboard.web.OperationsDashboardTopSkuResponse;
import com.tuowei.erp.system.log.mapper.OperationLogMapper;
import com.tuowei.erp.system.log.model.OperationLogEntity;
import com.tuowei.erp.workflow.mapper.WorkflowTaskMapper;
import com.tuowei.erp.workflow.model.WorkflowTaskEntity;
import org.junit.jupiter.api.AfterEach;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationsDashboardServiceTest {

    private static final ResourceBundleMessageSource MESSAGE_SOURCE = messageSource();

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9001L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 30, 10, 0)
    );

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private WorkflowTaskMapper workflowTaskMapper;

    @Mock
    private InventoryAlertService inventoryAlertService;

    @Mock
    private ReceivableMapper receivableMapper;

    @Mock
    private PayableMapper payableMapper;

    @Mock
    private PurchaseOrderMapper purchaseOrderMapper;

    @Mock
    private SalesOrderMapper salesOrderMapper;

    @Mock
    private OperationLogMapper operationLogMapper;

    @Mock
    private SalesDeliveryLineMapper salesDeliveryLineMapper;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(WorkflowTaskEntity.class);
        initTableInfo(ReceivableEntity.class);
        initTableInfo(PayableEntity.class);
        initTableInfo(PurchaseOrderEntity.class);
        initTableInfo(SalesOrderEntity.class);
        initTableInfo(OperationLogEntity.class);
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void aggregatesMetricsTodosAndTenantScopedQueries() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(workflowTaskMapper.selectCount(any())).thenReturn(2L, 1L);
        when(workflowTaskMapper.selectList(any())).thenReturn(List.of(
                workflowTask(9101L, "PURCHASE_ORDER", "PO-001", LocalDateTime.of(2026, 6, 30, 9, 0)),
                workflowTask(9102L, "EXPENSE", "EX-001", LocalDateTime.of(2026, 6, 30, 8, 0))
        ));
        when(inventoryAlertService.listLowStock(null, null)).thenReturn(List.of(
                new InventoryLowStockResponse(1L, 601L, 701L, new BigDecimal("2.0000"), new BigDecimal("10.0000"), new BigDecimal("8.0000"), "主料不足"),
                new InventoryLowStockResponse(2L, 602L, 702L, new BigDecimal("1.0000"), new BigDecimal("5.0000"), new BigDecimal("4.0000"), "辅料不足")
        ));
        when(receivableMapper.selectCount(any())).thenReturn(3L);
        when(receivableMapper.selectList(any())).thenReturn(List.of(
                receivable(9201L, "AR-001", LocalDate.of(2026, 6, 20), new BigDecimal("1000.00"), new BigDecimal("200.00"))
        ));
        when(payableMapper.selectCount(any())).thenReturn(4L);
        when(payableMapper.selectList(any())).thenReturn(List.of(
                payable(9301L, "AP-001", LocalDate.of(2026, 6, 18), new BigDecimal("900.00"), new BigDecimal("100.00"))
        ));
        when(purchaseOrderMapper.selectCount(any())).thenReturn(5L);
        when(salesOrderMapper.selectList(any())).thenReturn(List.of(
                salesOrder(9401L, new BigDecimal("1200.00")),
                salesOrder(9402L, new BigDecimal("2000.00"))
        ));
        when(operationLogMapper.selectList(any())).thenReturn(List.of(
                failedOperation(9501L, "purchase", "post", "GR-001", LocalDateTime.of(2026, 6, 30, 9, 30))
        ));
        when(salesDeliveryLineMapper.selectTopSkus(101L, 202L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 5))
                .thenReturn(List.of(new OperationsDashboardTopSkuResponse(
                        701L, "SKU-001", "畅销商品", "件", new BigDecimal("12.0000"), new BigDecimal("3600.00"))));

        var response = service().getOperationsDashboard();

        assertThat(response.summary().pendingApprovals()).isEqualTo(2);
        assertThat(response.summary().overdueApprovals()).isEqualTo(1);
        assertThat(response.summary().lowStockAlerts()).isEqualTo(2);
        assertThat(response.summary().openReceivables()).isEqualTo(3);
        assertThat(response.summary().openPayables()).isEqualTo(4);
        assertThat(response.summary().todayPurchaseOrders()).isEqualTo(5);
        assertThat(response.summary().todaySalesAmount()).isEqualByComparingTo("3200.00");
        assertThat(response.todos()).hasSize(7);
        assertThat(response.todos())
                .anySatisfy(todo -> {
                    assertThat(todo.id()).isEqualTo("workflow-9101");
                    assertThat(todo.type()).isEqualTo("WORKFLOW");
                    assertThat(todo.route()).isEqualTo("/workflow/tasks?businessType=PURCHASE_ORDER&businessId=10101&status=PENDING");
                })
                .anySatisfy(todo -> {
                    assertThat(todo.type()).isEqualTo("RECEIVABLE_OVERDUE");
                    assertThat(todo.priority()).isEqualTo("HIGH");
                    assertThat(todo.route()).isEqualTo("/finance/receivables");
                })
                .anySatisfy(todo -> {
                    assertThat(todo.type()).isEqualTo("FAILED_OPERATION");
                    assertThat(todo.route()).isEqualTo("/system/logs");
                });
        assertThat(response.lowStock()).hasSize(2);
        assertThat(response.failedOperations()).hasSize(1);
        assertThat(response.topSkus()).singleElement().satisfies(sku -> {
            assertThat(sku.productCode()).isEqualTo("SKU-001");
            assertThat(sku.quantity()).isEqualByComparingTo("12.0000");
        });
        assertThat(response.generatedAt()).isEqualTo(LocalDateTime.of(2026, 6, 30, 10, 0));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<WorkflowTaskEntity>> workflowCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(workflowTaskMapper, org.mockito.Mockito.times(2)).selectCount(workflowCaptor.capture());
        assertThat(workflowCaptor.getAllValues().get(0).getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("approver_user_id")
                .contains("status");
        assertThat(workflowCaptor.getAllValues().get(1).getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("due_time")
                .contains("<");
    }

    @Test
    void capsTodosAtTwelveItems() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(workflowTaskMapper.selectCount(any())).thenReturn(15L);
        List<WorkflowTaskEntity> tasks = new ArrayList<>();
        for (long i = 1; i <= 15; i++) {
            tasks.add(workflowTask(9600L + i, "PURCHASE_ORDER", "PO-" + i, LocalDateTime.of(2026, 6, 30, 9, 0).minusMinutes(i)));
        }
        when(workflowTaskMapper.selectList(any())).thenReturn(tasks);
        when(inventoryAlertService.listLowStock(null, null)).thenReturn(List.of());
        when(receivableMapper.selectCount(any())).thenReturn(0L);
        when(receivableMapper.selectList(any())).thenReturn(List.of());
        when(payableMapper.selectCount(any())).thenReturn(0L);
        when(payableMapper.selectList(any())).thenReturn(List.of());
        when(purchaseOrderMapper.selectCount(any())).thenReturn(0L);
        when(salesOrderMapper.selectList(any())).thenReturn(List.of());
        when(operationLogMapper.selectList(any())).thenReturn(List.of());

        var response = service().getOperationsDashboard();

        assertThat(response.todos()).hasSize(12);
    }

    @Test
    void localizesTodoTextForEnglishLocale() {
        LocaleContextHolder.setLocale(Locale.US);
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(workflowTaskMapper.selectCount(any())).thenReturn(1L, 0L);
        when(workflowTaskMapper.selectList(any())).thenReturn(List.of(
                workflowTask(9101L, "PURCHASE_ORDER", "PO-001", LocalDateTime.of(2026, 6, 30, 9, 0))
        ));
        when(inventoryAlertService.listLowStock(null, null)).thenReturn(List.of(
                new InventoryLowStockResponse(1L, 601L, 701L, new BigDecimal("2.0000"), new BigDecimal("10.0000"), new BigDecimal("8.0000"), "main shortage")
        ));
        when(receivableMapper.selectCount(any())).thenReturn(1L);
        when(receivableMapper.selectList(any())).thenReturn(List.of(
                receivable(9201L, "AR-001", LocalDate.of(2026, 6, 20), new BigDecimal("1000.00"), new BigDecimal("200.00"))
        ));
        when(payableMapper.selectCount(any())).thenReturn(1L);
        when(payableMapper.selectList(any())).thenReturn(List.of(
                payable(9301L, "AP-001", LocalDate.of(2026, 6, 18), new BigDecimal("900.00"), new BigDecimal("100.00"))
        ));
        when(purchaseOrderMapper.selectCount(any())).thenReturn(0L);
        when(salesOrderMapper.selectList(any())).thenReturn(List.of());
        when(operationLogMapper.selectList(any())).thenReturn(List.of(
                failedOperation(9501L, "purchase", "post", "GR-001", LocalDateTime.of(2026, 6, 30, 9, 30))
        ));
        when(salesDeliveryLineMapper.selectTopSkus(101L, 202L, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), 5))
                .thenReturn(List.of());

        var response = service().getOperationsDashboard();

        assertThat(response.todos())
                .anySatisfy(todo -> {
                    if (!"WORKFLOW".equals(todo.type())) {
                        return;
                    }
                    assertThat(todo.title()).isEqualTo("Pending approval: PO-001");
                    assertThat(todo.description()).isEqualTo("Purchase order");
                })
                .anySatisfy(todo -> {
                    if (!"LOW_STOCK".equals(todo.type())) {
                        return;
                    }
                    assertThat(todo.title()).isEqualTo("Low stock alert: product 701");
                    assertThat(todo.description()).isEqualTo("Warehouse 601, shortage 8.0000");
                })
                .anySatisfy(todo -> {
                    if (!"RECEIVABLE_OVERDUE".equals(todo.type())) {
                        return;
                    }
                    assertThat(todo.title()).isEqualTo("Overdue receivable: AR-001");
                    assertThat(todo.description()).isEqualTo("Business date 2026-06-20, open amount 800.00");
                })
                .anySatisfy(todo -> {
                    if (!"PAYABLE_OVERDUE".equals(todo.type())) {
                        return;
                    }
                    assertThat(todo.title()).isEqualTo("Overdue payable: AP-001");
                    assertThat(todo.description()).isEqualTo("Business date 2026-06-18, open amount 800.00");
                })
                .anySatisfy(todo -> {
                    if (!"FAILED_OPERATION".equals(todo.type())) {
                        return;
                    }
                    assertThat(todo.title()).isEqualTo("Operation failed: GR-001");
                    assertThat(todo.description()).isEqualTo("/api/purchase/receipts/1/post");
                });
    }

    private OperationsDashboardService service() {
        OperationsDashboardQueryService queryService = new OperationsDashboardQueryService(
                auditMetadataFactory,
                workflowTaskMapper,
                inventoryAlertService,
                receivableMapper,
                payableMapper,
                purchaseOrderMapper,
                salesOrderMapper,
                operationLogMapper,
                salesDeliveryLineMapper,
                Clock.fixed(Instant.parse("2026-06-30T02:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );
        return new OperationsDashboardService(
                queryService,
                new OperationsDashboardPresentationService(MESSAGE_SOURCE)
        );
    }

    private static WorkflowTaskEntity workflowTask(Long id, String businessType, String businessNo, LocalDateTime createdTime) {
        WorkflowTaskEntity entity = new WorkflowTaskEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setBusinessType(businessType);
        entity.setBusinessId(id + 1000L);
        entity.setBusinessNo(businessNo);
        entity.setTitle(businessNo + " 待审批");
        entity.setApproverUserId(AUDIT.userId());
        entity.setStatus("PENDING");
        entity.setCreatedTime(createdTime);
        return entity;
    }

    private static ReceivableEntity receivable(Long id, String no, LocalDate bizDate, BigDecimal original, BigDecimal settled) {
        ReceivableEntity entity = new ReceivableEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setReceivableNo(no);
        entity.setSourceNo("SO-001");
        entity.setBizDate(bizDate);
        entity.setOriginalAmount(original);
        entity.setSettledAmount(settled);
        entity.setStatus("OPEN");
        entity.setDeletedFlag(0);
        return entity;
    }

    private static PayableEntity payable(Long id, String no, LocalDate bizDate, BigDecimal original, BigDecimal settled) {
        PayableEntity entity = new PayableEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setPayableNo(no);
        entity.setSourceNo("PO-001");
        entity.setBizDate(bizDate);
        entity.setOriginalAmount(original);
        entity.setSettledAmount(settled);
        entity.setStatus("OPEN");
        entity.setDeletedFlag(0);
        return entity;
    }

    private static SalesOrderEntity salesOrder(Long id, BigDecimal amount) {
        SalesOrderEntity entity = new SalesOrderEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setOrderNo("SO-" + id);
        entity.setOrderDate(LocalDate.of(2026, 6, 30));
        entity.setTotalAmount(amount);
        entity.setDeletedFlag(0);
        return entity;
    }

    private static OperationLogEntity failedOperation(Long id, String module, String operation, String bizNo, LocalDateTime operationTime) {
        OperationLogEntity entity = new OperationLogEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setModule(module);
        entity.setOperation(operation);
        entity.setBizNo(bizNo);
        entity.setResult("FAILURE");
        entity.setMessage("过账失败");
        entity.setRequestUri("/api/purchase/receipts/1/post");
        entity.setOperationTime(operationTime);
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

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }
}

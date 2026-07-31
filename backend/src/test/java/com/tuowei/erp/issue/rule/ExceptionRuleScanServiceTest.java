package com.tuowei.erp.issue.rule;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.inventory.alert.service.InventoryAlertService;
import com.tuowei.erp.inventory.alert.web.InventoryLowStockResponse;
import com.tuowei.erp.issue.mapper.ExceptionTicketMapper;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.issue.rule.mapper.ExceptionRuleHitMapper;
import com.tuowei.erp.issue.rule.mapper.ExceptionRuleMapper;
import com.tuowei.erp.issue.rule.model.ExceptionRuleEntity;
import com.tuowei.erp.issue.rule.model.ExceptionRuleHitEntity;
import com.tuowei.erp.issue.rule.service.ExceptionRuleScanService;
import com.tuowei.erp.issue.sla.service.ExceptionSlaPolicyService;
import com.tuowei.erp.issue.service.ExceptionTicketService;
import com.tuowei.erp.issue.web.ExceptionTicketCreateRequest;
import com.tuowei.erp.issue.web.ExceptionTicketResponse;
import com.tuowei.erp.system.log.mapper.OperationLogMapper;
import com.tuowei.erp.system.log.model.OperationLogEntity;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExceptionRuleScanServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9001L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 30, 10, 0)
    );
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-30T02:00:00Z"),
            ZoneId.of("Asia/Shanghai")
    );

    @Mock
    private ExceptionRuleMapper ruleMapper;
    @Mock
    private ExceptionRuleHitMapper hitMapper;
    @Mock
    private ExceptionTicketMapper ticketMapper;
    @Mock
    private ExceptionTicketService ticketService;
    @Mock
    private ExceptionSlaPolicyService slaPolicyService;
    @Mock
    private InventoryAlertService inventoryAlertService;
    @Mock
    private ReceivableMapper receivableMapper;
    @Mock
    private PayableMapper payableMapper;
    @Mock
    private OperationLogMapper operationLogMapper;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ExceptionRuleEntity.class);
        initTableInfo(ExceptionRuleHitEntity.class);
        initTableInfo(ExceptionTicketEntity.class);
        initTableInfo(PayableEntity.class);
        initTableInfo(OperationLogEntity.class);
    }

    @Test
    void scanPayableOverdueUsesTenantScopeAndCreatesMappedTicket() {
        ExceptionRuleEntity rule = rule("PAYABLE_OVERDUE", new BigDecimal("45"));
        when(payableMapper.selectList(any())).thenReturn(List.of(payable()));
        when(slaPolicyService.resolveDueTime(any(), any(), any(), any(AuditMetadata.class)))
                .thenReturn(AUDIT.now().plusHours(24));
        when(ticketService.create(any(ExceptionTicketCreateRequest.class), any(AuditMetadata.class)))
                .thenReturn(ticket(9003L));

        var result = service().scanRule(rule, AUDIT);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.hitCount()).isEqualTo(1);
        assertThat(result.ticketCreatedCount()).isEqualTo(1);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<PayableEntity>> payableQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(payableMapper).selectList(payableQueryCaptor.capture());
        LambdaQueryWrapper<PayableEntity> payableQuery = payableQueryCaptor.getValue();
        assertThat(payableQuery.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "deleted_flag", "status", "biz_date");
        assertThat(payableQuery.getParamNameValuePairs().values())
                .contains(
                        AUDIT.companyId(),
                        AUDIT.accountBookId(),
                        0,
                        "UNSETTLED",
                        "PARTIALLY_SETTLED",
                        LocalDate.of(2026, 5, 16)
                );

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ExceptionTicketEntity>> ticketQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(ticketMapper).selectOne(ticketQueryCaptor.capture());
        LambdaQueryWrapper<ExceptionTicketEntity> ticketQuery = ticketQueryCaptor.getValue();
        assertThat(ticketQuery.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "source_type", "source_id");
        assertThat(ticketQuery.getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), "PAYABLE_OVERDUE", 7201L);

        ArgumentCaptor<ExceptionTicketCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(ExceptionTicketCreateRequest.class);
        verify(ticketService).create(requestCaptor.capture(), any(AuditMetadata.class));
        ExceptionTicketCreateRequest request = requestCaptor.getValue();
        assertThat(request.getCategory()).isEqualTo("PAYMENT_OVERDUE");
        assertThat(request.getPriority()).isEqualTo("MEDIUM");
        assertThat(request.getSourceType()).isEqualTo("PAYABLE_OVERDUE");
        assertThat(request.getSourceId()).isEqualTo(7201L);
        assertThat(request.getSourceNo()).isEqualTo("AP-001");
        assertThat(request.getSourceRoute()).isEqualTo("/finance/payables?keyword=AP-001");
        assertThat(request.getDescription()).contains("45 天", "剩余金额 600");
        assertThat(request.getAssigneeUserId()).isEqualTo(9002L);
        assertThat(request.getDueTime()).isEqualTo(AUDIT.now().plusHours(24));
    }

    @Test
    void scanOperationFailuresUsesConfiguredWindowAndMapsTicket() {
        ExceptionRuleEntity rule = rule("OPERATION_FAILURE", new BigDecimal("90"));
        when(operationLogMapper.selectList(any())).thenReturn(List.of(operationLog()));
        when(slaPolicyService.resolveDueTime(any(), any(), any(), any(AuditMetadata.class)))
                .thenReturn(AUDIT.now().plusHours(8));
        when(ticketService.create(any(ExceptionTicketCreateRequest.class), any(AuditMetadata.class)))
                .thenReturn(ticket(9004L));

        var result = service().scanRule(rule, AUDIT);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.hitCount()).isEqualTo(1);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<OperationLogEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(operationLogMapper).selectList(queryCaptor.capture());
        LambdaQueryWrapper<OperationLogEntity> query = queryCaptor.getValue();
        assertThat(query.getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "result", "operation_time");
        assertThat(query.getParamNameValuePairs().values())
                .contains(
                        AUDIT.companyId(),
                        AUDIT.accountBookId(),
                        "FAILURE",
                        AUDIT.now().minusMinutes(90)
                );

        ArgumentCaptor<ExceptionTicketCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(ExceptionTicketCreateRequest.class);
        verify(ticketService).create(requestCaptor.capture(), any(AuditMetadata.class));
        ExceptionTicketCreateRequest request = requestCaptor.getValue();
        assertThat(request.getCategory()).isEqualTo("SYSTEM_ERROR");
        assertThat(request.getSourceType()).isEqualTo("OPERATION_FAILURE");
        assertThat(request.getSourceId()).isEqualTo(7301L);
        assertThat(request.getSourceNo()).isEqualTo("PO-001");
        assertThat(request.getSourceRoute()).isEqualTo("/system/logs?result=FAILURE&bizNo=PO-001");
        assertThat(request.getDescription()).contains("采购 / 审核", "审批服务不可用");

        ArgumentCaptor<ExceptionRuleHitEntity> hitCaptor = ArgumentCaptor.forClass(ExceptionRuleHitEntity.class);
        verify(hitMapper).insert(hitCaptor.capture());
        assertThat(hitCaptor.getValue().getTriggerValue()).isEqualTo("/api/purchase/orders/11/approve");
        assertThat(hitCaptor.getValue().getThresholdValue()).isEqualTo("90");
    }

    @Test
    void scanFailureReturnsFailedAndUpdatesRuleStatusAndNextRun() {
        ExceptionRuleEntity rule = rule("LOW_STOCK", BigDecimal.ZERO);
        rule.setScheduleIntervalMinutes(20);
        String failureMessage = "库存扫描失败".repeat(100);
        when(inventoryAlertService.listLowStock(null, null, AUDIT))
                .thenThrow(new IllegalStateException(failureMessage));

        var result = service().scanRule(rule, AUDIT);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.hitCount()).isZero();
        assertThat(result.ticketCreatedCount()).isZero();
        assertThat(result.message()).isEqualTo(failureMessage);

        ArgumentCaptor<ExceptionRuleEntity> ruleCaptor = ArgumentCaptor.forClass(ExceptionRuleEntity.class);
        verify(ruleMapper).updateById(ruleCaptor.capture());
        ExceptionRuleEntity updatedRule = ruleCaptor.getValue();
        assertThat(updatedRule.getLastScanTime()).isEqualTo(AUDIT.now());
        assertThat(updatedRule.getLastScanStatus()).isEqualTo("FAILED");
        assertThat(updatedRule.getLastHitCount()).isZero();
        assertThat(updatedRule.getLastTicketCreatedCount()).isZero();
        assertThat(updatedRule.getLastErrorMessage())
                .hasSize(512)
                .isEqualTo(failureMessage.substring(0, 512));
        assertThat(updatedRule.getNextScanTime()).isEqualTo(AUDIT.now().plusMinutes(20));
        assertThat(updatedRule.getUpdatedBy()).isEqualTo(AUDIT.userId());
        verify(ticketMapper, never()).selectOne(any());
        verify(hitMapper, never()).insert(any(ExceptionRuleHitEntity.class));
        verify(ticketService, never()).create(any(ExceptionTicketCreateRequest.class), any(AuditMetadata.class));
    }

    @Test
    void scanDueRulesUsesEachRuleTenantAndSystemAudit() {
        ExceptionRuleEntity dueRule = rule("LOW_STOCK", BigDecimal.ZERO);
        dueRule.setCompanyId(303L);
        dueRule.setAccountBookId(404L);
        dueRule.setScheduleIntervalMinutes(30);
        when(ruleMapper.selectDueRulesForScheduler(AUDIT.now())).thenReturn(List.of(dueRule));
        when(inventoryAlertService.listLowStock(isNull(), isNull(), any(AuditMetadata.class)))
                .thenReturn(List.of(lowStock()));
        when(slaPolicyService.resolveDueTime(any(), any(), any(), any(AuditMetadata.class)))
                .thenReturn(AUDIT.now().plusHours(12));
        when(ticketService.create(any(ExceptionTicketCreateRequest.class), any(AuditMetadata.class)))
                .thenReturn(ticket(9005L));

        var results = service().scanDueRules();

        assertThat(results).singleElement().satisfies(result -> assertThat(result.status()).isEqualTo("SUCCESS"));
        verify(ruleMapper).selectDueRulesForScheduler(AUDIT.now());
        ArgumentCaptor<AuditMetadata> inventoryAuditCaptor = ArgumentCaptor.forClass(AuditMetadata.class);
        verify(inventoryAlertService).listLowStock(isNull(), isNull(), inventoryAuditCaptor.capture());
        assertThat(inventoryAuditCaptor.getValue())
                .isEqualTo(new AuditMetadata(0L, 303L, 404L, AUDIT.now()));

        ArgumentCaptor<AuditMetadata> ticketAuditCaptor = ArgumentCaptor.forClass(AuditMetadata.class);
        verify(ticketService).create(any(ExceptionTicketCreateRequest.class), ticketAuditCaptor.capture());
        assertThat(ticketAuditCaptor.getValue()).isEqualTo(inventoryAuditCaptor.getValue());
        assertThat(dueRule.getNextScanTime()).isEqualTo(AUDIT.now().plusMinutes(30));
    }

    private ExceptionRuleScanService service() {
        return new ExceptionRuleScanService(
                ruleMapper,
                hitMapper,
                ticketMapper,
                ticketService,
                slaPolicyService,
                inventoryAlertService,
                receivableMapper,
                payableMapper,
                operationLogMapper,
                CLOCK
        );
    }

    private static ExceptionRuleEntity rule(String ruleType, BigDecimal threshold) {
        ExceptionRuleEntity entity = new ExceptionRuleEntity();
        entity.setId(1001L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setRuleCode(ruleType + "_DEFAULT");
        entity.setRuleName(ruleType);
        entity.setRuleType(ruleType);
        entity.setCategory(switch (ruleType) {
            case "PAYABLE_OVERDUE", "RECEIVABLE_OVERDUE" -> "PAYMENT_OVERDUE";
            case "OPERATION_FAILURE" -> "SYSTEM_ERROR";
            default -> "LOW_STOCK";
        });
        entity.setPriority("PAYABLE_OVERDUE".equals(ruleType) ? "MEDIUM" : "HIGH");
        entity.setThresholdValue(threshold);
        entity.setThresholdUnit("OPERATION_FAILURE".equals(ruleType) ? "MINUTES" : "DAYS");
        entity.setEnabled(1);
        entity.setAssigneeUserId(9002L);
        entity.setScheduleIntervalMinutes(60);
        entity.setDeletedFlag(0);
        entity.setCreatedBy(AUDIT.userId());
        entity.setCreatedTime(AUDIT.now());
        entity.setUpdatedBy(AUDIT.userId());
        entity.setUpdatedTime(AUDIT.now());
        entity.setVersion(0);
        return entity;
    }

    private static PayableEntity payable() {
        PayableEntity entity = new PayableEntity();
        entity.setId(7201L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setPayableNo("AP-001");
        entity.setSourceType("PURCHASE_ORDER");
        entity.setSourceId(6201L);
        entity.setSourceNo("PO-001");
        entity.setBizDate(LocalDate.of(2026, 5, 1));
        entity.setOriginalAmount(new BigDecimal("800"));
        entity.setSettledAmount(new BigDecimal("200"));
        entity.setStatus("PARTIALLY_SETTLED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private static OperationLogEntity operationLog() {
        OperationLogEntity entity = new OperationLogEntity();
        entity.setId(7301L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setModule("采购");
        entity.setOperation("审核");
        entity.setBizNo("PO-001");
        entity.setResult("FAILURE");
        entity.setMessage("审批服务不可用");
        entity.setRequestUri("/api/purchase/orders/11/approve");
        entity.setOperationTime(AUDIT.now().minusMinutes(15));
        return entity;
    }

    private static InventoryLowStockResponse lowStock() {
        return new InventoryLowStockResponse(
                7001L,
                11L,
                22L,
                new BigDecimal("3"),
                new BigDecimal("10"),
                new BigDecimal("7"),
                "主仓原料低库存"
        );
    }

    private static ExceptionTicketResponse ticket(Long id) {
        return new ExceptionTicketResponse(
                id,
                "ET-20260630-0001",
                "LOW_STOCK",
                "HIGH",
                "库存低于安全线",
                "当前库存 3，安全库存 10",
                "LOW_STOCK",
                7001L,
                "W:11/P:22",
                "/inventory/alerts?warehouseId=11&productId=22",
                true,
                "W:11/P:22",
                "/reports/traces?keyword=W%3A11%2FP%3A22",
                "OPEN",
                9002L,
                LocalDateTime.of(2026, 7, 1, 10, 0),
                null,
                null,
                null,
                AUDIT.userId(),
                AUDIT.now(),
                AUDIT.now(),
                List.of()
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

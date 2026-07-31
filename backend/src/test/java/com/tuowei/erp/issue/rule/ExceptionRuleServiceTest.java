package com.tuowei.erp.issue.rule;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.finance.payable.mapper.PayableMapper;
import com.tuowei.erp.finance.payable.model.PayableEntity;
import com.tuowei.erp.finance.receivable.mapper.ReceivableMapper;
import com.tuowei.erp.finance.receivable.model.ReceivableEntity;
import com.tuowei.erp.inventory.alert.service.InventoryAlertService;
import com.tuowei.erp.inventory.alert.web.InventoryLowStockResponse;
import com.tuowei.erp.issue.mapper.ExceptionTicketMapper;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.issue.rule.mapper.ExceptionRuleHitMapper;
import com.tuowei.erp.issue.rule.mapper.ExceptionRuleMapper;
import com.tuowei.erp.issue.rule.model.ExceptionRuleEntity;
import com.tuowei.erp.issue.rule.model.ExceptionRuleHitEntity;
import com.tuowei.erp.issue.rule.service.ExceptionRuleScanService;
import com.tuowei.erp.issue.rule.service.ExceptionRuleService;
import com.tuowei.erp.issue.rule.web.ExceptionRulePageQuery;
import com.tuowei.erp.issue.rule.web.ExceptionRuleUpdateRequest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExceptionRuleServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9001L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 30, 10, 0)
    );

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

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
        initTableInfo(ReceivableEntity.class);
        initTableInfo(PayableEntity.class);
        initTableInfo(OperationLogEntity.class);
    }

    @Test
    void listsRulesWithTenantScopedFilters() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        ExceptionRulePageQuery query = new ExceptionRulePageQuery();
        query.setKeyword("逾期");
        query.setRuleType("receivable_overdue");
        query.setEnabled(true);
        query.setPageNo(1);
        query.setPageSize(20);
        when(ruleMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<ExceptionRuleEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(rule("RECEIVABLE_OVERDUE", 1)));
            return page;
        });

        var response = service().list(query);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records().get(0).ruleType()).isEqualTo("RECEIVABLE_OVERDUE");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ExceptionRuleEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(ruleMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("rule_type")
                .contains("enabled");
    }

    @Test
    void listBootstrapsBuiltInRulesForTenantWhenMissing() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(ruleMapper.selectCount(any())).thenReturn(0L);
        when(ruleMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<ExceptionRuleEntity> page = invocation.getArgument(0);
            page.setTotal(0);
            page.setRecords(List.of());
            return page;
        });

        service().list(new ExceptionRulePageQuery());

        ArgumentCaptor<ExceptionRuleEntity> entityCaptor = ArgumentCaptor.forClass(ExceptionRuleEntity.class);
        verify(ruleMapper, times(4)).insert(entityCaptor.capture());
        assertThat(entityCaptor.getAllValues())
                .extracting(ExceptionRuleEntity::getRuleCode)
                .containsExactly(
                        "LOW_STOCK_DEFAULT",
                        "RECEIVABLE_OVERDUE_DEFAULT",
                        "PAYABLE_OVERDUE_DEFAULT",
                        "OPERATION_FAILURE_DEFAULT"
                );
        assertThat(entityCaptor.getAllValues())
                .allSatisfy(rule -> {
                    assertThat(rule.getCompanyId()).isEqualTo(AUDIT.companyId());
                    assertThat(rule.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
                    assertThat(rule.getEnabled()).isEqualTo(1);
                    assertThat(rule.getDeletedFlag()).isZero();
                });
    }

    @Test
    void updatesRuleConfigurationAndAuditFields() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(ruleMapper.selectOne(any())).thenReturn(rule("RECEIVABLE_OVERDUE", 1));
        ExceptionRuleUpdateRequest request = new ExceptionRuleUpdateRequest();
        request.setThresholdValue(new BigDecimal("45"));
        request.setThresholdUnit("DAYS");
        request.setPriority("URGENT");
        request.setAssigneeUserId(9002L);
        request.setScheduleIntervalMinutes(120);
        request.setRemark("超过 45 天未结清");

        var response = service().update(1001L, request);

        assertThat(response.thresholdValue()).isEqualByComparingTo("45");
        assertThat(response.priority()).isEqualTo("URGENT");
        assertThat(response.scheduleIntervalMinutes()).isEqualTo(120);
        ArgumentCaptor<ExceptionRuleEntity> entityCaptor = ArgumentCaptor.forClass(ExceptionRuleEntity.class);
        verify(ruleMapper).updateById(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(entityCaptor.getValue().getUpdatedTime()).isEqualTo(AUDIT.now());
    }

    @Test
    void rejectsScheduleIntervalOutsideSupportedRange() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(ruleMapper.selectOne(any())).thenReturn(rule("RECEIVABLE_OVERDUE", 1));
        ExceptionRuleUpdateRequest request = new ExceptionRuleUpdateRequest();
        request.setScheduleIntervalMinutes(4);

        assertThatThrownBy(() -> service().update(1001L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("扫描间隔");
    }

    @Test
    void scanAllDelegatesEnabledTenantRulesAndAuditToScanService() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(ruleMapper.selectCount(any())).thenReturn(1L);
        List<ExceptionRuleEntity> rules = List.of(
                rule("LOW_STOCK", 1),
                rule("PAYABLE_OVERDUE", 1)
        );
        when(ruleMapper.selectList(any())).thenReturn(rules);
        ExceptionRuleScanService scanService = mock(ExceptionRuleScanService.class);
        when(scanService.scanRules(rules, AUDIT)).thenReturn(List.of());
        ExceptionRuleService facade = new ExceptionRuleService(
                auditMetadataFactory,
                ruleMapper,
                hitMapper,
                scanService
        );

        assertThat(facade.scanAll()).isEmpty();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ExceptionRuleEntity>> queryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(ruleMapper).selectList(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id", "account_book_id", "deleted_flag", "enabled");
        assertThat(queryCaptor.getValue().getParamNameValuePairs().values())
                .contains(AUDIT.companyId(), AUDIT.accountBookId(), 0, 1);
        verify(scanService).scanRules(rules, AUDIT);
    }

    @Test
    void scanLowStockCreatesHitAndTicket() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(ruleMapper.selectOne(any())).thenReturn(rule("LOW_STOCK", 1));
        when(inventoryAlertService.listLowStock(null, null, AUDIT)).thenReturn(List.of(new InventoryLowStockResponse(
                7001L,
                11L,
                22L,
                new BigDecimal("3"),
                new BigDecimal("10"),
                new BigDecimal("7"),
                "主仓原料低库存"
        )));
        when(hitMapper.selectOne(any())).thenReturn(null);
        when(ticketMapper.selectOne(any())).thenReturn(null);
        when(slaPolicyService.resolveDueTime("LOW_STOCK", "HIGH", AUDIT.now(), AUDIT))
                .thenReturn(AUDIT.now().plusHours(12));
        when(ticketService.create(any(ExceptionTicketCreateRequest.class), any(AuditMetadata.class))).thenReturn(ticket(9001L));

        var result = service().scanRule(1001L);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.hitCount()).isEqualTo(1);
        assertThat(result.ticketCreatedCount()).isEqualTo(1);
        assertThat(result.duplicateTicketCount()).isZero();

        ArgumentCaptor<ExceptionRuleHitEntity> hitCaptor = ArgumentCaptor.forClass(ExceptionRuleHitEntity.class);
        verify(hitMapper).insert(hitCaptor.capture());
        assertThat(hitCaptor.getValue().getRuleId()).isEqualTo(1001L);
        assertThat(hitCaptor.getValue().getRuleType()).isEqualTo("LOW_STOCK");
        assertThat(hitCaptor.getValue().getTicketId()).isEqualTo(9001L);

        ArgumentCaptor<ExceptionTicketCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(ExceptionTicketCreateRequest.class);
        verify(ticketService).create(requestCaptor.capture(), any(AuditMetadata.class));
        assertThat(requestCaptor.getValue().getCategory()).isEqualTo("LOW_STOCK");
        assertThat(requestCaptor.getValue().getSourceType()).isEqualTo("LOW_STOCK");
        assertThat(requestCaptor.getValue().getTitle()).contains("库存");
        assertThat(requestCaptor.getValue().getDueTime()).isEqualTo(AUDIT.now().plusHours(12));

        ArgumentCaptor<ExceptionRuleEntity> ruleCaptor = ArgumentCaptor.forClass(ExceptionRuleEntity.class);
        verify(ruleMapper, times(1)).updateById(ruleCaptor.capture());
        assertThat(ruleCaptor.getValue().getNextScanTime()).isEqualTo(AUDIT.now().plusMinutes(60));
    }

    @Test
    void scanReceivableOverdueCreatesTicketFromUnsettledReceivable() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(ruleMapper.selectOne(any())).thenReturn(rule("RECEIVABLE_OVERDUE", 1));
        when(receivableMapper.selectList(any())).thenReturn(List.of(receivable()));
        when(hitMapper.selectOne(any())).thenReturn(null);
        when(ticketMapper.selectOne(any())).thenReturn(null);
        when(slaPolicyService.resolveDueTime(any(), any(), any(), any(AuditMetadata.class)))
                .thenReturn(AUDIT.now().plusHours(18));
        when(ticketService.create(any(ExceptionTicketCreateRequest.class), any(AuditMetadata.class))).thenReturn(ticket(9002L));

        var result = service().scanRule(1001L);

        assertThat(result.hitCount()).isEqualTo(1);
        assertThat(result.ticketCreatedCount()).isEqualTo(1);
        ArgumentCaptor<ExceptionTicketCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(ExceptionTicketCreateRequest.class);
        verify(ticketService).create(requestCaptor.capture(), any(AuditMetadata.class));
        assertThat(requestCaptor.getValue().getSourceType()).isEqualTo("RECEIVABLE_OVERDUE");
        assertThat(requestCaptor.getValue().getSourceNo()).isEqualTo("AR-001");
        assertThat(requestCaptor.getValue().getDueTime()).isEqualTo(AUDIT.now().plusHours(18));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ReceivableEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(receivableMapper).selectList(wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("biz_date")
                .contains("status");
    }

    @Test
    void scanDeduplicatesExistingActiveTicket() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(ruleMapper.selectOne(any())).thenReturn(rule("LOW_STOCK", 1));
        when(inventoryAlertService.listLowStock(null, null, AUDIT)).thenReturn(List.of(new InventoryLowStockResponse(
                7001L,
                11L,
                22L,
                new BigDecimal("3"),
                new BigDecimal("10"),
                new BigDecimal("7"),
                "主仓原料低库存"
        )));
        when(hitMapper.selectOne(any())).thenReturn(null);
        when(ticketMapper.selectOne(any())).thenReturn(activeTicket(8001L));

        var result = service().scanRule(1001L);

        assertThat(result.ticketCreatedCount()).isZero();
        assertThat(result.duplicateTicketCount()).isEqualTo(1);
        verify(ticketService, never()).create(any());
        verify(ticketService, never()).create(any(ExceptionTicketCreateRequest.class), any(AuditMetadata.class));

        ArgumentCaptor<ExceptionRuleHitEntity> hitCaptor = ArgumentCaptor.forClass(ExceptionRuleHitEntity.class);
        verify(hitMapper).insert(hitCaptor.capture());
        assertThat(hitCaptor.getValue().getTicketId()).isEqualTo(8001L);
    }

    @Test
    void scanDueRulesUsesRuleTenantAndSystemAuditWithoutCurrentUser() {
        ExceptionRuleEntity dueRule = rule("RECEIVABLE_OVERDUE", 1);
        dueRule.setScheduleIntervalMinutes(30);
        dueRule.setNextScanTime(AUDIT.now().minusMinutes(1));
        when(ruleMapper.selectDueRulesForScheduler(AUDIT.now())).thenReturn(List.of(dueRule));
        when(receivableMapper.selectList(any())).thenReturn(List.of(receivable()));
        when(hitMapper.selectOne(any())).thenReturn(null);
        when(ticketMapper.selectOne(any())).thenReturn(null);
        when(slaPolicyService.resolveDueTime(any(), any(), any(), any(AuditMetadata.class)))
                .thenReturn(AUDIT.now().plusHours(18));
        when(ticketService.create(any(ExceptionTicketCreateRequest.class), any(AuditMetadata.class))).thenReturn(ticket(9002L));

        var results = service().scanDueRules();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo("SUCCESS");
        verify(ruleMapper).selectDueRulesForScheduler(AUDIT.now());
        verify(ruleMapper, never()).selectList(any());
        verify(auditMetadataFactory, never()).current();
        ArgumentCaptor<AuditMetadata> auditCaptor = ArgumentCaptor.forClass(AuditMetadata.class);
        verify(ticketService).create(any(ExceptionTicketCreateRequest.class), auditCaptor.capture());
        assertThat(auditCaptor.getValue().userId()).isZero();
        assertThat(auditCaptor.getValue().companyId()).isEqualTo(AUDIT.companyId());
        assertThat(auditCaptor.getValue().accountBookId()).isEqualTo(AUDIT.accountBookId());

        ArgumentCaptor<ExceptionRuleEntity> ruleCaptor = ArgumentCaptor.forClass(ExceptionRuleEntity.class);
        verify(ruleMapper).updateById(ruleCaptor.capture());
        assertThat(ruleCaptor.getValue().getNextScanTime()).isEqualTo(AUDIT.now().plusMinutes(30));
    }

    @Test
    void schedulerDueRuleQueryBypassesTenantInterceptor() throws NoSuchMethodException {
        InterceptorIgnore annotation = ExceptionRuleMapper.class
                .getMethod("selectDueRulesForScheduler", LocalDateTime.class)
                .getAnnotation(InterceptorIgnore.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.tenantLine()).isEqualTo("true");
    }

    @Test
    void rejectsScanningDisabledRule() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(ruleMapper.selectOne(any())).thenReturn(rule("LOW_STOCK", 0));

        assertThatThrownBy(() -> service().scanRule(1001L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("已停用");
    }

    private ExceptionRuleService service() {
        ExceptionRuleScanService scanService = new ExceptionRuleScanService(
                ruleMapper,
                hitMapper,
                ticketMapper,
                ticketService,
                slaPolicyService,
                inventoryAlertService,
                receivableMapper,
                payableMapper,
                operationLogMapper,
                Clock.fixed(Instant.parse("2026-06-30T02:00:00Z"), ZoneId.of("Asia/Shanghai"))
        );
        return new ExceptionRuleService(
                auditMetadataFactory,
                ruleMapper,
                hitMapper,
                scanService
        );
    }

    private static ExceptionRuleEntity rule(String ruleType, Integer enabled) {
        ExceptionRuleEntity entity = new ExceptionRuleEntity();
        entity.setId(1001L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setRuleCode(ruleType + "_DEFAULT");
        entity.setRuleName(switch (ruleType) {
            case "LOW_STOCK" -> "低库存";
            case "RECEIVABLE_OVERDUE" -> "应收逾期";
            case "PAYABLE_OVERDUE" -> "应付逾期";
            case "OPERATION_FAILURE" -> "失败操作日志";
            default -> ruleType;
        });
        entity.setRuleType(ruleType);
        entity.setCategory("RECEIVABLE_OVERDUE".equals(ruleType) || "PAYABLE_OVERDUE".equals(ruleType)
                ? "PAYMENT_OVERDUE" : ruleType);
        entity.setPriority("HIGH");
        entity.setThresholdValue(new BigDecimal("30"));
        entity.setThresholdUnit("RECEIVABLE_OVERDUE".equals(ruleType) || "PAYABLE_OVERDUE".equals(ruleType) ? "DAYS" : "QTY");
        entity.setEnabled(enabled);
        entity.setAssigneeUserId(9002L);
        entity.setScheduleIntervalMinutes(60);
        entity.setRemark("规则说明");
        entity.setDeletedFlag(0);
        entity.setCreatedBy(AUDIT.userId());
        entity.setCreatedTime(AUDIT.now());
        entity.setUpdatedBy(AUDIT.userId());
        entity.setUpdatedTime(AUDIT.now());
        entity.setVersion(0);
        return entity;
    }

    private static ReceivableEntity receivable() {
        ReceivableEntity entity = new ReceivableEntity();
        entity.setId(7101L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setReceivableNo("AR-001");
        entity.setSourceType("SALES_ORDER");
        entity.setSourceId(6101L);
        entity.setSourceNo("SO-001");
        entity.setBizDate(LocalDate.of(2026, 5, 1));
        entity.setOriginalAmount(new BigDecimal("1000"));
        entity.setSettledAmount(new BigDecimal("200"));
        entity.setStatus("PARTIALLY_SETTLED");
        entity.setDeletedFlag(0);
        return entity;
    }

    private static ExceptionTicketEntity activeTicket(Long id) {
        ExceptionTicketEntity entity = new ExceptionTicketEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setSourceType("LOW_STOCK");
        entity.setSourceId(7001L);
        entity.setStatus("OPEN");
        entity.setDeletedFlag(0);
        return entity;
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

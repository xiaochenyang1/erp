package com.tuowei.erp.system.readiness;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.readiness.mapper.ReadinessEvidenceMapper;
import com.tuowei.erp.system.readiness.mapper.ReadinessItemMapper;
import com.tuowei.erp.system.readiness.mapper.ReadinessRunMapper;
import com.tuowei.erp.system.readiness.model.ReadinessEvidenceEntity;
import com.tuowei.erp.system.readiness.model.ReadinessItemEntity;
import com.tuowei.erp.system.readiness.model.ReadinessRunEntity;
import com.tuowei.erp.system.readiness.service.ReadinessCommandService;
import com.tuowei.erp.system.readiness.service.ReadinessQueryService;
import com.tuowei.erp.system.readiness.web.ReadinessDecisionRequest;
import com.tuowei.erp.system.readiness.web.ReadinessEvidenceCreateRequest;
import com.tuowei.erp.system.readiness.web.ReadinessItemCreateRequest;
import com.tuowei.erp.system.readiness.web.ReadinessItemResultRequest;
import com.tuowei.erp.system.readiness.web.ReadinessPreflightItemResponse;
import com.tuowei.erp.system.readiness.web.ReadinessPreflightResponse;
import com.tuowei.erp.system.readiness.web.ReadinessRunCreateRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadinessCommandServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9001L,
            101L,
            202L,
            LocalDateTime.of(2026, 8, 20, 14, 15, 16, 123_000_000)
    );
    private static final Long RUN_ID = 7101L;
    private static final Long ITEM_ID = 7201L;
    private static final Long EVIDENCE_ID = 7301L;
    private static final List<String> DEFAULT_ITEM_CODES = List.of(
            "RELEASE_GATE",
            "DOCKER_COMPOSE_HEALTH",
            "AUTH_SMOKE",
            "PURCHASE_TO_PAYMENT",
            "SALES_TO_RECEIPT",
            "FINANCE_LEDGER",
            "PERIOD_LOCK",
            "INVENTORY_FINANCE_RECONCILIATION",
            "PRODUCTION_MANUFACTURING",
            "INITIAL_IMPORT",
            "BACKUP_ROLLBACK",
            "PREPROD_APPROVAL_GATE"
    );

    @Mock
    private ReadinessRunMapper runMapper;
    @Mock
    private ReadinessItemMapper itemMapper;
    @Mock
    private ReadinessEvidenceMapper evidenceMapper;
    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ReadinessRunEntity.class);
        initTableInfo(ReadinessItemEntity.class);
        initTableInfo(ReadinessEvidenceEntity.class);
    }

    @BeforeEach
    void setUp() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void createRunNormalizesFieldsAndPersistsDefaultItemsInChecklistOrder() {
        AtomicReference<String> statusAtInsert = new AtomicReference<>();
        when(runMapper.insert(any(ReadinessRunEntity.class))).thenAnswer(invocation -> {
            ReadinessRunEntity run = invocation.getArgument(0);
            statusAtInsert.set(run.getStatus());
            run.setId(RUN_ID);
            return 1;
        });
        AtomicLong itemId = new AtomicLong(ITEM_ID);
        when(itemMapper.insert(any(ReadinessItemEntity.class))).thenAnswer(invocation -> {
            ReadinessItemEntity item = invocation.getArgument(0);
            item.setId(itemId.getAndIncrement());
            return 1;
        });

        var response = service().createRun(new ReadinessRunCreateRequest(
                " abc123 ",
                " 1.2.0-rc1 ",
                " preprod ",
                " mysql-preprod ",
                " ",
                " core ",
                true,
                false,
                " release candidate "
        ));

        assertThat(statusAtInsert.get()).isEqualTo("DRAFT");
        assertThat(response.id()).isEqualTo(RUN_ID);
        assertThat(response.runNo()).isEqualTo("RDY20260820141516123");
        assertThat(response.releaseCommit()).isEqualTo("abc123");
        assertThat(response.releaseVersion()).isEqualTo("1.2.0-rc1");
        assertThat(response.environment()).isEqualTo("PREPROD");
        assertThat(response.databaseInstance()).isEqualTo("mysql-preprod");
        assertThat(response.redisInstance()).isNull();
        assertThat(response.dockerProfile()).isEqualTo("core");
        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.decision()).isEqualTo("PENDING");
        assertThat(response.remark()).isEqualTo("release candidate");

        ArgumentCaptor<ReadinessRunEntity> runCaptor = ArgumentCaptor.forClass(ReadinessRunEntity.class);
        verify(runMapper).insert(runCaptor.capture());
        ReadinessRunEntity run = runCaptor.getValue();
        assertThat(run.getCompanyId()).isEqualTo(AUDIT.companyId());
        assertThat(run.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
        assertThat(run.getStartedBy()).isEqualTo(AUDIT.userId());
        assertThat(run.getStartedTime()).isEqualTo(AUDIT.now());
        assertThat(run.getDeletedFlag()).isZero();
        assertThat(run.getVersion()).isZero();

        ArgumentCaptor<ReadinessItemEntity> itemCaptor = ArgumentCaptor.forClass(ReadinessItemEntity.class);
        verify(itemMapper, times(DEFAULT_ITEM_CODES.size())).insert(itemCaptor.capture());
        List<ReadinessItemEntity> items = itemCaptor.getAllValues();
        assertThat(items).extracting(ReadinessItemEntity::getItemCode).containsExactlyElementsOf(DEFAULT_ITEM_CODES);
        assertThat(items).extracting(ReadinessItemEntity::getPriority)
                .containsExactly("P0", "P0", "P0", "P0", "P0", "P0", "P0", "P0", "P1", "P1", "P1", "P0");
        assertThat(items).allSatisfy(item -> {
            assertThat(item.getRunId()).isEqualTo(RUN_ID);
            assertThat(item.getStatus()).isEqualTo("PENDING");
            assertThat(item.getCompanyId()).isEqualTo(AUDIT.companyId());
            assertThat(item.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
            assertThat(item.getCreatedBy()).isEqualTo(AUDIT.userId());
            assertThat(item.getCreatedTime()).isEqualTo(AUDIT.now());
            assertThat(item.getVersion()).isZero();
        });

        InOrder order = inOrder(runMapper, itemMapper);
        order.verify(runMapper).insert(any(ReadinessRunEntity.class));
        order.verify(itemMapper, times(DEFAULT_ITEM_CODES.size())).insert(any(ReadinessItemEntity.class));
        order.verify(runMapper).updateById(run);
    }

    @Test
    void addItemNormalizesCodesAndOpensDraftAfterItemInsert() {
        ReadinessRunEntity run = run("DRAFT");
        when(runMapper.selectOne(any())).thenReturn(run);
        when(itemMapper.insert(any(ReadinessItemEntity.class))).thenAnswer(invocation -> {
            ReadinessItemEntity item = invocation.getArgument(0);
            item.setId(ITEM_ID);
            return 1;
        });

        var response = service().addItem(RUN_ID, new ReadinessItemCreateRequest(
                " auth_smoke ",
                " 登录冒烟 ",
                " auth ",
                " p1 ",
                " 登录成功 "
        ));

        assertThat(response.id()).isEqualTo(ITEM_ID);
        assertThat(response.itemCode()).isEqualTo("AUTH_SMOKE");
        assertThat(response.itemName()).isEqualTo("登录冒烟");
        assertThat(response.category()).isEqualTo("AUTH");
        assertThat(response.priority()).isEqualTo("P1");
        assertThat(response.expectedResult()).isEqualTo("登录成功");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.evidence()).isEmpty();
        assertThat(run.getStatus()).isEqualTo("IN_PROGRESS");

        ArgumentCaptor<ReadinessItemEntity> itemCaptor = ArgumentCaptor.forClass(ReadinessItemEntity.class);
        verify(itemMapper).insert(itemCaptor.capture());
        ReadinessItemEntity item = itemCaptor.getValue();
        assertThat(item.getRunId()).isEqualTo(RUN_ID);
        assertThat(item.getCreatedBy()).isEqualTo(AUDIT.userId());
        assertThat(item.getUpdatedTime()).isEqualTo(AUDIT.now());

        InOrder order = inOrder(runMapper, itemMapper);
        order.verify(runMapper).selectOne(any());
        order.verify(itemMapper).insert(item);
        order.verify(runMapper).updateById(run);
    }

    @Test
    void addEvidenceLoadsItemThenRunAndNormalizesCodeFieldsBeforeInsert() {
        ReadinessRunEntity run = run("IN_PROGRESS");
        ReadinessItemEntity item = item("P1", "PENDING");
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(runMapper.selectOne(any())).thenReturn(run);
        when(evidenceMapper.insert(any(ReadinessEvidenceEntity.class))).thenAnswer(invocation -> {
            ReadinessEvidenceEntity evidence = invocation.getArgument(0);
            evidence.setId(EVIDENCE_ID);
            return 1;
        });

        var response = service().addEvidence(ITEM_ID, new ReadinessEvidenceCreateRequest(
                " log ",
                " get ",
                " /api/system/profile ",
                200,
                " auth ",
                8101L,
                " AUTH-001 ",
                " profile endpoint ok ",
                " ",
                " readiness_report ",
                8201L
        ));

        assertThat(response.id()).isEqualTo(EVIDENCE_ID);
        assertThat(response.runId()).isEqualTo(RUN_ID);
        assertThat(response.itemId()).isEqualTo(ITEM_ID);
        assertThat(response.evidenceType()).isEqualTo("LOG");
        assertThat(response.requestMethod()).isEqualTo("GET");
        assertThat(response.requestUri()).isEqualTo("/api/system/profile");
        assertThat(response.businessType()).isEqualTo("AUTH");
        assertThat(response.businessNo()).isEqualTo("AUTH-001");
        assertThat(response.summary()).isEqualTo("profile endpoint ok");
        assertThat(response.detail()).isNull();
        assertThat(response.attachmentBusinessType()).isEqualTo("READINESS_REPORT");
        assertThat(response.recordedBy()).isEqualTo(AUDIT.userId());
        assertThat(response.recordedTime()).isEqualTo(AUDIT.now());

        InOrder order = inOrder(itemMapper, runMapper, evidenceMapper);
        order.verify(itemMapper).selectOne(any());
        order.verify(runMapper).selectOne(any());
        order.verify(evidenceMapper).insert(any(ReadinessEvidenceEntity.class));
    }

    @Test
    void markItemResultPersistsNormalizedResultBeforeReloadingEvidence() {
        ReadinessRunEntity run = run("IN_PROGRESS");
        ReadinessItemEntity item = item("P1", "PENDING");
        ReadinessEvidenceEntity evidence = evidence();
        when(itemMapper.selectOne(any())).thenReturn(item);
        when(runMapper.selectOne(any())).thenReturn(run);
        when(evidenceMapper.selectList(any())).thenReturn(List.of(evidence));

        var response = service().markItemResult(ITEM_ID, new ReadinessItemResultRequest(
                " passed ",
                " all checks passed ",
                " "
        ));

        assertThat(response.status()).isEqualTo("PASSED");
        assertThat(response.actualResult()).isEqualTo("all checks passed");
        assertThat(response.failureReason()).isNull();
        assertThat(response.executedBy()).isEqualTo(AUDIT.userId());
        assertThat(response.executedTime()).isEqualTo(AUDIT.now());
        assertThat(response.evidence()).singleElement().satisfies(value ->
                assertThat(value.id()).isEqualTo(EVIDENCE_ID));
        assertThat(item.getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(item.getUpdatedTime()).isEqualTo(AUDIT.now());

        InOrder order = inOrder(itemMapper, runMapper, evidenceMapper);
        order.verify(itemMapper).selectOne(any());
        order.verify(runMapper).selectOne(any());
        order.verify(itemMapper).updateById(item);
        order.verify(evidenceMapper).selectList(any());
    }

    @Test
    void markItemResultRejectsSkippingP0BeforeAnyWrite() {
        when(itemMapper.selectOne(any())).thenReturn(item("P0", "PENDING"));
        when(runMapper.selectOne(any())).thenReturn(run("IN_PROGRESS"));

        assertThatThrownBy(() -> service().markItemResult(
                ITEM_ID,
                new ReadinessItemResultRequest(" skipped ", null, "not needed")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("P0 验收项不能跳过");

        verify(itemMapper, never()).updateById(any(ReadinessItemEntity.class));
        verify(evidenceMapper, never()).selectList(any());
    }

    @Test
    void markItemResultRequiresReasonWhenSkippingP1() {
        when(itemMapper.selectOne(any())).thenReturn(item("P1", "PENDING"));
        when(runMapper.selectOne(any())).thenReturn(run("IN_PROGRESS"));

        assertThatThrownBy(() -> service().markItemResult(
                ITEM_ID,
                new ReadinessItemResultRequest("SKIPPED", null, " ")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("P1 验收项跳过原因不能为空");

        verify(itemMapper, never()).updateById(any(ReadinessItemEntity.class));
    }

    @Test
    void goDecisionRequiresPassedStatusBeforeScanningItems() {
        when(runMapper.selectOne(any())).thenReturn(run("IN_PROGRESS"));

        assertThatThrownBy(() -> service().decide(
                RUN_ID,
                new ReadinessDecisionRequest("GO", "BLOCKED", "not ready")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Go 决策只能标记为通过");

        verify(itemMapper, never()).selectCount(any());
        verify(runMapper, never()).updateById(any(ReadinessRunEntity.class));
    }

    @Test
    void goDecisionRejectsWhenAnyP0OrP1ItemIsNotPassed() {
        when(runMapper.selectOne(any())).thenReturn(run("IN_PROGRESS"));
        when(itemMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service().decide(
                RUN_ID,
                new ReadinessDecisionRequest("GO", "PASSED", "ready")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("存在未通过或未执行的 P0/P1 验收项，不能标记发布通过");

        verify(runMapper, never()).updateById(any(ReadinessRunEntity.class));
    }

    @Test
    void goDecisionNormalizesFieldsAndClosesRunWhenAllRequiredItemsPassed() {
        ReadinessRunEntity run = run("IN_PROGRESS");
        when(runMapper.selectOne(any())).thenReturn(run);
        when(itemMapper.selectCount(any())).thenReturn(0L);

        var response = service().decide(
                RUN_ID,
                new ReadinessDecisionRequest(" go ", " passed ", " ready to release ")
        );

        assertThat(response.decision()).isEqualTo("GO");
        assertThat(response.status()).isEqualTo("PASSED");
        assertThat(response.decisionComment()).isEqualTo("ready to release");
        assertThat(response.decidedBy()).isEqualTo(AUDIT.userId());
        assertThat(response.decidedTime()).isEqualTo(AUDIT.now());
        verify(runMapper).updateById(run);
    }

    @Test
    void noGoDecisionRequiresCommentAndAcceptsBlockedStatus() {
        when(runMapper.selectOne(any())).thenReturn(run("IN_PROGRESS"));

        assertThatThrownBy(() -> service().decide(
                RUN_ID,
                new ReadinessDecisionRequest("NO_GO", "BLOCKED", " ")
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No-Go 决策说明不能为空");
        verify(runMapper, never()).updateById(any(ReadinessRunEntity.class));

        ReadinessRunEntity secondRun = run("IN_PROGRESS");
        when(runMapper.selectOne(any())).thenReturn(secondRun);
        var response = service().decide(
                RUN_ID,
                new ReadinessDecisionRequest(" no_go ", " blocked ", " deployment unhealthy ")
        );

        assertThat(response.decision()).isEqualTo("NO_GO");
        assertThat(response.status()).isEqualTo("BLOCKED");
        assertThat(response.decisionComment()).isEqualTo("deployment unhealthy");
        verify(runMapper).updateById(secondRun);
    }

    @Test
    void recordPreflightEvidenceCreatesItemThenUpdatesResultAndEvidenceInInputOrder() {
        ReadinessRunEntity run = run("DRAFT");
        when(runMapper.selectOne(any())).thenReturn(run);
        when(itemMapper.insert(any(ReadinessItemEntity.class))).thenAnswer(invocation -> {
            ReadinessItemEntity item = invocation.getArgument(0);
            item.setId(ITEM_ID);
            return 1;
        });
        when(evidenceMapper.insert(any(ReadinessEvidenceEntity.class))).thenAnswer(invocation -> {
            ReadinessEvidenceEntity evidence = invocation.getArgument(0);
            evidence.setId(EVIDENCE_ID);
            return 1;
        });
        ReadinessPreflightResponse preflight = new ReadinessPreflightResponse(
                "WARN",
                AUDIT.now().minusMinutes(1),
                List.of(
                        new ReadinessPreflightItemResponse(
                                "NEGATIVE_INVENTORY", "FAIL", "P0", "negative balance", 2, List.of("A", "B")
                        ),
                        new ReadinessPreflightItemResponse(
                                "RECEIVABLE_SETTLEMENT_RANGE", "PASS", "P0", "settlement ok", 0, List.of()
                        )
                )
        );

        assertThat(service().recordPreflightEvidence(RUN_ID, preflight)).isSameAs(preflight);

        ArgumentCaptor<ReadinessItemEntity> itemCaptor = ArgumentCaptor.forClass(ReadinessItemEntity.class);
        verify(itemMapper).insert(itemCaptor.capture());
        ReadinessItemEntity item = itemCaptor.getValue();
        assertThat(item.getItemCode()).isEqualTo("MIGRATION_PREFLIGHT");
        assertThat(item.getItemName()).isEqualTo("迁移前健康检查");
        assertThat(item.getCategory()).isEqualTo("MIGRATION");
        assertThat(item.getPriority()).isEqualTo("P0");
        assertThat(item.getStatus()).isEqualTo("BLOCKED");
        assertThat(item.getActualResult()).isEqualTo("迁移前健康检查：WARN");
        assertThat(item.getFailureReason()).isEqualTo("NEGATIVE_INVENTORY=FAIL(2)");
        assertThat(item.getExecutedBy()).isEqualTo(AUDIT.userId());
        assertThat(item.getExecutedTime()).isEqualTo(AUDIT.now());

        ArgumentCaptor<ReadinessEvidenceEntity> evidenceCaptor =
                ArgumentCaptor.forClass(ReadinessEvidenceEntity.class);
        verify(evidenceMapper).insert(evidenceCaptor.capture());
        ReadinessEvidenceEntity evidence = evidenceCaptor.getValue();
        assertThat(evidence.getRunId()).isEqualTo(RUN_ID);
        assertThat(evidence.getItemId()).isEqualTo(ITEM_ID);
        assertThat(evidence.getEvidenceType()).isEqualTo("API");
        assertThat(evidence.getRequestMethod()).isEqualTo("POST");
        assertThat(evidence.getRequestUri()).isEqualTo("/api/system/readiness/runs/" + RUN_ID + "/preflight-evidence");
        assertThat(evidence.getBusinessType()).isEqualTo("READINESS_PREFLIGHT");
        assertThat(evidence.getBusinessId()).isEqualTo(RUN_ID);
        assertThat(evidence.getBusinessNo()).isEqualTo(run.getRunNo());
        assertThat(evidence.getSummary()).isEqualTo("迁移前健康检查：WARN");
        assertThat(evidence.getDetail()).isEqualTo("""
                NEGATIVE_INVENTORY [P0] FAIL count=2 negative balance sample=A | B
                RECEIVABLE_SETTLEMENT_RANGE [P0] PASS count=0 settlement ok""");
        assertThat(run.getStatus()).isEqualTo("IN_PROGRESS");

        InOrder order = inOrder(runMapper, itemMapper, evidenceMapper);
        order.verify(runMapper).selectOne(any());
        order.verify(itemMapper).selectOne(any());
        order.verify(itemMapper).insert(item);
        order.verify(itemMapper).updateById(item);
        order.verify(evidenceMapper).insert(evidence);
        order.verify(runMapper).updateById(run);
    }

    private ReadinessCommandService service() {
        ReadinessQueryService queryService = new ReadinessQueryService(
                runMapper,
                itemMapper,
                evidenceMapper,
                auditMetadataFactory
        );
        return new ReadinessCommandService(
                runMapper,
                itemMapper,
                evidenceMapper,
                auditMetadataFactory,
                queryService
        );
    }

    private static ReadinessRunEntity run(String status) {
        ReadinessRunEntity run = new ReadinessRunEntity();
        run.setId(RUN_ID);
        run.setCompanyId(AUDIT.companyId());
        run.setAccountBookId(AUDIT.accountBookId());
        run.setRunNo("RDY20260820141516123");
        run.setReleaseCommit("abc123");
        run.setEnvironment("PREPROD");
        run.setStatus(status);
        run.setDecision("PENDING");
        run.setStartedBy(AUDIT.userId());
        run.setStartedTime(AUDIT.now());
        run.setDeletedFlag(0);
        run.setCreatedBy(AUDIT.userId());
        run.setCreatedTime(AUDIT.now());
        run.setUpdatedBy(AUDIT.userId());
        run.setUpdatedTime(AUDIT.now());
        run.setVersion(0);
        return run;
    }

    private static ReadinessItemEntity item(String priority, String status) {
        ReadinessItemEntity item = new ReadinessItemEntity();
        item.setId(ITEM_ID);
        item.setCompanyId(AUDIT.companyId());
        item.setAccountBookId(AUDIT.accountBookId());
        item.setRunId(RUN_ID);
        item.setItemCode("AUTH_SMOKE");
        item.setItemName("登录冒烟");
        item.setCategory("AUTH");
        item.setPriority(priority);
        item.setStatus(status);
        item.setDeletedFlag(0);
        item.setCreatedBy(AUDIT.userId());
        item.setCreatedTime(AUDIT.now());
        item.setUpdatedBy(AUDIT.userId());
        item.setUpdatedTime(AUDIT.now());
        item.setVersion(0);
        return item;
    }

    private static ReadinessEvidenceEntity evidence() {
        ReadinessEvidenceEntity evidence = new ReadinessEvidenceEntity();
        evidence.setId(EVIDENCE_ID);
        evidence.setCompanyId(AUDIT.companyId());
        evidence.setAccountBookId(AUDIT.accountBookId());
        evidence.setRunId(RUN_ID);
        evidence.setItemId(ITEM_ID);
        evidence.setEvidenceType("API");
        evidence.setSummary("profile endpoint ok");
        evidence.setRecordedBy(AUDIT.userId());
        evidence.setRecordedTime(AUDIT.now());
        evidence.setDeletedFlag(0);
        evidence.setVersion(0);
        return evidence;
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

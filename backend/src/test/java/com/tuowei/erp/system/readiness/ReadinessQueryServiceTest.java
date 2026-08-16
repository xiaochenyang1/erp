package com.tuowei.erp.system.readiness;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.system.readiness.mapper.ReadinessEvidenceMapper;
import com.tuowei.erp.system.readiness.mapper.ReadinessItemMapper;
import com.tuowei.erp.system.readiness.mapper.ReadinessRunMapper;
import com.tuowei.erp.system.readiness.model.ReadinessEvidenceEntity;
import com.tuowei.erp.system.readiness.model.ReadinessItemEntity;
import com.tuowei.erp.system.readiness.model.ReadinessRunEntity;
import com.tuowei.erp.system.readiness.service.ReadinessQueryService;
import com.tuowei.erp.system.readiness.web.ReadinessItemResponse;
import com.tuowei.erp.system.readiness.web.ReadinessRunPageQuery;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadinessQueryServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9L,
            101L,
            202L,
            LocalDateTime.parse("2026-08-14T09:30:00")
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
    void stubAudit() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
    }

    @Test
    void listRunsNormalizesFiltersCapsPaginationAndScopesTenant() {
        when(runMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        LocalDateTime from = LocalDateTime.parse("2026-08-01T00:00:00");
        LocalDateTime to = LocalDateTime.parse("2026-08-14T23:59:59");
        ReadinessRunPageQuery query = new ReadinessRunPageQuery();
        query.setReleaseCommit(" abc1234 ");
        query.setEnvironment(" preprod ");
        query.setStatus(" in_progress ");
        query.setDecision(" pending ");
        query.setCreatedTimeFrom(from);
        query.setCreatedTimeTo(to);
        query.setPageNo(0);
        query.setPageSize(999);

        var response = service().listRuns(query);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Page<ReadinessRunEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ReadinessRunEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(runMapper).selectPage(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getCurrent()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(200);
        assertThat(response.pageNo()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(200);
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("deleted_flag")
                .contains("release_commit")
                .contains("environment")
                .contains("status")
                .contains("decision")
                .contains("created_time");
        assertThat(wrapperCaptor.getValue().getParamNameValuePairs().values())
                .contains(
                        AUDIT.companyId(),
                        AUDIT.accountBookId(),
                        "abc1234",
                        "PREPROD",
                        "IN_PROGRESS",
                        "PENDING",
                        from,
                        to
                );
    }

    @Test
    void detailHydratesItemsAndEvidenceInBatches() {
        ReadinessRunEntity run = run();
        ReadinessItemEntity first = item(2001L, "RELEASE_GATE");
        ReadinessItemEntity second = item(2002L, "AUTH_SMOKE");
        when(runMapper.selectOne(any())).thenReturn(run);
        when(itemMapper.selectList(any())).thenReturn(List.of(first, second));
        when(evidenceMapper.selectList(any())).thenReturn(List.of(
                evidence(3001L, first.getId(), "release-check passed"),
                evidence(3002L, second.getId(), "profile endpoint ok")
        ));

        var response = service().detail(run.getId());

        assertThat(response.run().runNo()).isEqualTo("RDY20260814093000000");
        assertThat(response.items()).extracting(ReadinessItemResponse::itemCode)
                .containsExactly("RELEASE_GATE", "AUTH_SMOKE");
        assertThat(response.items().get(0).evidence()).singleElement()
                .satisfies(item -> assertThat(item.summary()).isEqualTo("release-check passed"));
        assertThat(response.items().get(1).evidence()).singleElement()
                .satisfies(item -> assertThat(item.summary()).isEqualTo("profile endpoint ok"));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ReadinessEvidenceEntity>> evidenceQueryCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(evidenceMapper).selectList(evidenceQueryCaptor.capture());
        assertThat(evidenceQueryCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("run_id")
                .contains("item_id")
                .contains("recorded_time");
        assertThat(evidenceQueryCaptor.getValue().getParamNameValuePairs().values())
                .contains(run.getId(), first.getId(), second.getId());
    }

    @Test
    void detailSkipsEvidenceQueryWhenRunHasNoItems() {
        when(runMapper.selectOne(any())).thenReturn(run());
        when(itemMapper.selectList(any())).thenReturn(List.of());

        var response = service().detail(1001L);

        assertThat(response.items()).isEmpty();
        verifyNoInteractions(evidenceMapper);
    }

    @Test
    void detailRejectsRunOutsideCurrentTenant() {
        when(runMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service().detail(9999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("验收运行单不存在");
        verifyNoInteractions(itemMapper, evidenceMapper);
    }

    private ReadinessQueryService service() {
        return new ReadinessQueryService(runMapper, itemMapper, evidenceMapper, auditMetadataFactory);
    }

    private ReadinessRunEntity run() {
        ReadinessRunEntity entity = new ReadinessRunEntity();
        entity.setId(1001L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setRunNo("RDY20260814093000000");
        entity.setReleaseCommit("abc1234");
        entity.setReleaseVersion("1.0.0-rc1");
        entity.setEnvironment("PREPROD");
        entity.setStatus("IN_PROGRESS");
        entity.setDecision("PENDING");
        entity.setStartedBy(AUDIT.userId());
        entity.setStartedTime(AUDIT.now());
        entity.setCreatedTime(AUDIT.now());
        return entity;
    }

    private ReadinessItemEntity item(Long id, String itemCode) {
        ReadinessItemEntity entity = new ReadinessItemEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setRunId(1001L);
        entity.setItemCode(itemCode);
        entity.setItemName(itemCode);
        entity.setCategory("DEPLOYMENT");
        entity.setPriority("P0");
        entity.setStatus("PASSED");
        entity.setCreatedTime(AUDIT.now());
        return entity;
    }

    private ReadinessEvidenceEntity evidence(Long id, Long itemId, String summary) {
        ReadinessEvidenceEntity entity = new ReadinessEvidenceEntity();
        entity.setId(id);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setRunId(1001L);
        entity.setItemId(itemId);
        entity.setEvidenceType("API");
        entity.setSummary(summary);
        entity.setRecordedBy(AUDIT.userId());
        entity.setRecordedTime(AUDIT.now());
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

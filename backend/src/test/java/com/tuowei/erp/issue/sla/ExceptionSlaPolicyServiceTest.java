package com.tuowei.erp.issue.sla;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.tuowei.erp.common.security.AuditMetadata;
import com.tuowei.erp.common.security.AuditMetadataFactory;
import com.tuowei.erp.issue.model.ExceptionTicketEntity;
import com.tuowei.erp.issue.sla.mapper.ExceptionSlaPolicyMapper;
import com.tuowei.erp.issue.sla.model.ExceptionSlaPolicyEntity;
import com.tuowei.erp.issue.sla.service.ExceptionSlaEscalationPolicy;
import com.tuowei.erp.issue.sla.service.ExceptionSlaPolicyService;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyPageQuery;
import com.tuowei.erp.issue.sla.web.ExceptionSlaPolicyUpdateRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExceptionSlaPolicyServiceTest {

    private static final AuditMetadata AUDIT = new AuditMetadata(
            9001L,
            101L,
            202L,
            LocalDateTime.of(2026, 6, 30, 10, 0)
    );

    @Mock
    private AuditMetadataFactory auditMetadataFactory;

    @Mock
    private ExceptionSlaPolicyMapper policyMapper;

    @BeforeAll
    static void initTableInfo() {
        initTableInfo(ExceptionSlaPolicyEntity.class);
        initTableInfo(ExceptionTicketEntity.class);
    }

    @Test
    void listBootstrapsDefaultPoliciesForTenantWhenMissing() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(policyMapper.selectCount(any())).thenReturn(0L);
        when(policyMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<ExceptionSlaPolicyEntity> page = invocation.getArgument(0);
            page.setTotal(0);
            page.setRecords(List.of());
            return page;
        });

        service().list(new ExceptionSlaPolicyPageQuery());

        ArgumentCaptor<ExceptionSlaPolicyEntity> policyCaptor = ArgumentCaptor.forClass(ExceptionSlaPolicyEntity.class);
        verify(policyMapper, org.mockito.Mockito.times(8)).insert(policyCaptor.capture());
        assertThat(policyCaptor.getAllValues())
                .extracting(policy -> policy.getCategory() + ":" + policy.getPriority())
                .containsExactly(
                        "GENERAL:LOW",
                        "GENERAL:MEDIUM",
                        "GENERAL:HIGH",
                        "GENERAL:URGENT",
                        "LOW_STOCK:HIGH",
                        "PAYMENT_OVERDUE:MEDIUM",
                        "PAYMENT_OVERDUE:HIGH",
                        "SYSTEM_ERROR:MEDIUM"
                );
        assertThat(policyCaptor.getAllValues())
                .allSatisfy(policy -> {
                    assertThat(policy.getCompanyId()).isEqualTo(AUDIT.companyId());
                    assertThat(policy.getAccountBookId()).isEqualTo(AUDIT.accountBookId());
                    assertThat(policy.getEnabled()).isEqualTo(1);
                    assertThat(policy.getDeletedFlag()).isZero();
                });
    }

    @Test
    void listsPoliciesWithTenantScopedFilters() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(policyMapper.selectCount(any())).thenReturn(1L);
        ExceptionSlaPolicyPageQuery query = new ExceptionSlaPolicyPageQuery();
        query.setCategory("payment_overdue");
        query.setPriority("high");
        query.setEnabled(true);
        query.setPageNo(1);
        query.setPageSize(20);
        when(policyMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<ExceptionSlaPolicyEntity> page = invocation.getArgument(0);
            page.setTotal(1);
            page.setRecords(List.of(policy("PAYMENT_OVERDUE", "HIGH", 12, true, "URGENT")));
            return page;
        });

        var response = service().list(query);

        assertThat(response.total()).isEqualTo(1);
        assertThat(response.records().get(0).category()).isEqualTo("PAYMENT_OVERDUE");

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<LambdaQueryWrapper<ExceptionSlaPolicyEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(policyMapper).selectPage(any(), wrapperCaptor.capture());
        assertThat(wrapperCaptor.getValue().getSqlSegment().toLowerCase(Locale.ROOT))
                .contains("company_id")
                .contains("account_book_id")
                .contains("category")
                .contains("priority")
                .contains("enabled");
    }

    @Test
    void updatesPolicyConfigurationAndAuditFields() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(policyMapper.selectOne(any())).thenReturn(policy("GENERAL", "HIGH", 24, true, "URGENT"));
        ExceptionSlaPolicyUpdateRequest request = new ExceptionSlaPolicyUpdateRequest();
        request.setDueHours(36);
        request.setEscalationEnabled(false);
        request.setEscalateToPriority("medium");
        request.setEnabled(false);
        request.setRemark("高优先级给 36 小时");

        var response = service().update(1001L, request);

        assertThat(response.dueHours()).isEqualTo(36);
        assertThat(response.escalationEnabled()).isFalse();
        assertThat(response.escalateToPriority()).isEqualTo("MEDIUM");
        assertThat(response.enabled()).isFalse();
        ArgumentCaptor<ExceptionSlaPolicyEntity> entityCaptor = ArgumentCaptor.forClass(ExceptionSlaPolicyEntity.class);
        verify(policyMapper).updateById(entityCaptor.capture());
        assertThat(entityCaptor.getValue().getUpdatedBy()).isEqualTo(AUDIT.userId());
        assertThat(entityCaptor.getValue().getUpdatedTime()).isEqualTo(AUDIT.now());
    }

    @Test
    void rejectsInvalidDueHours() {
        when(auditMetadataFactory.current()).thenReturn(AUDIT);
        when(policyMapper.selectOne(any())).thenReturn(policy("GENERAL", "HIGH", 24, true, "URGENT"));
        ExceptionSlaPolicyUpdateRequest request = new ExceptionSlaPolicyUpdateRequest();
        request.setDueHours(0);

        assertThatThrownBy(() -> service().update(1001L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SLA");
    }

    @Test
    void resolveDueTimeUsesExactPolicyBeforeGeneralFallback() {
        when(policyMapper.selectOne(any()))
                .thenReturn(policy("LOW_STOCK", "HIGH", 12, true, "URGENT"));

        LocalDateTime dueTime = service().resolveDueTime("low_stock", "high", AUDIT.now(), AUDIT);

        assertThat(dueTime).isEqualTo(AUDIT.now().plusHours(12));
    }

    @Test
    void resolveDueTimeFallsBackToGeneralPolicy() {
        when(policyMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(policy("GENERAL", "HIGH", 18, true, "URGENT"));

        LocalDateTime dueTime = service().resolveDueTime("quality_issue", "high", AUDIT.now(), AUDIT);

        assertThat(dueTime).isEqualTo(AUDIT.now().plusHours(18));
    }

    @Test
    void resolveEscalationReturnsConfiguredTargetPriority() {
        when(policyMapper.selectOne(any()))
                .thenReturn(policy("LOW_STOCK", "HIGH", 24, true, "URGENT"));

        ExceptionSlaEscalationPolicy escalation = service().resolveEscalation(ticket("LOW_STOCK", "HIGH"), AUDIT);

        assertThat(escalation.enabled()).isTrue();
        assertThat(escalation.targetPriority()).isEqualTo("URGENT");
    }

    @Test
    void resolveEscalationCanDisableEscalation() {
        when(policyMapper.selectOne(any()))
                .thenReturn(policy("LOW_STOCK", "HIGH", 24, false, "URGENT"));

        ExceptionSlaEscalationPolicy escalation = service().resolveEscalation(ticket("LOW_STOCK", "HIGH"), AUDIT);

        assertThat(escalation.enabled()).isFalse();
        assertThat(escalation.targetPriority()).isEqualTo("HIGH");
    }

    private ExceptionSlaPolicyService service() {
        return new ExceptionSlaPolicyService(auditMetadataFactory, policyMapper);
    }

    private static ExceptionSlaPolicyEntity policy(
            String category,
            String priority,
            Integer dueHours,
            boolean escalationEnabled,
            String escalateToPriority
    ) {
        ExceptionSlaPolicyEntity entity = new ExceptionSlaPolicyEntity();
        entity.setId(1001L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setCategory(category);
        entity.setPriority(priority);
        entity.setDueHours(dueHours);
        entity.setEscalationEnabled(escalationEnabled ? 1 : 0);
        entity.setEscalateToPriority(escalateToPriority);
        entity.setEnabled(1);
        entity.setRemark("策略说明");
        entity.setDeletedFlag(0);
        entity.setCreatedBy(AUDIT.userId());
        entity.setCreatedTime(AUDIT.now());
        entity.setUpdatedBy(AUDIT.userId());
        entity.setUpdatedTime(AUDIT.now());
        entity.setVersion(0);
        return entity;
    }

    private static ExceptionTicketEntity ticket(String category, String priority) {
        ExceptionTicketEntity entity = new ExceptionTicketEntity();
        entity.setId(2001L);
        entity.setCompanyId(AUDIT.companyId());
        entity.setAccountBookId(AUDIT.accountBookId());
        entity.setCategory(category);
        entity.setPriority(priority);
        entity.setStatus("OPEN");
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
